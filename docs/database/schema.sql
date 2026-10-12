-- Local Runtime Agent baseline schema for MariaDB 11.8.
-- This is a manual deployment script. It intentionally does not drop tables.
-- Review existing tables before applying it to a non-empty database.

CREATE TABLE IF NOT EXISTS TB_M26_AGENT (
    AGENT_ID VARCHAR(64) PRIMARY KEY COMMENT 'Agent unique identifier, generated from local hostname',
    HOSTNAME VARCHAR(256) COMMENT 'Machine hostname',
    OS_TYPE VARCHAR(32) COMMENT 'Operating system name',
    IP_ADDRESS VARCHAR(45) COMMENT 'Local IPv4 address used to identify this Agent',
    SPRING_BOOT_VERSION VARCHAR(32) COMMENT 'Spring Boot version reported by the Agent',
    INSTALLED_AT TIMESTAMP NULL COMMENT 'First registration timestamp',
    CREATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Row creation timestamp',
    LAST_UPDATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last row update timestamp'
) COMMENT='Local Runtime Agent registration and network identity';

CREATE TABLE IF NOT EXISTS TB_M26_MODEL_PROCESS (
    PROCESS_ID VARCHAR(64) PRIMARY KEY COMMENT 'Managed process unique identifier',
    AGENT_ID VARCHAR(64) NOT NULL COMMENT 'Agent responsible for this process',
    MODEL_NAME VARCHAR(256) COMMENT 'Human-readable model or process name',
    MODEL_TYPE VARCHAR(64) COMMENT 'Process category such as INFERENCE or RUNTIME',
    EXECUTABLE_PATH VARCHAR(512) COMMENT 'Executable or interpreter path',
    WORKING_DIRECTORY VARCHAR(512) COMMENT 'Working directory for the process',
    COMMAND_ARGS LONGTEXT COMMENT 'Command arguments as a JSON array',
    ENV_VARS LONGTEXT COMMENT 'Environment variables as a JSON object',
    AUTO_RESTART BOOLEAN DEFAULT TRUE COMMENT 'Whether crashed processes are restarted automatically',
    MAX_RESTART_ATTEMPTS INT DEFAULT 3 COMMENT 'Maximum automatic restart attempts',
    RESTART_DELAY_SEC INT DEFAULT 10 COMMENT 'Delay before automatic restart in seconds',
    TIMEOUT_SEC INT DEFAULT 3600 COMMENT 'Process operation timeout in seconds',
    MEMORY_LIMIT_MB INT COMMENT 'Configured memory limit in megabytes',
    CPU_LIMIT_PERCENT INT COMMENT 'Configured CPU limit percentage',
    HEALTH_CHECK_ENABLED BOOLEAN DEFAULT TRUE COMMENT 'Whether health checks are enabled',
    HEALTH_CHECK_INTERVAL_SEC INT DEFAULT 30 COMMENT 'Health check interval in seconds',
    HEALTH_CHECK_ENDPOINT VARCHAR(512) COMMENT 'Optional local health endpoint',
    PROCESS_STATE VARCHAR(32) COMMENT 'Latest lifecycle state of the process',
    PROCESS_PID INT COMMENT 'Latest operating system process ID',
    CPU_PERCENT DECIMAL(5,2) COMMENT 'Latest observed CPU usage percentage',
    MEMORY_MB INT COMMENT 'Latest observed memory usage in megabytes',
    LAST_HEALTH_CHECK TIMESTAMP NULL COMMENT 'Timestamp of the latest health check',
    LAST_HEARTBEAT TIMESTAMP NULL COMMENT 'Timestamp of the latest Agent status update',
    HEALTH_STATUS VARCHAR(32) COMMENT 'Latest health check result',
    UPTIME_SEC BIGINT COMMENT 'Latest observed process uptime in seconds',
    CRASH_COUNT INT DEFAULT 0 COMMENT 'Number of detected crashes',
    LAST_CRASH_TIME TIMESTAMP NULL COMMENT 'Timestamp of the latest detected crash',
    ERROR_MESSAGE LONGTEXT COMMENT 'Latest process or health error message',
    CREATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Row creation timestamp',
    LAST_UPDATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last row update timestamp'
) COMMENT='Process definition and latest runtime status for each managed model process';

