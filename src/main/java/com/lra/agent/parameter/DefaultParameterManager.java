package com.lra.agent.parameter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lra.db.entity.ModelParameter;
import com.lra.db.repository.ModelParameterRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default {@link ParameterManager} backed by the model_parameters table
 * (ARCHITECTURE.md §4.4, §7.5).
 */
@Component
public class DefaultParameterManager implements ParameterManager {

    private static final String ENV_PREFIX = "ENV_";
    private static final String ARG_PREFIX = "ARG_";

    private final ModelParameterRepository parameterRepository;
    private final ObjectMapper objectMapper;

    public DefaultParameterManager(ModelParameterRepository parameterRepository,
                                   ObjectMapper objectMapper) {
        this.parameterRepository = parameterRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> loadParameters(String processId) {
        List<ModelParameter> records =
                parameterRepository.findByProcessIdAndIsActive(processId, true);

        Map<String, Object> params = new HashMap<>();
        for (ModelParameter record : records) {
            params.put(record.getParamKey(),
                    parseValue(record.getParamValue(), parseType(record.getParamType())));
        }
        return params;
    }

    @Override
    public void applyParameters(String processId, Map<String, String> envVars, List<String> args) {
        Map<String, Object> params = loadParameters(processId);

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object rawValue = entry.getValue();
            if (rawValue == null) {
                continue;
            }
            String value = rawValue.toString();

            if (key.startsWith(ENV_PREFIX)) {
                envVars.put(key.substring(ENV_PREFIX.length()), value);
            } else if (key.startsWith(ARG_PREFIX)) {
                args.add("--" + key.substring(ARG_PREFIX.length()));
                args.add(value);
            }
        }
    }

    @Override
    public void updateParameter(String processId, String paramKey, String paramValue)
            throws ValidationException {
        validateParameter(paramKey, paramValue);

        List<ModelParameter> existing = parameterRepository
                .findByProcessIdAndParamKeyAndIsActive(processId, paramKey, true);
        for (ModelParameter record : existing) {
            record.setIsActive(false);
            parameterRepository.save(record);
        }

        ModelParameter newParam = new ModelParameter();
        newParam.setParamId(UUID.randomUUID().toString());
        newParam.setProcessId(processId);
        newParam.setParamKey(paramKey);
        newParam.setParamValue(paramValue);
        newParam.setParamType(inferType(paramValue).name());
        newParam.setVersion(getLatestVersion(processId, paramKey) + 1);
        newParam.setIsActive(true);

        parameterRepository.save(newParam);
    }

    @Override
    public void validateParameter(String paramKey, String paramValue) throws ValidationException {
        switch (paramKey) {
            case "polling_interval_sec":
                requireIntInRange(paramKey, paramValue, 10, 300);
                break;
            case "timeout_sec":
                requireIntInRange(paramKey, paramValue, 30, 3600);
                break;
            case "max_restart_attempts":
                requireIntInRange(paramKey, paramValue, 1, 10);
                break;
            default:
                // No specific rule for this key; accept as-is.
        }
    }

    private void requireIntInRange(String key, String value, int min, int max)
            throws ValidationException {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ValidationException(key + " must be an integer, got: " + value);
        }
        if (parsed < min || parsed > max) {
            throw new ValidationException(
                    String.format("%s must be %d-%d, got: %d", key, min, max, parsed));
        }
    }

    private int getLatestVersion(String processId, String paramKey) {
        return parameterRepository
                .findFirstByProcessIdAndParamKeyOrderByVersionDesc(processId, paramKey)
                .map(ModelParameter::getVersion)
                .orElse(0);
    }

    ParameterType inferType(String value) {
        if (value == null) {
            return ParameterType.STRING;
        }
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return ParameterType.BOOLEAN;
        }
        if (value.matches("^-?\\d+$")) {
            return ParameterType.INTEGER;
        }
        if (value.matches("^-?\\d+\\.\\d+$")) {
            return ParameterType.FLOAT;
        }
        if (value.startsWith("{") || value.startsWith("[")) {
            return ParameterType.JSON;
        }
        return ParameterType.STRING;
    }

    private ParameterType parseType(String type) {
        if (type == null) {
            return ParameterType.STRING;
        }
        try {
            return ParameterType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return ParameterType.STRING;
        }
    }

    Object parseValue(String value, ParameterType type) {
        if (value == null) {
            return null;
        }
        switch (type) {
            case INTEGER:
                return Integer.parseInt(value);
            case FLOAT:
                return Float.parseFloat(value);
            case BOOLEAN:
                return Boolean.parseBoolean(value);
            case JSON:
                try {
                    return objectMapper.readValue(value, Object.class);
                } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException("Invalid JSON parameter value: " + value, e);
                }
            case STRING:
            default:
                return value;
        }
    }
}
