-- Destructive local reset script for MariaDB.
-- WARNING: This permanently deletes all data in the Agent tables.
-- Use migrate-model-process-for-agent.sql and a separate column rename
-- migration for an existing database where data must be preserved.

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS TB_M26_EXECUTION_LOG;
DROP TABLE IF EXISTS TB_M26_MODEL_PARAMETER;
DROP TABLE IF EXISTS TB_M26_COMMAND;
DROP TABLE IF EXISTS TB_M26_MODEL_PROCESS;
DROP TABLE IF EXISTS TB_M26_AGENT;

SET FOREIGN_KEY_CHECKS = 1;

-- Run this file from the MariaDB client after the DROP statements:
SOURCE docs/database/schema.sql;
