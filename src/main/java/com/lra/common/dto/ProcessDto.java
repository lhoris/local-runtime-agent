package com.lra.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Runtime status of a single managed process, embedded in agent responses.
 */
public record ProcessDto(
        @JsonProperty("process_id") String processId,
        @JsonProperty("state") String state,
        @JsonProperty("pid") Integer pid,
        @JsonProperty("cpu_percent") Float cpuPercent,
        @JsonProperty("memory_mb") Integer memoryMb
) {
}
