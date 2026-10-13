package com.lra.server.service;

import com.lra.server.dto.AgentDetailDto;
import com.lra.server.dto.AgentDto;
import com.lra.server.dto.AgentsResponse;
import com.lra.server.dto.ProcessDto;
import com.lra.db.entity.Agent;
import com.lra.db.entity.ModelProcess;
import com.lra.db.repository.AgentRepository;
import com.lra.db.repository.ModelProcessRepository;
import com.lra.server.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Read access to agent inventory and current process status.
 */
@Service
@Transactional(readOnly = true)
public class AgentService {

    private final AgentRepository agentRepository;
    private final ModelProcessRepository modelProcessRepository;

    public AgentService(AgentRepository agentRepository,
                        ModelProcessRepository modelProcessRepository) {
        this.agentRepository = agentRepository;
        this.modelProcessRepository = modelProcessRepository;
    }

    /**
     * List all agents with their processes (GET /api/v1/agents).
     */
    public AgentsResponse listAgents() {
        List<AgentDto> agents = agentRepository.findAll().stream()
                .map(this::toAgentDto)
                .toList();
        return new AgentsResponse(agents);
    }

    /**
     * Fetch a single agent's detail (GET /api/v1/agents/{agentId}).
     */
    public AgentDetailDto getAgent(String agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> ApiException.notFound("Agent not found: " + agentId));
        List<ProcessDto> processes = toProcessDtos(agentId);
        return new AgentDetailDto(
                agent.getAgentId(),
                agent.getHostname(),
                agent.getOsType(),
                agent.getIpAddress(),
                deriveAgentStatus(agentId),
                lastHeartbeat(agentId),
                agent.getInstalledAt() == null ? null : agent.getInstalledAt().toInstant(),
                processes);
    }

    private AgentDto toAgentDto(Agent agent) {
        return new AgentDto(
                agent.getAgentId(),
                agent.getHostname(),
                deriveAgentStatus(agent.getAgentId()),
                lastHeartbeat(agent.getAgentId()),
                toProcessDtos(agent.getAgentId()));
    }

    private List<ProcessDto> toProcessDtos(String agentId) {
        return modelProcessRepository.findByAgentId(agentId).stream()
                .map(this::toProcessDto)
                .toList();
    }

    private ProcessDto toProcessDto(ModelProcess status) {
        return new ProcessDto(
                status.getProcessId(),
                status.getState(),
                status.getPid(),
                null,
                null);
    }

    private Instant lastHeartbeat(String agentId) {
        return modelProcessRepository.findByAgentId(agentId).stream()
                .map(ModelProcess::getLastHeartbeat)
                .filter(java.util.Objects::nonNull)
                .max(java.sql.Timestamp::compareTo)
                .map(java.sql.Timestamp::toInstant)
                .orElse(null);
    }

    /**
     * Roll the per-process health checks up into a single agent-level status.
     * Worst observed health wins; no status rows means UNKNOWN.
     */
    private String deriveAgentStatus(String agentId) {
        List<ModelProcess> statuses = modelProcessRepository.findByAgentId(agentId);
        if (statuses.isEmpty()) {
            return "UNKNOWN";
        }
        boolean anyUnhealthy = statuses.stream()
                .anyMatch(s -> "UNHEALTHY".equalsIgnoreCase(s.getHealthStatus()));
        if (anyUnhealthy) {
            return "UNHEALTHY";
        }
        boolean anyUnknown = statuses.stream()
                .anyMatch(s -> s.getHealthStatus() == null || "UNKNOWN".equalsIgnoreCase(s.getHealthStatus()));
        return anyUnknown ? "DEGRADED" : "HEALTHY";
    }
}
