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
import com.lra.db.entity.Command;
import com.lra.db.entity.ExecutionLog;
import com.lra.db.entity.ModelProcess;
import com.lra.db.repository.ModelProcessRepository;
import com.lra.db.repository.CommandRepository;
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
    @Mock private CommandRepository commandRepository;
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
                commandRepository,
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

    private Command command(String id, String type, String modelId) {
        Command cmd = new Command();
        cmd.setCommandId(id);
        cmd.setAgentId(AGENT_ID);
        cmd.setProcessId(modelId);
        cmd.setCommandType(type);
        cmd.setCommandStatus("PENDING");
        return cmd;
    }

    @Test
    void syncAgentStatusPersistsEachProcess() {
        when(processManager.getAllStatus()).thenReturn(List.of(runningStatus("model-1")));

        dbSyncManager.syncAgentStatus();

        verify(modelProcessRepository).save(argThat(record ->
                record.getProcessId().equals("model-1")
                        && record.getAgentId().equals(AGENT_ID)
                        && record.getState().equals("RUNNING")
                        && record.getPid() == 1234
                        && record.getMemoryMb() == 2048));
    }

    @Test
    void pollPendingCommandsExecutesAndMarksCompleted() {
        Command cmd = command("cmd-1", "START", "model-1");
        when(commandRepository.findByAgentIdAndCommandStatus(AGENT_ID, "PENDING"))
                .thenReturn(List.of(cmd));
        when(modelProcessRepository.findById("model-1"))
                .thenReturn(Optional.of(new ModelProcess()));

        dbSyncManager.pollPendingCommands();

        verify(processManager).startProcess(eq("model-1"), any(ModelProcess.class));
        verify(commandRepository).save(argThat(c -> c.getCommandStatus().equals("COMPLETED")
                && c.getProcessedAt() != null));
        verify(executionLogRepository).save(argThat((ExecutionLog logEntry) ->
                logEntry.getExecutionStatus().equals("SUCCESS")
                        && logEntry.getCommandType().equals("START")));
    }

    @Test
    void pollPendingCommandsMarksFailedWhenExecutionThrows() {
        Command cmd = command("cmd-1", "START", "model-1");
        when(commandRepository.findByAgentIdAndCommandStatus(AGENT_ID, "PENDING"))
                .thenReturn(List.of(cmd));
        when(modelProcessRepository.findById("model-1"))
                .thenReturn(Optional.of(new ModelProcess()));
        doThrow(new RuntimeException("boom"))
                .when(processManager).startProcess(eq("model-1"), any(ModelProcess.class));

        dbSyncManager.pollPendingCommands();

        verify(commandRepository).save(argThat(c -> c.getCommandStatus().equals("FAILED")
                && "boom".equals(c.getFailedReason())));
        verify(executionLogRepository).save(argThat((ExecutionLog logEntry) ->
                logEntry.getExecutionStatus().equals("FAILURE")));
    }

    @Test
    void oneCommandFailureDoesNotBlockOthers() {
        Command failing = command("cmd-1", "START", "model-1");
        Command succeeding = command("cmd-2", "STOP", "model-2");
        when(commandRepository.findByAgentIdAndCommandStatus(AGENT_ID, "PENDING"))
                .thenReturn(List.of(failing, succeeding));
        when(modelProcessRepository.findById("model-1"))
                .thenReturn(Optional.of(new ModelProcess()));
        doThrow(new RuntimeException("boom"))
                .when(processManager).startProcess(eq("model-1"), any(ModelProcess.class));

        dbSyncManager.pollPendingCommands();

        verify(processManager).stopProcess("model-2", StopStrategy.GRACEFUL);
        verify(commandRepository).save(argThat(c -> c.getCommandId().equals("cmd-2")
                && c.getCommandStatus().equals("COMPLETED")));
    }

    @Test
    void healthCheckCommandInvokesHealthChecker() throws HealthCheckException {
        dbSyncManager.setHealthChecker(healthChecker);
        Command cmd = command("cmd-1", "HEALTH_CHECK", "model-1");
        when(commandRepository.findByAgentIdAndCommandStatus(AGENT_ID, "PENDING"))
                .thenReturn(List.of(cmd));

        dbSyncManager.pollPendingCommands();

        verify(healthChecker).checkHealth("model-1");
        verify(commandRepository).save(argThat(c -> c.getCommandStatus().equals("COMPLETED")));
    }

    @Test
    void healthCheckFailsWhenCheckerUnavailable() {
        Command cmd = command("cmd-1", "HEALTH_CHECK", "model-1");
        when(commandRepository.findByAgentIdAndCommandStatus(AGENT_ID, "PENDING"))
                .thenReturn(List.of(cmd));

        dbSyncManager.pollPendingCommands();

        verify(commandRepository).save(argThat(c -> c.getCommandStatus().equals("FAILED")));
    }

    @Test
    void paramUpdateAppliesEachKeyThenRestarts() throws ValidationException {
        dbSyncManager.setParameterManager(parameterManager);
        Command cmd = command("cmd-1", "PARAM_UPDATE", "model-1");
        cmd.setParameters(Map.of("ARG_batch_size", 32));
        when(commandRepository.findByAgentIdAndCommandStatus(AGENT_ID, "PENDING"))
                .thenReturn(List.of(cmd));

        dbSyncManager.pollPendingCommands();

        verify(parameterManager).updateParameter("model-1", "ARG_batch_size", "32");
        verify(processManager).restartProcess("model-1");
        verify(commandRepository).save(argThat(c -> c.getCommandStatus().equals("COMPLETED")));
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
        Command cmd = command("cmd-1", "BOGUS", "model-1");
        when(commandRepository.findByAgentIdAndCommandStatus(AGENT_ID, "PENDING"))
                .thenReturn(List.of(cmd));

        dbSyncManager.pollPendingCommands();

        verify(commandRepository).save(argThat(c -> c.getCommandStatus().equals("FAILED")));
        verify(processManager, never()).startProcess(any(), any());
    }
}
