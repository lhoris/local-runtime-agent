-- Local Runtime Agent - initial schema (ARCHITECTURE.md §5)
-- Dialect: PostgreSQL. H2 (test) runs in PostgreSQL compatibility mode with a
-- JSONB domain aliased to JSON (see application-test.yml INIT clause).

CREATE TABLE agent_info (
    agent_id            VARCHAR(64) PRIMARY KEY,
    hostname            VARCHAR(256),
    os_type             VARCHAR(32),
    ip_address          VARCHAR(45),
    spring_boot_version VARCHAR(32),
    installed_at        TIMESTAMP,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP
);

CREATE TABLE process_config (
    process_id                VARCHAR(64) PRIMARY KEY,
    agent_id                  VARCHAR(64) NOT NULL,
    model_name                VARCHAR(256),
    model_type                VARCHAR(64),
    executable_path           VARCHAR(512),
    working_directory         VARCHAR(512),
    command_args              TEXT,
    env_vars                  TEXT,
    auto_restart              BOOLEAN DEFAULT TRUE,
    max_restart_attempts      INT DEFAULT 3,
    restart_delay_sec         INT DEFAULT 10,
    timeout_sec               INT DEFAULT 3600,
    memory_limit_mb           INT,
    cpu_limit_percent         INT,
    health_check_enabled      BOOLEAN DEFAULT TRUE,
    health_check_interval_sec INT DEFAULT 30,
    health_check_endpoint     VARCHAR(512),
    metadata                  JSONB,
    created_at                TIMESTAMP,
    updated_at                TIMESTAMP,
    CONSTRAINT fk_process_config_agent FOREIGN KEY (agent_id) REFERENCES agent_info (agent_id)
);

CREATE TABLE agent_status (
    status_id         VARCHAR(64) PRIMARY KEY,
    agent_id          VARCHAR(64) NOT NULL,
    process_id        VARCHAR(64),
    state             VARCHAR(32),
    pid               INT,
    cpu_percent       FLOAT,
    memory_mb         INT,
    last_health_check TIMESTAMP,
    health_status     VARCHAR(32),
    uptime_sec        BIGINT,
    crash_count       INT DEFAULT 0,
    last_crash_time   TIMESTAMP,
    error_message     TEXT,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    CONSTRAINT fk_agent_status_agent FOREIGN KEY (agent_id) REFERENCES agent_info (agent_id),
    CONSTRAINT fk_agent_status_process FOREIGN KEY (process_id) REFERENCES process_config (process_id)
);

CREATE INDEX idx_agent_process ON agent_status (agent_id, process_id);

CREATE TABLE commands (
    command_id     VARCHAR(64) PRIMARY KEY,
    agent_id       VARCHAR(64) NOT NULL,
    process_id     VARCHAR(64),
    command_type   VARCHAR(32),
    command_status VARCHAR(32),
    parameters     JSONB,
    created_by     VARCHAR(256),
    created_at     TIMESTAMP,
    processed_at   TIMESTAMP,
    failed_reason  TEXT,
    CONSTRAINT fk_commands_agent FOREIGN KEY (agent_id) REFERENCES agent_info (agent_id),
    CONSTRAINT fk_commands_process FOREIGN KEY (process_id) REFERENCES process_config (process_id)
);

CREATE INDEX idx_pending ON commands (command_status, agent_id);

CREATE TABLE model_parameters (
    param_id    VARCHAR(64) PRIMARY KEY,
    process_id  VARCHAR(64) NOT NULL,
    param_key   VARCHAR(256),
    param_value TEXT,
    param_type  VARCHAR(32),
    version     INT,
    is_active   BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    CONSTRAINT fk_model_parameters_process FOREIGN KEY (process_id) REFERENCES process_config (process_id),
    CONSTRAINT uk_param UNIQUE (process_id, param_key, version)
);

CREATE TABLE heartbeat_log (
    heartbeat_id   VARCHAR(64) PRIMARY KEY,
    agent_id       VARCHAR(64) NOT NULL,
    heartbeat_time TIMESTAMP,
    agent_status   JSONB,
    created_at     TIMESTAMP,
    CONSTRAINT fk_heartbeat_log_agent FOREIGN KEY (agent_id) REFERENCES agent_info (agent_id)
);

CREATE INDEX idx_agent_time ON heartbeat_log (agent_id, heartbeat_time);

CREATE TABLE execution_log (
    log_id           VARCHAR(64) PRIMARY KEY,
    agent_id         VARCHAR(64),
    process_id       VARCHAR(64),
    command_type     VARCHAR(32),
    execution_status VARCHAR(32),
    exit_code        INT,
    stdout_preview   TEXT,
    stderr_preview   TEXT,
    duration_sec     INT,
    created_at       TIMESTAMP,
    CONSTRAINT fk_execution_log_agent FOREIGN KEY (agent_id) REFERENCES agent_info (agent_id),
    CONSTRAINT fk_execution_log_process FOREIGN KEY (process_id) REFERENCES process_config (process_id)
);

CREATE INDEX idx_process_time ON execution_log (process_id, created_at);

-- Integrated status view for the central dashboard.
-- PostgreSQL requires every non-aggregated selected column in GROUP BY
-- (the design's MySQL loose GROUP BY is invalid here), so all are listed.
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
    COUNT(CASE WHEN c.command_status = 'PENDING' THEN 1 END) AS pending_commands
FROM agent_info ai
JOIN process_config pc ON ai.agent_id = pc.agent_id
LEFT JOIN agent_status ast ON pc.process_id = ast.process_id
LEFT JOIN commands c ON ai.agent_id = c.agent_id AND c.command_status = 'PENDING'
GROUP BY
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
    ast.last_health_check;
