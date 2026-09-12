package com.lra.agent.loop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lra.agent.process.DefaultProcessManager;
import com.lra.agent.process.ProcessBuilderLauncher;
import com.lra.agent.state.DefaultStateManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Boots the agent loop with its real collaborators to verify Spring wiring and
 * that a full cycle runs (ARCHITECTURE.md §7.2).
 *
 * <p>Currently wires the components that exist: {@link AgentMainLoop},
 * {@link AgentLoopScheduler}, {@link DefaultStateManager},
 * {@link DefaultProcessManager} + {@link ProcessBuilderLauncher}. The optional
 * {@code HealthChecker} (#6) and {@code DBSyncManager} (#7) are absent, so those
 * steps are skipped via {@code ObjectProvider}.
 *
 * <p>TODO(#6/#7/#8): add the real HealthChecker / DBSyncManager / ParameterManager
 * beans to {@link TestBeans} and assert those steps execute end-to-end.
 */
@SpringBootTest(
        classes = {
                AgentMainLoop.class,
                AgentLoopScheduler.class,
                DefaultStateManager.class,
                DefaultProcessManager.class,
                ProcessBuilderLauncher.class,
                AgentMainLoopIntegrationTest.TestBeans.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class AgentMainLoopIntegrationTest {

    @Autowired
    private AgentMainLoop agentMainLoop;

    @Test
    void loopBeanIsWired() {
        assertNotNull(agentMainLoop, "AgentMainLoop should be wired from the context");
    }

    @Test
    void loopCycleCompletesWithinBudget() {
        long start = System.currentTimeMillis();
        agentMainLoop.agentLoop();
        long durationMs = System.currentTimeMillis() - start;
        assertThat(durationMs)
                .as("one cycle must finish well within the 30s polling interval")
                .isLessThan(30_000L);
    }

    @Test
    void loopIsResilientAcrossRepeatedCycles() {
        assertDoesNotThrow(() -> {
            agentMainLoop.agentLoop();
            agentMainLoop.agentLoop();
        });
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
