package com.lra.agent.sync;

import com.lra.agent.health.HealthCheckException;
import com.lra.agent.health.HealthChecker;
import com.lra.agent.identity.AgentIdentityResolver;
import com.lra.agent.parameter.ParameterManager;
import com.lra.agent.parameter.ValidationException;
import com.lra.agent.process.ProcessManager;
import com.lra.agent.process.ProcessStatus;
import com.lra.agent.process.StopStrategy;
import com.lra.common.enums.ProcessState;
import com.lra.db.entity.Agent;
import com.lra.db.entity.ModelCommand;
import com.lra.db.entity.ExecutionLog;
import com.lra.db.entity.ModelProcess;
import com.lra.db.repository.ModelProcessRepository;
import com.lra.db.repository.ModelCommandRepository;
import com.lra.db.repository.ExecutionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DBSyncManagerTest {

    private static final String AGENT_ID = "agent-test";

    @Mock private AgentIdentityResolver agentIdentityResolver;
    @Mock private ProcessManager processManager;
    @Mock private ModelProcessRepository modelProcessRepository;
    @Mock private ModelCommandRepository modelCommandRepository;
    @Mock private ExecutionLogRepository executionLogRepository;
    @Mock private HealthChecker healthChecker;
    @Mock private ParameterManager parameterManager;

    private DefaultDBSyncManager dbSyncManager;

    @BeforeEach
    void setUp() {
        dbSyncManager = new DefaultDBSyncManager(
                agentIdentityResolver,
                processManager,
                modelProcessRepository,
                modelCommandRepository,
                executionLogRepository);
        when(agentIdentityResolver.resolveOrRegisterCurrentAgentId()).thenReturn(Optional.of(AGENT_ID));
    }

    private ProcessStatus runningStatus(String modelId) {
        ProcessStatus status = new ProcessStatus(modelId, ProcessState.RUNNING);
        status.setPid(1234);
        status.setCpuPercent(45.0f);
        status.setMemoryMb(2048);
        status.setUptimeSec(120L);
        return status;
    }

    private ModelCommand command(String id, String type, String modelId) {
        ModelCommand cmd = new ModelCommand();
        cmd.setCommandId(id);
        cmd.setAgentId(AGENT_ID);
        cmd.setProcessId(modelId);
        cmd.setCommandType(type);
        cmd.setCommandStatus("PENDING");
        return cmd;
    }

    @Test
    void syncAgentStatusPersistsEachProcess() {
        when(processManager.getAllStatus()).thenReturn(List.of(runningStatus("1")));

        dbSyncManager.syncAgentStatus();

        verify(modelProcessRepository).save(argThat(record ->
                record.getProcessId().equals("1")
                        && record.getAgentId().equals(AGENT_ID)
                        && record.getState().equals("RUNNING")
                        && record.getPid() == 1234
                        && record.getUptimeSec() == 120L));
    }

    @Test
    void modelProcessCommandUsesUnifiedCommandQueue() {
        ModelCommand command = command("cmd-model-1", "START", "1");
        when(modelCommandRepository.findByAgentIdAndCommandStatus(AGENT_ID, "PENDING"))
                .thenReturn(List.of(command));
        when(modelProcessRepository.findById(1L)).thenReturn(Optional.of(new ModelProcess()));

        dbSyncManager.pollPendingCommands();

        verify(processManager).startProcess(eq("1"), any(ModelProcess.class));
        verify(modelCommandRepository).save(argThat(row -> "COMPLETED".equals(row.getCommandStatus())));
    }

    @Test
    void pollPendingCommandsExecutesAndMarksCompleted() {
        ModelCommand cmd = command("cmd-1", "START", "1");
        when(modelCommandRepository.findByAgentIdAndCommandStatus(AGENT_ID, "PENDING"))
                .thenReturn(List.of(cmd));
        when(modelProcessRepository.findById(1L))
                .thenReturn(Optional.of(new ModelProcess()));

        dbSyncManager.pollPendingCommands();

        verify(processManager).startProcess(eq("1"), any(ModelProcess.class));
        verify(modelCommandRepository).save(argThat(c -> c.getCommandStatus().equals("COMPLETED")
                && c.getProcessedAt() != null));
        verify(executionLogRepository).save(argThat((ExecutionLog logEntry) ->
                logEntry.getExecutionStatus().equals("SUCCESS")
                        && logEntry.getCommandType().equals("START")));
    }

    @Test
    void attachCommandAttachesExistingProcess() {
        ModelCommand cmd = command("cmd-attach", "ATTACH", "1");
        when(modelCommandRepository.findByAgentIdAndCommandStatus(AGENT_ID, "PENDING"))
                .thenReturn(List.of(cmd));
        when(modelProcessRepository.findById(1L))
                .thenReturn(Optional.of(new ModelProcess()));

        dbSyncManager.pollPendingCommands();

        verify(processManager).attachExistingProcess(eq("1"), any(ModelProcess.class));
        verify(modelCommandRepository).save(argThat(c -> c.getCommandStatus().equals("COMPLETED")));
    }

    @Test
    void pollPendingCommandsMarksFailedWhenExecutionThrows() {
        ModelCommand cmd = command("cmd-1", "START", "1");
        when(modelCommandRepository.findByAgentIdAndCommandStatus(AGENT_ID, "PENDING"))
                .thenReturn(List.of(cmd));
        when(modelProcessRepository.findById(1L))
                .thenReturn(Optional.of(new ModelProcess()));
        doThrow(new RuntimeException("boom"))
                .when(processManager).startProcess(eq("1"), any(ModelProcess.class));

        dbSyncManager.pollPendingCommands();

        verify(modelCommandRepository).save(argThat(c -> c.getCommandStatus().equals("FAILED")
                && "boom".equals(c.getFailedReason())));
        verify(executionLogRepository).save(argThat((ExecutionLog logEntry) ->
                logEntry.getExecutionStatus().equals("FAILURE")));
    }

    @Test
    void oneCommandFailureDoesNotBlockOthers() {
        ModelCommand failing = command("cmd-1", "START", "1");
        ModelCommand succeeding = command("cmd-2", "STOP", "2");
        when(modelCommandRepository.findByAgentIdAndCommandStatus(AGENT_ID, "PENDING"))
                .thenReturn(List.of(failing, succeeding));
        when(modelProcessRepository.findById(1L))
                .thenReturn(Optional.of(new ModelProcess()));
        when(modelProcessRepository.findById(2L))
                .thenReturn(Optional.of(new ModelProcess()));
        doThrow(new RuntimeException("boom"))
                .when(processManager).startProcess(eq("1"), any(ModelProcess.class));

        dbSyncManager.pollPendingCommands();

        verify(processManager).stopProcess(
                eq("2"), any(ModelProcess.class), eq(StopStrategy.GRACEFUL));
        verify(modelCommandRepository).save(argThat(c -> c.getCommandId().equals("cmd-2")
                && c.getCommandStatus().equals("COMPLETED")));
    }

    @Test
    void healthCheckCommandInvokesHealthChecker() throws HealthCheckException {
        dbSyncManager.setHealthChecker(healthChecker);
        ModelCommand cmd = command("cmd-1", "HEALTH_CHECK", "model-1");
        when(modelCommandRepository.findByAgentIdAndCommandStatus(AGENT_ID, "PENDING"))
                .thenReturn(List.of(cmd));

        dbSyncManager.pollPendingCommands();

        verify(healthChecker).checkHealth("model-1");
        verify(modelCommandRepository).save(argThat(c -> c.getCommandStatus().equals("COMPLETED")));
    }

    @Test
    void healthCheckFailsWhenCheckerUnavailable() {
        ModelCommand cmd = command("cmd-1", "HEALTH_CHECK", "model-1");
        when(modelCommandRepository.findByAgentIdAndCommandStatus(AGENT_ID, "PENDING"))
                .thenReturn(List.of(cmd));

        dbSyncManager.pollPendingCommands();

        verify(modelCommandRepository).save(argThat(c -> c.getCommandStatus().equals("FAILED")));
    }

    @Test
    void paramUpdateAppliesEachKeyThenRestarts() throws ValidationException {
        dbSyncManager.setParameterManager(parameterManager);
        ModelCommand cmd = command("cmd-1", "PARAM_UPDATE", "1");
        cmd.setParameters(Map.of("ARG_batch_size", 32));
        when(modelCommandRepository.findByAgentIdAndCommandStatus(AGENT_ID, "PENDING"))
                .thenReturn(List.of(cmd));
        when(modelProcessRepository.findById(1L))
                .thenReturn(Optional.of(new ModelProcess()));

        dbSyncManager.pollPendingCommands();

        verify(parameterManager).updateParameter("1", "ARG_batch_size", "32");
        verify(processManager).restartProcess(eq("1"), any(ModelProcess.class));
        verify(modelCommandRepository).save(argThat(c -> c.getCommandStatus().equals("COMPLETED")));
    }

    @Test
    void logExecutionTruncatesPreviewsToLimit() {
        String longText = "x".repeat(1500);
        ExecutionResult result = new ExecutionResult(
                "cmd-1", "model-1", com.lra.common.enums.CommandType.START,
                ExecutionStatus.FAILURE, 1, longText, longText, 3);

        dbSyncManager.logExecution(result);

        verify(executionLogRepository).save(argThat((ExecutionLog logEntry) ->
                logEntry.getStdoutPreview().length() == 1000
                        && logEntry.getStderrPreview().length() == 1000
                        && logEntry.getExitCode() == 1));
    }

    @Test
    void unknownCommandTypeIsMarkedFailed() {
        ModelCommand cmd = command("cmd-1", "BOGUS", "model-1");
        when(modelCommandRepository.findByAgentIdAndCommandStatus(AGENT_ID, "PENDING"))
                .thenReturn(List.of(cmd));

        dbSyncManager.pollPendingCommands();

        verify(modelCommandRepository).save(argThat(c -> c.getCommandStatus().equals("FAILED")));
        verify(processManager, never()).startProcess(any(), any());
    }
}
