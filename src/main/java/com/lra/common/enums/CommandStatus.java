package com.lra.common.enums;

/**
 * Lifecycle status of a queued command (ARCHITECTURE.md §5.1 commands table).
 */
public enum CommandStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
