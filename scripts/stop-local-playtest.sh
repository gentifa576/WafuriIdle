#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
CLIENT_PID_FILE="$FRONTEND_DIR/.playtest-client.pid"

stop_tracked_client_if_running() {
  if [[ ! -f "$CLIENT_PID_FILE" ]]; then
    echo "No tracked frontend dev server found."
    return 0
  fi

  local pid
  pid="$(cat "$CLIENT_PID_FILE")"
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    echo "Stopping frontend dev server (PID $pid)..."
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  else
    echo "Tracked frontend dev server is not running."
  fi

  rm -f "$CLIENT_PID_FILE"
}

stop_tracked_client_if_running

echo "Stopping backend..."
(cd "$BACKEND_DIR" && ./gradlew stopServer >/dev/null)

echo "Local playtest services stopped."
