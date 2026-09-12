package com.lra.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for updating a model parameter (POST /api/v1/parameters).
 */
public record ParameterUpdateRequest(
        @JsonProperty("process_id") @NotBlank String processId,
        @JsonProperty("param_key") @NotBlank String paramKey,
        @JsonProperty("param_value") @NotBlank String paramValue,
        @JsonProperty("apply_immediately") boolean applyImmediately
) {
}
