package com.lra.server.controller;

import com.lra.common.dto.AgentDetailDto;
import com.lra.common.dto.AgentsResponse;
import com.lra.common.dto.CommandCreateRequest;
import com.lra.common.dto.CommandDto;
import com.lra.common.dto.ExecutionLogsResponse;
import com.lra.common.dto.ParameterDto;
import com.lra.common.dto.ParameterUpdateRequest;
import com.lra.server.service.AgentService;
import com.lra.server.service.CommandService;
import com.lra.server.service.ParameterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Central Server external REST API (ARCHITECTURE.md §6.2).
 */
@RestController
@RequestMapping("/api/v1")
public class ApiController {

    private final AgentService agentService;
    private final CommandService commandService;
    private final ParameterService parameterService;

    public ApiController(AgentService agentService,
                         CommandService commandService,
                         ParameterService parameterService) {
        this.agentService = agentService;
        this.commandService = commandService;
        this.parameterService = parameterService;
    }

    @GetMapping("/agents")
    public AgentsResponse listAgents() {
        return agentService.listAgents();
    }

    @GetMapping("/agents/{agentId}")
    public AgentDetailDto getAgent(@PathVariable String agentId) {
        return agentService.getAgent(agentId);
    }

    @PostMapping("/commands")
    public ResponseEntity<CommandDto> createCommand(@Valid @RequestBody CommandCreateRequest request) {
        CommandDto created = commandService.createCommand(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/parameters")
    public ResponseEntity<ParameterDto> updateParameter(@Valid @RequestBody ParameterUpdateRequest request) {
        ParameterDto updated = parameterService.updateParameter(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(updated);
    }

    @GetMapping("/execution-logs")
    public ExecutionLogsResponse getExecutionLogs(
            @RequestParam(name = "process_id", required = false) String processId,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset) {
        return commandService.getExecutionLogs(processId, limit, offset);
    }

    @GetMapping("/parameters/{processId}")
    public List<ParameterDto> getParameters(@PathVariable String processId) {
        return parameterService.getParameters(processId);
    }
}
