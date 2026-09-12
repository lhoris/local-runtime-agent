package com.lra.agent.health;

import com.lra.agent.process.ProcessManager;
import com.lra.agent.process.ProcessStatus;
import com.lra.agent.state.InvalidStateTransitionException;
import com.lra.agent.state.StateManager;
import com.lra.common.enums.HealthStatus;
import com.lra.common.enums.ProcessState;
import com.lra.db.entity.ProcessConfig;
import com.lra.db.repository.ProcessConfigRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PID-based health checker (ARCHITECTURE.md §4.5, §7.4). Determines liveness of
 * managed processes and drives CRASHED / DEGRADED state transitions when a
 * crash or resource-limit breach is detected.
 */
@Component
public class DefaultHealthChecker implements HealthChecker {

    private static final Logger log = LoggerFactory.getLogger(DefaultHealthChecker.class);

    private final ProcessManager processManager;
    private final ProcessConfigRepository processConfigRepository;
    private final CrashDetector crashDetector;
    private final StateManager stateManager;

    public DefaultHealthChecker(ProcessManager processManager,
                                ProcessConfigRepository processConfigRepository,
                                CrashDetector crashDetector,
                                StateManager stateManager) {
        this.processManager = processManager;
        this.processConfigRepository = processConfigRepository;
        this.crashDetector = crashDetector;
        this.stateManager = stateManager;
    }

    @Override
    public HealthStatus checkHealth(String modelId) throws HealthCheckException {
        ProcessStatus status = processManager.getStatus(modelId);

        if (status == null || status.getState() != ProcessState.RUNNING) {
            return HealthStatus.UNKNOWN;
        }

        Integer pid = status.getPid();
        if (pid == null) {
            return HealthStatus.UNKNOWN;
        }

        try {
            Optional<ProcessHandle> handle = ProcessHandle.of(pid);
            if (handle.isPresent() && handle.get().isAlive()) {
                return HealthStatus.HEALTHY;
            }
            return HealthStatus.UNHEALTHY;
        } catch (Exception e) {
            log.error("Health check failed for {} (pid={})", modelId, pid, e);
            return HealthStatus.UNKNOWN;
        }
    }

    @Override
    public void detectCrash(String modelId) throws HealthCheckException {
        ProcessStatus status = processManager.getStatus(modelId);

        if (!crashDetector.isCrashed(modelId, status)) {
            return;
        }

        log.warn("Process {} crashed (pid={})", modelId,
            status != null ? status.getPid() : null);
        transition(modelId, ProcessState.CRASHED);
    }

    @Override
    public void detectResource(String modelId) throws HealthCheckException {
        ProcessStatus status = processManager.getStatus(modelId);
        if (status == null || status.getState() != ProcessState.RUNNING) {
            return;
        }

        ProcessConfig config = processConfigRepository.findById(modelId).orElse(null);
        if (config == null) {
            return;
        }

        boolean exceeded = false;

        Integer cpuLimit = config.getCpuLimitPercent();
        Float cpu = status.getCpuPercent();
        if (cpuLimit != null && cpuLimit > 0 && cpu != null && cpu > cpuLimit) {
            log.warn("CPU limit exceeded for {}: {}% > {}%", modelId, cpu, cpuLimit);
            exceeded = true;
        }

        Integer memLimit = config.getMemoryLimitMb();
        Integer mem = status.getMemoryMb();
        if (memLimit != null && memLimit > 0 && mem != null && mem > memLimit) {
            log.warn("Memory limit exceeded for {}: {}MB > {}MB", modelId, mem, memLimit);
            exceeded = true;
        }

        if (exceeded) {
            transition(modelId, ProcessState.DEGRADED);
        }
    }

    private void transition(String modelId, ProcessState target) {
        try {
            stateManager.transitionTo(modelId, target);
        } catch (InvalidStateTransitionException e) {
            log.warn("Could not transition {} to {}: {}", modelId, target, e.getMessage());
        }
    }
}
