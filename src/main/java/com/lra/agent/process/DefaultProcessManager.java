package com.lra.agent.process;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.TimeUnit;
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
    private static final int DEFAULT_MAX_RESTART_ATTEMPTS = 3;

    private final ProcessLauncher launcher;
    private final ObjectMapper objectMapper;
    private final Map<String, ManagedProcess> processes = new ConcurrentHashMap<>();

    public DefaultProcessManager(ProcessLauncher launcher, ObjectMapper objectMapper) {
        this.launcher = launcher;
        this.objectMapper = objectMapper;
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
            mp.setState(parseState(definition.getState()));
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
        List<String> command = buildCommand(definition);
        File workingDir = definition.getWorkingDirectory() == null ? null
            : new File(definition.getWorkingDirectory());
        Map<String, String> env = parseEnv(definition.getEnvVars());

        try {
            Process process = launcher.launch(command, workingDir, env);
            mp.setProcess(process);
            mp.setStartTime(Instant.now());
            mp.setPid(toInt(process.pid()));
            // RUNNING is confirmed here by liveness; health-gated confirmation is
            // performed later by the monitor/HealthChecker (§7.3 step 4).
            if (process.isAlive()) {
                transition(mp, ProcessState.RUNNING);
            } else {
                log.error("Process {} exited immediately (exit={})", modelId, safeExitValue(process));
                transition(mp, ProcessState.CRASHED);
            }
        } catch (Exception e) {
            log.error("Failed to start process {}: {}", modelId, e.getMessage(), e);
            transition(mp, ProcessState.STOPPED);
        }
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
            stopAliveProcess(mp, strategy);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while stopping {}", modelId);
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
        stopProcess(modelId, StopStrategy.GRACEFUL);
        sleepSeconds(restartDelaySec(definition));
        startProcess(modelId, definition);
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

    private List<String> buildCommand(ModelProcess definition) {
        List<String> command = new ArrayList<>();
        if (definition.getExecutablePath() == null || definition.getExecutablePath().isBlank()) {
            throw new IllegalArgumentException("executablePath is required for " + definition.getProcessId());
        }
        command.add(definition.getExecutablePath());
        command.addAll(parseArgs(definition.getCommandArgs()));
        return command;
    }

    private List<String> parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Invalid command_args JSON, ignoring: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, String> parseEnv(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.error("Invalid env_vars JSON, ignoring: {}", e.getMessage());
            return Map.of();
        }
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

    private static Integer safeExitValue(Process process) {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException e) {
            return null;
        }
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
