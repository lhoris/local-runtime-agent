package com.lra.common.enums;

/**
 * Lifecycle state of a managed process (ARCHITECTURE.md §5.1 agent_status.state).
 */
public enum ProcessState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    CRASHED,
    DEGRADED,
    UNHEALTHY
}
