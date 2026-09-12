package com.lra.agent.parameter;

/**
 * Value type of a model parameter, stored in model_parameters.param_type
 * (ARCHITECTURE.md §4.4).
 */
public enum ParameterType {
    STRING,
    INTEGER,
    FLOAT,
    BOOLEAN,
    JSON
}
