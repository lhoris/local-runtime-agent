package com.lra.common.enums;

/**
 * Type of command dispatched to an agent (ARCHITECTURE.md §5.1 commands table).
 */
public enum CommandType {
    START,
    ATTACH,
    STOP,
    RESTART,
    PARAM_UPDATE,
    HEALTH_CHECK
}
