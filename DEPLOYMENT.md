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

## Windows Service (WinSW)

Windows doesn't have a built-in equivalent to systemd, so we use [WinSW](https://github.com/winsw/winsw)
(Windows Service Wrapper) to host the Java process. The setup is two steps:
**download WinSW, then run the installer.**

### Layout

The installer lays the agent out as follows:

| Path                                | Purpose                             |
| ----------------------------------- | ----------------------------------- |
| `C:\LRA\lib\local-runtime-agent-<version>.jar` | The application jar (stable name). |
| `C:\LRA\logs\`                      | File logs (`local-runtime-agent.log`). |
| `C:\LRA\config\application-prod.yml` | Configuration (DB creds in env vars). |
| `C:\LRA\bin\local-runtime-agent.xml` | WinSW service configuration.       |
| `C:\LRA\bin\local-runtime-agent.exe` | WinSW wrapper (generated).         |
| `C:\LRA\scripts\windows\`           | Install/uninstall/start scripts.   |

### Prerequisites

1. **Java 21** installed and on `PATH`.
   ```powershell
   java -version  # should print version 21.x
   ```

2. **PostgreSQL** reachable with working credentials.

3. **WinSW.exe** — download from [releases](https://github.com/winsw/winsw/releases).
   Look for `WinSW-x64.exe` (x64 systems). Rename it to `WinSW.exe`.

### Quick start (Windows VM)

Assume you have:
- `local-runtime-agent-0.0.1-SNAPSHOT.zip` (from `mvn package`)
- `WinSW.exe` (downloaded)

**Step 1: Unpack and place WinSW**

```powershell
# Extract the zip to a deployment location, e.g., C:\LRA
Expand-Archive .\local-runtime-agent-0.0.1-SNAPSHOT.zip -DestinationPath C:\LRA
cd C:\LRA

# Copy WinSW.exe to scripts\windows\
Copy-Item .\WinSW.exe .\scripts\windows\
```

**Step 2: Set environment variables**

The service reads environment variables from Windows; set the DB credentials:

```powershell
[Environment]::SetEnvironmentVariable("DB_HOST", "your-postgres-host", "Machine")
[Environment]::SetEnvironmentVariable("DB_PORT", "5432", "Machine")
[Environment]::SetEnvironmentVariable("DB_NAME", "lra_db", "Machine")
[Environment]::SetEnvironmentVariable("DB_USER", "postgres", "Machine")
[Environment]::SetEnvironmentVariable("DB_PASSWORD", "your-password", "Machine")
[Environment]::SetEnvironmentVariable("SERVER_PORT", "8080", "Machine")
[Environment]::SetEnvironmentVariable("AGENT_ID", "agent-vm-01", "Machine")
[Environment]::SetEnvironmentVariable("LOG_FILE", "C:\LRA\logs\local-runtime-agent.log", "Machine")
```

**Step 3: Install the service (elevated PowerShell)**

```powershell
# Run elevated: right-click PowerShell → "Run as Administrator"
cd C:\LRA
.\scripts\windows\install-service.ps1
```

This will:
1. Validate WinSW.exe is present.
2. Find the jar in `lib\`.
3. Generate `bin\local-runtime-agent.xml` (service config).
4. Copy WinSW.exe to `bin\local-runtime-agent.exe`.
5. Register the service with Windows SCM.

**Step 4: Start the service**

```powershell
# Still elevated
.\scripts\windows\start-service.ps1
```

Or use Windows Service Manager:
```powershell
Get-Service "local-runtime-agent" | Start-Service
```

### Verify

```powershell
# Check service status
Get-Service "local-runtime-agent"

# View live logs
Get-Content C:\LRA\logs\local-runtime-agent.log -Tail 20 -Wait

# Or check Windows Event Viewer for java.exe errors
```

A healthy start logs the Spring banner and `Started ... in N seconds`, followed
by agent loop cycles.

### Common operations

```powershell
# Restart the service (e.g., after config changes)
Restart-Service -Name "local-runtime-agent"

# Stop the service
Stop-Service -Name "local-runtime-agent"

# Remove the service
.\scripts\windows\uninstall-service.ps1
```

### Upgrade

Rebuild, uninstall, update, then reinstall:

```powershell
# On your dev machine
./mvnw clean package
# → produces target\local-runtime-agent-<version>.zip

# On the Windows VM
.\scripts\windows\uninstall-service.ps1
Expand-Archive .\local-runtime-agent-<new-version>.zip -DestinationPath C:\LRA -Force
Copy-Item .\WinSW.exe .\C:\LRA\scripts\windows\
.\C:\LRA\scripts\windows\install-service.ps1
.\C:\LRA\scripts\windows\start-service.ps1
```

### Troubleshooting

| Symptom | Likely cause / fix |
| ------- | ------------------ |
| `WinSW.exe not found` | Download it from [releases](https://github.com/winsw/winsw/releases) and place in `scripts\windows\`, or set `$env:WINSW_EXE`. |
| `java` not found | Add Java 21 to `PATH` or reinstall Java. Verify with `java -version`. |
| Service fails to start; Event Viewer shows datasource error | Check env vars: `[Environment]::GetEnvironmentVariables("Machine")` should list `DB_HOST`, `DB_NAME`, etc. Restart PowerShell after setting them. |
| `Port 8080 already in use` | Change `SERVER_PORT` env var to a different port (e.g., 9090). Restart the service. |
| Service exits after 5 seconds | Check `C:\LRA\logs\local-runtime-agent.log` for errors. Common causes: wrong DB creds, DB not reachable. |

### Run manually (no service)

For testing or troubleshooting without installing a service:

```powershell
cd C:\LRA
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:DB_HOST = "your-postgres-host"
$env:DB_NAME = "lra_db"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "your-password"
.\bin\start.bat
```

This runs the agent in the foreground; Ctrl+C to stop.

---

## Troubleshooting (all platforms)

| Symptom | Likely cause / fix |
| ------- | ------------------ |
| Port `8080` already in use | Another process holds the port. Change `SERVER_PORT` (env) or `server.port` in `application.yml`. |
| DB connection refused / auth failed | Verify DB creds in `/etc/lra-agent/lra-agent.env` (Linux) or the environment. Confirm PostgreSQL is reachable and `DB_SSLMODE` matches the server. |
| Managed process never starts | Check the row in `process_config`: `executable_path` must exist and be runnable, and `command_args` must be a valid JSON array. |
| App starts under `dev` unexpectedly | `SPRING_PROFILES_ACTIVE` not set to `prod`; the base profile falls back to dev-friendly defaults. |
