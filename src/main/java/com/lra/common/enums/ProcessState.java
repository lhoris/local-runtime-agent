package com.lra.common.enums;

/**
 * Lifecycle state of a managed process (ARCHITECTURE.md §5.1 TB_M26_MODEL_PROCESS.PROCESS_STATE).
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
