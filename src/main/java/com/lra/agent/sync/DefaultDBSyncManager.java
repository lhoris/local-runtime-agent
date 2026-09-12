package com.lra.agent.sync;

import com.lra.agent.health.HealthChecker;
import com.lra.agent.parameter.ParameterManager;
import com.lra.agent.process.ProcessManager;
import com.lra.agent.process.ProcessStatus;
import com.lra.agent.process.StopStrategy;
import com.lra.common.enums.CommandStatus;
import com.lra.common.enums.CommandType;
import com.lra.db.entity.ModelProcess;
import com.lra.db.entity.Command;
import com.lra.db.entity.ExecutionLog;
import com.lra.db.entity.ProcessConfig;
import com.lra.db.repository.ModelProcessRepository;
import com.lra.db.repository.CommandRepository;
import com.lra.db.repository.ExecutionLogRepository;
import com.lra.db.repository.ProcessConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default polling-based synchronizer between the agent and the central database
 * (ARCHITECTURE.md §4.3, §7.2, §7.3).
 *
 * <p>{@link HealthChecker} and {@link ParameterManager} are optional dependencies:
 * the HEALTH_CHECK and PARAM_UPDATE command types are only serviceable once those
 * components are present in the context; the core START/STOP/RESTART flow works
 * regardless.
 */
