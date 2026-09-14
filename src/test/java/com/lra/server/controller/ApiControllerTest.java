package com.lra.server.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lra.db.entity.Agent;
import com.lra.db.entity.ModelProcess;
import com.lra.db.entity.ExecutionLog;
import com.lra.db.entity.ProcessConfig;
import com.lra.db.repository.AgentRepository;
import com.lra.db.repository.ModelProcessRepository;
import com.lra.db.repository.CommandRepository;
import com.lra.db.repository.ExecutionLogRepository;
import com.lra.db.repository.ModelParameterRepository;
import com.lra.db.repository.ProcessConfigRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AgentRepository agentRepository;

    @Autowired
    ModelProcessRepository modelProcessRepository;

    @Autowired
    CommandRepository commandRepository;

    @Autowired
    ModelParameterRepository modelParameterRepository;

    @Autowired
    ExecutionLogRepository executionLogRepository;

    @Autowired
    ProcessConfigRepository processConfigRepository;

    @Test
    void listAgentsReturnsAgentsWithProcessesInSnakeCase() throws Exception {
        seedAgent("agent-1", "host-1");
        seedProcess("proc-1", "agent-1");
        seedStatus("status-1", "agent-1", "proc-1", "RUNNING", "HEALTHY", 1234);

        mockMvc.perform(get("/api/v1/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agents", hasSize(1)))
                .andExpect(jsonPath("$.agents[0].agent_id", is("agent-1")))
                .andExpect(jsonPath("$.agents[0].hostname", is("host-1")))
                .andExpect(jsonPath("$.agents[0].status", is("HEALTHY")))
                .andExpect(jsonPath("$.agents[0].processes", hasSize(1)))
                .andExpect(jsonPath("$.agents[0].processes[0].process_id", is("proc-1")))
                .andExpect(jsonPath("$.agents[0].processes[0].pid", is(1234)));
    }

    @Test
    void getAgentReturnsNotFoundForUnknownAgent() throws Exception {
        mockMvc.perform(get("/api/v1/agents/{id}", "does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    @Test
    void getAgentReturnsDetailForKnownAgent() throws Exception {
        seedAgent("agent-2", "host-2");

        mockMvc.perform(get("/api/v1/agents/{id}", "agent-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agent_id", is("agent-2")))
                .andExpect(jsonPath("$.hostname", is("host-2")));
    }

    @Test
    void createCommandRejectsMissingCommandType() throws Exception {
        String body = "{\"agent_id\":\"agent-1\"}";

        mockMvc.perform(post("/api/v1/commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void createCommandPersistsPendingCommand() throws Exception {
        seedAgent("agent-1", "host-1");
        seedProcess("proc-1", "agent-1");
        String body = "{\"agent_id\":\"agent-1\",\"process_id\":\"proc-1\","
                + "\"command_type\":\"RESTART\",\"parameters\":{}}";

        mockMvc.perform(post("/api/v1/commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.command_id").exists())
                .andExpect(jsonPath("$.agent_id", is("agent-1")))
                .andExpect(jsonPath("$.command_type", is("RESTART")))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    @Disabled("Parameter update feature - pending enterprise implementation")
    void updateParameterCreatesActiveVersionAndIsReturnedByGet() throws Exception {
        seedAgent("agent-9", "host-9");
        seedProcess("proc-9", "agent-9");
        String body = "{\"process_id\":\"proc-9\",\"param_key\":\"temperature\","
                + "\"param_value\":\"0.7\",\"apply_immediately\":false}";

        mockMvc.perform(post("/api/v1/parameters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.process_id", is("proc-9")))
                .andExpect(jsonPath("$.param_key", is("temperature")))
                .andExpect(jsonPath("$.param_value", is("0.7")))
                .andExpect(jsonPath("$.param_type", is("FLOAT")))
                .andExpect(jsonPath("$.version", is(1)))
                .andExpect(jsonPath("$.is_active", is(true)));

        mockMvc.perform(get("/api/v1/parameters/{processId}", "proc-9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].param_key", is("temperature")));
    }

    @Test
    @Disabled("Execution log filtering test - pending database schema refinement")
    void executionLogsReturnFilteredPagedResults() throws Exception {
        seedAgent("agent-1", "host-1");
        seedProcess("proc-7", "agent-1");
        seedProcess("proc-other", "agent-1");
        seedLog("log-1", "agent-1", "proc-7");
        seedLog("log-2", "agent-1", "proc-7");
        seedLog("log-3", "agent-1", "proc-other");

        mockMvc.perform(get("/api/v1/execution-logs")
                        .param("process_id", "proc-7")
                        .param("limit", "10")
                        .param("offset", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(2)))
                .andExpect(jsonPath("$.logs", hasSize(2)))
                .andExpect(jsonPath("$.logs[0].process_id", is("proc-7")));
    }

    private void seedAgent(String agentId, String hostname) {
        Agent agent = new Agent();
        agent.setAgentId(agentId);
        agent.setHostname(hostname);
        agent.setOsType("LINUX");
        agentRepository.save(agent);
    }

    private void seedProcess(String processId, String agentId) {
        ProcessConfig config = new ProcessConfig();
        config.setProcessId(processId);
        config.setAgentId(agentId);
        config.setModelName("model-" + processId);
        config.setModelType("INFERENCE");
        processConfigRepository.save(config);
    }

    private void seedStatus(String statusId, String agentId, String processId,
                            String state, String health, int pid) {
        ModelProcess status = new ModelProcess();
        status.setProcessId(processId);
        status.setAgentId(agentId);
        status.setState(state);
        status.setHealthStatus(health);
        status.setPid(pid);
        modelProcessRepository.save(status);
    }

    private void seedLog(String logId, String agentId, String processId) {
        ExecutionLog log = new ExecutionLog();
        log.setLogId(logId);
        log.setAgentId(agentId);
        log.setProcessId(processId);
        log.setCommandType("START");
        log.setExecutionStatus("SUCCESS");
        log.setExitCode(0);
        log.setDurationSec(1);
        executionLogRepository.save(log);
    }
}
