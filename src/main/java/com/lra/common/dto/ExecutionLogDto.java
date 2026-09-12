package com.lra.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Response view of an execution log entry (GET /api/v1/execution-logs).
 */
public record ExecutionLogDto(
        @JsonProperty("log_id") String logId,
        @JsonProperty("agent_id") String agentId,
        @JsonProperty("process_id") String processId,
        @JsonProperty("command_type") String commandType,
        @JsonProperty("execution_status") String executionStatus,
        @JsonProperty("exit_code") Integer exitCode,
        @JsonProperty("stdout_preview") String stdoutPreview,
        @JsonProperty("stderr_preview") String stderrPreview,
        @JsonProperty("duration_sec") Integer durationSec,
        @JsonProperty("created_at") Instant createdAt
) {
}
