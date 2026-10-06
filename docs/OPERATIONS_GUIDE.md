# Local Runtime Agent 운영 확인 가이드

이 문서는 Agent를 기동한 뒤 SQL과 로그를 함께 보면서 정상 동작을 확인하는 절차입니다.

Agent의 핵심 동작은 다음과 같습니다.

```text
애플리케이션 기동
-> 로컬 IPv4 탐지
-> TB_M26_AGENT 자동 등록 또는 기존 행 조회
-> 해당 AGENT_ID의 TB_M26_MODEL_PROCESS 조회
-> Python 프로세스 상태 확인
-> Health/Crash 확인 및 자동 재시작
-> 명령 처리
-> TB_M26_MODEL_PROCESS에 상태와 heartbeat 저장
```

감시 대상은 별도 등록 테이블에 넣지 않습니다. 현재 Agent의 `TB_M26_MODEL_PROCESS` 행이 매 Loop의 기준입니다.

## 1. 사전 준비

필요한 것:

- Java 21
- MariaDB 11.8 접속 정보
- 실행할 Python 프로그램과 실행 경로
- 현재 PC의 로컬 IPv4 주소

DB 스키마는 애플리케이션이 자동으로 변경하지 않습니다. 먼저 다음 SQL을 적용합니다.

```sql
SOURCE docs/database/schema.sql;
SOURCE docs/database/verify-schema.sql;
```

`verify-schema.sql` 결과에서 테이블/컬럼 상태가 `MISSING`이면 Agent를 실행하지 말고 DB 스키마를 먼저 보정합니다.

## 2. 환경 변수 설정

### Windows PowerShell

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:DB_HOST = "localhost"
$env:DB_PORT = "3306"
$env:DB_NAME = "agent_db"
$env:DB_USER = "agent"
$env:DB_PASSWORD = "your-password"
$env:AGENT_POLLING_INTERVAL_SEC = "30"
```

### Linux

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_NAME=agent_db
export DB_USER=agent
export DB_PASSWORD='your-password'
export AGENT_POLLING_INTERVAL_SEC=30
```

`AGENT_ID` 환경 변수는 설정하지 않습니다. Agent ID는 로컬 hostname을 기준으로 자동 생성됩니다.

## 3. 애플리케이션 기동

개발 실행:

```powershell
.\mvnw.cmd spring-boot:run
```

패키지 실행:

```powershell
java -jar target\local-runtime-agent-0.0.1-SNAPSHOT.jar
```

Linux에서는 다음처럼 실행할 수 있습니다.

```bash
./mvnw spring-boot:run
# 또는
java -jar target/local-runtime-agent-0.0.1-SNAPSHOT.jar
```

## 4. Agent 자동 등록 확인

애플리케이션 기동 직후 DB에서 현재 PC의 IP를 조회합니다.

```sql
SET @local_ip = '192.168.0.7';

SELECT AGENT_ID,
       HOSTNAME,
       OS_TYPE,
       IP_ADDRESS,
       INSTALLED_AT
FROM TB_M26_AGENT
WHERE IP_ADDRESS = @local_ip;
```

정상 결과는 1행입니다. 최초 기동이면 로그에 다음과 비슷한 메시지가 보입니다.

```text
Registered local Agent agent-DESKTOP-... with IP 192.168.0.7
```

이미 등록된 IP라면 새 행을 만들지 않고 기존 `AGENT_ID`를 재사용합니다.

Agent가 등록되지 않으면 다음을 확인합니다.

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `TB_M26_AGENT` 테이블 존재 여부
- 현재 PC의 IPv4 주소가 실제 DB 접속 주소와 일치하는지
- 로그의 `local IPs [...]` 목록

## 5. 감시할 Python 프로세스 등록

Agent ID를 확인한 뒤 `TB_M26_MODEL_PROCESS`에 프로세스 정의를 넣습니다.

