-- Local Runtime Agent baseline schema for MariaDB 11.8.
-- This is a manual deployment script. It intentionally does not drop tables.
-- Review existing tables before applying it to a non-empty database.

CREATE TABLE IF NOT EXISTS TB_M26_AGENT (
    CREATED_OBJECT_TYPE VARCHAR(1) COMMENT 'Creation object type',
    CREATED_OBJECT_ID VARCHAR(22) COMMENT 'Creation object identifier',
    CREATED_PROGRAM_ID VARCHAR(22) COMMENT 'Creation program identifier',
    CREATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp',
    LAST_UPDATED_OBJECT_TYPE VARCHAR(1) COMMENT 'Last update object type',
    LAST_UPDATED_OBJECT_ID VARCHAR(22) COMMENT 'Last update object identifier',
    LAST_UPDATED_PROGRAM_ID VARCHAR(22) COMMENT 'Last update program identifier',
    LAST_UPDATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp',
    DATA_END_STATUS VARCHAR(1) DEFAULT 'N' COMMENT 'Data end status',
    DATA_END_OBJECT_TYPE VARCHAR(1) COMMENT 'Data end object type',
    DATA_END_OBJECT_ID VARCHAR(22) COMMENT 'Data end object identifier',
    DATA_END_PROGRAM_ID VARCHAR(22) COMMENT 'Data end program identifier',
    DATA_END_TIMESTAMP TIMESTAMP NULL COMMENT 'Data end timestamp',
    AGENT_ID VARCHAR(64) PRIMARY KEY COMMENT 'Agent unique identifier, generated from local hostname',
    HOSTNAME VARCHAR(256) COMMENT 'Machine hostname',
    OS_TYPE VARCHAR(32) COMMENT 'Operating system name',
    IP_ADDRESS VARCHAR(45) COMMENT 'Local IPv4 address used to identify this Agent',
    SPRING_BOOT_VERSION VARCHAR(32) COMMENT 'Spring Boot version reported by the Agent',
    INSTALLED_AT TIMESTAMP NULL COMMENT 'First registration timestamp',
    WORKED_AT DATETIME NULL COMMENT 'Local timestamp of the latest Agent loop execution'
) COMMENT='Local Runtime Agent registration and network identity';

