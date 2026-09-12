package com.lra.agent.state;

import com.lra.common.enums.ProcessState;

/**
 * Manages the lifecycle state of each managed process and enforces the
 * transition rules defined by the process state machine (ARCHITECTURE.md §4.2, §7.1).
 */
public interface StateManager {

    /**
     * Returns the current state for the model, or {@link ProcessState#STOPPED}
     * if the model has no recorded state yet.
     */
    ProcessState getCurrentState(String modelId);

    /**
     * Transitions the model to the given state, validating the transition and
     * notifying registered listeners.
     *
     * @throws InvalidStateTransitionException if the transition is not permitted
     */
    void transitionTo(String modelId, ProcessState newState) throws InvalidStateTransitionException;

    /**
     * Validates that a transition from {@code from} to {@code to} is permitted.
     *
     * @throws InvalidStateTransitionException if the transition is not permitted
     */
    void validateTransition(ProcessState from, ProcessState to) throws InvalidStateTransitionException;

    /**
     * Registers a listener notified on every successful state change.
     */
    void addEventListener(StateChangeListener listener);
}
