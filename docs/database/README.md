# Database Setup

Flyway is intentionally not used by this application. Database changes are applied by the DBA or deployment operator.

## Order

1. Review and apply `schema.sql` in MariaDB.
2. Run `verify-schema.sql` against the target database.
3. Edit and run `seed-local-agent.sql` when you want to preconfigure a process row.
4. Start the Agent. It automatically creates the `TB_M26_AGENT` row from the local hostname and IP.
5. Confirm that the process row uses the created `AGENT_ID`.

`TB_M26_MODEL_PROCESS` contains both the process definition and the latest runtime status. The Agent does not maintain a separate monitoring-target registration table.

## Important values

- `TB_M26_AGENT.IP_ADDRESS` identifies the local Agent. The row is created automatically on first startup.
- `AGENT_ID` is generated from the local hostname; it does not need to be configured in application properties.
- `TB_M26_MODEL_PROCESS.AGENT_ID` assigns a process to an Agent.
- `COMMAND_ARGS` and `ENV_VARS` are JSON values.
- `AUTO_RESTART` and `HEALTH_CHECK_ENABLED` should be stored as boolean-compatible values (`1`/`0`) for MariaDB.