CREATE TABLE IF NOT EXISTS TB_M26_MODEL_PROCESS (
    CREATED_OBJECT_TYPE VARCHAR(1) COMMENT 'Creation object type',
    CREATED_OBJECT_ID VARCHAR(22) COMMENT 'Creation object identifier',
    CREATED_PROGRAM_ID VARCHAR(22) COMMENT 'Creation program identifier',
    CREATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp',
    LAST_UPDATED_OBJECT_TYPE VARCHAR(1) COMMENT 'Last update object type',
    LAST_UPDATED_OBJECT_ID VARCHAR(22) COMMENT 'Last update object identifier',
    LAST_UPDATED_PROGRAM_ID VARCHAR(22) COMMENT 'Last update program identifier',
    LAST_UPDATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp',
    DATA_END_STATUS VARCHAR(1) DEFAULT 'N' COMMENT 'Data end status',
    DATA_END_OBJECT_TYPE VARCHAR(1) COMMENT 'Data end object type',
    DATA_END_OBJECT_ID VARCHAR(22) COMMENT 'Data end object identifier',
    DATA_END_PROGRAM_ID VARCHAR(22) COMMENT 'Data end program identifier',
    DATA_END_TIMESTAMP TIMESTAMP NULL COMMENT 'Data end timestamp',
    -- Existing vision-monitor business columns
    MODEL_PROCESS_ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT 'Shared vision-monitor model process identifier',
    PROCESS_AREA_CODE VARCHAR(64) COMMENT 'Existing vision-monitor process area code',
    MODEL_NAME VARCHAR(256) COMMENT 'Human-readable model or process name',
    AUTOMATION_NAME VARCHAR(256) COMMENT 'Existing vision-monitor automation name',
    SERVER_IP VARCHAR(45) COMMENT 'Existing server IP for the process',
    PYTHON_PROJECT_PATH VARCHAR(512) COMMENT 'Python project directory containing start.bat, stop.bat, and restart.bat',
    PROCESS_STATUS VARCHAR(32) COMMENT 'Shared process lifecycle status',
    MONITORING_STATUS VARCHAR(32) COMMENT 'Existing vision-monitor monitoring status',
    CONTROL_STATUS VARCHAR(32) COMMENT 'Existing vision-monitor control status',
    LAST_STATUS_TIMESTAMP TIMESTAMP NULL COMMENT 'Existing latest status timestamp',
    DESCRIPTION LONGTEXT COMMENT 'Existing process description',

    -- Agent ownership and execution columns
    AGENT_ID VARCHAR(64) NOT NULL COMMENT 'Agent responsible for this process',
    AUTO_RESTART BOOLEAN DEFAULT TRUE COMMENT 'Whether crashed processes are restarted automatically',
    MAX_RESTART_ATTEMPTS INT DEFAULT 3 COMMENT 'Maximum automatic restart attempts',
    RESTART_DELAY_SEC INT DEFAULT 10 COMMENT 'Delay before automatic restart in seconds',
    TIMEOUT_SEC INT DEFAULT 3600 COMMENT 'Process operation timeout in seconds',
    HEALTH_CHECK_ENABLED BOOLEAN DEFAULT TRUE COMMENT 'Whether health checks are enabled',
    HEALTH_CHECK_INTERVAL_SEC INT DEFAULT 30 COMMENT 'Health check interval in seconds',
    HEALTH_CHECK_ENDPOINT VARCHAR(512) COMMENT 'Optional local health endpoint',
    PROCESS_PID INT COMMENT 'Latest operating system process ID',
    LAST_HEALTH_CHECK TIMESTAMP NULL COMMENT 'Timestamp of the latest health check',
    LAST_HEARTBEAT TIMESTAMP NULL COMMENT 'Timestamp of the latest Agent status update',
    HEALTH_STATUS VARCHAR(32) COMMENT 'Latest health check result',
    UPTIME_SEC BIGINT COMMENT 'Latest observed process uptime in seconds',
    ERROR_MESSAGE LONGTEXT COMMENT 'Latest process or health error message'
) COMMENT='Process definition and latest runtime status for each managed model process';


CREATE TABLE IF NOT EXISTS TB_M26_MODEL_COMMAND (
    CREATED_OBJECT_TYPE VARCHAR(1) COMMENT 'Creation object type',
    CREATED_OBJECT_ID VARCHAR(22) COMMENT 'Creation object identifier',
    CREATED_PROGRAM_ID VARCHAR(22) COMMENT 'Creation program identifier',
    CREATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp',
    LAST_UPDATED_OBJECT_TYPE VARCHAR(1) COMMENT 'Last update object type',
    LAST_UPDATED_OBJECT_ID VARCHAR(22) COMMENT 'Last update object identifier',
    LAST_UPDATED_PROGRAM_ID VARCHAR(22) COMMENT 'Last update program identifier',
    LAST_UPDATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp',
    DATA_END_STATUS VARCHAR(1) DEFAULT 'N' COMMENT 'Data end status',
    DATA_END_OBJECT_TYPE VARCHAR(1) COMMENT 'Data end object type',
    DATA_END_OBJECT_ID VARCHAR(22) COMMENT 'Data end object identifier',
    DATA_END_PROGRAM_ID VARCHAR(22) COMMENT 'Data end program identifier',
    DATA_END_TIMESTAMP TIMESTAMP NULL COMMENT 'Data end timestamp',
    MODEL_COMMAND_ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT 'Model command identifier',
    MODEL_PROCESS_ID BIGINT UNSIGNED NOT NULL COMMENT 'Target model process identifier',
    TARGET_SERVER_IP VARCHAR(45) NOT NULL COMMENT 'Target Agent server IP',
    COMMAND_TYPE VARCHAR(20) NOT NULL COMMENT 'Command type such as START, STOP, or RESTART',
    COMMAND_STATUS VARCHAR(20) NOT NULL DEFAULT 'REQUESTED' COMMENT 'Command lifecycle status',
    REQUESTED_BY VARCHAR(100) NOT NULL COMMENT 'User or system that requested the command',
    REQUESTED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Command request timestamp',
    CLAIMED_TIMESTAMP TIMESTAMP NULL COMMENT 'Agent claim timestamp',
    PROCESSED_TIMESTAMP TIMESTAMP NULL COMMENT 'Command completion timestamp',
    AGENT_ID VARCHAR(100) COMMENT 'Agent that claimed the command',
    RESULT_CODE VARCHAR(50) COMMENT 'Execution result code',
    RESULT_MESSAGE VARCHAR(2000) COMMENT 'Execution result message',
    RETRY_COUNT INT NOT NULL DEFAULT 0 COMMENT 'Retry count'
) COMMENT='Model process command queue consumed by local Agents';

