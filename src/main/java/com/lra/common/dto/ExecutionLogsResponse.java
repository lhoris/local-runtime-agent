package com.lra.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Paginated envelope for the execution log endpoint (GET /api/v1/execution-logs).
 */
public record ExecutionLogsResponse(
        @JsonProperty("logs") List<ExecutionLogDto> logs,
        @JsonProperty("total") long total
) {
}
