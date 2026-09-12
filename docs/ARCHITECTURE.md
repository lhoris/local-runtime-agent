# Local Runtime Agent - 시스템 설계서

분산 AI 모델 프로세스 관리 시스템 (Spring Boot 기반 PC-local Agent)

**작성일**: 2025-01-15 | **상태**: MVP 설계 | **버전**: 1.0

---

## Quick Start (요약)

Agent는 중앙 서버 없이도 자체 동작하는 자율 루프(Autonomous Loop)이며, 공용 DB 폴링으로 명령/상태를 교환한다.

- **Process Manager** — `ProcessBuilder`로 프로세스 start/stop/restart, PID 추출
- **Health Checker** — PID liveness + crash 감지
- **DB Sync Manager** — 30초 주기 폴링으로 대기 명령(START/STOP/RESTART) 처리
- **State Manager** — STOPPED → STARTING → RUNNING → STOPPING, 이상 시 CRASHED
- **Parameter Manager** — JSON 기반 동적 파라미터, 재시작 시 적용
- **Auto-restart** — crash 감지 시 재시작 횟수 한도 내 자동 복구

핵심 흐름: `DB 명령 → 폴링 → ProcessManager 실행 → 상태 갱신 → DB 반영`. 상세는 아래 12개 섹션 참고.
빌드·실행·배포는 [`../README.md`](../README.md) 및 [`../DEPLOYMENT.md`](../DEPLOYMENT.md) 참고.

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
│ Spring Boot     │                 │ Spring Boot  │            │ MariaDB 11.8 │
│ Agent           │ ←polls every 30s→ │ Server      │ ←→ READ/WRITE │              │
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
| **Shared Database** | 상태 저장소, 명령 큐, 파라미터 저장소 | MariaDB 11.8 |
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
    void syncAgentStatus();  // Agent 상태를 DB에 기록 (agent_status + agent_info.last_heartbeat 포함)
    void pollPendingCommands();  // DB의 미처리 명령 확인
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

## 5. 데이터베이스 스키마 (엔터프라이즈 표준)

### 5.1 테이블 설계 원칙

- 테이블명 프리픽스: `TB_M26_`
- 컬럼 순서: Audit (14개) → PK → Attributes
- 제약조건: PK만 사용 (FK, CHECK 제거)
- Audit 항목: CREATED_*, LAST_UPDATED_*, DATA_END_*

### 5.2 핵심 테이블

#### TB_M26_AGENT
```sql
CREATE TABLE TB_M26_AGENT (
    -- Audit
    CREATED_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '생성Object유형',
    CREATED_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '생성ObjectID',
    CREATED_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '생성프로그램ID',
    CREATED_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '생성일시',
    LAST_UPDATED_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '최종변경Object유형',
    LAST_UPDATED_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '최종변경ObjectID',
    LAST_UPDATED_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '최종변경프로그램ID',
    LAST_UPDATED_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '최종변경일시',
    DATA_END_STATUS VARCHAR(1) DEFAULT 'N' COMMENT '데이터종료여부',
    DATA_END_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '데이터종료Object유형',
    DATA_END_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '데이터종료ObjectID',
    DATA_END_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '데이터종료프로그램ID',
    DATA_END_TIMESTAMP DATETIME DEFAULT NULL COMMENT '데이터종료일시',
    
    -- PK
    AGENT_ID VARCHAR(22) PRIMARY KEY COMMENT 'Agent ID',
    
    -- Attributes
    HOSTNAME VARCHAR(256) COMMENT 'Agent 호스트명',
    OS_TYPE VARCHAR(32) COMMENT 'OS 유형 (WINDOWS, LINUX)',
    IP_ADDRESS VARCHAR(45) COMMENT 'IP 주소',
    SPRING_BOOT_VERSION VARCHAR(32) COMMENT 'Spring Boot 버전',
    INSTALLED_AT DATETIME COMMENT 'Agent 설치일시'
) COMMENT='Local Runtime Agent 정보';
```

