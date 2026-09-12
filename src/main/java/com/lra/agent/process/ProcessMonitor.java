package com.lra.agent.process;

/**
 * Periodic monitoring, crash detection and auto-restart for managed processes
 * (ARCHITECTURE.md §4.1, §7.4). Intended to be driven by the agent polling loop.
 */
public interface ProcessMonitor {

    /** Refresh runtime status (liveness, uptime) of all managed processes. */
    void monitor();

    /** Detect dead/unhealthy processes and transition them (e.g. to CRASHED). */
    void detectUnhealthy();

    /** Restart crashed processes whose config permits it and whose attempts remain. */
    void autoRestart();
}
