package com.lra.agent.sync;

import com.lra.agent.health.HealthChecker;
import com.lra.agent.identity.AgentIdentityResolver;
import com.lra.agent.parameter.ParameterManager;
import com.lra.agent.process.ProcessManager;
import com.lra.agent.process.ProcessStatus;
import com.lra.agent.process.StopStrategy;
import com.lra.common.enums.CommandStatus;
import com.lra.common.enums.CommandType;
import com.lra.db.entity.Agent;
import com.lra.db.entity.ModelProcess;
import com.lra.db.entity.Command;
import com.lra.db.entity.ModelCommand;
import com.lra.db.entity.ExecutionLog;
import com.lra.db.repository.ModelProcessRepository;
import com.lra.db.repository.CommandRepository;
import com.lra.db.repository.ModelCommandRepository;
import com.lra.db.repository.ExecutionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.sql.Timestamp;
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

    private static final ZoneId DATABASE_ZONE = ZoneId.of("Asia/Seoul");

    private static final Logger log = LoggerFactory.getLogger(DefaultDBSyncManager.class);
    private static final int PREVIEW_LIMIT = 1000;

    private final AgentIdentityResolver agentIdentityResolver;
    private final ProcessManager processManager;
    private final ModelProcessRepository modelProcessRepository;
    private final CommandRepository commandRepository;
    private final ModelCommandRepository modelCommandRepository;
    private final ExecutionLogRepository executionLogRepository;

    private HealthChecker healthChecker;
    private ParameterManager parameterManager;

    public DefaultDBSyncManager(AgentIdentityResolver agentIdentityResolver,
                                ProcessManager processManager,
                                ModelProcessRepository modelProcessRepository,
                                CommandRepository commandRepository,
                                ModelCommandRepository modelCommandRepository,
                                ExecutionLogRepository executionLogRepository) {
        this.agentIdentityResolver = agentIdentityResolver;
        this.processManager = processManager;
        this.modelProcessRepository = modelProcessRepository;
        this.commandRepository = commandRepository;
        this.modelCommandRepository = modelCommandRepository;
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
        String agentId = currentAgentIdOrNull();
        if (agentId == null) {
            log.warn("상태 동기화 중단: 현재 로컬 Agent를 찾을 수 없음");
            return;
        }
        List<ProcessStatus> statuses = processManager.getAllStatus();
        log.info("프로세스 상태 DB 동기화 시작: agent={}, processCount={}", agentId, statuses.size());
        Timestamp now = databaseNow();
        for (ProcessStatus status : statuses) {
            ModelProcess record = modelProcessRepository.findById(parseModelProcessId(status.getModelId()))
                    .orElseGet(ModelProcess::new);
            record.setProcessId(status.getModelId());
            record.setAgentId(agentId);
            record.setState(status.getState() != null ? status.getState().toString() : null);
            record.setPid(status.getPid());
            record.setHealthStatus(status.getHealthStatus() != null
                    ? status.getHealthStatus().name()
                    : null);
            record.setUptimeSec(status.getUptimeSec());
            record.setLastHealthCheck(now);
            record.setLastHeartbeat(now);
            modelProcessRepository.save(record);
            log.info("프로세스 상태 DB 저장 완료: modelProcessId={}, state={}, pid={}, healthStatus={}",
                    status.getModelId(), status.getState(), status.getPid(), status.getHealthStatus());
        }
        log.info("프로세스 상태 DB 동기화 완료: agent={}", agentId);
    }

    @Override
    public void pollPendingCommands() {
        if (sharedCommandQueueEnabled()) {
            pollSharedModelCommands();
            return;
        }
        String agentId = currentAgentIdOrNull();
        if (agentId == null) {
            log.warn("명령 조회 중단: 현재 로컬 Agent를 찾을 수 없음");
            return;
        }
        List<Command> commands = commandRepository.findByAgentIdAndCommandStatus(
                agentId, CommandStatus.PENDING.name());
        log.info("PENDING 명령 조회 완료: agent={}, commandCount={}", agentId, commands.size());

        for (Command cmd : commands) {
            long startMillis = System.currentTimeMillis();
            log.info("명령 처리 시작: commandId={}, type={}, modelProcessId={}",
                    cmd.getCommandId(), cmd.getCommandType(), cmd.getProcessId());
            try {
                ExecutionResult result = executeCommand(cmd);
                cmd.setCommandStatus(CommandStatus.COMPLETED.name());
                cmd.setProcessedAt(databaseNow());
                commandRepository.save(cmd);
                logExecution(result);
                log.info("명령 처리 완료: commandId={}, type={}, modelProcessId={}, status=COMPLETED",
                        cmd.getCommandId(), cmd.getCommandType(), cmd.getProcessId());
            } catch (Exception e) {
                log.warn("명령 처리 실패: commandId={}, type={}, modelProcessId={}, reason={}",
                        cmd.getCommandId(), cmd.getCommandType(), cmd.getProcessId(), e.getMessage());
                cmd.setCommandStatus(CommandStatus.FAILED.name());
                cmd.setProcessedAt(databaseNow());
                cmd.setFailedReason(e.getMessage());
                commandRepository.save(cmd);

                int durationSec = (int) ((System.currentTimeMillis() - startMillis) / 1000);
                logExecution(ExecutionResult.failure(
                        cmd.getCommandId(), cmd.getProcessId(),
                        parseCommandTypeOrNull(cmd.getCommandType()), e.getMessage(), durationSec));
                log.info("명령 실패 상태 저장 완료: commandId={}, status=FAILED", cmd.getCommandId());
            }
        }
    }

    @Override
    public void logExecution(ExecutionResult result) {
        ExecutionLog entry = new ExecutionLog();
        entry.setLogId(UUID.randomUUID().toString());
        entry.setAgentId(currentAgentIdOrNull());
        entry.setProcessId(result.getModelId());
        entry.setCommandType(result.getCommandType());
        entry.setExecutionStatus(result.getStatus() != null ? result.getStatus().name() : null);
        entry.setExitCode(result.getExitCode());
        entry.setStdoutPreview(truncate(result.getStdoutPreview()));
        entry.setStderrPreview(truncate(result.getStderrPreview()));
        entry.setDurationSec(result.getDurationSec());
        executionLogRepository.save(entry);
        log.info("명령 실행 이력 저장 완료: logId={}, type={}, modelProcessId={}, status={}",
                entry.getLogId(), entry.getCommandType(), entry.getProcessId(), entry.getExecutionStatus());
    }

    private boolean sharedCommandQueueEnabled() {
        return modelCommandRepository != null;
    }

    private void pollSharedModelCommands() {
        Agent agent = currentAgentOrNull();
        if (agent == null || agent.getIpAddress() == null || agent.getIpAddress().isBlank()) {
            log.warn("Command polling skipped: current Agent or IP is unavailable");
            return;
        }
        List<ModelCommand> commands = modelCommandRepository
                .findByTargetServerIpAndCommandStatusOrderByRequestedTimestampAsc(
                        agent.getIpAddress(), "REQUESTED");
        for (ModelCommand command : commands) {
            long startMillis = System.currentTimeMillis();
            command.setCommandStatus("PROCESSING");
            command.setAgentId(agent.getAgentId());
            command.setClaimedTimestamp(databaseNow());
            modelCommandRepository.save(command);
            try {
                ExecutionResult result = executeSharedCommand(command);
                command.setCommandStatus("COMPLETED");
                command.setProcessedTimestamp(databaseNow());
                command.setResultCode("SUCCESS");
                modelCommandRepository.save(command);
                safeLogExecution(result);
            } catch (Exception ex) {
                command.setCommandStatus("FAILED");
                command.setProcessedTimestamp(databaseNow());
                command.setResultCode("FAILURE");
                command.setResultMessage(ex.getMessage());
                modelCommandRepository.save(command);
                int durationSec = (int) ((System.currentTimeMillis() - startMillis) / 1000);
                safeLogExecution(ExecutionResult.failure(
                        command.getCommandId(), command.getProcessId(),
                        parseCommandTypeOrNull(command.getCommandType()), ex.getMessage(), durationSec));
            }
        }
    }

    private ExecutionResult executeSharedCommand(ModelCommand command) throws Exception {
        long startMillis = System.currentTimeMillis();
        String type = command.getCommandType();
        String modelId = command.getProcessId();
        processManager.executeControlCommand(modelId, requireProcess(modelId), type);
        int durationSec = (int) ((System.currentTimeMillis() - startMillis) / 1000);
        return ExecutionResult.success(command.getCommandId(), modelId, type, durationSec);
    }

    private void safeLogExecution(ExecutionResult result) {
        try {
            logExecution(result);
        } catch (RuntimeException ex) {
            log.warn("Execution log persistence failed after command processing", ex);
        }
    }

    private ExecutionResult executeCommand(Command cmd) throws Exception {
        long startMillis = System.currentTimeMillis();
        CommandType type = parseCommandType(cmd.getCommandType());
        String modelId = cmd.getProcessId();
        log.info("명령 실행 분기: type={}, modelProcessId={}", type, modelId);

        switch (type) {
            case START -> processManager.startProcess(modelId, requireProcess(modelId));
            case ATTACH -> processManager.attachExistingProcess(modelId, requireProcess(modelId));
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

    private ModelProcess requireProcess(String modelId) {
        return modelProcessRepository.findById(parseModelProcessId(modelId))
                .orElseThrow(() -> new IllegalStateException(
                        "No model process found: " + modelId));
    }

    private Long parseModelProcessId(String modelId) {
        try {
            return Long.valueOf(modelId);
        } catch (NumberFormatException | NullPointerException ex) {
            throw new IllegalArgumentException("MODEL_PROCESS_ID must be numeric: " + modelId, ex);
        }
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
        snapshot.put("agentId", currentAgentIdOrNull());
        snapshot.put("processes", processes);
        return snapshot;
    }

    private String currentAgentIdOrNull() {
        return agentIdentityResolver.resolveOrRegisterCurrentAgentId().orElse(null);
    }

    private Agent currentAgentOrNull() {
        return agentIdentityResolver.resolveOrRegisterCurrentAgent().orElse(null);
    }

    private Timestamp databaseNow() {
        return Timestamp.valueOf(LocalDateTime.now(DATABASE_ZONE));
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= PREVIEW_LIMIT ? value : value.substring(0, PREVIEW_LIMIT);
    }
}
