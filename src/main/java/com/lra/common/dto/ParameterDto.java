package com.lra.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Response view of a model parameter
 * (POST /api/v1/parameters, GET /api/v1/parameters/{processId}).
 */
public record ParameterDto(
        @JsonProperty("param_id") String paramId,
        @JsonProperty("process_id") String processId,
        @JsonProperty("param_key") String paramKey,
        @JsonProperty("param_value") String paramValue,
        @JsonProperty("param_type") String paramType,
        @JsonProperty("version") Integer version,
        @JsonProperty("is_active") Boolean isActive,
        @JsonProperty("updated_at") Instant updatedAt
) {
}
