# vision-monitor 연동 전달 문서

이 문서는 `local-runtime-agent`와 `vision-monitor`가 공유하는 테이블과 동작 계약을 전달하기 위한 문서입니다.
두 프로젝트는 같은 `TB_M26_MODEL_PROCESS` 데이터를 기준으로 사용해야 합니다.

## 1. 적용 기준

- Agent는 Flyway를 사용하지 않습니다.
- 전체 신규 DB 구성은 `docs/database/schema.sql`을 기준으로 합니다.
- 로컬 테스트 DB를 초기화할 때만 `docs/database/schema-reset.sql`을 사용합니다.
- 기존 데이터가 있는 DB에는 DROP/CREATE를 실행하지 않습니다.
- 기존 DB 변경은 백업 후 ALTER migration 파일을 검토해서 적용해야 합니다.
- 모든 공유 테이블은 공통 AUDIT 14개 컬럼을 가져야 합니다.

## 2. 핵심 식별자 의미

### MODEL_PROCESS_ID

`MODEL_PROCESS_ID`는 감시 대상 모델 프로세스의 업무 식별자입니다.
Agent가 `START`, `STOP`, `RESTART` 명령을 실행할 때 대상 모델 프로세스를 찾는 기준입니다.

### PROCESS_PID

`PROCESS_PID`는 운영체제에서 실제 실행 중인 Python 프로세스의 PID입니다.

- 애플리케이션 시작 시에는 NULL일 수 있습니다.
- `START` 또는 `RESTART` 후 Agent가 실제 `python.exe`를 찾아 갱신합니다.
- `cmd.exe` 또는 `run.bat`의 래퍼 PID를 저장하지 않습니다.
- PID가 살아 있으면 `PROCESS_STATUS = RUNNING`, 종료되면 `STOPPED` 또는 `CRASHED`로 반영합니다.

## 3. TB_M26_MODEL_PROCESS

기존 vision-monitor 비즈니스 컬럼을 유지하면서 Agent 관리 컬럼을 추가한 통합 테이블입니다.
별도의 `PROCESS_CONFIG` 또는 감시대상 등록 테이블은 사용하지 않습니다.

### 기존 비즈니스 컬럼

| 컬럼 | 의미 |
|---|---|
| `MODEL_PROCESS_ID` | 공유 모델 프로세스 식별자, `BIGINT UNSIGNED`, PK |
| `PROCESS_AREA_CODE` | 프로세스 영역 코드 |
| `MODEL_NAME` | 모델 또는 프로세스 이름 |
| `AUTOMATION_NAME` | 자동화 이름 |
| `SERVER_IP` | 프로세스가 실행되는 서버 IP |
| `PYTHON_PROJECT_PATH` | `run.bat`, `stop.bat`, `restart.bat`가 있는 Python 프로젝트 디렉터리 |
| `PROCESS_STATUS` | 공통 프로세스 상태 |
| `MONITORING_STATUS` | vision-monitor 기존 모니터링 상태 |
| `CONTROL_STATUS` | vision-monitor 기존 제어 상태 |
| `LAST_STATUS_TIMESTAMP` | 마지막 상태 변경 시간 |
| `DESCRIPTION` | 설명 |

### Agent 관리 컬럼

| 컬럼 | 의미 |
|---|---|
| `AGENT_ID` | 해당 프로세스를 담당하는 Agent |
| `AUTO_RESTART` | 비정상 종료 시 자동 재시작 여부 |
| `MAX_RESTART_ATTEMPTS` | 자동 재시작 최대 횟수 |
| `RESTART_DELAY_SEC` | 재시작 전 대기 시간 설정값 |
| `TIMEOUT_SEC` | 제어 작업 제한 시간 |
| `HEALTH_CHECK_ENABLED` | 헬스체크 사용 여부 |
| `HEALTH_CHECK_INTERVAL_SEC` | 헬스체크 주기 |
| `HEALTH_CHECK_ENDPOINT` | 선택적 로컬 헬스체크 주소 |
| `PROCESS_PID` | 실제 Python OS 프로세스 PID |
| `LAST_HEALTH_CHECK` | 마지막 헬스체크 시간 |
| `LAST_HEARTBEAT` | Agent가 마지막으로 상태를 저장한 시간 |
| `HEALTH_STATUS` | 마지막 헬스체크 결과 |
| `UPTIME_SEC` | 마지막으로 관찰한 가동 시간 |
| `ERROR_MESSAGE` | 최근 실행 또는 헬스체크 오류 |

## 4. 명령 및 이력 테이블

현재 Agent 기준 DDL의 타입은 아래와 같습니다.

| 테이블 | 컬럼 | 현재 타입 | 용도 |
|---|---|---|---|
| `TB_M26_MODEL_COMMAND` | `MODEL_PROCESS_ID` | `VARCHAR(64)` | 명령 대상 모델 프로세스 ID |
| `TB_M26_MODEL_PARAMETER` | `MODEL_PROCESS_ID` | `VARCHAR(64)` | 파라미터 대상 모델 프로세스 ID |
| `TB_M26_EXECUTION_LOG` | `MODEL_PROCESS_ID` | `VARCHAR(64)` | 실행 이력 대상 모델 프로세스 ID |

`TB_M26_MODEL_PROCESS.MODEL_PROCESS_ID`는 현재 `BIGINT UNSIGNED`입니다. 따라서
vision-monitor와 스키마를 최종 통일할 때는 위 세 테이블의 `MODEL_PROCESS_ID`도
`BIGINT UNSIGNED`로 변경하는 것을 권장합니다. 이 변경은 기존 Java API의 문자열 요청값을
입구에서 숫자로 변환하는 작업과 함께 진행해야 합니다.

