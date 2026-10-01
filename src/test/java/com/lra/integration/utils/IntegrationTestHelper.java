package com.lra.integration.utils;

import com.lra.db.entity.Agent;
import com.lra.db.entity.ModelProcess;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Shared fixtures and helpers for the system integration tests: entity builders
 * for seeding the shared DB and a small polling primitive for asserting on
 * asynchronous agent behaviour (in lieu of an awaitility dependency).
 */
public final class IntegrationTestHelper {

    /** Classpath location of the long-running test process script. */
    public static final String TEST_PROCESS_SCRIPT = "fixtures/test_process.py";

    private IntegrationTestHelper() {
    }

    public static Agent newAgent(String agentId, String hostname) {
        Agent agent = new Agent();
        agent.setAgentId(agentId);
        agent.setHostname(hostname);
        agent.setOsType("LINUX");
        agent.setIpAddress("127.0.0.1");
        return agent;
    }

    public static ModelProcess newProcess(String processId, String agentId, String modelType) {
        ModelProcess config = new ModelProcess();
        config.setProcessId(processId);
        config.setAgentId(agentId);
        config.setModelName("model-" + processId);
        config.setModelType(modelType);
        config.setAutoRestart(true);
        config.setMaxRestartAttempts(3);
        config.setRestartDelaySec(1);
        return config;
    }

    public static ModelProcess newStatus(String processId, String agentId,
                                         String state, String healthStatus, Integer pid) {
        ModelProcess status = new ModelProcess();
        status.setProcessId(processId);
        status.setAgentId(agentId);
        status.setState(state);
        status.setHealthStatus(healthStatus);
        status.setPid(pid);
        status.setCrashCount(0);
        return status;
    }

    /**
     * Poll {@code condition} until it returns true or {@code timeout} elapses.
     *
     * @throws AssertionError if the condition never became true in time
     */
    public static void awaitUntil(Duration timeout, Duration pollInterval, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep(pollInterval);
        }
        if (!condition.getAsBoolean()) {
            throw new AssertionError("Condition not met within " + timeout);
        }
    }

    public static void awaitUntil(Duration timeout, BooleanSupplier condition) {
        awaitUntil(timeout, Duration.ofMillis(100), condition);
    }

    public static String uniqueId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while polling", e);
        }
    }
}
