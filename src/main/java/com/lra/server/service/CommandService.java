package com.lra.server.service;

import com.lra.common.dto.CommandCreateRequest;
import com.lra.common.dto.CommandDto;
import com.lra.common.dto.ExecutionLogDto;
import com.lra.common.dto.ExecutionLogsResponse;
import com.lra.common.enums.CommandStatus;
import com.lra.db.entity.Command;
import com.lra.db.entity.ExecutionLog;
import com.lra.db.repository.CommandRepository;
import com.lra.db.repository.ExecutionLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Command registration (dispatch queue) and execution-log queries.
 */
@Service
public class CommandService {

    private static final String CREATED_BY = "central-server";

    private final CommandRepository commandRepository;
    private final ExecutionLogRepository executionLogRepository;

    public CommandService(CommandRepository commandRepository,
                          ExecutionLogRepository executionLogRepository) {
        this.commandRepository = commandRepository;
        this.executionLogRepository = executionLogRepository;
    }

    /**
     * Register a command for an agent to poll (POST /api/v1/commands).
     * Persisted with status PENDING; the agent picks it up on its next cycle.
     */
    @Transactional
    public CommandDto createCommand(CommandCreateRequest request) {
        Command command = new Command();
        command.setCommandId(UUID.randomUUID().toString());
        command.setAgentId(request.agentId());
        command.setProcessId(request.processId());
        command.setCommandType(request.commandType().name());
        command.setCommandStatus(CommandStatus.PENDING.name());
        command.setParameters(request.parameters());
        Command saved = commandRepository.save(command);
        return toCommandDto(saved);
    }

    /**
     * Query execution logs with pagination (GET /api/v1/execution-logs),
     * newest first. Filters by process when {@code processId} is supplied.
     */
    @Transactional(readOnly = true)
    public ExecutionLogsResponse getExecutionLogs(String processId, int limit, int offset) {
        int pageSize = Math.max(limit, 1);
        int pageNumber = Math.max(offset, 0) / pageSize;
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ExecutionLog> page = (processId == null || processId.isBlank())
                ? executionLogRepository.findAll(pageable)
                : executionLogRepository.findByProcessId(processId, pageable);

        return new ExecutionLogsResponse(
                page.getContent().stream().map(this::toLogDto).toList(),
                page.getTotalElements());
    }

    private CommandDto toCommandDto(Command command) {
        return new CommandDto(
                command.getCommandId(),
                command.getAgentId(),
                command.getProcessId(),
                command.getCommandType(),
                command.getCommandStatus(),
                null);
    }

    private ExecutionLogDto toLogDto(ExecutionLog log) {
        return new ExecutionLogDto(
                log.getLogId(),
                log.getAgentId(),
                log.getProcessId(),
                log.getCommandType(),
                log.getExecutionStatus(),
                log.getExitCode(),
                log.getStdoutPreview(),
                log.getStderrPreview(),
                log.getDurationSec(),
                null);
    }
}
