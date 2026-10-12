-- Local Runtime Agent 명령 테스트용 DML
-- 아래 값만 현재 환경에 맞게 수정한 뒤 실행합니다.
-- 기본값은 START입니다. STOP 또는 RESTART로 바꿔 같은 방식으로 테스트할 수 있습니다.

SET @local_ip = '192.168.0.7';
SET @model_process_id = 1001;
SET @command_type = 'START';

SET @agent_id = (
    SELECT AGENT_ID
    FROM TB_M26_AGENT
    WHERE IP_ADDRESS = @local_ip
    ORDER BY AGENT_ID
    LIMIT 1
);

-- START 테스트 전에는 대상 프로세스를 초기화합니다.
-- 이미 실행 중인 프로세스에 START를 넣으면 Agent가 중복 실행하지 않고 무시합니다.
UPDATE TB_M26_MODEL_PROCESS
SET PROCESS_STATUS = 'STOPPED',
    PROCESS_PID = NULL
WHERE MODEL_PROCESS_ID = @model_process_id
  AND @command_type = 'START';

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
    CAST(@model_process_id AS CHAR),
    @command_type,
    'PENDING',
    JSON_OBJECT()
);

-- 등록된 명령 확인
SELECT
    COMMAND_ID,
    AGENT_ID,
    PROCESS_ID,
    COMMAND_TYPE,
    COMMAND_STATUS,
    CREATED_TIMESTAMP
FROM TB_M26_COMMAND
WHERE AGENT_ID = @agent_id
ORDER BY CREATED_TIMESTAMP DESC
LIMIT 1;
