package com.lra.agent.loop;

import com.lra.agent.health.HealthCheckException;
import com.lra.agent.health.HealthChecker;
import com.lra.agent.process.ProcessManager;
import com.lra.agent.process.ProcessStatus;
import com.lra.agent.state.InvalidStateTransitionException;
import com.lra.agent.state.StateManager;
import com.lra.agent.sync.DBSyncManager;
import com.lra.common.enums.HealthStatus;
import com.lra.common.enums.ProcessState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMainLoopTest {

    private ProcessManager processManager;
    private StateManager stateManager;
    private HealthChecker healthChecker;
    private DBSyncManager dbSyncManager;

    private AgentMainLoop loop;

    @BeforeEach
    void setUp() {
        processManager = mock(ProcessManager.class);
        stateManager = mock(StateManager.class);
        healthChecker = mock(HealthChecker.class);
        dbSyncManager = mock(DBSyncManager.class);
        loop = new AgentMainLoop(processManager, stateManager,
                provider(healthChecker), provider(dbSyncManager));
    }

    @Test
    void runsStepsInOrder() throws Exception {
        when(processManager.getAllStatus()).thenReturn(List.of(running("m1")));
        when(healthChecker.checkHealth("m1")).thenReturn(HealthStatus.HEALTHY);

        loop.agentLoop();

        var order = inOrder(processManager, healthChecker, dbSyncManager);
        order.verify(processManager).getAllStatus();
        order.verify(healthChecker).checkHealth("m1");
        order.verify(healthChecker).detectCrash("m1");
        order.verify(dbSyncManager).pollPendingCommands();
        order.verify(dbSyncManager).syncAgentStatus();
    }

    @Test
    void unhealthyCheckTransitionsState() throws Exception {
        when(processManager.getAllStatus()).thenReturn(List.of(running("m1")));
        when(healthChecker.checkHealth("m1")).thenReturn(HealthStatus.UNHEALTHY);

        loop.agentLoop();

        verify(stateManager).transitionTo("m1", ProcessState.UNHEALTHY);
    }

    @Test
    void nonRunningProcessSkipsHealthCheck() throws Exception {
        when(processManager.getAllStatus())
                .thenReturn(List.of(new ProcessStatus("m1", ProcessState.STOPPED)));

        loop.agentLoop();

        verify(healthChecker, never()).checkHealth(any());
    }

    @Test
    void healthCheckFailureDoesNotStopCycle() throws Exception {
        when(processManager.getAllStatus()).thenReturn(List.of(running("m1")));
        when(healthChecker.checkHealth("m1")).thenThrow(new HealthCheckException("down"));

        loop.agentLoop();

        verify(dbSyncManager).syncAgentStatus();
    }

    @Test
    void monitorFailureStillRunsDbSync() {
        when(processManager.getAllStatus()).thenThrow(new RuntimeException("boom"));

        loop.agentLoop();

        verify(dbSyncManager).pollPendingCommands();
        verify(dbSyncManager).syncAgentStatus();
    }

    @Test
    void invalidStateTransitionIsSwallowed() throws Exception {
        when(processManager.getAllStatus()).thenReturn(List.of(running("m1")));
        when(healthChecker.checkHealth("m1")).thenReturn(HealthStatus.UNHEALTHY);
        doThrow(new InvalidStateTransitionException(ProcessState.RUNNING, ProcessState.UNHEALTHY))
                .when(stateManager).transitionTo(eq("m1"), eq(ProcessState.UNHEALTHY));

        loop.agentLoop();

        verify(dbSyncManager).syncAgentStatus();
    }

    @Test
    void missingOptionalComponentsAreSkipped() {
        AgentMainLoop bareLoop = new AgentMainLoop(processManager, stateManager,
                emptyProvider(), emptyProvider());
        when(processManager.getAllStatus()).thenReturn(List.of(running("m1")));

        bareLoop.agentLoop();

        verify(processManager).getAllStatus();
    }

    private static ProcessStatus running(String modelId) {
        return new ProcessStatus(modelId, ProcessState.RUNNING);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyProvider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
