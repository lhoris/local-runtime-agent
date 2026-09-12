package com.lra.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Envelope for the agent list endpoint (GET /api/v1/agents).
 */
public record AgentsResponse(
        @JsonProperty("agents") List<AgentDto> agents
) {
}
