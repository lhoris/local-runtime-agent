package com.lra.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lra.common.enums.CommandType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request body for registering a command (POST /api/v1/commands).
 */
public record CommandCreateRequest(
        @JsonProperty("agent_id") @NotBlank String agentId,
        @JsonProperty("process_id") String processId,
        @JsonProperty("command_type") @NotNull CommandType commandType,
        @JsonProperty("parameters") Map<String, Object> parameters
) {
}
