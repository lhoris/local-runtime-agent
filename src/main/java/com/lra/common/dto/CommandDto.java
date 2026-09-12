package com.lra.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Response view of a queued command (POST /api/v1/commands).
 */
public record CommandDto(
        @JsonProperty("command_id") String commandId,
        @JsonProperty("agent_id") String agentId,
        @JsonProperty("process_id") String processId,
        @JsonProperty("command_type") String commandType,
        @JsonProperty("status") String status,
        @JsonProperty("created_at") Instant createdAt
) {
}
