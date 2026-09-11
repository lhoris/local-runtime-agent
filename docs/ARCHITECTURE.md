# Local Runtime Agent - 시스템 설계서

분산 AI 모델 프로세스 관리 시스템 (Spring Boot 기반 PC-local Agent)

**작성일**: 2025-01-15 | **상태**: MVP 설계 | **버전**: 1.0

---

## 목차

1. [시스템 개요](#1-시스템-개요)
2. [핵심 철학](#2-핵심-철학-loop-engineering)
3. [아키텍처](#3-시스템-아키텍처)
4. [주요 컴포넌트](#4-주요-컴포넌트-설계)
5. [데이터베이스](#5-데이터베이스-스키마)
6. [API 명세](#6-api-명세)
7. [처리 흐름](#7-상태-및-명령-처리-흐름)
8. [확장 구조](#8-hookaadapter-확장-구조)
9. [배포 및 운영](#9-배포-및-운영)
10. [신뢰성 & 보안](#10-장애-및-보안-고려사항)
11. [MVP 범위](#11-mvp-minimum-viable-product-범위)
12. [로드맵](#12-개발-로드맵)

---

## 1. 시스템 개요

### 목표

각 PC(Windows/Linux)에 설치된 Spring Boot 기반의 **Agent**가 로컬 Python AI 모델 프로세스를 자동으로 관리하고, 중앙 서버의 지시에 따라 상태를 리포팅하며 명령을 수행하는 분산 시스템.

### 핵심 역할

- **Agent (PC Local)**: 프로세스 라이프사이클 관리, 상태 모니터링, 자동 복구
- **Central Server**: 전체 Agent 상태 집계, 명령 디스패치, 모델 파라미터 관리
- **Shared Database**: 상태 동기화, 명령 큐, 파라미터 저장소

### 체계도

```
Windows/Linux PC                     Central Server              Shared Database
┌─────────────────┐                 ┌──────────────┐            ┌──────────────┐
│ Spring Boot     │                 │ Spring Boot  │            │ PostgreSQL   │
│ Agent           │ ←polls every 30s→ │ Server      │ ←→ READ/WRITE │ MySQL       │
│                 │                 │              │            │              │
│ ┌─────────────┐ │                 │ • Status     │            │ 테이블:       │
│ │ Process Mgr │ │                 │   Aggregation│            │ • agent_info │
│ │ Health Check│ │                 │ • Command    │            │ • commands   │
│ │ Auto Restart│ │                 │   Dispatcher │            │ • agent_status
│ └─────────────┘ │                 │ • Dashboard  │            │ • 등...       │
│                 │                 │ • REST API   │            │              │
│ ┌─────────────┐ │                 └──────────────┘            └──────────────┘
│ │ Python      │ │
│ │ Model 1     │ │
│ ├─────────────┤ │
│ │ Model 2     │ │
│ ├─────────────┤ │
│ │ Runtime     │ │
│ └─────────────┘ │
└─────────────────┘
```

---

## 2. 핵심 철학: Loop Engineering

### 네 가지 설계 원칙

#### 1️⃣ Autonomous Loop First
Agent는 중앙 서버의 명령 없이도 자체 로직으로 동작. 중앙 서버는 옵션적 제어 레이어.

#### 2️⃣ State Machine 기반 설계
프로세스 상태는 명확한 State Machine으로 정의. 상태 전이가 일관되고 예측 가능해야 함.

#### 3️⃣ Polling over Subscription
Agent가 DB를 주기적으로 폴링하여 명령을 확인. 복잡한 메시지 브로커 제거, 구현 단순화.

#### 4️⃣ Graceful Degradation
중앙 서버 불가 시에도 Agent는 계속 동작. 로컬 상태만으로 프로세스 관리 가능.

### Human Gate 최소화 전략

- **Interface-driven design**: 구현체는 Interface 뒤에 숨김. 테스트와 Mock이 용이
- **Configuration-over-convention**: 모델별 동작은 설정 파일로 정의. 하드코딩 제거
- **Progressive Enhancement**: MVP는 최소 기능만. 신규 기능은 확장 구조를 따름
- **Self-healing 우선**: 자동 복구로 수동 개입 줄임

---

## 3. 시스템 아키텍처

### 계층 구조

| 계층 | 역할 | 기술 스택 |
|------|------|---------|
| **Agent (PC Local)** | 프로세스 라이프사이클, 모니터링, 자동 복구 | Spring Boot, ProcessBuilder, JVM |
| **Shared Database** | 상태 저장소, 명령 큐, 파라미터 저장소 | PostgreSQL / MySQL |
| **Central Server** | 명령 디스패치, 상태 수집, 대시보드 | Spring Boot, REST API |
| **Python Processes** | 실제 AI 모델 실행 | Python, 선택적 REST API |

### 통신 흐름

- **Agent → DB (Pull)**: 폴링으로 명령 확인, 상태 업데이트
- **Agent → Local Process**: ProcessBuilder로 시작/종료/모니터링
- **Central Server → DB (Push)**: 명령 등록, 파라미터 수정
- **Optional: Local API**: 프로세스 Health Check, 모델 응답 (선택적)

---

## 4. 주요 컴포넌트 설계

### 4.1 ProcessManager (핵심)

```java
public interface ProcessManager {
    void startProcess(String modelId, ProcessConfig config);
    void stopProcess(String modelId, StopStrategy strategy);
    void restartProcess(String modelId);
    ProcessStatus getStatus(String modelId);
    List<ProcessStatus> getAllStatus();
}

public interface ProcessMonitor {
    void monitor();  // 주기적 상태 체크
    void detectUnhealthy();  // 비정상 감지
    void autoRestart();  // 자동 재시작
}
```

**구현 전략**:
- ProcessBuilder 사용 (Windows/Linux 호환)
- PID 추출 및 추적
- CPU/메모리 모니터링 (OS 명령어 또는 JVM)
- 타임아웃 관리

### 4.2 StateManager

**상태 정의**:
```
STOPPED → STARTING → RUNNING → STOPPING
                        ↓
                      CRASHED → (auto restart)
                        ↓
                      DEGRADED
```

**구현**:
- State Enum: STOPPED, STARTING, RUNNING, STOPPING, CRASHED, DEGRADED, UNHEALTHY
- StateTransitionValidator: 유효하지 않은 전이 방지
- StateChangeListener: 상태 변화 감시

### 4.3 DBSyncManager (폴링 엔진)

```java
public interface DBSyncManager {
    void syncAgentStatus();  // Agent 상태를 DB에 기록
    void pollPendingCommands();  // DB의 미처리 명령 확인
    void updateHeartbeat();  // 살아있음 신호
    void logExecution(ExecutionResult result);  // 결과 기록
}
```

**주요 로직**:
- 30초마다 폴링
- `commands.command_status = 'PENDING'` 조회
- 명령 순차 실행
- 상태 업데이트

### 4.4 ParameterManager

**기능**:
- DB에서 모델별 파라미터 로드
- 프로세스 재시작 시점에 환경변수/인자로 적용
- 파라미터 버전 관리
- Validation (타입, 범위)

### 4.5 HealthChecker (자동 복구 엔진)

```java
public interface HealthChecker {
    HealthStatus checkHealth(String modelId);
    
    // 검사 전략:
    // - Process Alive Check (PID 존재 여부)
    // - Local REST API Call (선택적)
    // - Resource Limit Exceeded
    // - Crash Detection
}
```

### 4.6 Extension Framework (Hook/Adapter)

```java
public interface ProcessHook {
    void onBeforeStart(ProcessConfig config);
    void onAfterStart(Process process);
    void onBeforeStop(Process process);
    void onAfterStop();
    void onCrash(ProcessConfig config);
}

public interface HealthAdapter {
    HealthStatus check(ProcessConfig config);
    // 모델별 특수 health check 로직
}
```

---

## 5. 데이터베이스 스키마

### 5.1 핵심 테이블

#### agent_info
```sql
CREATE TABLE agent_info (
    agent_id VARCHAR(64) PRIMARY KEY,
    hostname VARCHAR(256),
    os_type VARCHAR(32),  -- WINDOWS, LINUX
    ip_address VARCHAR(45),
    spring_boot_version VARCHAR(32),
    installed_at TIMESTAMP,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

#### process_config
```sql
CREATE TABLE process_config (
    process_id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    model_name VARCHAR(256),
    model_type VARCHAR(64),  -- INFERENCE, TRAINING, RUNTIME
    executable_path VARCHAR(512),  -- Python 스크립트 경로
    working_directory VARCHAR(512),
    command_args TEXT,  -- JSON 배열
    env_vars TEXT,  -- JSON 객체
    auto_restart BOOLEAN DEFAULT true,
    max_restart_attempts INT DEFAULT 3,
    restart_delay_sec INT DEFAULT 10,
    timeout_sec INT DEFAULT 3600,
    memory_limit_mb INT,
    cpu_limit_percent INT,
    health_check_enabled BOOLEAN DEFAULT true,
    health_check_interval_sec INT DEFAULT 30,
    health_check_endpoint VARCHAR(512),  -- 선택적 REST API
    metadata JSON,  -- 모델별 추가 정보
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (agent_id) REFERENCES agent_info(agent_id)
);
```

#### agent_status (현재 상태)
```sql
CREATE TABLE agent_status (
    status_id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    process_id VARCHAR(64),
    state VARCHAR(32),  -- STOPPED, STARTING, RUNNING, STOPPING, CRASHED, DEGRADED
    pid INT,
    cpu_percent FLOAT,
    memory_mb INT,
    last_health_check TIMESTAMP,
    health_status VARCHAR(32),  -- HEALTHY, UNHEALTHY, UNKNOWN
    uptime_sec BIGINT,
    crash_count INT DEFAULT 0,
    last_crash_time TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (agent_id) REFERENCES agent_info(agent_id),
    FOREIGN KEY (process_id) REFERENCES process_config(process_id),
    INDEX idx_agent_process (agent_id, process_id)
);
```

#### commands (명령 큐)
```sql
CREATE TABLE commands (
    command_id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    process_id VARCHAR(64),
    command_type VARCHAR(32),  -- START, STOP, RESTART, PARAM_UPDATE, HEALTH_CHECK
    command_status VARCHAR(32),  -- PENDING, PROCESSING, COMPLETED, FAILED
    parameters JSON,  -- 명령별 파라미터
    created_by VARCHAR(256),  -- 중앙 서버 또는 사용자
    created_at TIMESTAMP,
    processed_at TIMESTAMP,
    failed_reason TEXT,
    FOREIGN KEY (agent_id) REFERENCES agent_info(agent_id),
    FOREIGN KEY (process_id) REFERENCES process_config(process_id),
    INDEX idx_pending (command_status, agent_id)
);
```

#### model_parameters
```sql
CREATE TABLE model_parameters (
    param_id VARCHAR(64) PRIMARY KEY,
    process_id VARCHAR(64) NOT NULL,
    param_key VARCHAR(256),
    param_value TEXT,
    param_type VARCHAR(32),  -- STRING, INTEGER, FLOAT, BOOLEAN, JSON
    version INT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    FOREIGN KEY (process_id) REFERENCES process_config(process_id),
    UNIQUE KEY uk_param (process_id, param_key, version)
);
```

#### heartbeat_log
```sql
CREATE TABLE heartbeat_log (
    heartbeat_id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    heartbeat_time TIMESTAMP,
    agent_status JSON,  -- 전체 상태 스냅샷 (선택적 압축)
    created_at TIMESTAMP,
    FOREIGN KEY (agent_id) REFERENCES agent_info(agent_id),
    INDEX idx_agent_time (agent_id, heartbeat_time)
);
```

#### execution_log (감사 로그)
```sql
CREATE TABLE execution_log (
    log_id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64),
    process_id VARCHAR(64),
    command_type VARCHAR(32),
    execution_status VARCHAR(32),  -- SUCCESS, FAILURE
    exit_code INT,
    stdout_preview TEXT,  -- 처음 1000자
    stderr_preview TEXT,
    duration_sec INT,
    created_at TIMESTAMP,
    FOREIGN KEY (agent_id) REFERENCES agent_info(agent_id),
    FOREIGN KEY (process_id) REFERENCES process_config(process_id),
    INDEX idx_process_time (process_id, created_at)
);
```

### 5.2 조회 뷰

```sql
-- 통합 상태 뷰 (중앙 서버 대시보드용)
CREATE VIEW model_status AS
SELECT
    ai.agent_id,
    ai.hostname,
    pc.process_id,
    pc.model_name,
    ast.state,
    ast.pid,
    ast.cpu_percent,
    ast.memory_mb,
    ast.health_status,
    ast.uptime_sec,
    ast.crash_count,
    ast.last_health_check,
    COUNT(CASE WHEN c.command_status = 'PENDING' THEN 1 END) as pending_commands
FROM agent_info ai
JOIN process_config pc ON ai.agent_id = pc.agent_id
LEFT JOIN agent_status ast ON pc.process_id = ast.process_id
LEFT JOIN commands c ON ai.agent_id = c.agent_id AND c.command_status = 'PENDING'
GROUP BY ai.agent_id, pc.process_id;
```

### 5.3 인덱싱 전략

- `agent_id`: 폴링 성능 중요
- `command_status, agent_id`: 폴링 쿼리 최적화
- `process_id, created_at`: 로그 조회 성능
- `agent_id, heartbeat_time`: 상태 타임시리즈 조회

---

## 6. API 명세

### 6.1 Agent Internal API (내부용)

#### POST /api/v1/agent/init
Agent 시작 시 초기화, 설정 로드

```json
{
    "agent_id": "agent-001",
    "hostname": "ml-server-01",
    "processes": [
        {
            "process_id": "model-llm-1",
            "executable": "/models/llm/main.py",
            "args": ["--model", "gpt-3"],
            "env": {"CUDA_VISIBLE_DEVICES": "0,1"}
        }
    ]
}
```

#### POST /api/v1/agent/sync
주기적 상태 동기화 (매 폴링 사이클)

Agent는 이를 통해 DB의 명령 조회 및 상태 업데이트.

### 6.2 Central Server API (외부용)

#### GET /api/v1/agents
모든 Agent 목록 및 상태

```json
{
    "agents": [
        {
            "agent_id": "agent-001",
            "hostname": "ml-server-01",
            "status": "HEALTHY",
            "last_heartbeat": "2025-01-15T10:30:00Z",
            "processes": [
                {
                    "process_id": "model-llm-1",
                    "state": "RUNNING",
                    "pid": 12345,
                    "cpu_percent": 45.2,
                    "memory_mb": 8192
                }
            ]
        }
    ]
}
```

#### GET /api/v1/agents/{agentId}
특정 Agent 상세 조회

#### POST /api/v1/commands
명령 등록 (DB에 INSERT, Agent가 폴링하여 처리)

```json
{
    "agent_id": "agent-001",
    "process_id": "model-llm-1",
    "command_type": "RESTART",
    "parameters": {}
}
```

#### POST /api/v1/parameters
모델 파라미터 수정

```json
{
    "process_id": "model-llm-1",
    "param_key": "temperature",
    "param_value": "0.7",
    "apply_immediately": false
}
```

#### GET /api/v1/execution-logs
실행 로그 조회

Query Parameters:
- `process_id`: 필터링
- `limit`: 페이지 크기 (기본 50)
- `offset`: 오프셋

#### GET /api/v1/parameters/{processId}
프로세스의 현재 파라미터 조회

### 6.3 선택적: Python 프로세스 Local API

```
GET http://localhost:8001/health

{
    "status": "healthy",
    "uptime_ms": 3600000,
    "memory_mb": 2048,
    "inference_latency_ms": 150
}
```

---

## 7. 상태 및 명령 처리 흐름

### 7.1 프로세스 라이프사이클

```
STOPPED
  ↓ (startProcess)
STARTING (timeout: 30초)
  ├─ Success → RUNNING
  └─ Failure → STOPPED (error logged)

RUNNING
  ├─ (monitorProcess) → Health OK
  ├─ (detectCrash) → CRASHED
  ├─ (restartProcess) → STOPPING
  └─ (stopProcess) → STOPPING

STOPPING (timeout: 10초)
  ├─ Success → STOPPED
  └─ Timeout → FORCEKILL → STOPPED

CRASHED
  ├─ (autoRestart enabled) → STARTING
  └─ (autoRestart disabled) → STOPPED

DEGRADED
  ├─ (recoveryAttempt) → RUNNING or CRASHED
  └─ (manual restart) → STARTING
```

### 7.2 Agent 주기적 루프 (매 30초)

1. **Monitor**: 모든 프로세스 상태 체크 (PID 확인, CPU/메모리)
2. **HealthCheck**: 비정상 감지 (Crash, Zombie, Resource Exceed)
3. **AutoRestart**: 필요시 자동 재시작
4. **PollDB**: 미처리 명령 확인 (`command_status = 'PENDING'`)
5. **ExecuteCommands**: 명령 순차 실행
6. **SyncStatus**: 현재 상태를 `agent_status`에 기록
7. **Heartbeat**: `heartbeat_log`에 기록

### 7.3 명령 처리 흐름 (START 예시)

```
① Central Server: INSERT INTO commands
   (agent_id, process_id, command_type='START', status='PENDING')

② Agent Poll: SELECT * FROM commands 
   WHERE agent_id=? AND status='PENDING'

③ Agent Execute:
   - 프로세스 config 로드
   - ProcessBuilder 생성
   - ProcessManager.startProcess() 호출
   - 프로세스 PID 확보
   - 상태 → STARTING

④ Agent Monitor (다음 사이클):
   - PID 확인 (프로세스 살아있는지)
   - Health check 수행
   - 성공 → state = RUNNING
   - 실패 → state = CRASHED or STOPPED

⑤ Agent Update:
   - UPDATE agent_status SET state='RUNNING', pid=12345
   - UPDATE commands SET status='COMPLETED', processed_at=NOW()
   - INSERT INTO execution_log
```

### 7.4 자동 재시작 로직

```java
// CrashDetector (각 폴링 사이클마다)
if (process.state == RUNNING && !processExists(pid)) {
    agent_status.state = CRASHED
    agent_status.crash_count++

    if (config.auto_restart == true &&
        crash_count < config.max_restart_attempts) {
        // 재시작 딜레이 적용
        sleep(config.restart_delay_sec)
        processManager.startProcess(process_id)
    } else {
        agent_status.state = STOPPED
        log.error("Process crashed, max attempts reached")
    }
}
```

### 7.5 파라미터 적용 방식

**⚠️ 즉시 적용 불가**: Python 프로세스는 런타임에 파라미터 변경이 어려움

**재시작 시 적용 (권장)**:
1. Central Server가 parameter 변경
2. 자동 또는 수동으로 RESTART 명령 등록
3. Agent 폴링 시 명령 감지
4. 프로세스 재시작, 새 파라미터 로드

**선택적 즉시 적용**: 프로세스가 Local API로 파라미터 변경을 지원하면, Agent가 HTTP 요청으로 전달 가능

---

## 8. Hook/Adapter 확장 구조

### 목표

모델별 특수한 동작(시작 전 데이터 준비, 종료 후 로그 수집, 특수 Health Check 등)을 Interface 기반으로 확장 가능하게 설계.

### 8.1 ProcessHook 패턴

```java
public interface ProcessHook {
    void onBeforeStart(ProcessConfig config) throws HookException;
    void onAfterStart(Process process, ProcessStatus status);
    void onBeforeStop(ProcessStatus status);
    void onAfterStop(ProcessStatus status);
    void onCrash(ProcessConfig config, Exception cause);
}

// 구현 예시: LLM 모델 초기화 Hook
public class LlmInitHook implements ProcessHook {
    @Override
    public void onBeforeStart(ProcessConfig config) {
        // 모델 가중치 파일 다운로드/확인
        // 캐시 디렉토리 생성
        // 환경변수 설정
    }

    @Override
    public void onAfterStart(Process process, ProcessStatus status) {
        // 워밍업 요청 전송 (선택적)
        // 텍스트 임베딩 테스트
    }
}
```

### 8.2 HealthAdapter 패턴

```java
public interface HealthAdapter {
    HealthStatus check(ProcessStatus status) throws HealthCheckException;
    int getCheckIntervalSec();
}

// 구현 예시: REST API 기반 Health Check
public class RestApiHealthAdapter implements HealthAdapter {
    @Override
    public HealthStatus check(ProcessStatus status) {
        String endpoint = status.config.health_check_endpoint;
        if (endpoint == null) {
            return HealthStatus.UNKNOWN;
        }

        try {
            Response response = httpClient.get(endpoint + "/health");
            if (response.statusCode() == 200) {
                return HealthStatus.HEALTHY;
            }
        } catch (Exception e) {
            return HealthStatus.UNHEALTHY;
        }
    }
}

// 구현 예시: 리소스 기반 Health Check
public class ResourceHealthAdapter implements HealthAdapter {
    @Override
    public HealthStatus check(ProcessStatus status) {
        if (status.cpu_percent > 95 || status.memory_mb > config.memory_limit) {
            return HealthStatus.DEGRADED;
        }
        return HealthStatus.HEALTHY;
    }
}
```

### 8.3 Registry & Injection

```java
@Configuration
public class ExtensionConfig {

    @Bean
    public ProcessHookRegistry hookRegistry() {
        ProcessHookRegistry registry = new ProcessHookRegistry();
        registry.register("llm-model", new LlmInitHook());
        registry.register("vision-model", new VisionInitHook());
        registry.register("generic", new DefaultHook());
        return registry;
    }

    @Bean
    public HealthAdapterRegistry healthRegistry() {
        HealthAdapterRegistry registry = new HealthAdapterRegistry();
        registry.register("rest-api", new RestApiHealthAdapter());
        registry.register("resource", new ResourceHealthAdapter());
        registry.register("custom-llm", new LlmSpecificHealthAdapter());
        return registry;
    }
}

// Runtime에 Hook 사용
ProcessStatus status = processManager.startProcess(config);
List<ProcessHook> hooks = hookRegistry.getHooks(config.model_type);
for (ProcessHook hook : hooks) {
    hook.onAfterStart(status.process, status);
}
```

### 8.4 설정 기반 확장 (선택적)

```yaml
agent:
  processes:
    - process_id: model-llm-1
      executable: /models/llm/main.py
      hooks:
        - type: llm_init
          config:
            warmup_enabled: true
      health_adapters:
        - type: rest_api
          endpoint: http://localhost:8001/health
        - type: resource
          cpu_threshold: 90
          memory_threshold: 8192
```

### 8.5 확장 포인트 요약

| 확장 포인트 | 용도 | 구현 난도 |
|-----------|------|---------|
| `ProcessHook` | 프로세스 라이프사이클 이벤트 | 낮음 |
| `HealthAdapter` | Health Check 로직 (모델별) | 중간 |
| `RestartStrategy` | 재시작 정책 커스터마이징 | 중간 |
| `StateTransitionListener` | 상태 전이 감시 | 낮음 |

---

## 9. 배포 및 운영

### 9.1 Windows 배포

Windows Service로 등록:

```bash
# 1. Jar 빌드
./gradlew clean build

# 2. WinSW (Windows Service Wrapper) 사용
# winsw-2.12.0-bin.exe 다운로드

# 3. service.xml 설정
<service>
  <id>LocalRuntimeAgent</id>
  <name>Local Runtime Agent</name>
  <description>Python Model Process Manager</description>
  <executable>java</executable>
  <arguments>-jar "C:\Program Files\LocalRuntimeAgent\app.jar"</arguments>
  <log>C:\Program Files\LocalRuntimeAgent\logs</log>
</service>

# 4. 서비스 설치
winsw-2.12.0-bin.exe install service.xml

# 5. 시작
sc start LocalRuntimeAgent
```

### 9.2 Linux 배포

systemd로 등록:

```bash
# 1. systemd 파일 생성
# /etc/systemd/system/local-runtime-agent.service

[Unit]
Description=Local Runtime Agent
After=network.target

[Service]
Type=simple
User=mluser
WorkingDirectory=/opt/local-runtime-agent
ExecStart=java -jar /opt/local-runtime-agent/app.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target

# 2. 활성화
systemctl daemon-reload
systemctl enable local-runtime-agent.service
systemctl start local-runtime-agent.service

# 3. 상태 확인
systemctl status local-runtime-agent.service
```

### 9.3 설정 파일 구조

```yaml
agent:
  id: "agent-${HOSTNAME}"
  polling_interval_sec: 30
  health_check_interval_sec: 30

database:
  url: jdbc:postgresql://central-db:5432/agent_db
  username: ${DB_USER}
  password: ${DB_PASSWORD}

processes:
  - process_id: model-llm-1
    executable: /models/llm/inference.py
    working_dir: /models/llm
    auto_restart: true
    max_restart_attempts: 3
    timeout_sec: 600

logging:
  level: INFO
  file: /var/log/agent/application.log
```

### 9.4 운영 가이드

**주기적 점검**:
- **일일**: Agent 로그, Crash 여부 확인
- **주간**: DB 크기, 쿼리 성능 모니터링
- **월간**: 중앙 서버 대시보드 리뷰, 파라미터 최적화

---

## 10. 장애 및 보안 고려사항

### 10.1 신뢰성 (Reliability)

#### 네트워크 장애 대응
- **문제**: Agent가 DB에 접속 불가
- **대응**: 폴링 실패 시 기존 설정으로 프로세스 자동 관리 지속. 중앙 서버 명령은 대기.

#### 프로세스 Zombie 처리
```java
if (processExists(pid) && !processResponding(pid)) {
    // Zombie 프로세스 감지
    forcefullyTerminate(pid);
    agent_status.state = CRASHED;
}
```

#### DB 장애
- **Connection Pool**: HikariCP로 자동 재시도
- **Graceful Degradation**: DB 불가 시 로컬 상태만으로 프로세스 유지
- **로컬 캐시**: 마지막 상태 메모리에 보관 (선택적)

### 10.2 보안 (Security)

#### Database 보안
- **인증**: DB 계정 격리 (Agent 읽기/쓰기 권한)
- **암호화**: TLS 연결, 비밀번호는 환경변수로 관리
- **격리**: Network firewall으로 Agent와 DB 사이만 통신 허용

#### 프로세스 격리
- **사용자 권한**: Python 프로세스는 제한된 사용자로 실행 (root 금지)
- **파일 시스템**: 프로세스별 디렉토리 격리, 접근 제어
- **리소스 제한**: cgroups (Linux) / Job Objects (Windows) 사용

#### 감사 로그 (Audit)
- 모든 명령 실행 기록 (execution_log)
- 파라미터 변경 이력
- 비정상 재시작, 크래시 기록
- 중앙 서버에서 접근 제한 및 로깅

#### Command Injection 방지
```java
// ✅ 권장: ProcessBuilder 사용
ProcessBuilder pb = new ProcessBuilder(
    "/usr/bin/python3",
    "/models/model.py",
    "--param1", value1,  // 값이 자동으로 이스케이프됨
    "--param2", value2
);

// ❌ 금지: Runtime.exec (Shell injection)
Runtime.exec("python /models/model.py --param1 " + value);
```

---

## 11. MVP (Minimum Viable Product) 범위

### 11.1 MVP 스코프

| 기능 | MVP 포함 | V2+ | 비고 |
|------|---------|-----|------|
| **프로세스 관리** | ✅ | - | 시작, 종료, 재시작 |
| **상태 모니터링** | ✅ | - | PID, CPU, 메모리 |
| **Health Check** | ✅ | - | PID 존재 여부만 (기본) |
| **자동 재시작** | ✅ | - | Crash 감지 시 |
| **DB 폴링** | ✅ | - | 30초 간격 |
| **명령 처리** | ✅ | - | START, STOP, RESTART |
| **파라미터 관리** | ✅ | - | 재시작 시 적용 |
| **Hook/Adapter** | ⚠️ | - | 기본 구조만 (구현체 최소) |
| **Local REST API** | ❌ | ✅ | 선택적 (V2에서) |
| **중앙 서버 대시보드** | ❌ | ✅ | 기본 API만 (UI 없음) |
| **고급 Health Adapter** | ❌ | ✅ | REST API, 커스텀 로직 |
| **모니터링 & 알림** | ❌ | ✅ | Prometheus, Grafana |

### 11.2 MVP 개발 순서

1. Core Models (Entity, DTO)
2. Database (테이블 생성, 마이그레이션)
3. ProcessManager (프로세스 시작/종료/모니터링)
4. StateManager (State Machine 구현)
5. DBSyncManager (폴링 루프, 명령 처리)
6. HealthChecker (PID 기반 상태 확인)
7. Agent Main Loop (통합 루프)
8. Central Server API (REST 엔드포인트)
9. 기본 Hooks (최소한의 확장 구조)
10. 통합 테스트 & 배포

### 11.3 MVP 성공 기준

✅ Agent가 Python 프로세스 3개 이상 안정적으로 관리 가능  
✅ Crash 자동 감지 및 재시작 (재시작 횟수 제한)  
✅ 중앙 서버에서 START/STOP 명령 등록 시 Agent가 실행  
✅ 파라미터 변경 후 재시작으로 적용 가능  
✅ 30초 폴링 간격에서 200ms 이내 응답성  
✅ DB 장애 시에도 프로세스 유지 가능

---

## 12. 개발 로드맵

### 12.1 Sprint 계획 (2주 기준, 총 6주 = 3 Sprint)

| Sprint | 기간 | 목표 | 결과물 |
|--------|------|------|--------|
| **Sprint 1** | 주 1-2 | Git + Core Models + DB | 저장소 구조, Entity, 마이그레이션 |
| **Sprint 2** | 주 3-4 | ProcessManager + StateManager + DBSync | 프로세스 관리, 폴링 루프, 명령 처리 |
| **Sprint 3** | 주 5-6 | Main Loop + API + 배포 + 테스트 | 통합, API, Windows/Linux 서비스 |

### 12.2 Post-MVP (V2 이상)

- Advanced Health Adapters (REST API Health Check, 커스텀 로직)
- 대시보드 UI (React/Vue 기반 모니터링)
- 모니터링 통합 (Prometheus metrics, Grafana)
- 고급 자동화 (성능 기반 자동 스케일링, 동적 파라미터 조정)
- 다중 Agent 조율 (로드 밸런싱, 장애 조치)

### 12.3 장기 비전 (V3+)

- Kubernetes integration (선택적)
- GPU/TPU 리소스 스케줄링
- ML Ops 통합 (MLflow, Kubeflow)
- 예측 기반 자동 재시작

---

## 요약

### 이 설계의 핵심

✅ **자율성**: Agent는 중앙 서버 없이도 동작  
✅ **단순성**: Polling 기반, 메시지 브로커 불필요  
✅ **확장성**: Hook/Adapter로 모델별 로직 추가  
✅ **신뢰성**: State Machine, Graceful Degradation  
✅ **운영성**: 자동 복구, 감사 로그, 명확한 상태  

---

**설계서 버전**: 1.0  
**최종 수정**: 2025-01-15  
**상태**: MVP 준비 완료
