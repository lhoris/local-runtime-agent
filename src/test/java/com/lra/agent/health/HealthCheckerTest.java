package com.lra.agent.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lra.agent.process.ProcessManager;
import com.lra.agent.process.ProcessStatus;
import com.lra.agent.state.InvalidStateTransitionException;
import com.lra.agent.state.StateManager;
import com.lra.common.enums.HealthStatus;
import com.lra.common.enums.ProcessState;
import com.lra.db.entity.ModelProcess;
import com.lra.db.repository.ModelProcessRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthCheckerTest {

    private static final String MODEL_ID = "model-a";
    private static final int PID = 1234;

    @Mock
    private ProcessManager processManager;
    @Mock
    private ModelProcessRepository modelProcessRepository;
    @Mock
    private CrashDetector crashDetector;
    @Mock
    private StateManager stateManager;

    private DefaultHealthChecker healthChecker;

    @BeforeEach
    void setUp() {
        healthChecker = new DefaultHealthChecker(
            processManager, modelProcessRepository, crashDetector, stateManager);
    }

    private ProcessStatus runningStatus() {
        ProcessStatus status = new ProcessStatus(MODEL_ID, ProcessState.RUNNING);
        status.setPid(PID);
        return status;
    }

    @Test
    void testHealthyProcess() throws Exception {
        when(processManager.getStatus(MODEL_ID)).thenReturn(runningStatus());

        ProcessHandle handle = org.mockito.Mockito.mock(ProcessHandle.class);
        when(handle.isAlive()).thenReturn(true);
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            mocked.when(() -> ProcessHandle.of(PID)).thenReturn(Optional.of(handle));

            assertThat(healthChecker.checkHealth(MODEL_ID)).isEqualTo(HealthStatus.HEALTHY);
        }
    }

    @Test
    void testUnhealthyWhenPidNotAlive() throws Exception {
        when(processManager.getStatus(MODEL_ID)).thenReturn(runningStatus());

        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            mocked.when(() -> ProcessHandle.of(PID)).thenReturn(Optional.empty());

            assertThat(healthChecker.checkHealth(MODEL_ID)).isEqualTo(HealthStatus.UNHEALTHY);
        }
    }

    @Test
    void testUnknownWhenNotRunning() throws Exception {
        ProcessStatus stopped = new ProcessStatus(MODEL_ID, ProcessState.STOPPED);
        when(processManager.getStatus(MODEL_ID)).thenReturn(stopped);

        assertThat(healthChecker.checkHealth(MODEL_ID)).isEqualTo(HealthStatus.UNKNOWN);
    }

    @Test
    void testUnknownWhenUntracked() throws Exception {
        when(processManager.getStatus(MODEL_ID)).thenReturn(null);

        assertThat(healthChecker.checkHealth(MODEL_ID)).isEqualTo(HealthStatus.UNKNOWN);
    }

    @Test
    void testCrashedProcessTransitionsToCrashed() throws Exception {
        ProcessStatus status = runningStatus();
        when(processManager.getStatus(MODEL_ID)).thenReturn(status);
        when(crashDetector.isCrashed(MODEL_ID, status)).thenReturn(true);

        healthChecker.detectCrash(MODEL_ID);

        verify(stateManager).transitionTo(MODEL_ID, ProcessState.CRASHED);
    }

    @Test
    void testHealthyProcessDoesNotTransition() throws Exception {
        ProcessStatus status = runningStatus();
        when(processManager.getStatus(MODEL_ID)).thenReturn(status);
        when(crashDetector.isCrashed(MODEL_ID, status)).thenReturn(false);

        healthChecker.detectCrash(MODEL_ID);

        verify(stateManager, never()).transitionTo(eq(MODEL_ID), eq(ProcessState.CRASHED));
    }

    @Test
    void testCrashDetectSwallowsInvalidTransition() throws Exception {
        ProcessStatus status = runningStatus();
        when(processManager.getStatus(MODEL_ID)).thenReturn(status);
        when(crashDetector.isCrashed(MODEL_ID, status)).thenReturn(true);
        org.mockito.Mockito.doThrow(
                new InvalidStateTransitionException(ProcessState.RUNNING, ProcessState.CRASHED))
            .when(stateManager).transitionTo(MODEL_ID, ProcessState.CRASHED);

        // Should not propagate the transition failure.
        healthChecker.detectCrash(MODEL_ID);

        verify(stateManager).transitionTo(MODEL_ID, ProcessState.CRASHED);
    }

    @Test
    void testCpuExceededTransitionsToDegraded() throws Exception {
        ProcessStatus status = runningStatus();
        status.setCpuPercent(95f);
        when(processManager.getStatus(MODEL_ID)).thenReturn(status);

        ModelProcess definition = new ModelProcess();
        definition.setCpuLimitPercent(90);
        when(modelProcessRepository.findById(MODEL_ID)).thenReturn(Optional.of(definition));

        healthChecker.detectResource(MODEL_ID);

        verify(stateManager).transitionTo(MODEL_ID, ProcessState.DEGRADED);
    }

    @Test
    void testMemoryExceededTransitionsToDegraded() throws Exception {
        ProcessStatus status = runningStatus();
        status.setMemoryMb(2048);
        when(processManager.getStatus(MODEL_ID)).thenReturn(status);

        ModelProcess definition = new ModelProcess();
        definition.setMemoryLimitMb(1024);
        when(modelProcessRepository.findById(MODEL_ID)).thenReturn(Optional.of(definition));

        healthChecker.detectResource(MODEL_ID);

        verify(stateManager).transitionTo(MODEL_ID, ProcessState.DEGRADED);
    }

    @Test
    void testWithinLimitsDoesNotTransition() throws Exception {
        ProcessStatus status = runningStatus();
        status.setCpuPercent(50f);
        status.setMemoryMb(512);
        when(processManager.getStatus(MODEL_ID)).thenReturn(status);

        ModelProcess definition = new ModelProcess();
        definition.setCpuLimitPercent(90);
        definition.setMemoryLimitMb(1024);
        when(modelProcessRepository.findById(MODEL_ID)).thenReturn(Optional.of(definition));

        healthChecker.detectResource(MODEL_ID);

        verify(stateManager, never()).transitionTo(eq(MODEL_ID), eq(ProcessState.DEGRADED));
    }

    @Test
    void testCrashDetectorFlagsDeadPid() {
        // A PID that is essentially certain not to exist.
        ProcessStatus status = new ProcessStatus(MODEL_ID, ProcessState.RUNNING);
        status.setPid(Integer.MAX_VALUE);
        CrashDetector realDetector = new CrashDetector();

        assertThat(realDetector.isCrashed(MODEL_ID, status)).isTrue();
    }

    @Test
    void testCrashDetectorIgnoresNonRunning() {
        ProcessStatus status = new ProcessStatus(MODEL_ID, ProcessState.STOPPED);
        status.setPid(PID);
        CrashDetector realDetector = new CrashDetector();

        assertThat(realDetector.isCrashed(MODEL_ID, status)).isFalse();
    }
}