CREATE TABLE IF NOT EXISTS TB_M26_COMMAND (
    CREATED_OBJECT_TYPE VARCHAR(1) COMMENT 'Creation object type',
    CREATED_OBJECT_ID VARCHAR(22) COMMENT 'Creation object identifier',
    CREATED_PROGRAM_ID VARCHAR(22) COMMENT 'Creation program identifier',
    CREATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp',
    LAST_UPDATED_OBJECT_TYPE VARCHAR(1) COMMENT 'Last update object type',
    LAST_UPDATED_OBJECT_ID VARCHAR(22) COMMENT 'Last update object identifier',
    LAST_UPDATED_PROGRAM_ID VARCHAR(22) COMMENT 'Last update program identifier',
    LAST_UPDATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp',
    DATA_END_STATUS VARCHAR(1) DEFAULT 'N' COMMENT 'Data end status',
    DATA_END_OBJECT_TYPE VARCHAR(1) COMMENT 'Data end object type',
    DATA_END_OBJECT_ID VARCHAR(22) COMMENT 'Data end object identifier',
    DATA_END_PROGRAM_ID VARCHAR(22) COMMENT 'Data end program identifier',
    DATA_END_TIMESTAMP TIMESTAMP NULL COMMENT 'Data end timestamp',
    COMMAND_ID VARCHAR(64) PRIMARY KEY COMMENT 'Command unique identifier',
    AGENT_ID VARCHAR(64) NOT NULL COMMENT 'Target Agent identifier',
    MODEL_PROCESS_ID VARCHAR(64) COMMENT 'Target model process identifier',
    COMMAND_TYPE VARCHAR(32) COMMENT 'Command type such as START, STOP, or RESTART',
    COMMAND_STATUS VARCHAR(32) COMMENT 'Command lifecycle status',
    COMMAND_PARAMETERS LONGTEXT COMMENT 'Command parameters as a JSON object',
    PROCESSED_AT TIMESTAMP NULL COMMENT 'Command completion timestamp',
    FAILED_REASON LONGTEXT COMMENT 'Failure reason when command processing fails'
) COMMENT='Command queue consumed by local Agents';

CREATE INDEX IF NOT EXISTS IDX_M26_COMMAND_PENDING
    ON TB_M26_COMMAND (AGENT_ID, COMMAND_STATUS);

