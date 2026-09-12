#!/usr/bin/env bash
# Launches the Local Runtime Agent from a distribution layout (bin/, lib/, config/).
set -euo pipefail

APP_HOME="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$(ls "$APP_HOME"/lib/*.jar 2>/dev/null | head -n1 || true)"

if [ -z "$JAR" ]; then
    echo "No application jar found in $APP_HOME/lib" >&2
    exit 1
fi

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"

exec java ${JAVA_OPTS:-} -jar "$JAR" \
    --spring.config.additional-location="file:$APP_HOME/config/"