CREATE INDEX IF NOT EXISTS IDX_M26_MODEL_PROCESS_AGENT
    ON TB_M26_MODEL_PROCESS (AGENT_ID, PROCESS_ID);

CREATE TABLE IF NOT EXISTS TB_M26_COMMAND (
    COMMAND_ID VARCHAR(64) PRIMARY KEY COMMENT 'Command unique identifier',
    AGENT_ID VARCHAR(64) NOT NULL COMMENT 'Target Agent identifier',
    PROCESS_ID VARCHAR(64) COMMENT 'Target process identifier',
    COMMAND_TYPE VARCHAR(32) COMMENT 'Command type such as START, STOP, or RESTART',
    COMMAND_STATUS VARCHAR(32) COMMENT 'Command lifecycle status',
    COMMAND_PARAMETERS LONGTEXT COMMENT 'Command parameters as a JSON object',
    PROCESSED_AT TIMESTAMP NULL COMMENT 'Command completion timestamp',
    FAILED_REASON LONGTEXT COMMENT 'Failure reason when command processing fails',
    CREATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Command creation timestamp',
    LAST_UPDATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last command update timestamp'
) COMMENT='Command queue consumed by local Agents';

CREATE INDEX IF NOT EXISTS IDX_M26_COMMAND_PENDING
    ON TB_M26_COMMAND (AGENT_ID, COMMAND_STATUS);

CREATE TABLE IF NOT EXISTS TB_M26_MODEL_PARAMETER (
    PARAMETER_ID VARCHAR(64) PRIMARY KEY COMMENT 'Parameter unique identifier',
    PROCESS_ID VARCHAR(64) NOT NULL COMMENT 'Target process identifier',
    PARAM_KEY VARCHAR(256) COMMENT 'Parameter name',
    PARAM_VALUE LONGTEXT COMMENT 'Parameter value serialized as text or JSON',
    PARAM_TYPE VARCHAR(32) COMMENT 'Parameter type such as STRING, INTEGER, or JSON',
    PARAM_VERSION INT DEFAULT 1 COMMENT 'Parameter revision number',
    IS_ACTIVE BOOLEAN DEFAULT TRUE COMMENT 'Whether this parameter is active',
    CREATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Parameter creation timestamp',
    LAST_UPDATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last parameter update timestamp'
) COMMENT='Versioned runtime parameters for managed processes';

CREATE TABLE IF NOT EXISTS TB_M26_EXECUTION_LOG (
    LOG_ID VARCHAR(64) PRIMARY KEY COMMENT 'Execution log unique identifier',
    AGENT_ID VARCHAR(64) COMMENT 'Agent that executed the command',
    PROCESS_ID VARCHAR(64) COMMENT 'Process targeted by the command',
    COMMAND_TYPE VARCHAR(32) COMMENT 'Executed command type',
    EXECUTION_STATUS VARCHAR(32) COMMENT 'Execution result status',
    EXIT_CODE INT COMMENT 'Operating system exit code',
    STDOUT_PREVIEW TEXT COMMENT 'Truncated standard output preview',
    STDERR_PREVIEW TEXT COMMENT 'Truncated standard error preview',
    DURATION_SEC INT COMMENT 'Execution duration in seconds',
    CREATED_TIMESTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'Execution log creation timestamp'
) COMMENT='Historical command execution results';

CREATE INDEX IF NOT EXISTS IDX_M26_EXECUTION_LOG_PROCESS_TIME
    ON TB_M26_EXECUTION_LOG (PROCESS_ID, CREATED_TIMESTAMP);
