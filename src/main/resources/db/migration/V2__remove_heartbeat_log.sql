-- Consolidate heartbeat functionality: add last_heartbeat to agent_status, drop heartbeat_log
ALTER TABLE agent_status ADD COLUMN IF NOT EXISTS last_heartbeat TIMESTAMP;

DROP TABLE IF EXISTS heartbeat_log;

DROP INDEX IF EXISTS idx_agent_time;