#### TB_M26_PROCESS_CONFIG
```sql
CREATE TABLE TB_M26_PROCESS_CONFIG (
    -- Audit
    CREATED_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '생성Object유형',
    CREATED_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '생성ObjectID',
    CREATED_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '생성프로그램ID',
    CREATED_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '생성일시',
    LAST_UPDATED_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '최종변경Object유형',
    LAST_UPDATED_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '최종변경ObjectID',
    LAST_UPDATED_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '최종변경프로그램ID',
    LAST_UPDATED_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '최종변경일시',
    DATA_END_STATUS VARCHAR(1) DEFAULT 'N' COMMENT '데이터종료여부',
    DATA_END_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '데이터종료Object유형',
    DATA_END_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '데이터종료ObjectID',
    DATA_END_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '데이터종료프로그램ID',
    DATA_END_TIMESTAMP DATETIME DEFAULT NULL COMMENT '데이터종료일시',
    
    -- PK
    PROCESS_ID VARCHAR(22) PRIMARY KEY COMMENT '프로세스 ID',
    
    -- Attributes
    AGENT_ID VARCHAR(22) COMMENT 'Agent ID',
    MODEL_NAME VARCHAR(256) COMMENT '모델명',
    MODEL_TYPE VARCHAR(64) COMMENT '모델 유형 (INFERENCE, TRAINING, RUNTIME)',
    EXECUTABLE_PATH VARCHAR(512) COMMENT '실행 파일 경로',
    WORKING_DIRECTORY VARCHAR(512) COMMENT '작업 디렉토리',
    COMMAND_ARGS LONGTEXT COMMENT '명령 인자 (JSON)',
    ENV_VARS LONGTEXT COMMENT '환경 변수 (JSON)',
    AUTO_RESTART VARCHAR(1) DEFAULT 'Y' COMMENT '자동 재시작 여부',
    MAX_RESTART_ATTEMPTS INT DEFAULT 3 COMMENT '최대 재시작 횟수',
    RESTART_DELAY_SEC INT DEFAULT 10 COMMENT '재시작 대기 초 단위',
    TIMEOUT_SEC INT DEFAULT 3600 COMMENT '타임아웃 초 단위',
    MEMORY_LIMIT_MB INT COMMENT '메모리 제한 (MB)',
    CPU_LIMIT_PERCENT INT COMMENT 'CPU 제한 (백분율)',
    HEALTH_CHECK_ENABLED VARCHAR(1) DEFAULT 'Y' COMMENT 'Health Check 활성화 여부',
    HEALTH_CHECK_INTERVAL_SEC INT DEFAULT 30 COMMENT 'Health Check 간격 초 단위',
    HEALTH_CHECK_ENDPOINT VARCHAR(512) COMMENT 'Health Check REST API 엔드포인트',
    METADATA LONGTEXT COMMENT '추가 메타정보 (JSON)'
) COMMENT='프로세스 설정';
```

#### TB_M26_MODEL_PROCESS
```sql
CREATE TABLE TB_M26_MODEL_PROCESS (
    -- Audit
    CREATED_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '생성Object유형',
    CREATED_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '생성ObjectID',
    CREATED_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '생성프로그램ID',
    CREATED_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '생성일시',
    LAST_UPDATED_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '최종변경Object유형',
    LAST_UPDATED_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '최종변경ObjectID',
    LAST_UPDATED_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '최종변경프로그램ID',
    LAST_UPDATED_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '최종변경일시',
    DATA_END_STATUS VARCHAR(1) DEFAULT 'N' COMMENT '데이터종료여부',
    DATA_END_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '데이터종료Object유형',
    DATA_END_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '데이터종료ObjectID',
    DATA_END_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '데이터종료프로그램ID',
    DATA_END_TIMESTAMP DATETIME DEFAULT NULL COMMENT '데이터종료일시',
    
    -- PK
    STATUS_ID VARCHAR(22) PRIMARY KEY COMMENT '상태 ID',
    
    -- Attributes
    AGENT_ID VARCHAR(22) COMMENT 'Agent ID',
    PROCESS_ID VARCHAR(22) COMMENT '프로세스 ID',
    PROCESS_STATE VARCHAR(32) COMMENT '프로세스 상태',
    PROCESS_PID INT COMMENT '프로세스 PID',
    CPU_PERCENT DECIMAL(5,2) COMMENT 'CPU 사용률',
    MEMORY_MB INT COMMENT '메모리 사용량 (MB)',
    HEALTH_STATUS VARCHAR(32) COMMENT 'Health 상태',
    UPTIME_SEC BIGINT COMMENT '가동 시간 (초)',
    CRASH_COUNT INT DEFAULT 0 COMMENT '비정상 종료 횟수',
    LAST_HEALTH_CHECK DATETIME COMMENT '마지막 Health Check 일시',
    LAST_CRASH_TIME DATETIME COMMENT '마지막 비정상 종료 일시',
    LAST_HEARTBEAT DATETIME COMMENT '마지막 하트비트 일시',
    ERROR_MESSAGE LONGTEXT COMMENT '에러 메시지'
) COMMENT='Agent 상태';
```

