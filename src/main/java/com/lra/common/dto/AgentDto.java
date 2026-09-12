package com.lra.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Summary view of an agent and its processes (GET /api/v1/agents).
 */
public record AgentDto(
        @JsonProperty("agent_id") String agentId,
        @JsonProperty("hostname") String hostname,
        @JsonProperty("status") String status,
        @JsonProperty("last_heartbeat") Instant lastHeartbeat,
        @JsonProperty("processes") List<ProcessDto> processes
) {
}
