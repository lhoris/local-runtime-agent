package com.lra.agent.sync;

/**
 * Synchronizes agent runtime state with the central database on a periodic loop:
 * publishes status (including heartbeat via agent_info.last_heartbeat), consumes queued commands,
 * and records execution outcomes (ARCHITECTURE.md §4.3, §7.2, §7.3).
 */
public interface DBSyncManager {

    /** Persist the current status of every managed process to agent_status (including heartbeat). */
    void syncAgentStatus();

    /** Fetch and execute pending commands for this agent, recording each outcome. */
    void pollPendingCommands();

    /** Append a command execution outcome to execution_log. */
    void logExecution(ExecutionResult result);
}
