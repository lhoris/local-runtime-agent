# Database Setup

Flyway is intentionally not used by this application. Database changes are applied by the DBA or deployment operator.

## Order

1. Review and apply `schema.sql` in MariaDB. This is the complete application DDL and does not require Flyway.
2. Run `verify-schema.sql` against the target database.
3. Edit and run `seed-local-agent.sql` when you want to preconfigure a process row.
4. Start the Agent. It automatically creates the `TB_M26_AGENT` row from the local hostname and IP.
5. Confirm that the process row uses the created `AGENT_ID`.
6. Run `smoke-test.sql` to verify Agent registration, process assignment, and heartbeat updates.
7. Run `runtime-verification.sql` to inspect the complete loop flow, process PID, heartbeat, command results, and execution logs.
8. Run `local-test/python-process/command-test.sql` to insert a `START`, `STOP`, or `RESTART` command for the local Python smoke test.

`TB_M26_MODEL_PROCESS` keeps the existing vision-monitor business columns and also contains the Agent execution/runtime columns. The Agent does not maintain a separate monitoring-target registration table.

## Important values

- `TB_M26_AGENT.IP_ADDRESS` identifies the local Agent. The row is created automatically on first startup.
- `AGENT_ID` is generated from the local hostname; it does not need to be configured in application properties.
- `TB_M26_MODEL_PROCESS.AGENT_ID` assigns a process to an Agent.
- `TB_M26_MODEL_PROCESS.MODEL_PROCESS_ID` is the shared vision-monitor primary key.
- `TB_M26_COMMAND.PROCESS_ID` is the command/API target value and contains the model process ID as text.
- `PROCESS_STATUS` is the shared lifecycle status column used by both applications.
- `PYTHON_PROJECT_PATH` points to a project directory containing `run.bat`, `stop.bat`, and `restart.bat`.

All shared tables use the 14-column audit contract: `CREATED_*`, `LAST_UPDATED_*`, and `DATA_END_*`.

For an existing vision-monitor database, run `migrate-model-process-for-agent.sql` and then `migrate-audit-columns.sql` after taking a backup. This is an ALTER-only migration: it preserves existing rows and business columns. Do not use the fresh `CREATE TABLE` definition to replace the existing tables, and do not run `simplify-model-process-columns.sql` unless the removed legacy columns have been verified to be unused by vision-monitor.
- `AUTO_RESTART` and `HEALTH_CHECK_ENABLED` should be stored as boolean-compatible values (`1`/`0`) for MariaDB.