CREATE TABLE IF NOT EXISTS TB_M26_MODEL_PARAMETER (
    CREATED_OBJECT_TYPE VARCHAR(1) COMMENT 'Creation object type',
    CREATED_OBJECT_ID VARCHAR(22) COMMENT 'Creation object identifier',
    CREATED_PROGRAM_ID VARCHAR(22) COMMENT 'Creation program identifier',
    CREATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp',
    LAST_UPDATED_OBJECT_TYPE VARCHAR(1) COMMENT 'Last update object type',
    LAST_UPDATED_OBJECT_ID VARCHAR(22) COMMENT 'Last update object identifier',
    LAST_UPDATED_PROGRAM_ID VARCHAR(22) COMMENT 'Last update program identifier',
    LAST_UPDATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp',
    DATA_END_STATUS VARCHAR(1) DEFAULT 'N' COMMENT 'Data end status',
    DATA_END_OBJECT_TYPE VARCHAR(1) COMMENT 'Data end object type',
    DATA_END_OBJECT_ID VARCHAR(22) COMMENT 'Data end object identifier',
    DATA_END_PROGRAM_ID VARCHAR(22) COMMENT 'Data end program identifier',
    DATA_END_TIMESTAMP TIMESTAMP NULL COMMENT 'Data end timestamp',
    PARAMETER_ID VARCHAR(64) PRIMARY KEY COMMENT 'Parameter unique identifier',
    MODEL_PROCESS_ID VARCHAR(64) NOT NULL COMMENT 'Target model process identifier',
    PARAM_KEY VARCHAR(256) COMMENT 'Parameter name',
    PARAM_VALUE LONGTEXT COMMENT 'Parameter value serialized as text or JSON',
    PARAM_TYPE VARCHAR(32) COMMENT 'Parameter type such as STRING, INTEGER, or JSON',
    PARAM_VERSION INT DEFAULT 1 COMMENT 'Parameter revision number',
    IS_ACTIVE BOOLEAN DEFAULT TRUE COMMENT 'Whether this parameter is active'
) COMMENT='Versioned runtime parameters for managed processes';

CREATE TABLE IF NOT EXISTS TB_M26_EXECUTION_LOG (
    CREATED_OBJECT_TYPE VARCHAR(1) COMMENT 'Creation object type',
    CREATED_OBJECT_ID VARCHAR(22) COMMENT 'Creation object identifier',
    CREATED_PROGRAM_ID VARCHAR(22) COMMENT 'Creation program identifier',
    CREATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation timestamp',
    LAST_UPDATED_OBJECT_TYPE VARCHAR(1) COMMENT 'Last update object type',
    LAST_UPDATED_OBJECT_ID VARCHAR(22) COMMENT 'Last update object identifier',
    LAST_UPDATED_PROGRAM_ID VARCHAR(22) COMMENT 'Last update program identifier',
    LAST_UPDATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last update timestamp',
    DATA_END_STATUS VARCHAR(1) DEFAULT 'N' COMMENT 'Data end status',
    DATA_END_OBJECT_TYPE VARCHAR(1) COMMENT 'Data end object type',
    DATA_END_OBJECT_ID VARCHAR(22) COMMENT 'Data end object identifier',
    DATA_END_PROGRAM_ID VARCHAR(22) COMMENT 'Data end program identifier',
    DATA_END_TIMESTAMP TIMESTAMP NULL COMMENT 'Data end timestamp',
    LOG_ID VARCHAR(64) PRIMARY KEY COMMENT 'Execution log unique identifier',
    AGENT_ID VARCHAR(64) COMMENT 'Agent that executed the command',
    MODEL_PROCESS_ID VARCHAR(64) COMMENT 'Model process targeted by the command',
    COMMAND_TYPE VARCHAR(32) COMMENT 'Executed command type',
    EXECUTION_STATUS VARCHAR(32) COMMENT 'Execution result status',
    EXIT_CODE INT COMMENT 'Operating system exit code',
    STDOUT_PREVIEW TEXT COMMENT 'Truncated standard output preview',
    STDERR_PREVIEW TEXT COMMENT 'Truncated standard error preview',
    DURATION_SEC INT COMMENT 'Execution duration in seconds'
) COMMENT='Historical command execution results';

CREATE INDEX IF NOT EXISTS IDX_M26_EXECUTION_LOG_PROCESS_TIME
    ON TB_M26_EXECUTION_LOG (MODEL_PROCESS_ID, CREATED_TIMESTAMP);
