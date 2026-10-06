package com.lra.agent.loop;

import com.lra.agent.health.HealthCheckException;
import com.lra.agent.health.HealthChecker;
import com.lra.agent.identity.AgentIdentityResolver;
import com.lra.agent.process.ProcessManager;
import com.lra.agent.process.ProcessMonitor;
import com.lra.agent.process.ProcessStatus;
import com.lra.agent.state.InvalidStateTransitionException;
import com.lra.agent.state.StateManager;
import com.lra.agent.sync.DBSyncManager;
import com.lra.common.enums.HealthStatus;
import com.lra.common.enums.ProcessState;
import com.lra.db.entity.ModelProcess;
import com.lra.db.repository.ModelProcessRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic agent loop.
 *
 * <p>The order is intentionally plain: load the model-process rows this agent
 * owns, check those rows against the local runtime, recover what can be recovered,
 * execute new commands, then publish the latest status.
 */
@Component
public class AgentMainLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentMainLoop.class);

    private final AgentIdentityResolver agentIdentityResolver;
    private final ProcessManager processManager;
    private final ModelProcessRepository modelProcessRepository;
    private final StateManager stateManager;
    private final ObjectProvider<HealthChecker> healthCheckerProvider;
    private final ObjectProvider<ProcessMonitor> processMonitorProvider;
    private final DBSyncManager dbSyncManager;

    public AgentMainLoop(AgentIdentityResolver agentIdentityResolver,
                         ProcessManager processManager,
                         ModelProcessRepository modelProcessRepository,
                         StateManager stateManager,
                         ObjectProvider<HealthChecker> healthCheckerProvider,
                         ObjectProvider<ProcessMonitor> processMonitorProvider,
                         DBSyncManager dbSyncManager) {
        this.agentIdentityResolver = agentIdentityResolver;
        this.processManager = processManager;
        this.modelProcessRepository = modelProcessRepository;
        this.stateManager = stateManager;
        this.healthCheckerProvider = healthCheckerProvider;
        this.processMonitorProvider = processMonitorProvider;
        this.dbSyncManager = dbSyncManager;
    }

    @Scheduled(fixedRateString = "${agent.polling-interval-sec:30}000")
    public void agentLoop() {
        long startedAt = System.currentTimeMillis();
        log.info("Agent loop cycle started");

        Optional<String> agentId = agentIdentityResolver.resolveOrRegisterCurrentAgentId();
        if (agentId.isEmpty()) {
            log.warn("Agent loop skipped: local Agent could not be resolved or registered");
            return;
        }

        Optional<List<ModelProcess>> loadedProcesses = loadProcessDefinitionsFromDb(agentId.get());
        if (loadedProcesses.isEmpty()) {
            log.warn("Agent loop skipped: model process query failed for agent {}", agentId.get());
            return;
        }

        List<ModelProcess> processes = loadedProcesses.get();
        processManager.reconcileDefinitions(processes);
        List<ProcessStatus> statuses = checkManagedProcessSafety(processes);
        healthCheckRunningProcesses(statuses);
        detectUnsafeProcesses(statuses);
        recoverCrashedProcesses();
        pollPendingCommands();
        publishCurrentStatus();

        log.info("Agent loop cycle completed in {} ms", System.currentTimeMillis() - startedAt);
    }

    /** Step 1: read the processes this agent is responsible for supervising. */
    private Optional<List<ModelProcess>> loadProcessDefinitionsFromDb(String agentId) {
        try {
            List<ModelProcess> definitions = modelProcessRepository.findByAgentId(agentId);
            log.debug("Loaded {} model process definition(s) for agent {}", definitions.size(), agentId);
            return Optional.of(definitions);
        } catch (RuntimeException ex) {
            log.warn("Loading model process definitions failed", ex);
            return Optional.empty();
        }
    }

    /** Step 2: compare the in-memory view with the actual local process state. */
    private List<ProcessStatus> checkManagedProcessSafety(List<ModelProcess> processes) {
        List<ProcessStatus> statuses = new ArrayList<>();
        for (ModelProcess process : processes) {
            try {
                ProcessStatus status = processManager.checkStatus(process);
                if (status != null) {
                    log.debug("Process {} state: {}", status.getModelId(), status.getState());
                    statuses.add(status);
                }
            } catch (RuntimeException ex) {
                log.warn("Process safety check failed for {}", process.getProcessId(), ex);
            }
        }
        return statuses;
    }

    /** Step 3: ask each running process whether it is healthy. */
    private void healthCheckRunningProcesses(List<ProcessStatus> statuses) {
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

    /** Step 4: detect crashes and resource-limit breaches. */
    private void detectUnsafeProcesses(List<ProcessStatus> statuses) {
        HealthChecker healthChecker = healthCheckerProvider.getIfAvailable();
        if (healthChecker == null) {
            return;
        }
        for (ProcessStatus status : statuses) {
            try {
                healthChecker.detectCrash(status.getModelId());
                healthChecker.detectResource(status.getModelId());
            } catch (HealthCheckException ex) {
                log.warn("Crash/resource detection failed for {}", status.getModelId(), ex);
            }
        }
    }

    /** Step 5: restart crashed processes when their row allows auto-restart. */
    private void recoverCrashedProcesses() {
        ProcessMonitor processMonitor = processMonitorProvider.getIfAvailable();
        if (processMonitor == null) {
            return;
        }
        try {
            processMonitor.autoRestart();
        } catch (RuntimeException ex) {
            log.warn("Auto-restart step failed", ex);
        }
    }

    /** Step 6: fetch and execute pending commands. */
    private void pollPendingCommands() {
        try {
            dbSyncManager.pollPendingCommands();
        } catch (RuntimeException ex) {
            log.warn("Poll/execute commands step failed", ex);
        }
    }

    /** Step 7: publish process status to the central DB, including heartbeat. */
    private void publishCurrentStatus() {
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
