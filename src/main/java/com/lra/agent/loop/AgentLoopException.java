package com.lra.agent.loop;

/**
 * Wraps an unexpected failure that escapes an individual agent-loop step
 * (ARCHITECTURE.md §7.2). Individual steps are isolated so a single failure does
 * not abort the cycle; this exists for callers that choose to fail hard.
 */
public class AgentLoopException extends RuntimeException {

    public AgentLoopException(String message, Throwable cause) {
        super(message, cause);
    }
}
