# Deployment Guide

This document covers deploying the **Local Runtime Agent** as a long-running
service. The agent is a single Spring Boot fat jar; deployment wraps it in the
platform's service manager so it starts on boot and restarts on failure.

## Prerequisites

- **Java 21** (JRE or JDK) on the target host.
- A reachable **PostgreSQL** instance and its credentials.
- A built jar: `./mvnw clean package` produces
  `target/local-runtime-agent-<version>.jar`.

Runtime configuration is supplied entirely through environment variables — see
[`.env.example`](.env.example) for the full list. In production the `prod`
profile has **no defaults** for `DB_HOST`, `DB_NAME`, `DB_USER`, or
`DB_PASSWORD`; they must be provided.

---

## Linux (systemd)

### Layout

The installer lays the agent out as follows:

| Path                                   | Purpose                                  |
| -------------------------------------- | ---------------------------------------- |
| `/opt/lra-agent/lib/local-runtime-agent.jar` | The application jar (stable name). |
| `/opt/lra-agent/logs/`                 | File logs (`application.log`).           |
| `/etc/lra-agent/lra-agent.env`         | Environment file (DB creds, profile).    |
| `/etc/systemd/system/local-runtime-agent.service` | The systemd unit.             |

The service runs as a dedicated, non-login system user `lra-agent`.

### Install

```bash
# From the repository root, after ./mvnw clean package
sudo scripts/linux/install-service.sh
```

The installer will:

1. Create the `lra-agent` system user and group (if missing).
2. Create `/opt/lra-agent/{lib,logs}` and `/etc/lra-agent`.
3. Copy the built jar to `/opt/lra-agent/lib/local-runtime-agent.jar`.
4. Seed `/etc/lra-agent/lra-agent.env` from `.env.example` (only if absent).
5. Install the unit, `daemon-reload`, `enable`, and `start` the service.

> **Important:** on a fresh install, edit `/etc/lra-agent/lra-agent.env` and set
> `SPRING_PROFILES_ACTIVE=prod` plus real database credentials, then restart:
> `sudo systemctl restart local-runtime-agent`.

### Verify

```bash
systemctl status local-runtime-agent      # or: systemctl status lra-agent
journalctl -u local-runtime-agent -f      # follow live logs
```

A healthy start logs the Spring banner and `Started ... in N seconds`, followed
by agent loop cycles.

### Common operations

```bash
sudo systemctl restart local-runtime-agent   # apply config/jar changes
sudo systemctl stop local-runtime-agent
sudo scripts/linux/start-service.sh           # start + print status
```

The short alias `lra-agent` works for all of the above once the service is
enabled (the unit declares `Alias=lra-agent.service`).

### Upgrade

Rebuild, re-run the installer (it overwrites the jar in place and leaves your
env file untouched), and restart:

```bash
./mvnw clean package
sudo scripts/linux/install-service.sh
sudo systemctl restart local-runtime-agent
```

### Uninstall

```bash
sudo scripts/linux/uninstall-service.sh            # keeps config + logs
sudo scripts/linux/uninstall-service.sh --purge    # also removes /opt, /etc, user
```

### Service behavior notes

- **Restart policy:** `Restart=always`, `RestartSec=5` — systemd restarts the
  JVM if it exits for any reason. This is distinct from the agent's own
  auto-restart of managed Python processes.
- **Graceful stop:** `TimeoutStopSec=30` gives the agent time to shut down
  managed child processes before systemd force-kills the JVM.
- **Hardening:** the unit runs with `NoNewPrivileges`, `PrivateTmp`,
  `ProtectSystem=full`, and `ProtectHome`, and may only write under
  `/opt/lra-agent/logs`.
- **Ordering:** starts `After=network-online.target postgresql.service`. If
  PostgreSQL runs on a different host, the `postgresql.service` dependency is
  simply a no-op.

### Troubleshooting

| Symptom | Likely cause / fix |
| ------- | ------------------ |
| `status=203/EXEC` | `java` not at `/usr/bin/java`. Symlink it or edit `ExecStart`. |
| Fails immediately, log shows datasource error | `lra-agent.env` missing DB creds or `SPRING_PROFILES_ACTIVE` not `prod`. |
| `Flyway ... validate failed` | Schema drift; check the DB matches the migrations in `db/migration`. |
| No logs in `/opt/lra-agent/logs` | Check journal instead (`journalctl -u local-runtime-agent`); file logging depends on `LOG_FILE`. |

---

## Windows Service (planned)

Windows deployment is designed but the install scripts are **not yet provided** —
run the jar manually or wrap it as a service until the tooling lands.

### Run manually

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
# set DB_HOST / DB_NAME / DB_USER / DB_PASSWORD ...
java -jar target\local-runtime-agent-0.0.1-SNAPSHOT.jar
```

### Planned approach

- **WinSW** (Windows Service Wrapper) driven by a `service.xml` that points at the
  jar, with a PowerShell wrapper (`scripts/windows/install-service.ps1`) to
  register/start the service. See `docs/ARCHITECTURE.md` §9 for the reference
  `service.xml`.
- Environment variables will be supplied via the service definition rather than
  an `EnvironmentFile`.

Until then, live verification of the Windows path must be done on a Windows host.

---

## Troubleshooting (all platforms)

| Symptom | Likely cause / fix |
| ------- | ------------------ |
| Port `8080` already in use | Another process holds the port. Change `SERVER_PORT` (env) or `server.port` in `application.yml`. |
| DB connection refused / auth failed | Verify DB creds in `/etc/lra-agent/lra-agent.env` (Linux) or the environment. Confirm PostgreSQL is reachable and `DB_SSLMODE` matches the server. |
| Managed process never starts | Check the row in `process_config`: `executable_path` must exist and be runnable, and `command_args` must be a valid JSON array. |
| App starts under `dev` unexpectedly | `SPRING_PROFILES_ACTIVE` not set to `prod`; the base profile falls back to dev-friendly defaults. |
