package com.lra.agent.process;

/**
 * How a process should be terminated (ARCHITECTURE.md §4.1).
 */
public enum StopStrategy {
    /** Request termination (SIGTERM / Process.destroy) and wait for a grace period. */
    GRACEFUL,
    /** Kill immediately (SIGKILL / Process.destroyForcibly). */
    FORCEFUL
}
