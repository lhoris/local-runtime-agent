package com.lra.agent.parameter;

import java.util.List;
import java.util.Map;

/**
 * Loads, validates, versions and applies tuning parameters for a model process
 * (ARCHITECTURE.md §4.4, §7.5).
 */
public interface ParameterManager {

    /**
     * Loads the active parameters for {@code processId}, parsed to their declared type.
     */
    Map<String, Object> loadParameters(String processId);

    /**
     * Applies the active parameters to a launch: {@code ENV_}-prefixed keys become
     * environment variables and {@code ARG_}-prefixed keys become {@code --key value}
     * command arguments (ARCHITECTURE.md §7.5).
     */
    void applyParameters(String processId, Map<String, String> envVars, List<String> args);

    /**
     * Validates then persists a new active version of {@code paramKey}, deactivating
     * the prior active version.
     *
     * @throws ValidationException if the value is invalid for the key
     */
    void updateParameter(String processId, String paramKey, String paramValue) throws ValidationException;

    /**
     * Validates a value for the given key against its range/format rules.
     *
     * @throws ValidationException if the value is invalid
     */
    void validateParameter(String paramKey, String paramValue) throws ValidationException;
}