현재 명령 DML은 숫자 ID를 문자열로 넣습니다.

```sql
INSERT INTO TB_M26_MODEL_COMMAND (
    COMMAND_ID,
    AGENT_ID,
    MODEL_PROCESS_ID,
    COMMAND_TYPE,
    COMMAND_STATUS,
    COMMAND_PARAMETERS
)
SELECT
    UUID(),
    a.AGENT_ID,
    '1001',
    'START',
    'PENDING',
    JSON_OBJECT()
FROM TB_M26_AGENT a
WHERE a.IP_ADDRESS = '192.168.0.7'
LIMIT 1;
```

`1001`은 OS PID가 아니라 `TB_M26_MODEL_PROCESS.MODEL_PROCESS_ID`입니다.

## 5. 공통 AUDIT 컬럼

아래 14개 컬럼은 `TB_M26_AGENT`, `TB_M26_MODEL_PROCESS`, `TB_M26_MODEL_COMMAND`,
`TB_M26_MODEL_PARAMETER`, `TB_M26_EXECUTION_LOG`에 모두 존재해야 합니다.

```text
CREATED_OBJECT_TYPE
CREATED_OBJECT_ID
CREATED_PROGRAM_ID
CREATED_TIMESTAMP
LAST_UPDATED_OBJECT_TYPE
LAST_UPDATED_OBJECT_ID
LAST_UPDATED_PROGRAM_ID
LAST_UPDATED_TIMESTAMP
DATA_END_STATUS
DATA_END_OBJECT_TYPE
DATA_END_OBJECT_ID
DATA_END_PROGRAM_ID
DATA_END_TIMESTAMP
```

각 컬럼의 COMMENT는 `docs/database/schema.sql`에 정의되어 있습니다.

## 6. Agent 실행 흐름

```text
1. 로컬 IP로 TB_M26_AGENT 조회 또는 등록
2. 해당 AGENT_ID의 TB_M26_MODEL_PROCESS 조회
3. 각 모델 프로세스의 PROCESS_PID 생존 여부 확인
4. 상태와 헬스 결과를 TB_M26_MODEL_PROCESS에 저장
5. TB_M26_MODEL_COMMAND에서 PENDING 명령 조회
6. MODEL_PROCESS_ID로 모델 프로세스 정의 조회
7. PYTHON_PROJECT_PATH의 BAT 파일 실행
8. 실제 python.exe PID 탐색
9. PROCESS_PID와 PROCESS_STATUS 저장
10. TB_M26_EXECUTION_LOG에 실행 결과 저장
```

`START`가 성공하려면 먼저 `TB_M26_MODEL_PROCESS`에 명령 대상 ID가 존재해야 하고,
`PYTHON_PROJECT_PATH`에 `run.bat`이 있어야 합니다.

## 7. 확인 SQL

```sql
SELECT MODEL_PROCESS_ID,
       AGENT_ID,
       PYTHON_PROJECT_PATH,
       PROCESS_STATUS,
       PROCESS_PID,
       HEALTH_STATUS,
       LAST_HEARTBEAT,
       ERROR_MESSAGE
FROM TB_M26_MODEL_PROCESS
ORDER BY MODEL_PROCESS_ID;
```

```sql
SELECT COMMAND_ID,
       AGENT_ID,
       MODEL_PROCESS_ID,
       COMMAND_TYPE,
       COMMAND_STATUS,
       FAILED_REASON,
       PROCESSED_AT
FROM TB_M26_MODEL_COMMAND
ORDER BY CREATED_TIMESTAMP DESC
LIMIT 20;
```

```sql
SELECT LOG_ID,
       MODEL_PROCESS_ID,
       COMMAND_TYPE,
       EXECUTION_STATUS,
       EXIT_CODE,
       STDOUT_PREVIEW,
       STDERR_PREVIEW,
       CREATED_TIMESTAMP
FROM TB_M26_EXECUTION_LOG
ORDER BY CREATED_TIMESTAMP DESC
LIMIT 20;
```

정상적인 `START` 또는 `RESTART` 결과는 다음과 같습니다.

```text
COMMAND_STATUS   = COMPLETED
FAILED_REASON     = NULL
EXECUTION_STATUS  = SUCCESS
PROCESS_STATUS    = RUNNING
PROCESS_PID       = 실제 python.exe PID
```

## 8. 전달해야 할 파일

- 전체 CREATE DDL: `docs/database/schema.sql`
- 로컬 초기화 DDL: `docs/database/schema-reset.sql`
- 기존 테이블 Agent 확장: `docs/database/migrate-model-process-for-agent.sql`
- AUDIT 컬럼 보완: `docs/database/migrate-audit-columns.sql`
- 스키마 검증: `docs/database/verify-schema.sql`
- 동작 검증: `docs/database/runtime-verification.sql`
- Python 명령 테스트: `local-test/python-process/command-test.sql`

vision-monitor는 특히 `TB_M26_MODEL_PROCESS`의 기존 비즈니스 컬럼과 AUDIT 컬럼을
삭제하지 않아야 하며, `PROCESS_PID`와 `MODEL_PROCESS_ID`를 같은 의미로 취급하면 안 됩니다.
## Control Script Naming

The command queue uses `COMMAND_TYPE` as the control script name. The Agent
executes `<COMMAND_TYPE>.bat` from `PYTHON_PROJECT_PATH`.

- `START` executes `start.bat`.
- `STOP` executes `stop.bat`.
- `RESTART` executes `restart.bat`.
- A custom value such as `CUSTOM1` executes `custom1.bat`.

Only letters, digits, underscores, and hyphens are accepted in command types;
path separators and other shell syntax are rejected.