```sql
SET @agent_id = (
    SELECT AGENT_ID
    FROM TB_M26_AGENT
    WHERE IP_ADDRESS = '192.168.0.7'
    ORDER BY AGENT_ID
    LIMIT 1
);

INSERT INTO TB_M26_MODEL_PROCESS (
    PROCESS_ID,
    AGENT_ID,
    MODEL_NAME,
    MODEL_TYPE,
    EXECUTABLE_PATH,
    WORKING_DIRECTORY,
    COMMAND_ARGS,
    ENV_VARS,
    AUTO_RESTART,
    MAX_RESTART_ATTEMPTS,
    RESTART_DELAY_SEC,
    HEALTH_CHECK_ENABLED,
    HEALTH_CHECK_INTERVAL_SEC,
    PROCESS_STATE,
    CRASH_COUNT
) VALUES (
    'model-local-01',
    @agent_id,
    'Example Python Model',
    'RUNTIME',
    'C:/models/example/start.py',
    'C:/models/example',
    JSON_ARRAY(),
    JSON_OBJECT(),
    TRUE,
    3,
    10,
    TRUE,
    30,
    'STOPPED',
    0
);
```

Linux 실행 파일은 예를 들어 다음처럼 등록합니다.

```sql
UPDATE TB_M26_MODEL_PROCESS
SET EXECUTABLE_PATH = '/usr/bin/python3',
    COMMAND_ARGS = JSON_ARRAY('/opt/models/example/start.py')
WHERE PROCESS_ID = 'model-local-01';
```

다음 SQL로 Agent에 할당된 감시 대상이 보이는지 확인합니다.

```sql
SELECT PROCESS_ID,
       AGENT_ID,
       MODEL_NAME,
       EXECUTABLE_PATH,
       PROCESS_STATE,
       AUTO_RESTART
FROM TB_M26_MODEL_PROCESS
WHERE AGENT_ID = @agent_id
ORDER BY PROCESS_ID;
```

다음 Loop부터 로그에 아래 메시지가 보입니다.

```text
Loaded 1 model process definition(s) for agent agent-...
Process model-local-01 state: STOPPED
```

## 6. START 명령 확인

Agent는 `TB_M26_COMMAND`의 `PENDING` 명령을 주기적으로 읽습니다.

```sql
INSERT INTO TB_M26_COMMAND (
    COMMAND_ID,
    AGENT_ID,
    PROCESS_ID,
    COMMAND_TYPE,
    COMMAND_STATUS,
    COMMAND_PARAMETERS
) VALUES (
    UUID(),
    @agent_id,
    'model-local-01',
    'START',
    'PENDING',
    JSON_OBJECT()
);
```

처리 후 확인:

```sql
SELECT COMMAND_ID,
       PROCESS_ID,
       COMMAND_TYPE,
       COMMAND_STATUS,
       PROCESSED_AT,
       FAILED_REASON
FROM TB_M26_COMMAND
WHERE AGENT_ID = @agent_id
ORDER BY CREATED_TIMESTAMP DESC
LIMIT 10;
```

정상이라면 `COMMAND_STATUS`가 `COMPLETED`가 되고, 프로세스 상태는 다음처럼 바뀝니다.

```sql
SELECT PROCESS_ID,
       PROCESS_STATE,
       PROCESS_PID,
       LAST_HEARTBEAT,
       HEALTH_STATUS
FROM TB_M26_MODEL_PROCESS
WHERE PROCESS_ID = 'model-local-01';
```

예상 상태:

```text
PROCESS_STATE = RUNNING
PROCESS_PID   = 운영체제가 할당한 PID
```

## 7. STOP과 RESTART 확인

STOP:

```sql
INSERT INTO TB_M26_COMMAND (
    COMMAND_ID, AGENT_ID, PROCESS_ID, COMMAND_TYPE, COMMAND_STATUS, COMMAND_PARAMETERS
) VALUES (
    UUID(), @agent_id, 'model-local-01', 'STOP', 'PENDING', JSON_OBJECT()
);
```

RESTART:

```sql
INSERT INTO TB_M26_COMMAND (
    COMMAND_ID, AGENT_ID, PROCESS_ID, COMMAND_TYPE, COMMAND_STATUS, COMMAND_PARAMETERS
) VALUES (
    UUID(), @agent_id, 'model-local-01', 'RESTART', 'PENDING', JSON_OBJECT()
);
```

