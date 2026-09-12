package com.lra.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Standard error body returned by the REST layer.
 */
public record ErrorResponse(
        @JsonProperty("status") int status,
        @JsonProperty("error") String error,
        @JsonProperty("message") String message,
        @JsonProperty("details") List<String> details,
        @JsonProperty("timestamp") Instant timestamp
) {
    public static ErrorResponse of(int status, String error, String message, List<String> details) {
        return new ErrorResponse(status, error, message, details, Instant.now());
    }
}
