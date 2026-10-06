package com.lra.agent.identity;

import com.lra.db.entity.Agent;
import com.lra.db.repository.AgentRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({AgentIdentityResolver.class, LocalIpResolver.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "agent.auto-register=true")
class AgentIdentityResolverIntegrationTest {

    @Autowired
    private AgentIdentityResolver resolver;

    @Autowired
    private AgentRepository agentRepository;

    @Test
    void registersAndReadsBackTheLocalAgent() {
        Optional<String> agentId = resolver.resolveOrRegisterCurrentAgentId();

        assertThat(agentId).isPresent();
        Agent saved = agentRepository.findById(agentId.get()).orElseThrow();
        assertThat(saved.getAgentId()).isEqualTo(agentId.get());
        assertThat(saved.getIpAddress()).isNotBlank();
        assertThat(saved.getHostname()).isNotBlank();
    }
}