#### TB_M26_COMMAND
```sql
CREATE TABLE TB_M26_COMMAND (
    -- Audit
    CREATED_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '생성Object유형',
    CREATED_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '생성ObjectID',
    CREATED_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '생성프로그램ID',
    CREATED_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '생성일시',
    LAST_UPDATED_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '최종변경Object유형',
    LAST_UPDATED_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '최종변경ObjectID',
    LAST_UPDATED_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '최종변경프로그램ID',
    LAST_UPDATED_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '최종변경일시',
    DATA_END_STATUS VARCHAR(1) DEFAULT 'N' COMMENT '데이터종료여부',
    DATA_END_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '데이터종료Object유형',
    DATA_END_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '데이터종료ObjectID',
    DATA_END_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '데이터종료프로그램ID',
    DATA_END_TIMESTAMP DATETIME DEFAULT NULL COMMENT '데이터종료일시',
    
    -- PK
    COMMAND_ID VARCHAR(22) PRIMARY KEY COMMENT '명령 ID',
    
    -- Attributes
    AGENT_ID VARCHAR(22) COMMENT 'Agent ID',
    PROCESS_ID VARCHAR(22) COMMENT '프로세스 ID',
    COMMAND_TYPE VARCHAR(32) COMMENT '명령 유형 (START, STOP, RESTART, PARAM_UPDATE, HEALTH_CHECK)',
    COMMAND_STATUS VARCHAR(32) COMMENT '명령 상태 (PENDING, PROCESSING, COMPLETED, FAILED)',
    COMMAND_PARAMETERS LONGTEXT COMMENT '명령 파라미터 (JSON)',
    PROCESSED_AT DATETIME COMMENT '처리 완료 일시',
    FAILED_REASON LONGTEXT COMMENT '실패 사유'
) COMMENT='명령 큐';
```

#### TB_M26_MODEL_PARAMETER
```sql
CREATE TABLE TB_M26_MODEL_PARAMETER (
    -- Audit
    CREATED_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '생성Object유형',
    CREATED_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '생성ObjectID',
    CREATED_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '생성프로그램ID',
    CREATED_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '생성일시',
    LAST_UPDATED_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '최종변경Object유형',
    LAST_UPDATED_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '최종변경ObjectID',
    LAST_UPDATED_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '최종변경프로그램ID',
    LAST_UPDATED_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '최종변경일시',
    DATA_END_STATUS VARCHAR(1) DEFAULT 'N' COMMENT '데이터종료여부',
    DATA_END_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '데이터종료Object유형',
    DATA_END_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '데이터종료ObjectID',
    DATA_END_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '데이터종료프로그램ID',
    DATA_END_TIMESTAMP DATETIME DEFAULT NULL COMMENT '데이터종료일시',
    
    -- PK
    PARAMETER_ID VARCHAR(22) PRIMARY KEY COMMENT '파라미터 ID',
    
    -- Attributes
    PROCESS_ID VARCHAR(22) COMMENT '프로세스 ID',
    PARAM_KEY VARCHAR(256) COMMENT '파라미터 키',
    PARAM_VALUE LONGTEXT COMMENT '파라미터 값',
    PARAM_TYPE VARCHAR(32) COMMENT '파라미터 유형 (STRING, INTEGER, FLOAT, BOOLEAN, JSON)',
    PARAM_VERSION INT DEFAULT 1 COMMENT '파라미터 버전',
    IS_ACTIVE VARCHAR(1) DEFAULT 'Y' COMMENT '활성 여부'
) COMMENT='모델 파라미터';
```