각 명령 후 다음을 다시 조회합니다.

```sql
SELECT PROCESS_STATE, PROCESS_PID, LAST_HEARTBEAT, ERROR_MESSAGE
FROM TB_M26_MODEL_PROCESS
WHERE PROCESS_ID = 'model-local-01';
```

## 8. 자동 재시작 확인

먼저 설정을 확인합니다.

```sql
SELECT AUTO_RESTART,
       MAX_RESTART_ATTEMPTS,
       CRASH_COUNT,
       LAST_CRASH_TIME,
       PROCESS_STATE
FROM TB_M26_MODEL_PROCESS
WHERE PROCESS_ID = 'model-local-01';
```

실행 중인 Python 프로세스를 테스트 목적으로 종료한 뒤 다음 Loop를 기다립니다.

```text
Process ... is no longer alive ... marking CRASHED
Auto-restarting ...
```

DB에서는 다음을 확인합니다.

```sql
SELECT PROCESS_STATE,
       PROCESS_PID,
       CRASH_COUNT,
       LAST_CRASH_TIME,
       LAST_HEARTBEAT
FROM TB_M26_MODEL_PROCESS
WHERE PROCESS_ID = 'model-local-01';
```

`AUTO_RESTART = FALSE`이면 자동 재시작하지 않고 `STOPPED`로 남습니다.

## 9. 정상 동작 최종 확인

제공된 smoke test를 실행합니다.

```sql
SOURCE docs/database/smoke-test.sql;
```

또는 직접 확인합니다.

```sql
SELECT a.AGENT_ID,
       a.IP_ADDRESS,
       COUNT(p.PROCESS_ID) AS PROCESS_COUNT,
       SUM(CASE WHEN p.PROCESS_STATE = 'RUNNING' THEN 1 ELSE 0 END) AS RUNNING_COUNT,
       MAX(p.LAST_HEARTBEAT) AS LAST_HEARTBEAT
FROM TB_M26_AGENT a
LEFT JOIN TB_M26_MODEL_PROCESS p ON p.AGENT_ID = a.AGENT_ID
WHERE a.IP_ADDRESS = @local_ip
GROUP BY a.AGENT_ID, a.IP_ADDRESS;
```

정상 기준:

- Agent가 1행 존재한다.
- 현재 PC의 IP가 `IP_ADDRESS`와 일치한다.
- 감시 대상 프로세스가 `PROCESS_COUNT`에 포함된다.
- 실행 중이면 `RUNNING_COUNT`가 1 이상이다.
- `LAST_HEARTBEAT`가 현재 시각에 가깝게 갱신된다.
- START/STOP/RESTART 명령이 `COMPLETED`가 된다.

## 10. 문제 상황별 확인 위치

| 증상 | 먼저 확인할 곳 |
| --- | --- |
| Agent 행이 생기지 않음 | DB 접속 환경 변수, `TB_M26_AGENT`, 로그의 `local IPs` |
| 프로세스가 조회되지 않음 | `TB_M26_MODEL_PROCESS.AGENT_ID`와 현재 Agent ID |
| 프로세스가 시작되지 않음 | `EXECUTABLE_PATH`, `WORKING_DIRECTORY`, `COMMAND_ARGS` JSON |
| 명령이 처리되지 않음 | `TB_M26_COMMAND.COMMAND_STATUS`, `AGENT_ID`, `PROCESS_ID` |
| heartbeat가 멈춤 | Agent Loop 로그와 `LAST_HEARTBEAT` |
| 재시작되지 않음 | `AUTO_RESTART`, `MAX_RESTART_ATTEMPTS`, `CRASH_COUNT` |

가장 먼저 볼 소스 파일은 [AgentMainLoop.java](../src/main/java/com/lra/agent/loop/AgentMainLoop.java)입니다. Agent 등록 방식은 [AgentIdentityResolver.java](../src/main/java/com/lra/agent/identity/AgentIdentityResolver.java), 프로세스 정의는 `TB_M26_MODEL_PROCESS`에서 확인합니다.
