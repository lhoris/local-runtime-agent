package com.lra.agent.loop;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduling support so {@link AgentMainLoop#agentLoop()} runs
 * on the {@code agent.polling-interval-sec} cadence (ARCHITECTURE.md §7.2).
 */
@Configuration
@EnableScheduling
public class AgentLoopScheduler {
}
