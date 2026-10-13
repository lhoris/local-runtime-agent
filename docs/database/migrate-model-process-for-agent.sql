-- Safe ALTER migration for an existing vision-monitor TB_M26_MODEL_PROCESS table.
--
-- This script preserves existing rows and existing business columns.
-- It only adds missing Agent columns, fills missing identifiers, and adds an
-- index. It does not DROP, DELETE, or replace any existing table.
--
-- Run docs/database/migrate-audit-columns.sql separately when the shared audit
-- columns are also required. Take a database backup before any DDL change.

-- 0. Preflight: understand the current table before changing it.
SHOW CREATE TABLE TB_M26_MODEL_PROCESS;
SELECT COUNT(*) AS EXISTING_PROCESS_COUNT
FROM TB_M26_MODEL_PROCESS;

-- 1. Add the shared vision-monitor identifier and Agent columns.
ALTER TABLE TB_M26_MODEL_PROCESS
    ADD COLUMN IF NOT EXISTS MODEL_PROCESS_ID BIGINT UNSIGNED NULL COMMENT 'Shared vision-monitor model process identifier' AFTER DATA_END_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS AGENT_ID VARCHAR(64) NULL COMMENT 'Agent responsible for this process' AFTER MODEL_PROCESS_ID,
    ADD COLUMN IF NOT EXISTS PYTHON_PROJECT_PATH VARCHAR(512) NULL COMMENT 'Python project directory containing start.bat, stop.bat, and restart.bat',
    ADD COLUMN IF NOT EXISTS AUTO_RESTART BOOLEAN DEFAULT TRUE COMMENT 'Whether crashed processes are restarted automatically',
    ADD COLUMN IF NOT EXISTS MAX_RESTART_ATTEMPTS INT DEFAULT 3 COMMENT 'Maximum automatic restart attempts',
    ADD COLUMN IF NOT EXISTS RESTART_DELAY_SEC INT DEFAULT 10 COMMENT 'Delay before automatic restart in seconds',
    ADD COLUMN IF NOT EXISTS TIMEOUT_SEC INT DEFAULT 3600 COMMENT 'Process operation timeout in seconds',
    ADD COLUMN IF NOT EXISTS HEALTH_CHECK_ENABLED BOOLEAN DEFAULT TRUE COMMENT 'Whether health checks are enabled',
    ADD COLUMN IF NOT EXISTS HEALTH_CHECK_INTERVAL_SEC INT DEFAULT 30 COMMENT 'Health check interval in seconds',
    ADD COLUMN IF NOT EXISTS HEALTH_CHECK_ENDPOINT VARCHAR(512) NULL COMMENT 'Optional local health endpoint',
    ADD COLUMN IF NOT EXISTS PROCESS_PID INT NULL COMMENT 'Latest operating system process ID',
    ADD COLUMN IF NOT EXISTS LAST_HEALTH_CHECK TIMESTAMP NULL COMMENT 'Timestamp of the latest health check',
    ADD COLUMN IF NOT EXISTS LAST_HEARTBEAT TIMESTAMP NULL COMMENT 'Timestamp of the latest Agent status update',
    ADD COLUMN IF NOT EXISTS HEALTH_STATUS VARCHAR(32) NULL COMMENT 'Latest health check result',
    ADD COLUMN IF NOT EXISTS UPTIME_SEC BIGINT NULL COMMENT 'Latest observed process uptime in seconds',
    ADD COLUMN IF NOT EXISTS ERROR_MESSAGE LONGTEXT NULL COMMENT 'Latest process or health error message';

-- 2. Populate MODEL_PROCESS_ID for existing rows. Existing rows are retained;
-- only the new identifier column is filled.
SET @next_model_process_id = (
    SELECT COALESCE(MAX(MODEL_PROCESS_ID), 0)
    FROM TB_M26_MODEL_PROCESS
);

UPDATE TB_M26_MODEL_PROCESS
SET MODEL_PROCESS_ID = (@next_model_process_id := @next_model_process_id + 1)
WHERE MODEL_PROCESS_ID IS NULL;

SELECT MODEL_PROCESS_ID, COUNT(*) AS ROW_COUNT
FROM TB_M26_MODEL_PROCESS
GROUP BY MODEL_PROCESS_ID
HAVING COUNT(*) > 1;

-- 3. Resolve Agent ownership from the existing SERVER_IP column.
UPDATE TB_M26_MODEL_PROCESS p
JOIN TB_M26_AGENT a ON a.IP_ADDRESS = p.SERVER_IP
SET p.AGENT_ID = a.AGENT_ID
WHERE p.AGENT_ID IS NULL OR p.AGENT_ID = '';

-- 4. Inspect rows that still need an Agent assignment before enforcing NOT NULL.
SELECT MODEL_PROCESS_ID, PROCESS_AREA_CODE, MODEL_NAME, SERVER_IP
FROM TB_M26_MODEL_PROCESS
WHERE AGENT_ID IS NULL OR AGENT_ID = '';

SELECT MODEL_PROCESS_ID
FROM TB_M26_MODEL_PROCESS
WHERE MODEL_PROCESS_ID IS NULL;

-- 5. After the previous query returns no rows, enforce the Agent contract.
-- Execute this block only after every existing row has an AGENT_ID.
ALTER TABLE TB_M26_MODEL_PROCESS
    MODIFY COLUMN MODEL_PROCESS_ID BIGINT UNSIGNED NOT NULL,
    MODIFY COLUMN AGENT_ID VARCHAR(64) NOT NULL;

-- Make the shared identifier usable by JPA and future inserts.
CREATE UNIQUE INDEX IF NOT EXISTS UK_M26_MODEL_PROCESS_ID
    ON TB_M26_MODEL_PROCESS (MODEL_PROCESS_ID);

ALTER TABLE TB_M26_MODEL_PROCESS
    MODIFY COLUMN MODEL_PROCESS_ID BIGINT UNSIGNED NOT NULL AUTO_INCREMENT;

CREATE INDEX IF NOT EXISTS IDX_M26_MODEL_PROCESS_AGENT
    ON TB_M26_MODEL_PROCESS (AGENT_ID, MODEL_PROCESS_ID);
