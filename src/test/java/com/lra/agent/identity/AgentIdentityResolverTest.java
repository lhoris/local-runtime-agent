package com.lra.agent.identity;

import com.lra.db.entity.Agent;
import com.lra.db.repository.AgentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentIdentityResolverTest {

    @Test
    void selectsRegisteredAgentFromAnyLocalIp() {
        LocalIpResolver localIpResolver = mock(LocalIpResolver.class);
        AgentRepository agentRepository = mock(AgentRepository.class);
        Agent agent = new Agent();
        agent.setAgentId("agent-2");

        when(localIpResolver.resolveAll()).thenReturn(List.of("192.168.56.1", "10.0.0.12"));
        when(agentRepository.findByIpAddress("192.168.56.1")).thenReturn(List.of());
        when(agentRepository.findByIpAddress("10.0.0.12")).thenReturn(List.of(agent));

        AgentIdentityResolver resolver = new AgentIdentityResolver(localIpResolver, agentRepository);

        assertEquals(Optional.of("agent-2"), resolver.resolveOrRegisterCurrentAgentId());
        verify(agentRepository).findByIpAddress("10.0.0.12");
    }

    @Test
    void registersAgentWhenNoMatchingRowExists() {
        LocalIpResolver localIpResolver = mock(LocalIpResolver.class);
        AgentRepository agentRepository = mock(AgentRepository.class);
        when(localIpResolver.resolveAll()).thenReturn(List.of("10.0.0.12"));
        when(agentRepository.findByIpAddress("10.0.0.12")).thenReturn(List.of());
        when(localIpResolver.resolveHostname()).thenReturn(Optional.of("test-host"));
        when(agentRepository.findByHostname("test-host")).thenReturn(List.of());
        Agent saved = new Agent();
        saved.setAgentId("agent-test-host");
        saved.setIpAddress("10.0.0.12");
        when(agentRepository.save(org.mockito.ArgumentMatchers.any(Agent.class))).thenReturn(saved);

        AgentIdentityResolver resolver = new AgentIdentityResolver(localIpResolver, agentRepository);

        assertEquals(Optional.of("agent-test-host"), resolver.resolveOrRegisterCurrentAgentId());
        verify(agentRepository).save(org.mockito.ArgumentMatchers.any(Agent.class));
    }
}
