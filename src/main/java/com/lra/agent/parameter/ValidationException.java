package com.lra.agent.parameter;

/**
 * Thrown when a parameter value fails validation for its key
 * (ARCHITECTURE.md §4.4, §7.5).
 */
public class ValidationException extends Exception {

    public ValidationException(String message) {
        super(message);
    }
}
