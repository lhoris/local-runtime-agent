package com.lra.agent.state;

import com.lra.common.enums.ProcessState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StateManagerTest {

    private DefaultStateManager stateManager;

    @BeforeEach
    void setUp() {
        stateManager = new DefaultStateManager();
    }

    @Test
    void unknownModelDefaultsToStopped() {
        assertEquals(ProcessState.STOPPED, stateManager.getCurrentState("model-1"));
    }

    @Test
    void testValidTransition() throws InvalidStateTransitionException {
        stateManager.transitionTo("model-1", ProcessState.STARTING);
        assertEquals(ProcessState.STARTING, stateManager.getCurrentState("model-1"));
    }

    @Test
    void testInvalidTransition() throws InvalidStateTransitionException {
        stateManager.transitionTo("model-1", ProcessState.STARTING);
        assertThrows(InvalidStateTransitionException.class,
                () -> stateManager.transitionTo("model-1", ProcessState.STOPPING));
    }

    @Test
    void invalidTransitionLeavesStateUnchanged() throws InvalidStateTransitionException {
        stateManager.transitionTo("model-1", ProcessState.STARTING);
        assertThrows(InvalidStateTransitionException.class,
                () -> stateManager.transitionTo("model-1", ProcessState.DEGRADED));
        assertEquals(ProcessState.STARTING, stateManager.getCurrentState("model-1"));
    }

    @Test
    void testFullLifecycle() throws InvalidStateTransitionException {
        stateManager.transitionTo("model-1", ProcessState.STARTING);
        stateManager.transitionTo("model-1", ProcessState.RUNNING);
        stateManager.transitionTo("model-1", ProcessState.STOPPING);
        stateManager.transitionTo("model-1", ProcessState.STOPPED);
        assertEquals(ProcessState.STOPPED, stateManager.getCurrentState("model-1"));
    }

    @Test
    void testCrashAndAutoRestart() throws InvalidStateTransitionException {
        stateManager.transitionTo("model-1", ProcessState.STARTING);
        stateManager.transitionTo("model-1", ProcessState.RUNNING);
        stateManager.transitionTo("model-1", ProcessState.CRASHED);
        stateManager.transitionTo("model-1", ProcessState.STARTING);
        assertEquals(ProcessState.STARTING, stateManager.getCurrentState("model-1"));
    }

    @Test
    void testStateChangeListener() throws InvalidStateTransitionException {
        StateChangeListener listener = mock(StateChangeListener.class);
        stateManager.addEventListener(listener);

        stateManager.transitionTo("model-1", ProcessState.STARTING);
        verify(listener).onStateChange("model-1", ProcessState.STOPPED, ProcessState.STARTING);
    }

    @Test
    void listenerNotFiredOnInvalidTransition() throws InvalidStateTransitionException {
        stateManager.transitionTo("model-1", ProcessState.STARTING);
        StateChangeListener listener = mock(StateChangeListener.class);
        stateManager.addEventListener(listener);

        assertThrows(InvalidStateTransitionException.class,
                () -> stateManager.transitionTo("model-1", ProcessState.STOPPING));
        verify(listener, times(0))
                .onStateChange(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void faultyListenerDoesNotBlockOthers() throws InvalidStateTransitionException {
        List<ProcessState> observed = new ArrayList<>();
        stateManager.addEventListener((id, oldState, newState) -> {
            throw new RuntimeException("boom");
        });
        stateManager.addEventListener((id, oldState, newState) -> observed.add(newState));

        stateManager.transitionTo("model-1", ProcessState.STARTING);
        assertEquals(List.of(ProcessState.STARTING), observed);
    }

    @Test
    void validateTransitionRejectsInvalidPair() {
        assertThrows(InvalidStateTransitionException.class,
                () -> stateManager.validateTransition(ProcessState.STARTING, ProcessState.STOPPING));
    }

    @Test
    void stateMachineTables() {
        assertTrue(StateMachine.isValidTransition(ProcessState.STOPPED, ProcessState.STARTING));
        assertTrue(StateMachine.isValidTransition(ProcessState.RUNNING, ProcessState.DEGRADED));
        assertTrue(StateMachine.isValidTransition(ProcessState.DEGRADED, ProcessState.RUNNING));
        assertTrue(StateMachine.isValidTransition(ProcessState.CRASHED, ProcessState.STARTING));

        assertFalse(StateMachine.isValidTransition(ProcessState.STARTING, ProcessState.STOPPING));
        assertFalse(StateMachine.isValidTransition(ProcessState.CRASHED, ProcessState.DEGRADED));
        assertFalse(StateMachine.isValidTransition(ProcessState.STOPPED, ProcessState.RUNNING));
        assertFalse(StateMachine.isValidTransition(null, ProcessState.RUNNING));
    }
}
