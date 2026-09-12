package com.lra.agent.state;

import com.lra.common.enums.ProcessState;

/**
 * Thrown when a requested state transition (from -> to) is not permitted by the
 * process state machine (ARCHITECTURE.md §7.1).
 */
public class InvalidStateTransitionException extends Exception {

    private final ProcessState from;
    private final ProcessState to;

    public InvalidStateTransitionException(ProcessState from, ProcessState to) {
        super(String.format("Invalid state transition: %s -> %s", from, to));
        this.from = from;
        this.to = to;
    }

    public ProcessState getFrom() {
        return from;
    }

    public ProcessState getTo() {
        return to;
    }
}
