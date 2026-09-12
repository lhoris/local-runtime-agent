package com.lra.agent.health;

import com.lra.common.enums.HealthStatus;

/**
 * Inspects the health of managed processes (ARCHITECTURE.md §4.5, §7.4).
 */
public interface HealthChecker {

    /**
     * Determine the current health of a process (typically called on the
     * {@code agent.health-check-interval-sec} cadence).
     */
    HealthStatus checkHealth(String modelId) throws HealthCheckException;

    /**
     * Detect whether a process that is believed to be RUNNING has actually
     * crashed, and raise the corresponding health event if so.
     */
    void detectCrash(String modelId) throws HealthCheckException;

    /**
     * Detect resource-limit breaches (CPU / memory) and raise a DEGRADED
     * health event if any configured limit is exceeded.
     */
    void detectResource(String modelId) throws HealthCheckException;
}