#### TB_M26_EXECUTION_LOG
```sql
CREATE TABLE TB_M26_EXECUTION_LOG (
    -- Audit
    CREATED_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '생성Object유형',
    CREATED_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '생성ObjectID',
    CREATED_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '생성프로그램ID',
    CREATED_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '생성일시',
    LAST_UPDATED_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '최종변경Object유형',
    LAST_UPDATED_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '최종변경ObjectID',
    LAST_UPDATED_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '최종변경프로그램ID',
    LAST_UPDATED_TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP() COMMENT '최종변경일시',
    DATA_END_STATUS VARCHAR(1) DEFAULT 'N' COMMENT '데이터종료여부',
    DATA_END_OBJECT_TYPE VARCHAR(1) DEFAULT NULL COMMENT '데이터종료Object유형',
    DATA_END_OBJECT_ID VARCHAR(22) DEFAULT NULL COMMENT '데이터종료ObjectID',
    DATA_END_PROGRAM_ID VARCHAR(22) DEFAULT NULL COMMENT '데이터종료프로그램ID',
    DATA_END_TIMESTAMP DATETIME DEFAULT NULL COMMENT '데이터종료일시',
    
    -- PK
    LOG_ID VARCHAR(22) PRIMARY KEY COMMENT '로그 ID',
    
    -- Attributes
    AGENT_ID VARCHAR(22) COMMENT 'Agent ID',
    PROCESS_ID VARCHAR(22) COMMENT '프로세스 ID',
    COMMAND_TYPE VARCHAR(32) COMMENT '명령 유형',
    EXECUTION_STATUS VARCHAR(32) COMMENT '실행 상태 (SUCCESS, FAILURE)',
    EXIT_CODE INT COMMENT '종료 코드',
    STDOUT_PREVIEW LONGTEXT COMMENT '표준 출력 미리보기 (첫 1000자)',
    STDERR_PREVIEW LONGTEXT COMMENT '표준 에러 미리보기 (첫 1000자)',
    DURATION_SEC INT COMMENT '실행 시간 (초)'
) COMMENT='명령 실행 로그';
```

### 5.3 조회 뷰

```sql
-- 통합 상태 뷰 (중앙 서버 대시보드용)
CREATE VIEW V_M26_MODEL_STATUS AS
SELECT
    ai.AGENT_ID,
    ai.HOSTNAME,
    pc.PROCESS_ID,
    pc.MODEL_NAME,
    ast.PROCESS_STATE,
    ast.PROCESS_PID,
    ast.CPU_PERCENT,
    ast.MEMORY_MB,
    ast.HEALTH_STATUS,
    ast.UPTIME_SEC,
    ast.CRASH_COUNT,
    ast.LAST_HEALTH_CHECK,
    COUNT(CASE WHEN c.COMMAND_STATUS = 'PENDING' THEN 1 END) as PENDING_COMMANDS
FROM TB_M26_AGENT ai
JOIN TB_M26_PROCESS_CONFIG pc ON ai.AGENT_ID = pc.AGENT_ID AND ai.DATA_END_STATUS = 'N' AND pc.DATA_END_STATUS = 'N'
LEFT JOIN TB_M26_MODEL_PROCESS ast ON pc.PROCESS_ID = ast.PROCESS_ID AND ast.DATA_END_STATUS = 'N'
LEFT JOIN TB_M26_COMMAND c ON ai.AGENT_ID = c.AGENT_ID AND c.COMMAND_STATUS = 'PENDING' AND c.DATA_END_STATUS = 'N'
GROUP BY ai.AGENT_ID, pc.PROCESS_ID;
```

### 5.4 인덱싱 전략 (선택적, 조회 성능 최적화)

- `TB_M26_COMMAND`: `(AGENT_ID, COMMAND_STATUS, CREATED_TIMESTAMP)`
- `TB_M26_MODEL_PROCESS`: `(AGENT_ID, PROCESS_ID, LAST_UPDATED_TIMESTAMP)`
- `TB_M26_EXECUTION_LOG`: `(PROCESS_ID, CREATED_TIMESTAMP)`
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
6. **SyncStatus**: 현재 상태를 `agent_status`에 기록하고, `agent_info.last_heartbeat` 업데이트

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
  url: jdbc:mysql://central-db:3306/agent_db?serverTimezone=UTC&allowPublicKeyRetrieval=true
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
