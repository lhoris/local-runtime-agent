# Database Setup

Flyway is intentionally not used by this application. Database changes are applied by the DBA or deployment operator.

## Order

1. Review and apply `schema.sql` in MariaDB.
2. Run `verify-schema.sql` against the target database.
3. Remove or archive the legacy `TB_M26_PROCESS_CONFIG` table after its data has been migrated into `TB_M26_MODEL_PROCESS`.
4. Edit and run `seed-local-agent.sql` for each Agent machine.
5. Start the Agent and confirm that its local IP matches the registered `IP_ADDRESS`.

`TB_M26_MODEL_PROCESS` contains both the process definition and the latest runtime status. The Agent does not maintain a separate monitoring-target registration table.

## Important values

- `TB_M26_AGENT.IP_ADDRESS` identifies the local Agent. The `AGENT_ID` value is resolved from that row.
- `TB_M26_MODEL_PROCESS.AGENT_ID` assigns a process to an Agent.
- `COMMAND_ARGS` and `ENV_VARS` are JSON values.
- `AUTO_RESTART` and `HEALTH_CHECK_ENABLED` should be stored as boolean-compatible values (`1`/`0`) for MariaDB.
