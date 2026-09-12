package com.lra.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Detailed view of a single agent (GET /api/v1/agents/{agentId}).
 */
public record AgentDetailDto(
        @JsonProperty("agent_id") String agentId,
        @JsonProperty("hostname") String hostname,
        @JsonProperty("os_type") String osType,
        @JsonProperty("ip_address") String ipAddress,
        @JsonProperty("spring_boot_version") String springBootVersion,
        @JsonProperty("status") String status,
        @JsonProperty("last_heartbeat") Instant lastHeartbeat,
        @JsonProperty("installed_at") Instant installedAt,
        @JsonProperty("processes") List<ProcessDto> processes
) {
}
