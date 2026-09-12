package com.lra.server.service;

import com.lra.common.dto.CommandCreateRequest;
import com.lra.common.dto.ParameterDto;
import com.lra.common.dto.ParameterUpdateRequest;
import com.lra.common.enums.CommandType;
import com.lra.db.entity.ModelParameter;
import com.lra.db.entity.ProcessConfig;
import com.lra.db.repository.ModelParameterRepository;
import com.lra.db.repository.ProcessConfigRepository;
import com.lra.server.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Model parameter storage and retrieval.
 */
@Service
public class ParameterService {

    private final ModelParameterRepository parameterRepository;
    private final ProcessConfigRepository processConfigRepository;
    private final CommandService commandService;

    public ParameterService(ModelParameterRepository parameterRepository,
                            ProcessConfigRepository processConfigRepository,
                            CommandService commandService) {
        this.parameterRepository = parameterRepository;
        this.processConfigRepository = processConfigRepository;
        this.commandService = commandService;
    }

    /**
     * Create a new active version of a model parameter (POST /api/v1/parameters).
     * The prior active version(s) for the same key are deactivated. When
     * {@code applyImmediately} is set, a RESTART command is enqueued so the
     * process reloads the value (ARCHITECTURE.md §7.5).
     */
    @Transactional
    public ParameterDto updateParameter(ParameterUpdateRequest request) {
        int nextVersion = parameterRepository
                .findFirstByProcessIdAndParamKeyOrderByVersionDesc(request.processId(), request.paramKey())
                .map(mp -> mp.getVersion() == null ? 1 : mp.getVersion() + 1)
                .orElse(1);

        List<ModelParameter> active = parameterRepository
                .findByProcessIdAndParamKeyAndIsActive(request.processId(), request.paramKey(), true);
        active.forEach(mp -> mp.setIsActive(false));
        parameterRepository.saveAll(active);

        ModelParameter param = new ModelParameter();
        param.setParamId(UUID.randomUUID().toString());
        param.setProcessId(request.processId());
        param.setParamKey(request.paramKey());
        param.setParamValue(request.paramValue());
        param.setParamType(inferType(request.paramValue()));
        param.setVersion(nextVersion);
        param.setIsActive(true);
        ModelParameter saved = parameterRepository.save(param);

        if (request.applyImmediately()) {
            enqueueRestart(request.processId());
        }
        return toDto(saved);
    }

    /**
     * List the active parameters for a process (GET /api/v1/parameters/{processId}).
     */
    @Transactional(readOnly = true)
    public List<ParameterDto> getParameters(String processId) {
        return parameterRepository.findByProcessIdAndIsActive(processId, true).stream()
                .map(this::toDto)
                .toList();
    }

    private void enqueueRestart(String processId) {
        ProcessConfig config = processConfigRepository.findById(processId)
                .orElseThrow(() -> ApiException.badRequest(
                        "Cannot apply immediately: process not found: " + processId));
        commandService.createCommand(new CommandCreateRequest(
                config.getAgentId(), processId, CommandType.RESTART, Map.of()));
    }

    private ParameterDto toDto(ModelParameter param) {
        return new ParameterDto(
                param.getParamId(),
                param.getProcessId(),
                param.getParamKey(),
                param.getParamValue(),
                param.getParamType(),
                param.getVersion(),
                param.getIsActive(),
                param.getUpdatedAt());
    }

    private String inferType(String value) {
        if (value == null) {
            return "STRING";
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return "BOOLEAN";
        }
        if (value.matches("-?\\d+")) {
            return "INTEGER";
        }
        if (value.matches("-?\\d*\\.\\d+")) {
            return "FLOAT";
        }
        return "STRING";
    }
}
