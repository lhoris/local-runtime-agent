#!/usr/bin/env bash
# Installs the Local Runtime Agent as a systemd service.
# Run as root. Assumes the distribution is unpacked to /opt/lra-agent.
set -euo pipefail

APP_HOME="${APP_HOME:-/opt/lra-agent}"
UNIT_SRC="$(cd "$(dirname "$0")" && pwd)/local-runtime-agent.service"
UNIT_DST="/etc/systemd/system/local-runtime-agent.service"
SERVICE_USER="${SERVICE_USER:-lra}"

if [ "$(id -u)" -ne 0 ]; then
    echo "Must run as root" >&2
    exit 1
fi

if ! id "$SERVICE_USER" >/dev/null 2>&1; then
    echo "Creating service user '$SERVICE_USER'"
    useradd --system --no-create-home --shell /usr/sbin/nologin "$SERVICE_USER"
fi

chmod +x "$APP_HOME/bin/start.sh"
chown -R "$SERVICE_USER:$SERVICE_USER" "$APP_HOME"

install -m 0644 "$UNIT_SRC" "$UNIT_DST"
systemctl daemon-reload
systemctl enable local-runtime-agent.service

echo "Installed. Start with: systemctl start local-runtime-agent"
