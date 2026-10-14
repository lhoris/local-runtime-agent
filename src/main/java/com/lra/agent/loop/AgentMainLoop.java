package com.lra.agent.loop;

import com.lra.agent.health.HealthCheckException;
import com.lra.agent.health.HealthChecker;
import com.lra.agent.identity.AgentIdentityResolver;
import com.lra.agent.process.ProcessManager;
import com.lra.agent.process.ProcessStatus;
import com.lra.agent.state.InvalidStateTransitionException;
import com.lra.agent.state.StateManager;
import com.lra.agent.sync.DBSyncManager;
import com.lra.common.enums.HealthStatus;
import com.lra.common.enums.ProcessState;
import com.lra.db.entity.ModelProcess;
import com.lra.db.repository.ModelProcessRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic agent loop.
 *
 * <p>The order is intentionally plain: load the model-process rows this agent
 * owns, check those rows against the local runtime, record abnormal states,
 * execute new commands, then publish the latest status.
 */
@Component
public class AgentMainLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentMainLoop.class);

    private final AgentIdentityResolver agentIdentityResolver;
    private final ProcessManager processManager;
    private final ModelProcessRepository modelProcessRepository;
    private final StateManager stateManager;
    private final ObjectProvider<HealthChecker> healthCheckerProvider;
    private final DBSyncManager dbSyncManager;

    public AgentMainLoop(AgentIdentityResolver agentIdentityResolver,
                         ProcessManager processManager,
                         ModelProcessRepository modelProcessRepository,
                         StateManager stateManager,
                         ObjectProvider<HealthChecker> healthCheckerProvider,
                         DBSyncManager dbSyncManager) {
        this.agentIdentityResolver = agentIdentityResolver;
        this.processManager = processManager;
        this.modelProcessRepository = modelProcessRepository;
        this.stateManager = stateManager;
        this.healthCheckerProvider = healthCheckerProvider;
        this.dbSyncManager = dbSyncManager;
    }

    @Scheduled(fixedRateString = "${agent.polling-interval-sec:30}000")
    public void agentLoop() {
        long startedAt = System.currentTimeMillis();
        log.info("Agent Main Loop 주기 시작");

        log.info("[1/7] 로컬 IP 주소로 Agent 식별 및 등록 확인");
        Optional<String> agentId = agentIdentityResolver.resolveOrRegisterCurrentAgentId();
        if (agentId.isEmpty()) {
            log.warn("Agent loop skipped: local Agent could not be resolved or registered");
            return;
        }

        log.info("[2/7] Agent {}의 감시 대상 MODEL_PROCESS 조회", agentId.get());
        Optional<List<ModelProcess>> loadedProcesses = loadProcessDefinitionsFromDb(agentId.get());
        if (loadedProcesses.isEmpty()) {
            log.warn("Agent loop skipped: model process query failed for agent {}", agentId.get());
            return;
        }

        List<ModelProcess> processes = loadedProcesses.get();
        log.info("Agent {}가 감시할 MODEL_PROCESS {}건 조회 완료", agentId.get(), processes.size());
        log.info("[3/7] DB 설정과 로컬 프로세스 실행 상태 비교");
        processManager.reconcileDefinitions(processes);
        List<ProcessStatus> statuses = checkManagedProcessSafety(processes);
        log.info("[4/7] 프로세스 {}건 헬스체크 수행", statuses.size());
        healthCheckRunningProcesses(statuses);
        log.info("[5/7] 비정상 종료 및 안전하지 않은 프로세스 감지");
        detectUnsafeProcesses(statuses);
        log.info("[6/7] 비정상 프로세스 상태 기록 및 대기 명령 조회/처리");
        logAbnormalProcessStates(statuses);
        pollPendingCommands();
        log.info("[7/7] 프로세스 상태와 Agent heartbeat를 DB에 동기화");
        publishCurrentStatus();

        log.info("Agent Main Loop 주기 완료: agent={}, 확인 프로세스={}, 소요시간={}ms",
                agentId.get(), statuses.size(), System.currentTimeMillis() - startedAt);
    }

    /** Step 1: read the processes this agent is responsible for supervising. */
    private Optional<List<ModelProcess>> loadProcessDefinitionsFromDb(String agentId) {
        try {
            List<ModelProcess> definitions = modelProcessRepository.findByAgentId(agentId);
            log.info("Agent {}의 MODEL_PROCESS {}건을 DB에서 읽음", agentId, definitions.size());
            return Optional.of(definitions);
        } catch (RuntimeException ex) {
            log.warn("Loading model process definitions failed", ex);
            return Optional.empty();
        }
    }

    /** Step 2: compare the in-memory view with the actual local process state. */
    private List<ProcessStatus> checkManagedProcessSafety(List<ModelProcess> processes) {
        List<ProcessStatus> statuses = new ArrayList<>();
        for (ModelProcess process : processes) {
            try {
                ProcessStatus status = processManager.checkStatus(process);
                if (status != null) {
                    log.info("프로세스 상태 확인: MODEL_PROCESS_ID={}, 모델명={}, 상태={}, PID={}",
                            status.getModelId(), process.getModelName(), status.getState(), status.getPid());
                    statuses.add(status);
                }
            } catch (RuntimeException ex) {
                log.warn("Process safety check failed for {}", process.getProcessId(), ex);
            }
        }
        return statuses;
    }

    /** Step 3: ask each running process whether it is healthy. */
    private void healthCheckRunningProcesses(List<ProcessStatus> statuses) {
        HealthChecker healthChecker = healthCheckerProvider.getIfAvailable();
        if (healthChecker == null) {
            return;
        }
        for (ProcessStatus status : statuses) {
            if (status.getState() != ProcessState.RUNNING) {
                status.setHealthStatus(HealthStatus.UNKNOWN);
                log.info("헬스체크 결과: modelProcessId={}, healthStatus=UNKNOWN (프로세스 미실행)",
                        status.getModelId());
                continue;
            }
            try {
                HealthStatus health = healthChecker.checkHealth(status.getModelId());
                status.setHealthStatus(health);
                log.info("헬스체크 결과: modelProcessId={}, healthStatus={}",
                        status.getModelId(), health);
                if (health == HealthStatus.UNHEALTHY) {
                    transition(status.getModelId(), ProcessState.UNHEALTHY);
                }
            } catch (HealthCheckException ex) {
                status.setHealthStatus(HealthStatus.UNKNOWN);
                log.info("헬스체크 결과: modelProcessId={}, healthStatus=UNKNOWN (오류)",
                        status.getModelId());
                log.warn("Health check failed for {}", status.getModelId(), ex);
            }
        }
    }

    /** Step 4: detect crashes and resource-limit breaches. */
    private void detectUnsafeProcesses(List<ProcessStatus> statuses) {
        HealthChecker healthChecker = healthCheckerProvider.getIfAvailable();
        if (healthChecker == null) {
            return;
        }
        for (ProcessStatus status : statuses) {
            try {
                healthChecker.detectCrash(status.getModelId());
                healthChecker.detectResource(status.getModelId());
            } catch (HealthCheckException ex) {
                log.warn("Crash/resource detection failed for {}", status.getModelId(), ex);
            }
        }
    }

    /** Step 5: report abnormal states without changing the process lifecycle. */
    private void logAbnormalProcessStates(List<ProcessStatus> statuses) {
        for (ProcessStatus status : statuses) {
            if (status.getState() == ProcessState.CRASHED
                    || status.getState() == ProcessState.UNHEALTHY) {
                log.warn("비정상 프로세스 감지: modelProcessId={}, state={}, pid={}, error={}",
                        status.getModelId(), status.getState(), status.getPid(), status.getErrorMessage());
            }
        }
    }

    /** Step 6: fetch and execute pending commands. */
    private void pollPendingCommands() {
        try {
            dbSyncManager.pollPendingCommands();
        } catch (RuntimeException ex) {
            log.warn("Poll/execute commands step failed", ex);
        }
    }

    /** Step 7: publish process status to the central DB, including heartbeat. */
    private void publishCurrentStatus() {
        try {
            dbSyncManager.syncAgentStatus();
        } catch (RuntimeException ex) {
            log.warn("Sync status step failed", ex);
        }
    }

    private void transition(String modelId, ProcessState target) {
        try {
            stateManager.transitionTo(modelId, target);
        } catch (InvalidStateTransitionException ex) {
            log.debug("Skipped transition for {} -> {}: {}", modelId, target, ex.getMessage());
        }
    }
}