@Service
public class DefaultDBSyncManager implements DBSyncManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultDBSyncManager.class);
    private static final int PREVIEW_LIMIT = 1000;

    private final String agentId;
    private final ProcessManager processManager;
    private final ProcessConfigRepository processConfigRepository;
    private final ModelProcessRepository modelProcessRepository;
    private final CommandRepository commandRepository;
    private final ExecutionLogRepository executionLogRepository;

    private HealthChecker healthChecker;
    private ParameterManager parameterManager;

    public DefaultDBSyncManager(@Value("${agent.id}") String agentId,
                                ProcessManager processManager,
                                ProcessConfigRepository processConfigRepository,
                                ModelProcessRepository modelProcessRepository,
                                CommandRepository commandRepository,
                                ExecutionLogRepository executionLogRepository) {
        this.agentId = agentId;
        this.processManager = processManager;
        this.processConfigRepository = processConfigRepository;
        this.modelProcessRepository = modelProcessRepository;
        this.commandRepository = commandRepository;
        this.executionLogRepository = executionLogRepository;
    }

    @Autowired(required = false)
    public void setHealthChecker(HealthChecker healthChecker) {
        this.healthChecker = healthChecker;
    }

    @Autowired(required = false)
    public void setParameterManager(ParameterManager parameterManager) {
        this.parameterManager = parameterManager;
    }

    @Override
    @Transactional
    public void syncAgentStatus() {
        List<ProcessStatus> statuses = processManager.getAllStatus();
        Instant now = Instant.now();
        for (ProcessStatus status : statuses) {
            ModelProcess record = new ModelProcess();
            record.setProcessId(status.getModelId());
            record.setAgentId(agentId);
            record.setState(status.getState() != null ? status.getState().toString() : null);
            record.setPid(status.getPid());
            record.setCpuPercent(status.getCpuPercent());
            record.setMemoryMb(status.getMemoryMb());
            record.setUptimeSec(status.getUptimeSec());
            record.setLastHealthCheck(now);
            record.setLastHeartbeat(now);
            modelProcessRepository.save(record);
        }
    }

    @Override
    public void pollPendingCommands() {
        List<Command> commands = commandRepository.findByAgentIdAndCommandStatus(
                agentId, CommandStatus.PENDING.name());

        for (Command cmd : commands) {
            long startMillis = System.currentTimeMillis();
            try {
                ExecutionResult result = executeCommand(cmd);
                cmd.setCommandStatus(CommandStatus.COMPLETED.name());
                cmd.setProcessedAt(Instant.now());
                commandRepository.save(cmd);
                logExecution(result);
            } catch (Exception e) {
                log.warn("Command {} ({}) failed: {}",
                        cmd.getCommandId(), cmd.getCommandType(), e.getMessage());
                cmd.setCommandStatus(CommandStatus.FAILED.name());
                cmd.setProcessedAt(Instant.now());
                cmd.setFailedReason(e.getMessage());
                commandRepository.save(cmd);

                int durationSec = (int) ((System.currentTimeMillis() - startMillis) / 1000);
                logExecution(ExecutionResult.failure(
                        cmd.getCommandId(), cmd.getProcessId(),
                        parseCommandTypeOrNull(cmd.getCommandType()), e.getMessage(), durationSec));
            }
        }
    }

    @Override
    public void logExecution(ExecutionResult result) {
        ExecutionLog entry = new ExecutionLog();
        entry.setLogId(UUID.randomUUID().toString());
        entry.setAgentId(agentId);
        entry.setProcessId(result.getModelId());
        entry.setCommandType(result.getCommandType() != null ? result.getCommandType().name() : null);
        entry.setExecutionStatus(result.getStatus() != null ? result.getStatus().name() : null);
        entry.setExitCode(result.getExitCode());
        entry.setStdoutPreview(truncate(result.getStdoutPreview()));
        entry.setStderrPreview(truncate(result.getStderrPreview()));
        entry.setDurationSec(result.getDurationSec());
        executionLogRepository.save(entry);
    }

    private ExecutionResult executeCommand(Command cmd) throws Exception {
        long startMillis = System.currentTimeMillis();
        CommandType type = parseCommandType(cmd.getCommandType());
        String modelId = cmd.getProcessId();

        switch (type) {
            case START -> processManager.startProcess(modelId, requireConfig(modelId));
            case STOP -> processManager.stopProcess(modelId, StopStrategy.GRACEFUL);
            case RESTART -> processManager.restartProcess(modelId);
            case PARAM_UPDATE -> {
                if (parameterManager == null) {
                    throw new IllegalStateException("ParameterManager is not available");
                }
                Map<String, Object> parameters = cmd.getParameters();
                if (parameters != null) {
                    for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                        String value = entry.getValue() != null ? entry.getValue().toString() : null;
                        parameterManager.updateParameter(modelId, entry.getKey(), value);
                    }
                }
                processManager.restartProcess(modelId);
            }
            case HEALTH_CHECK -> {
                if (healthChecker == null) {
                    throw new IllegalStateException("HealthChecker is not available");
                }
                healthChecker.checkHealth(modelId);
            }
        }

        int durationSec = (int) ((System.currentTimeMillis() - startMillis) / 1000);
        return ExecutionResult.success(cmd.getCommandId(), modelId, type, durationSec);
    }

    private CommandType parseCommandType(String raw) {
        try {
            return CommandType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown command type: " + raw);
        }
    }

    private CommandType parseCommandTypeOrNull(String raw) {
        try {
            return CommandType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private ProcessConfig requireConfig(String modelId) {
        return processConfigRepository.findById(modelId)
                .orElseThrow(() -> new IllegalStateException(
                        "No process config found for model: " + modelId));
    }

    private Map<String, Object> snapshotStatus() {
        List<Map<String, Object>> processes = new ArrayList<>();
        for (ProcessStatus status : processManager.getAllStatus()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("modelId", status.getModelId());
            entry.put("state", status.getState() != null ? status.getState().toString() : null);
            entry.put("pid", status.getPid());
            entry.put("cpuPercent", status.getCpuPercent());
            entry.put("memoryMb", status.getMemoryMb());
            entry.put("uptimeSec", status.getUptimeSec());
            processes.add(entry);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("agentId", agentId);
        snapshot.put("processes", processes);
        return snapshot;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= PREVIEW_LIMIT ? value : value.substring(0, PREVIEW_LIMIT);
    }
}
