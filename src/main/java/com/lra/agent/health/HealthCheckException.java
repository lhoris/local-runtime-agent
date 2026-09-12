package com.lra.agent.health;

/**
 * Raised when a health check cannot be completed (ARCHITECTURE.md §4.5).
 */
public class HealthCheckException extends Exception {

    public HealthCheckException(String message) {
        super(message);
    }

    public HealthCheckException(String message, Throwable cause) {
        super(message, cause);
    }
}
