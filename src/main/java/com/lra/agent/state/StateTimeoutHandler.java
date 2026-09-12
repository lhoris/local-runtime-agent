package com.lra.agent.state;

import com.lra.common.enums.ProcessState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Forces transient states to {@link ProcessState#CRASHED} when they exceed their
 * allotted time (ARCHITECTURE.md §7.1):
 * <ul>
 *   <li>STARTING longer than 30s -> CRASHED</li>
 *   <li>STOPPING longer than 10s -> CRASHED (forceful terminate)</li>
 * </ul>
 * A pending timeout for a model is cancelled when a new transition is scheduled or
 * when the model reaches a stable state.
 */
@Component
public class StateTimeoutHandler {

    private static final Logger logger = LoggerFactory.getLogger(StateTimeoutHandler.class);

    static final Duration STARTING_TIMEOUT = Duration.ofSeconds(30);
    static final Duration STOPPING_TIMEOUT = Duration.ofSeconds(10);

    private final StateManager stateManager;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    @Autowired
    public StateTimeoutHandler(StateManager stateManager) {
        this(stateManager, Executors.newSingleThreadScheduledExecutor(daemonFactory()));
    }

    StateTimeoutHandler(StateManager stateManager, ScheduledExecutorService scheduler) {
        this.stateManager = stateManager;
        this.scheduler = scheduler;
    }

    /**
     * Schedules a forced transition to CRASHED if the model is still in {@code state}
     * after the state's timeout. No-op for states without a timeout.
     */
    public void scheduleTimeout(String modelId, ProcessState state) {
        Duration timeout = timeoutFor(state);
        if (timeout == null) {
            cancel(modelId);
            return;
        }
        scheduleTransition(modelId, state, ProcessState.CRASHED, timeout);
    }

    /**
     * Cancels any pending timeout for the model (e.g. once it reaches a stable state).
     */
    public void cancel(String modelId) {
        ScheduledFuture<?> future = pending.remove(modelId);
        if (future != null) {
            future.cancel(false);
        }
    }

    void scheduleTransition(String modelId, ProcessState expected,
                            ProcessState target, Duration timeout) {
        cancel(modelId);
        ScheduledFuture<?> future = scheduler.schedule(
                () -> fireTimeout(modelId, expected, target),
                timeout.toMillis(), TimeUnit.MILLISECONDS);
        pending.put(modelId, future);
    }

    private void fireTimeout(String modelId, ProcessState expected, ProcessState target) {
        pending.remove(modelId);
        if (stateManager.getCurrentState(modelId) != expected) {
            return;
        }
        try {
            logger.warn("State timeout for {} in {} -> forcing {}", modelId, expected, target);
            stateManager.transitionTo(modelId, target);
        } catch (InvalidStateTransitionException ex) {
            logger.warn("Timeout transition {} -> {} rejected for {}", expected, target, modelId, ex);
        }
    }

    private Duration timeoutFor(ProcessState state) {
        return switch (state) {
            case STARTING -> STARTING_TIMEOUT;
            case STOPPING -> STOPPING_TIMEOUT;
            default -> null;
        };
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    private static ThreadFactory daemonFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "state-timeout-handler");
            thread.setDaemon(true);
            return thread;
        };
    }
}
