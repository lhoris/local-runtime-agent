-- Replace the target IP and model process ID before running this smoke test.
-- COMMAND_TYPE maps directly to <COMMAND_TYPE>.bat in the Python project.

INSERT INTO TB_M26_MODEL_COMMAND (
    COMMAND_ID,
    AGENT_ID,
    MODEL_PROCESS_ID,
    COMMAND_TYPE,
    COMMAND_STATUS,
    COMMAND_PARAMETERS
)
SELECT UUID(),
       AGENT_ID,
       '1001',
       'RESTART',
       'PENDING',
       '{}'
  FROM TB_M26_AGENT
 WHERE IP_ADDRESS = '192.168.0.7'
 LIMIT 1;
