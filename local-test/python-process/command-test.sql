-- Python process 폴더에서 바로 실행하는 명령 테스트용 단일 INSERT 문
-- 아래 IP와 MODEL_PROCESS_ID만 현재 환경에 맞게 수정합니다.
-- START를 STOP 또는 RESTART로 바꾸면 같은 방식으로 테스트할 수 있습니다.

INSERT INTO TB_M26_COMMAND (
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
    'RESTART',
    'PENDING',
    JSON_OBJECT()
FROM TB_M26_AGENT a
WHERE a.IP_ADDRESS = '192.168.0.7'
LIMIT 1;
