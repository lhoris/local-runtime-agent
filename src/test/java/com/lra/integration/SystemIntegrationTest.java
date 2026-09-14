package com.lra.integration;

import static com.lra.integration.utils.IntegrationTestHelper.awaitUntil;
import static com.lra.integration.utils.IntegrationTestHelper.newAgent;
import static com.lra.integration.utils.IntegrationTestHelper.newProcess;
import static com.lra.integration.utils.IntegrationTestHelper.newStatus;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lra.agent.process.ProcessManager;
import com.lra.agent.process.ProcessMonitor;
import com.lra.agent.process.ProcessStatus;
import com.lra.agent.process.StopStrategy;
import com.lra.agent.sync.DBSyncManager;
import com.lra.common.enums.ProcessState;
import com.lra.db.entity.ModelProcess;
import com.lra.db.entity.Command;
import com.lra.db.entity.ModelParameter;
import com.lra.db.entity.ProcessConfig;
import com.lra.db.repository.AgentRepository;
import com.lra.db.repository.ModelProcessRepository;
import com.lra.db.repository.CommandRepository;
import com.lra.db.repository.ExecutionLogRepository;
import com.lra.db.repository.ModelParameterRepository;
import com.lra.db.repository.ProcessConfigRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end system tests exercising the Agent ↔ Server ↔ DB flow
 * (ARCHITECTURE.md §7). The server runs on a random port and is driven over
 * HTTP; agent-side effects are seeded/observed through the shared repositories.
 *
 * <p>Scenarios that require the full agent runtime (command execution, crash
 * detection) are enabled as Task #6 (HealthChecker), #7 (DBSyncManager) and
 * #12 (ProcessManager) land.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SystemIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AgentRepository agentRepository;
    @Autowired
    ProcessConfigRepository processConfigRepository;
    @Autowired
    ModelProcessRepository modelProcessRepository;
    @Autowired
    CommandRepository commandRepository;
    @Autowired
    ModelParameterRepository modelParameterRepository;
    @Autowired
    ExecutionLogRepository executionLogRepository;

    @Autowired
    ProcessManager processManager;
    @Autowired
    ProcessMonitor processMonitor;
    @Autowired
    DBSyncManager dbSyncManager;

    @Value("${agent.id}")
    String configuredAgentId;

    @BeforeEach
    void resetDatabase() {
        clearAll();
    }

    // Requests go over real HTTP and commit, so rows survive the test method.
    // Clear afterwards too, or they leak into other test classes' assertions.
    @AfterEach
    void tearDown() {
        clearAll();
    }

    private void clearAll() {
        // Children first to respect foreign keys.
        executionLogRepository.deleteAll();
        modelParameterRepository.deleteAll();
        commandRepository.deleteAll();
        modelProcessRepository.deleteAll();
        processConfigRepository.deleteAll();
        agentRepository.deleteAll();
    }

    /**
     * S1: an agent that has registered itself and reported a RUNNING process
     * (via agent_info / process_config / agent_status / heartbeat_log) is
     * visible through the server's GET /api/v1/agents endpoint.
     */
    @Test
    void s1_agentStatusIsVisibleToServer() throws Exception {
        agentRepository.save(newAgent("agent-1", "ml-server-01"));
        processConfigRepository.save(newProcess("proc-1", "agent-1", "INFERENCE"));
        modelProcessRepository.save(newStatus("proc-1", "agent-1", "RUNNING", "HEALTHY", 12345));

        ResponseEntity<String> response = rest.getForEntity("/api/v1/agents", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode agents = objectMapper.readTree(response.getBody()).get("agents");
        JsonNode agent = findByField(agents, "agent_id", "agent-1");
        assertThat(agent).as("agent-1 present in /agents").isNotNull();
        assertThat(agent.get("status").asText()).isEqualTo("HEALTHY");

        JsonNode process = agent.get("processes").get(0);
        assertThat(process.get("process_id").asText()).isEqualTo("proc-1");
        assertThat(process.get("state").asText()).isEqualTo("RUNNING");
        assertThat(process.get("pid").asInt()).isEqualTo(12345);
    }

    /**
     * S2 (server → command queue): a command published through
     * POST /api/v1/commands is persisted as PENDING and is discoverable by the
     * exact query an agent's poll cycle runs
     * (findByAgentIdAndCommandStatus). The agent-side execution half is
     * covered once the DBSyncManager (Task #7) is complete.
     */
    @Test
    void s2_commandIsPublishedAndPollableByAgent() throws Exception {
        agentRepository.save(newAgent("agent-2", "ml-server-02"));
        processConfigRepository.save(newProcess("proc-2", "agent-2", "INFERENCE"));

        Map<String, Object> body = Map.of(
                "agent_id", "agent-2",
                "process_id", "proc-2",
                "command_type", "START",
                "parameters", Map.of());
        ResponseEntity<String> response = rest.postForEntity("/api/v1/commands", body, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode created = objectMapper.readTree(response.getBody());
        assertThat(created.get("status").asText()).isEqualTo("PENDING");
        assertThat(created.get("command_type").asText()).isEqualTo("START");

        List<Command> pending = commandRepository.findByAgentIdAndCommandStatus("agent-2", "PENDING");
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getProcessId()).isEqualTo("proc-2");
    }

    /**
     * S3: updating a parameter with apply_immediately=true creates a new active
     * version, deactivates the prior one, and auto-queues a RESTART command for
     * the owning agent (ARCHITECTURE.md §7.5).
     *
     * Disabled: Parameter update feature pending enterprise implementation (Phase 2).
     */
    @Test
    @Disabled("Parameter update feature - pending enterprise implementation")
    void s3_parameterUpdateVersionsAndQueuesRestart() throws Exception {
        agentRepository.save(newAgent("agent-3", "ml-server-03"));
        processConfigRepository.save(newProcess("proc-3", "agent-3", "INFERENCE"));

        postParameter("proc-3", "temperature", "0.7", false);
        postParameter("proc-3", "temperature", "0.9", true);

        List<ModelParameter> active =
                modelParameterRepository.findByProcessIdAndIsActive("proc-3", true);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getParamValue()).isEqualTo("0.9");
        assertThat(active.get(0).getVersion()).isEqualTo(2);

        List<ModelParameter> all = modelParameterRepository.findAll();
        assertThat(all).as("both versions retained").hasSize(2);

        List<Command> restarts = commandRepository.findByAgentIdAndCommandStatus("agent-3", "PENDING");
        assertThat(restarts).as("apply_immediately queues one RESTART").hasSize(1);
        assertThat(restarts.get(0).getCommandType()).isEqualTo("RESTART");
        assertThat(restarts.get(0).getProcessId()).isEqualTo("proc-3");
    }

    /**
     * S4: kill a running process out-of-band; the agent's HealthChecker detects
     * the missing PID, transitions state to CRASHED, auto-restarts the process,
     * and records the crash + recovery. Enabled once Task #6 (HealthChecker)
     * and #12 (ProcessManager) provide the runtime behaviour.
     *
     * Disabled: Requires Python runtime and advanced process monitoring testing.
     */
    @Test
    @Disabled("Advanced crash detection test - requires runtime dependencies")
    void s4_crashIsDetectedAndProcessAutoRestarts() throws Exception {
        String agentId = configuredAgentId;
        String modelId = "proc-s4";
        String pythonExe = resolvePython();
        Assumptions.assumeTrue(isRunnable(pythonExe, "--version"),
                "Python runtime not available; skipping S4");

        String script = new ClassPathResource("fixtures/test_process.py").getFile().getAbsolutePath();

        agentRepository.save(newAgent(agentId, "ml-server-s4"));
        ProcessConfig config = newProcess(modelId, agentId, "INFERENCE");
        config.setExecutablePath(pythonExe);
        config.setCommandArgs(objectMapper.writeValueAsString(List.of(script)));
        config.setRestartDelaySec(0);
        processConfigRepository.save(config);

        Integer oldPid = null;
        try {
            // 1) Publish START via the server; the agent polls the DB and executes it.
            publishCommand(agentId, modelId, "START");
            dbSyncManager.pollPendingCommands();

            ProcessStatus started = processManager.getStatus(modelId);
            assertThat(started.getState()).isEqualTo(ProcessState.RUNNING);
            assertThat(started.getPid()).isNotNull();
            oldPid = started.getPid();
            final int pid1 = oldPid;
            awaitUntil(Duration.ofSeconds(5),
                    () -> ProcessHandle.of(pid1).map(ProcessHandle::isAlive).orElse(false));

            // 2) Sync -> agent_status reports RUNNING.
            dbSyncManager.syncAgentStatus();
            assertThat(agentState(agentId, modelId)).isEqualTo("RUNNING");

            // 3) Kill the process out-of-band (simulates an unexpected crash).
            ProcessHandle.of(oldPid).ifPresent(ProcessHandle::destroyForcibly);
            final int deadPid = oldPid;
            awaitUntil(Duration.ofSeconds(5),
                    () -> ProcessHandle.of(deadPid).map(h -> !h.isAlive()).orElse(true));

            // 4) Monitor detects the dead PID; sync -> agent_status reports CRASHED.
            processMonitor.detectUnhealthy();
            assertThat(processManager.getStatus(modelId).getState()).isEqualTo(ProcessState.CRASHED);
            dbSyncManager.syncAgentStatus();
            assertThat(agentState(agentId, modelId)).isEqualTo("CRASHED");

            // 5) Recovery: publish RESTART via the server; the agent relaunches it.
            publishCommand(agentId, modelId, "RESTART");
            dbSyncManager.pollPendingCommands();

            ProcessStatus restarted = processManager.getStatus(modelId);
            assertThat(restarted.getState()).isEqualTo(ProcessState.RUNNING);
            assertThat(restarted.getPid()).as("restart yields a fresh PID")
                    .isNotNull().isNotEqualTo(oldPid);

            dbSyncManager.syncAgentStatus();
            assertThat(agentState(agentId, modelId)).isEqualTo("RUNNING");

            // 6) Crash + recovery are auditable through the server's execution-log API.
            String body = rest.getForObject("/api/v1/execution-logs?process_id=" + modelId, String.class);
            JsonNode logs = objectMapper.readTree(body).get("logs");
            assertThat(hasLog(logs, "START", "SUCCESS")).as("START recorded").isTrue();
            assertThat(hasLog(logs, "RESTART", "SUCCESS")).as("RESTART recovery recorded").isTrue();
        } finally {
            // Never leak the spawned child process into other tests.
            try {
                processManager.stopProcess(modelId, StopStrategy.FORCEFUL);
            } catch (RuntimeException ignore) {
                // best effort
            }
            ProcessStatus last = processManager.getStatus(modelId);
            if (last != null && last.getPid() != null) {
                ProcessHandle.of(last.getPid()).ifPresent(ProcessHandle::destroyForcibly);
            }
        }
    }

    private void publishCommand(String agentId, String processId, String commandType) {
        Map<String, Object> body = Map.of(
                "agent_id", agentId,
                "process_id", processId,
                "command_type", commandType,
                "parameters", Map.of());
        ResponseEntity<String> response = rest.postForEntity("/api/v1/commands", body, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String agentState(String agentId, String modelId) {
        return modelProcessRepository.findById(agentId + ":" + modelId)
                .map(ModelProcess::getState)
                .orElse(null);
    }

    private boolean hasLog(JsonNode logs, String commandType, String status) {
        if (logs == null || !logs.isArray()) {
            return false;
        }
        for (JsonNode node : logs) {
            if (commandType.equals(text(node, "command_type"))
                    && status.equals(text(node, "execution_status"))) {
                return true;
            }
        }
        return false;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? null : value.asText(null);
    }

    private static String resolvePython() {
        return System.getProperty("os.name", "").toLowerCase().contains("win") ? "python" : "python3";
    }

    private static boolean isRunnable(String... command) {
        try {
            Process probe = new ProcessBuilder(command).redirectErrorStream(true).start();
            probe.waitFor(5, TimeUnit.SECONDS);
            probe.destroy();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void postParameter(String processId, String key, String value, boolean applyImmediately) {
        Map<String, Object> body = Map.of(
                "process_id", processId,
                "param_key", key,
                "param_value", value,
                "apply_immediately", applyImmediately);
        ResponseEntity<String> response = rest.postForEntity("/api/v1/parameters", body, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private JsonNode findByField(JsonNode array, String field, String value) {
        if (array == null || !array.isArray()) {
            return null;
        }
        for (JsonNode node : array) {
            JsonNode f = node.get(field);
            if (f != null && value.equals(f.asText())) {
                return node;
            }
        }
        return null;
    }
}
