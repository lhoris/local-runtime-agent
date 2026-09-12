#!/usr/bin/env bash
# Removes the Local Runtime Agent systemd service. Run as root.
set -euo pipefail

UNIT_DST="/etc/systemd/system/local-runtime-agent.service"

if [ "$(id -u)" -ne 0 ]; then
    echo "Must run as root" >&2
    exit 1
fi

systemctl stop local-runtime-agent.service 2>/dev/null || true
systemctl disable local-runtime-agent.service 2>/dev/null || true
rm -f "$UNIT_DST"
systemctl daemon-reload

echo "Uninstalled local-runtime-agent service."
