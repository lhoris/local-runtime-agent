# Local Runtime Agent — Deployment Guide

The build produces `target/lra-agent-<version>-dist.zip` via `mvn package`. Unpack it
anywhere; it contains:

```
lra-agent-<version>/
├─ bin/            start.sh (Linux/macOS), start.bat (Windows)
├─ lib/            lra-agent-<version>.jar  (executable Spring Boot jar)
├─ config/         application.yml, application-prod.yml
├─ scripts/
│  ├─ windows/     install/uninstall/start-service.ps1 (+ WinSW.exe you supply)
│  └─ linux/       install/uninstall/start-service.sh, local-runtime-agent.service
└─ DEPLOY.md
```

## Configuration

The agent runs with the `prod` profile by default. `config/application-prod.yml`
reads all connection settings from the environment:

| Variable | Purpose |
|----------|---------|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | PostgreSQL connection |
| `DB_USER`, `DB_PASSWORD` | PostgreSQL credentials |
| `SERVER_PORT` | HTTP port (default 8080) |
| `AGENT_ID` | Agent identifier |
| `LOG_FILE` | Log file path |

## Run directly

```bash
# Linux/macOS
DB_HOST=... DB_USER=... DB_PASSWORD=... DB_NAME=... ./bin/start.sh
```
```bat
rem Windows
set DB_HOST=... & set DB_USER=... & set DB_PASSWORD=... & set DB_NAME=...
bin\start.bat
```

## Install as a service

### Linux (systemd)

Unpack to `/opt/lra-agent`, put environment variables in `/etc/lra-agent/agent.env`,
then (as root):

```bash
/opt/lra-agent/scripts/linux/install-service.sh
systemctl start local-runtime-agent
```

Restart policy: `Restart=always`, `RestartSec=5`.

### Windows (WinSW)

A plain Java process cannot answer the Windows Service Control Manager, so the
service is hosted by [WinSW](https://github.com/winsw/winsw/releases). Download
`WinSW.exe`, place it in `scripts\windows\` (or set `$env:WINSW_EXE`), then from an
elevated PowerShell:

```powershell
scripts\windows\install-service.ps1
scripts\windows\start-service.ps1
```

The installer generates `bin\local-runtime-agent.xml` with the resolved jar path
and a restart-on-failure policy (5s delay).

## Verification status

- The distribution zip and its layout are produced and verified by `mvn package`.
- Live OS-service registration (systemd `systemctl`, Windows SCM) requires
  root/administrator on a real host and is **not** exercised by the build; verify
  manually on the target machine.
