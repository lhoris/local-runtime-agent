package com.lra.agent.loop;

import com.lra.agent.health.HealthCheckException;
import com.lra.agent.health.HealthChecker;
import com.lra.agent.process.ProcessManager;
import com.lra.agent.process.ProcessMonitor;
import com.lra.agent.process.ProcessStatus;
import com.lra.agent.state.InvalidStateTransitionException;
import com.lra.agent.state.StateManager;
import com.lra.agent.sync.DBSyncManager;
import com.lra.common.enums.HealthStatus;
import com.lra.common.enums.ProcessState;
import com.lra.db.entity.ModelProcess;
import com.lra.db.repository.ModelProcessRepository;
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
    private ModelProcessRepository modelProcessRepository;
    private StateManager stateManager;
    private HealthChecker healthChecker;
    private ProcessMonitor processMonitor;
    private DBSyncManager dbSyncManager;

    private AgentMainLoop loop;

    @BeforeEach
    void setUp() {
        processManager = mock(ProcessManager.class);
        modelProcessRepository = mock(ModelProcessRepository.class);
        stateManager = mock(StateManager.class);
        healthChecker = mock(HealthChecker.class);
        processMonitor = mock(ProcessMonitor.class);
        dbSyncManager = mock(DBSyncManager.class);
        loop = new AgentMainLoop("agent-test", processManager, provider(modelProcessRepository), stateManager,
                provider(healthChecker), provider(processMonitor), provider(dbSyncManager));
    }

    @Test
    void runsStepsInOrder() throws Exception {
        ModelProcess definition = definition("m1");
        when(modelProcessRepository.findByAgentId("agent-test")).thenReturn(List.of(definition));
        when(processManager.checkStatus(definition)).thenReturn(running("m1"));
        when(healthChecker.checkHealth("m1")).thenReturn(HealthStatus.HEALTHY);

        loop.agentLoop();

        var order = inOrder(modelProcessRepository, processManager, healthChecker, processMonitor, dbSyncManager);
        order.verify(modelProcessRepository).findByAgentId("agent-test");
        order.verify(processManager).checkStatus(definition);
        order.verify(healthChecker).checkHealth("m1");
        order.verify(healthChecker).detectCrash("m1");
        order.verify(processMonitor).autoRestart();
        order.verify(dbSyncManager).pollPendingCommands();
        order.verify(dbSyncManager).syncAgentStatus();
    }

    @Test
    void unhealthyCheckTransitionsState() throws Exception {
        ModelProcess definition = definition("m1");
        when(modelProcessRepository.findByAgentId("agent-test")).thenReturn(List.of(definition));
        when(processManager.checkStatus(definition)).thenReturn(running("m1"));
        when(healthChecker.checkHealth("m1")).thenReturn(HealthStatus.UNHEALTHY);

        loop.agentLoop();

        verify(stateManager).transitionTo("m1", ProcessState.UNHEALTHY);
    }

    @Test
    void nonRunningProcessSkipsHealthCheck() throws Exception {
        ModelProcess definition = definition("m1");
        when(modelProcessRepository.findByAgentId("agent-test")).thenReturn(List.of(definition));
        when(processManager.checkStatus(definition))
                .thenReturn(new ProcessStatus("m1", ProcessState.STOPPED));

        loop.agentLoop();

        verify(healthChecker, never()).checkHealth(any());
    }

    @Test
    void healthCheckFailureDoesNotStopCycle() throws Exception {
        ModelProcess definition = definition("m1");
        when(modelProcessRepository.findByAgentId("agent-test")).thenReturn(List.of(definition));
        when(processManager.checkStatus(definition)).thenReturn(running("m1"));
        when(healthChecker.checkHealth("m1")).thenThrow(new HealthCheckException("down"));

        loop.agentLoop();

        verify(dbSyncManager).syncAgentStatus();
    }

    @Test
    void monitorFailureStillRunsDbSync() {
        ModelProcess definition = definition("m1");
        when(modelProcessRepository.findByAgentId("agent-test")).thenReturn(List.of(definition));
        when(processManager.checkStatus(definition)).thenThrow(new RuntimeException("boom"));

        loop.agentLoop();

        verify(dbSyncManager).pollPendingCommands();
        verify(dbSyncManager).syncAgentStatus();
    }

    @Test
    void invalidStateTransitionIsSwallowed() throws Exception {
        ModelProcess definition = definition("m1");
        when(modelProcessRepository.findByAgentId("agent-test")).thenReturn(List.of(definition));
        when(processManager.checkStatus(definition)).thenReturn(running("m1"));
        when(healthChecker.checkHealth("m1")).thenReturn(HealthStatus.UNHEALTHY);
        doThrow(new InvalidStateTransitionException(ProcessState.RUNNING, ProcessState.UNHEALTHY))
                .when(stateManager).transitionTo(eq("m1"), eq(ProcessState.UNHEALTHY));

        loop.agentLoop();

        verify(dbSyncManager).syncAgentStatus();
    }

    @Test
    void missingOptionalComponentsAreSkipped() {
        AgentMainLoop bareLoop = new AgentMainLoop("agent-test", processManager, emptyProvider(), stateManager,
                emptyProvider(), emptyProvider(), emptyProvider());

        bareLoop.agentLoop();

        verify(processManager, never()).checkStatus(any());
    }

    private static ProcessStatus running(String modelId) {
        return new ProcessStatus(modelId, ProcessState.RUNNING);
    }

    private static ModelProcess definition(String processId) {
        ModelProcess process = new ModelProcess();
        process.setProcessId(processId);
        process.setAgentId("agent-test");
        return process;
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
