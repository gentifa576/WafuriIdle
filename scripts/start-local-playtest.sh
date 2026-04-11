#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
BACKEND_GRADLE_USER_HOME="$BACKEND_DIR/.gradle-user-home"

BACKEND_HEALTH_URL="http://127.0.0.1:8080/health"
CLIENT_URL="http://127.0.0.1:5173"
CLIENT_PID_FILE="$FRONTEND_DIR/.playtest-client.pid"
CLIENT_LOG_FILE="$FRONTEND_DIR/.playtest-client.log"

gradle_wrapper_command() {
  case "$(uname -s)" in
    CYGWIN*|MINGW*|MSYS*)
      echo "./gradlew.bat"
      ;;
    *)
      echo "./gradlew"
      ;;
  esac
}

wait_for_url() {
  local url="$1"
  local name="$2"
  local attempts="${3:-60}"

  for ((i = 1; i <= attempts; i += 1)); do
    if curl --silent --fail --output /dev/null "$url"; then
      return 0
    fi
    sleep 1
  done

  echo "$name did not become ready in time: $url" >&2
  return 1
}

stop_tracked_client_if_running() {
  if [[ ! -f "$CLIENT_PID_FILE" ]]; then
    return 0
  fi

  local pid
  pid="$(cat "$CLIENT_PID_FILE")"
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    echo "Stopping existing frontend dev server (PID $pid)..."
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  fi
  rm -f "$CLIENT_PID_FILE"
}

start_client() {
  if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
    echo "Frontend dependencies are missing." >&2
    echo "Run 'cd frontend && npm install' once, then rerun this script." >&2
    exit 1
  fi

  stop_tracked_client_if_running

  echo "Starting frontend dev server..."
  (
    cd "$FRONTEND_DIR"
    nohup npm run dev -- --host 127.0.0.1 --strictPort >"$CLIENT_LOG_FILE" 2>&1 &
    echo $! >"$CLIENT_PID_FILE"
  )

  wait_for_url "$CLIENT_URL" "Frontend dev server" 60 || {
    echo "Frontend log:" >&2
    if [[ -f "$CLIENT_LOG_FILE" ]]; then
      tail -n 50 "$CLIENT_LOG_FILE" >&2
    fi
    exit 1
  }
}

echo "Stopping backend if it is already running..."
(cd "$BACKEND_DIR" && GRADLE_USER_HOME="$BACKEND_GRADLE_USER_HOME" "$(gradle_wrapper_command)" stopServer >/dev/null)

echo "Starting backend..."
(cd "$BACKEND_DIR" && GRADLE_USER_HOME="$BACKEND_GRADLE_USER_HOME" "$(gradle_wrapper_command)" runServer >/dev/null)

echo "Waiting for backend health check..."
wait_for_url "$BACKEND_HEALTH_URL" "Backend" 60

start_client

echo
echo "Local playtest is ready."
echo "Open: $CLIENT_URL"
echo "Frontend log: $CLIENT_LOG_FILE"
echo "Backend log: $BACKEND_DIR/build/server/server.log"
