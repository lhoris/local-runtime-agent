#!/usr/bin/env bash
# Starts the service and tails its status. Run as root.
set -euo pipefail

systemctl start local-runtime-agent.service
systemctl status --no-pager local-runtime-agent.service
