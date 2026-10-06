package com.lra.agent.identity;

import com.lra.db.entity.Agent;
import com.lra.db.repository.AgentRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resolves the current DB agent row from this machine's local IP address.
 */
@Component
public class AgentIdentityResolver {

    private static final Logger log = LoggerFactory.getLogger(AgentIdentityResolver.class);

    private final LocalIpResolver localIpResolver;
    private final AgentRepository agentRepository;

    public AgentIdentityResolver(LocalIpResolver localIpResolver, AgentRepository agentRepository) {
        this.localIpResolver = localIpResolver;
        this.agentRepository = agentRepository;
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

    public Optional<String> resolveCurrentAgentId() {
        return resolveCurrentAgent().map(Agent::getAgentId);
    }
}
