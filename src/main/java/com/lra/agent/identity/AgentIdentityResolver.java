package com.lra.agent.identity;

import com.lra.db.entity.Agent;
import com.lra.db.repository.AgentRepository;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the current DB agent row from this machine's local IP address.
 */
@Component
public class AgentIdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(AgentIdentityResolver.class);

    private final LocalIpResolver localIpResolver;
    private final AgentRepository agentRepository;
    private final boolean autoRegister;

    public AgentIdentityResolver(LocalIpResolver localIpResolver, AgentRepository agentRepository) {
        this(localIpResolver, agentRepository, true);
    }

    @Autowired
    public AgentIdentityResolver(LocalIpResolver localIpResolver,
                                 AgentRepository agentRepository,
                                 @Value("${agent.auto-register:true}") boolean autoRegister) {
        this.localIpResolver = localIpResolver;
        this.agentRepository = agentRepository;
        this.autoRegister = autoRegister;
    }

    public Optional<Agent> resolveCurrentAgent() {
        List<String> localIps = localIpResolver.resolveAll();
        if (localIps.isEmpty()) {
            log.warn("Cannot resolve current agent: local IP is unavailable");
            return Optional.empty();
        }

        for (String localIp : localIps) {
            List<Agent> agents = agentRepository.findByIpAddress(localIp);
            if (agents.isEmpty()) {
                continue;
            }
            if (agents.size() > 1) {
                log.warn("Multiple TB_M26_AGENT rows found for IP {}; using {}", localIp, agents.get(0).getAgentId());
            }
            return Optional.of(agents.get(0));
        }
        log.warn("Cannot resolve current agent: no TB_M26_AGENT row for local IPs {}", localIps);
        return Optional.empty();
    }

    public Optional<String> resolveOrRegisterCurrentAgentId() {
        return resolveOrRegisterCurrentAgent().map(Agent::getAgentId);
    }

    public Optional<Agent> resolveOrRegisterCurrentAgent() {
        List<String> localIps = localIpResolver.resolveAll();
        if (localIps.isEmpty()) {
            log.warn("Cannot register current agent: local IP is unavailable");
            return Optional.empty();
        }

        Optional<Agent> existing = resolveCurrentAgent();
        if (existing.isPresent()) {
            Agent agent = existing.get();
            String currentIp = localIps.get(0);
            if (!currentIp.equals(agent.getIpAddress())) {
                agent.setIpAddress(currentIp);
                agentRepository.save(agent);
            }
            return existing;
        }

        if (!autoRegister) {
            return Optional.empty();
        }

        String hostname = localIpResolver.resolveHostname().orElse("local-agent");
        Agent agent = agentRepository.findByHostname(hostname).stream().findFirst().orElseGet(Agent::new);
        if (agent.getAgentId() == null || agent.getAgentId().isBlank()) {
            agent.setAgentId(buildAgentId(hostname, localIps.get(0)));
            agent.setInstalledAt(Instant.now());
        }
        agent.setHostname(hostname);
        agent.setOsType(System.getProperty("os.name", "unknown"));
        agent.setIpAddress(localIps.get(0));
        Agent saved = agentRepository.save(agent);
        log.info("Registered local Agent {} with IP {}", saved.getAgentId(), saved.getIpAddress());
        return Optional.of(saved);
    }

    private String buildAgentId(String hostname, String ipAddress) {
        String normalized = hostname.replaceAll("[^A-Za-z0-9-]", "-");
        String candidate = "agent-" + normalized;
        if (candidate.length() <= 64) {
            return candidate;
        }
        return "agent-" + ipAddress.replace('.', '-');
    }
}
