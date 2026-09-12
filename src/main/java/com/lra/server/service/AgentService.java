package com.lra.server.service;

import com.lra.common.dto.AgentDetailDto;
import com.lra.common.dto.AgentDto;
import com.lra.common.dto.AgentsResponse;
import com.lra.common.dto.ProcessDto;
import com.lra.db.entity.AgentInfo;
import com.lra.db.entity.AgentStatus;
import com.lra.db.repository.AgentInfoRepository;
import com.lra.db.repository.AgentStatusRepository;
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

    private final AgentInfoRepository agentInfoRepository;
    private final AgentStatusRepository agentStatusRepository;

    public AgentService(AgentInfoRepository agentInfoRepository,
                        AgentStatusRepository agentStatusRepository) {
        this.agentInfoRepository = agentInfoRepository;
        this.agentStatusRepository = agentStatusRepository;
    }

    /**
     * List all agents with their processes (GET /api/v1/agents).
     */
    public AgentsResponse listAgents() {
        List<AgentDto> agents = agentInfoRepository.findAll().stream()
                .map(this::toAgentDto)
                .toList();
        return new AgentsResponse(agents);
    }

    /**
     * Fetch a single agent's detail (GET /api/v1/agents/{agentId}).
     */
    public AgentDetailDto getAgent(String agentId) {
        AgentInfo agent = agentInfoRepository.findById(agentId)
                .orElseThrow(() -> ApiException.notFound("Agent not found: " + agentId));
        List<ProcessDto> processes = toProcessDtos(agentId);
        return new AgentDetailDto(
                agent.getAgentId(),
                agent.getHostname(),
                agent.getOsType(),
                agent.getIpAddress(),
                agent.getSpringBootVersion(),
                deriveAgentStatus(agentId),
                lastHeartbeat(agentId),
                agent.getInstalledAt(),
                processes);
    }

    private AgentDto toAgentDto(AgentInfo agent) {
        return new AgentDto(
                agent.getAgentId(),
                agent.getHostname(),
                deriveAgentStatus(agent.getAgentId()),
                lastHeartbeat(agent.getAgentId()),
                toProcessDtos(agent.getAgentId()));
    }

    private List<ProcessDto> toProcessDtos(String agentId) {
        return agentStatusRepository.findByAgentId(agentId).stream()
                .map(this::toProcessDto)
                .toList();
    }

    private ProcessDto toProcessDto(AgentStatus status) {
        return new ProcessDto(
                status.getProcessId(),
                status.getState(),
                status.getPid(),
                status.getCpuPercent(),
                status.getMemoryMb());
    }

    private Instant lastHeartbeat(String agentId) {
        return agentStatusRepository.findByAgentId(agentId).stream()
                .map(AgentStatus::getLastHeartbeat)
                .filter(java.util.Objects::nonNull)
                .max(java.time.Instant::compareTo)
                .orElse(null);
    }

    /**
     * Roll the per-process health checks up into a single agent-level status.
     * Worst observed health wins; no status rows means UNKNOWN.
     */
    private String deriveAgentStatus(String agentId) {
        List<AgentStatus> statuses = agentStatusRepository.findByAgentId(agentId);
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
