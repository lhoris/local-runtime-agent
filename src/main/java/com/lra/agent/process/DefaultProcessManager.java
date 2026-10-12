package com.lra.agent.process;

import com.lra.common.enums.ProcessState;
import com.lra.db.entity.ModelProcess;
import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link ProcessManager} backed by {@link ProcessLauncher}/{@link ProcessBuilder}.
 * Tracks each managed process in memory; state persistence is the
 * DBSyncManager's responsibility (ARCHITECTURE.md §4.3).
 */
@Component
public class DefaultProcessManager implements ProcessManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultProcessManager.class);

    /** Grace period before a GRACEFUL stop escalates to a forceful kill. */
    static final long GRACEFUL_TIMEOUT_SEC = 15;
    private static final long PYTHON_DISCOVERY_TIMEOUT_SEC = 15;
    private static final int DEFAULT_MAX_RESTART_ATTEMPTS = 3;

    private final ProcessLauncher launcher;
    private final Map<String, ManagedProcess> processes = new ConcurrentHashMap<>();

    public DefaultProcessManager(ProcessLauncher launcher) {
        this.launcher = launcher;
    }

    @Override
    public ProcessStatus checkStatus(ModelProcess definition) {
        if (definition == null || definition.getProcessId() == null || definition.getProcessId().isBlank()) {
            throw new IllegalArgumentException("process definition must have a processId");
        }

        String modelId = definition.getProcessId();
        ManagedProcess mp = processes.computeIfAbsent(modelId, id -> new ManagedProcess(id, definition));
        mp.setDefinition(definition);

        if (mp.getProcess() == null) {
            mp.setPid(definition.getPid());
            if (mp.getPid() == null) {
                log.info("PID가 없어 {} 상태를 STOPPED로 판단", modelId);
                mp.setState(ProcessState.STOPPED);
            } else {
                mp.setState(parseState(definition.getState()));
            }
        }

        boolean alive = mp.isAlive();
        log.info("PID 생존 여부 확인: modelProcessId={}, pid={}, alive={}, 현재상태={}",
                modelId, mp.getPid(), alive, mp.getState());

        if (mp.getProcess() == null && alive && mp.getState() != ProcessState.RUNNING) {
                log.info("DB에 저장된 PID {}가 살아 있어 {} 상태를 RUNNING으로 갱신", mp.getPid(), modelId);
                transition(mp, ProcessState.RUNNING);
        }
        refresh(mp);
        return toStatus(mp);
    }

    @Override
    public void reconcileDefinitions(List<ModelProcess> definitions) {
        Set<String> configuredIds = definitions.stream()
            .map(ModelProcess::getProcessId)
            .filter(id -> id != null && !id.isBlank())
            .collect(java.util.stream.Collectors.toSet());

        for (String modelId : new ArrayList<>(processes.keySet())) {
            if (!configuredIds.contains(modelId)) {
                log.info("Removing process {} because it is no longer configured for this agent", modelId);
                stopProcess(modelId, StopStrategy.GRACEFUL);
                processes.remove(modelId);
            }
        }
    }

    @Override
    public void startProcess(String modelId, ModelProcess definition) {
        if (definition == null) {
            throw new IllegalArgumentException("process definition must not be null for " + modelId);
        }
        ManagedProcess mp = processes.computeIfAbsent(modelId, id -> new ManagedProcess(id, definition));
        mp.setDefinition(definition);

        if (mp.isAlive()) {
            log.warn("startProcess ignored, {} already running (pid={})", modelId, mp.getPid());
            return;
        }

        transition(mp, ProcessState.STARTING);
        try {
            log.info("START 명령 실행: modelProcessId={}, run.bat 호출", modelId);
            Process process = runControlScript(definition, "start.bat");
            attachStartedPythonProcess(mp, definition, process);
            // RUNNING is confirmed here by liveness; health-gated confirmation is
            // performed later by the monitor/HealthChecker (§7.3 step 4).
            if (mp.isAlive()) {
                transition(mp, ProcessState.RUNNING);
            } else {
                log.error("Process {} exited immediately (pid={})", modelId, mp.getPid());
                transition(mp, ProcessState.CRASHED);
            }
        } catch (Exception e) {
            log.error("Failed to start process {}: {}", modelId, e.getMessage(), e);
            transition(mp, ProcessState.STOPPED);
            throw new IllegalStateException("START failed for " + modelId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void attachExistingProcess(String modelId, ModelProcess definition) {
        if (definition == null) {
            throw new IllegalArgumentException("process definition must not be null for " + modelId);
        }

        ManagedProcess mp = processes.computeIfAbsent(modelId, id -> new ManagedProcess(id, definition));
        mp.setDefinition(definition);
        if (mp.isAlive()) {
            log.info("Process {} is already attached and alive (pid={})", modelId, mp.getPid());
            return;
        }

        java.util.Optional<ProcessHandle> match = findMatchingProcess(definition);
        if (match.isEmpty()) {
            throw new IllegalStateException(
                "No running local process matched the configured project directory for " + modelId);
        }

        ProcessHandle handle = match.get();
        mp.setProcess(null);
        mp.setPid(toInt(handle.pid()));
        mp.setStartTime(handle.info().startInstant().orElse(Instant.now()));
        transition(mp, ProcessState.RUNNING);
        log.info("Attached existing process {} to {}", handle.pid(), modelId);
    }

    @Override
    public void stopProcess(String modelId, StopStrategy strategy) {
        ManagedProcess mp = processes.get(modelId);
        if (mp == null || !mp.isAlive()) {
            log.warn("stopProcess ignored, {} is not managed", modelId);
            return;
        }
        transition(mp, ProcessState.STOPPING);
        try {
            if (mp.getDefinition() != null) {
                log.info("STOP 명령 실행: modelProcessId={}, stop.bat 호출", modelId);
                runControlScript(mp.getDefinition(), "stop.bat");
            } else {
                stopAliveProcess(mp, strategy);
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to stop process {}: {}", modelId, e.getMessage());
        } finally {
            transition(mp, ProcessState.STOPPED);
        }
    }

    @Override
    public void restartProcess(String modelId) {
        ManagedProcess mp = processes.get(modelId);
        if (mp == null) {
            log.warn("restartProcess ignored, {} is not managed", modelId);
            return;
        }
        ModelProcess definition = mp.getDefinition();
        if (definition == null) {
            log.warn("restartProcess ignored, {} has no definition", modelId);
            return;
        }
        try {
            log.info("RESTART 명령 실행: modelProcessId={}, restart.bat 호출", modelId);
            Process process = runControlScript(definition, "restart.bat");
            attachStartedPythonProcess(mp, definition, process);
            transition(mp, mp.isAlive() ? ProcessState.RUNNING : ProcessState.CRASHED);
        } catch (Exception e) {
            log.error("Failed to restart process {}: {}", modelId, e.getMessage(), e);
            transition(mp, ProcessState.STOPPED);
            throw new IllegalStateException("RESTART failed for " + modelId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void executeControlCommand(String modelId, ModelProcess definition, String commandType) {
        if (definition == null) {
            throw new IllegalArgumentException("process definition must not be null for " + modelId);
        }
        String normalized = normalizeCommandType(commandType);
        switch (normalized.toUpperCase(java.util.Locale.ROOT)) {
            case "START" -> startProcess(modelId, definition);
            case "STOP" -> stopProcess(modelId, StopStrategy.GRACEFUL);
            case "RESTART" -> restartProcess(modelId);
            default -> {
                log.info("Custom control command: modelProcessId={}, script={}.bat", modelId, normalized);
                try {
                    runControlScript(definition, normalized + ".bat");
                } catch (Exception ex) {
                    if (ex instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    throw new IllegalStateException("Custom command failed for " + modelId + ": " + ex.getMessage(), ex);
                }
            }
        }
    }

    @Override
    public ProcessStatus getStatus(String modelId) {
        ManagedProcess mp = processes.get(modelId);
        if (mp == null) {
            return null;
        }
        refresh(mp);
        return toStatus(mp);
    }

    @Override
    public List<ProcessStatus> getAllStatus() {
        List<ProcessStatus> all = new ArrayList<>(processes.size());
        for (ManagedProcess mp : processes.values()) {
            refresh(mp);
            all.add(toStatus(mp));
        }
        return all;
    }

    // --- package-private hooks for DefaultProcessMonitor -------------------

    Collection<ManagedProcess> managedProcesses() {
        return processes.values();
    }

    /**
     * Reconcile in-memory state with actual process liveness. Detecting a
     * RUNNING/STARTING process that has died increments the crash count exactly
     * once (the CRASHED guard makes this idempotent across repeated calls).
     */
    void refresh(ManagedProcess mp) {
        ProcessState state = mp.getState();
        if ((state == ProcessState.RUNNING || state == ProcessState.STARTING) && !mp.isAlive()) {
            mp.incrementCrashCount();
            log.warn("Process {} is no longer alive (pid={}), marking CRASHED (crashCount={})",
                mp.getModelId(), mp.getPid(), mp.getCrashCount());
            transition(mp, ProcessState.CRASHED);
        }
    }

    ProcessStatus toStatus(ManagedProcess mp) {
        ProcessStatus status = new ProcessStatus(mp.getModelId(), mp.getState());
        status.setPid(mp.getPid());
        status.setUptimeSec(mp.isAlive() ? mp.uptimeSec() : 0L);
        status.setLastUpdate(LocalDateTime.now());
        // cpuPercent/memoryMb require a platform-specific collector; left null for
        // the MVP (§11: MVP health check is PID existence only).
        return status;
    }

    static int maxRestartAttempts(ModelProcess definition) {
        Integer max = definition == null ? null : definition.getMaxRestartAttempts();
        return max == null ? DEFAULT_MAX_RESTART_ATTEMPTS : max;
    }

    private static int restartDelaySec(ModelProcess definition) {
        Integer delay = definition == null ? null : definition.getRestartDelaySec();
        return delay == null ? 0 : delay;
    }

    // --- helpers -----------------------------------------------------------

    private File projectDirectory(ModelProcess definition) {
        if (definition.getPythonProjectPath() == null || definition.getPythonProjectPath().isBlank()) {
            throw new IllegalArgumentException("pythonProjectPath is required for " + definition.getProcessId());
        }
        File directory = new File(definition.getPythonProjectPath());
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("pythonProjectPath must be a directory: " + directory);
        }
        return directory;
    }

    private List<String> controlScriptCommand(String scriptName) {
        return List.of("cmd.exe", "/c", scriptName);
    }

    private String normalizeCommandType(String commandType) {
        String normalized = commandType == null ? "" : commandType.trim().toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9_-]{0,19}")) {
            throw new IllegalArgumentException("Invalid command type: " + commandType);
        }
        return normalized;
    }

    private Process runControlScript(ModelProcess definition, String scriptName) throws Exception {
        File directory = projectDirectory(definition);
        File script = new File(directory, scriptName);
        if (!script.isFile()) {
            throw new IllegalStateException("Required control script is missing: " + script);
        }
        log.info("제어 스크립트 실행: script={}, workingDirectory={}", script.getName(), directory);
        return launcher.launch(controlScriptCommand(scriptName), directory, Map.of());
    }

    private java.util.Optional<ProcessHandle> findMatchingProcess(ModelProcess definition) {
        String projectPath = normalize(definition.getPythonProjectPath());
        return ProcessHandle.allProcesses()
            .filter(handle -> java.util.Arrays.stream(handle.info().arguments().orElse(new String[0]))
                .map(this::normalize)
                .anyMatch(argument -> argument.contains(projectPath)))
            .findFirst();
    }

    /** Store the Python child PID instead of the cmd.exe wrapper PID. */
    private void attachStartedPythonProcess(ManagedProcess managed,
                                            ModelProcess definition,
                                            Process controlProcess) {
        java.util.Optional<ProcessHandle> python = findPythonProcess(definition, controlProcess);
        if (python.isPresent()) {
            ProcessHandle handle = python.get();
            managed.setProcess(null);
            managed.setPid(toInt(handle.pid()));
            managed.setStartTime(handle.info().startInstant().orElse(Instant.now()));
            log.info("실제 Python 프로세스 연결: modelProcessId={}, pythonPid={}, cmdPid={}",
                    managed.getModelId(), handle.pid(), controlProcess.pid());
            return;
        }

        String command = ProcessHandle.of(controlProcess.pid())
                .flatMap(handle -> handle.info().command())
                .orElse("");
        if (command.isBlank() || !normalize(command).contains("cmd.exe")) {
            managed.setProcess(controlProcess);
            managed.setPid(toInt(controlProcess.pid()));
            managed.setStartTime(Instant.now());
            return;
        }
        throw new IllegalStateException(
                "start.bat started cmd.exe, but no Python process was found for project: "
                        + definition.getPythonProjectPath());
    }

    private java.util.Optional<ProcessHandle> findPythonProcess(ModelProcess definition,
                                                                  Process controlProcess) {
        String projectPath = normalize(definition.getPythonProjectPath());
        ProcessHandle controlHandle = controlProcess.toHandle();
        if (controlHandle == null) {
            return java.util.Optional.empty();
        }
        int attempts = (int) (PYTHON_DISCOVERY_TIMEOUT_SEC * 10);
        for (int attempt = 0; attempt < attempts; attempt++) {
            java.util.Optional<ProcessHandle> child = findDescendant(controlHandle);
            if (child.isPresent()) {
                return child;
            }
            java.util.Optional<ProcessHandle> matching = findPythonByProjectPath(projectPath);
            if (matching.isPresent()) {
                return matching;
            }
            sleepMillis(100);
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<ProcessHandle> findPythonByProjectPath(String projectPath) {
        return ProcessHandle.allProcesses()
                .filter(handle -> isPython(handle)
                        && java.util.Arrays.stream(handle.info().arguments().orElse(new String[0]))
                        .map(this::normalize)
                        .anyMatch(argument -> argument.contains(projectPath)))
                .findFirst();
    }

    private java.util.Optional<ProcessHandle> findDescendant(ProcessHandle parent) {
        for (ProcessHandle child : parent.children().toList()) {
            if (isPython(child)) {
                return java.util.Optional.of(child);
            }
            java.util.Optional<ProcessHandle> descendant = findDescendant(child);
            if (descendant.isPresent()) {
                return descendant;
            }
        }
        return java.util.Optional.empty();
    }

    private void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isPython(ProcessHandle handle) {
        return handle.info().command()
                .map(this::normalize)
                .map(command -> command.endsWith("python.exe") || command.endsWith("python3.exe"))
                .orElse(false);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
    }


    void transition(ManagedProcess mp, ProcessState next) {
        ProcessState prev = mp.getState();
        if (prev != next) {
            log.info("Process {} state {} -> {}", mp.getModelId(), prev, next);
            mp.setState(next);
        }
    }

    private void sleepSeconds(int seconds) {
        if (seconds <= 0) {
            return;
        }
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Integer toInt(long pid) {
        return pid > Integer.MAX_VALUE ? null : (int) pid;
    }

    private void stopAliveProcess(ManagedProcess mp, StopStrategy strategy) throws InterruptedException {
        Process process = mp.getProcess();
        if (process != null) {
            if (strategy == StopStrategy.FORCEFUL) {
                process.destroyForcibly();
                process.waitFor(GRACEFUL_TIMEOUT_SEC, TimeUnit.SECONDS);
                return;
            }

            process.destroy();
            boolean exited = process.waitFor(GRACEFUL_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!exited) {
                log.warn("Graceful stop timed out for {}, escalating to forceful kill", mp.getModelId());
                process.destroyForcibly();
                process.waitFor(GRACEFUL_TIMEOUT_SEC, TimeUnit.SECONDS);
            }
            return;
        }

        Integer pid = mp.getPid();
        if (pid == null) {
            return;
        }
        ProcessHandle.of(pid).ifPresent(handle -> {
            if (strategy == StopStrategy.FORCEFUL) {
                handle.destroyForcibly();
            } else {
                handle.destroy();
                try {
                    handle.onExit().get(GRACEFUL_TIMEOUT_SEC, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException | TimeoutException e) {
                    log.warn("Graceful stop timed out for {}, escalating to forceful kill", mp.getModelId());
                    handle.destroyForcibly();
                }
            }
        });
    }

    private ProcessState parseState(String raw) {
        if (raw == null || raw.isBlank()) {
            return ProcessState.STOPPED;
        }
        try {
            return ProcessState.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown stored process state '{}', treating as STOPPED", raw);
            return ProcessState.STOPPED;
        }
    }
}
