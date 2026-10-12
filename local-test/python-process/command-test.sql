-- Replace the target IP and model process ID before running this smoke test.
-- COMMAND_TYPE maps directly to <COMMAND_TYPE>.bat in the Python project.

INSERT INTO TB_M26_MODEL_COMMAND (
    MODEL_PROCESS_ID,
    TARGET_SERVER_IP,
    COMMAND_TYPE,
    COMMAND_STATUS,
    REQUESTED_BY
)
VALUES (
    1001,
    '192.168.0.7',
    'RESTART',
    'REQUESTED',
    'local-smoke-test'
);
