package com.lra.agent.loop;

import com.lra.agent.health.HealthCheckException;
import com.lra.agent.health.HealthChecker;
import com.lra.agent.process.ProcessManager;
import com.lra.agent.process.ProcessStatus;
import com.lra.agent.state.InvalidStateTransitionException;
import com.lra.agent.state.StateManager;
import com.lra.agent.sync.DBSyncManager;
import com.lra.common.enums.HealthStatus;
import com.lra.common.enums.ProcessState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodic agent loop that integrates the core components (ARCHITECTURE.md §7.2).
 * Each cycle runs, in order: Monitor -> HealthCheck -> AutoRestart (crash/resource
 * detection) -> PollDB/ExecuteCommands -> SyncStatus.
 *
 * <p>Every step is isolated: a failure in one step is logged and does not abort
 * the remaining steps or the cycle. {@link HealthChecker} and {@link DBSyncManager}
 * are resolved lazily via {@link ObjectProvider} so the agent can boot and run its
 * process/state steps before those components (Tasks #6/#7) are wired in.
 */
@Component
public class AgentMainLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentMainLoop.class);

    private final ProcessManager processManager;
    private final StateManager stateManager;
    private final ObjectProvider<HealthChecker> healthCheckerProvider;
    private final ObjectProvider<DBSyncManager> dbSyncManagerProvider;

    public AgentMainLoop(ProcessManager processManager,
                         StateManager stateManager,
                         ObjectProvider<HealthChecker> healthCheckerProvider,
                         ObjectProvider<DBSyncManager> dbSyncManagerProvider) {
        this.processManager = processManager;
        this.stateManager = stateManager;
        this.healthCheckerProvider = healthCheckerProvider;
        this.dbSyncManagerProvider = dbSyncManagerProvider;
    }

    @Scheduled(fixedRateString = "${agent.polling-interval-sec:30}000")
    public void agentLoop() {
        long startedAt = System.currentTimeMillis();
        log.info("Agent loop cycle started");

        List<ProcessStatus> statuses = monitor();
        healthCheck(statuses);         // 프로세스가 응답하는가?
        detect(statuses);
        pollPendingCommands();         // 명령어가 있는가?(혹시 뭔가 일할게 있나?)
        syncStatus();                  // Agent의 상태를 DB에 저장 (heartbeat 포함)

        log.info("Agent loop cycle completed in {} ms", System.currentTimeMillis() - startedAt);
    }

    /** Step 1 — snapshot every managed process. */
    private List<ProcessStatus> monitor() {
        try {
            List<ProcessStatus> statuses = processManager.getAllStatus();
            for (ProcessStatus status : statuses) {
                log.debug("Process {} state: {}", status.getModelId(), status.getState());
            }
            return statuses;
        } catch (RuntimeException ex) {
            log.warn("Monitor step failed", ex);
            return List.of();
        }
    }

    /** Step 2 — health-check RUNNING processes and reflect UNHEALTHY into state. */
    private void healthCheck(List<ProcessStatus> statuses) {
        HealthChecker healthChecker = healthCheckerProvider.getIfAvailable();
        if (healthChecker == null) {
            return;
        }
        for (ProcessStatus status : statuses) {
            if (status.getState() != ProcessState.RUNNING) {
                continue;
            }
            try {
                HealthStatus health = healthChecker.checkHealth(status.getModelId());
                if (health == HealthStatus.UNHEALTHY) {
                    transition(status.getModelId(), ProcessState.UNHEALTHY);
                }
            } catch (HealthCheckException ex) {
                log.warn("Health check failed for {}", status.getModelId(), ex);
            }
        }
    }

    /** Step 3 — crash / resource detection drives auto-restart via health events. */
    private void detect(List<ProcessStatus> statuses) {
        HealthChecker healthChecker = healthCheckerProvider.getIfAvailable();
        if (healthChecker == null) {
            return;
        }
        for (ProcessStatus status : statuses) {
            try {
                healthChecker.detectCrash(status.getModelId());       // 프로세스가 살아있는가?
                healthChecker.detectResource(status.getModelId());    // CPU/메모리가 정상인가?
            } catch (HealthCheckException ex) {
                log.warn("Crash/resource detection failed for {}", status.getModelId(), ex);
            }
        }
    }

    /** Steps 4 & 5 — fetch and execute pending commands. */
    private void pollPendingCommands() {
        DBSyncManager dbSyncManager = dbSyncManagerProvider.getIfAvailable();
        if (dbSyncManager == null) {
            return;
        }
        try {
            dbSyncManager.pollPendingCommands();
        } catch (RuntimeException ex) {
            log.warn("Poll/execute commands step failed", ex);
        }
    }

    /** Step 6 — publish process status to the central DB (including heartbeat). */
    private void syncStatus() {
        DBSyncManager dbSyncManager = dbSyncManagerProvider.getIfAvailable();
        if (dbSyncManager == null) {
            return;
        }
        try {
            dbSyncManager.syncAgentStatus();
        } catch (RuntimeException ex) {
            log.warn("Sync status step failed", ex);
        }
    }

    private void transition(String modelId, ProcessState target) {
        try {
            stateManager.transitionTo(modelId, target);
        } catch (InvalidStateTransitionException ex) {
            log.debug("Skipped transition for {} -> {}: {}", modelId, target, ex.getMessage());
        }
    }
}
