#!/usr/bin/env bash
# =============================================================================
# Shared configuration + helpers for the RideLink run/monitor scripts.
# Sourced by start-all.sh, stop-all.sh and health-check.sh - not run directly.
#
# To add a service: append one entry to each of the four arrays below, keeping
# the same index across all of them. Order matters: start-all.sh launches
# services top to bottom and stop-all.sh stops them bottom to top.
# =============================================================================
set -u

# Repo root = parent of this scripts/ folder, regardless of where we're called from.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT_DIR/logs"

# Parallel arrays (no associative arrays, so this still works on macOS bash 3.2).
SERVICE_NAMES=(account-service driver-service ride-service fare-payment-service frontend)
SERVICE_DIRS=(
  "account-service/account-service"          # Maven project lives one level deeper
  "driver-service/driver-service"
  "ride-service/ride-service"
  "fare-payment-service/fare-payment-service"
  "frontend"
)
SERVICE_PORTS=(8081 8082 8083 8084 3000)
SERVICE_TYPES=(maven maven maven maven npm)   # how to launch: maven | npm

# Tunables (override via environment, e.g. STARTUP_TIMEOUT=300 ./scripts/start-all.sh)
STARTUP_TIMEOUT="${STARTUP_TIMEOUT:-120}"   # seconds to wait for a port to respond
POLL_INTERVAL="${POLL_INTERVAL:-2}"         # seconds between startup polls

# --- OS detection -----------------------------------------------------------
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) OS_FAMILY=windows ;;
  Darwin*)              OS_FAMILY=mac ;;
  *)                    OS_FAMILY=linux ;;
esac

# --- Colors (disabled when not writing to a terminal, or NO_COLOR is set) ---
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  C_RED=$'\033[31m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'
  C_BLUE=$'\033[34m'; C_BOLD=$'\033[1m'; C_DIM=$'\033[2m'; C_RESET=$'\033[0m'
else
  C_RED=; C_GREEN=; C_YELLOW=; C_BLUE=; C_BOLD=; C_DIM=; C_RESET=
fi

# --- Helpers ----------------------------------------------------------------

# http_code <port> [path] -> prints the HTTP status code of http://localhost:<port><path>
# (path defaults to /), or 000 if nothing answered.
http_code() {
  local code
  code="$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "http://localhost:$1${2:-/}" 2>/dev/null)"
  echo "${code:-000}"
}

# is_up <port> -> success if *any* real HTTP response comes back (200/401/403/404/...).
is_up() {
  [ "$(http_code "$1")" != "000" ]
}

# port_pids <port> -> prints the PIDs (one per line) listening on that port.
# On Windows these are native Windows PIDs (for taskkill), elsewhere normal PIDs.
port_pids() {
  local port="$1"
  if [ "$OS_FAMILY" = windows ]; then
    # Equivalent to `netstat -ano | findstr :<port>`, but matched on the exact
    # local-address column + LISTENING so :8081 doesn't also match :80810 or a
    # client connection whose *remote* port happens to be 8081.
    netstat -ano 2>/dev/null | tr -d '\r' |
      awk -v p=":$port" '$1 ~ /^TCP/ && $4 == "LISTENING" && substr($2, length($2) - length(p) + 1) == p { print $5 }' |
      sort -u
  elif command -v lsof >/dev/null 2>&1; then
    lsof -ti ":$port" -sTCP:LISTEN 2>/dev/null | sort -u
  elif command -v fuser >/dev/null 2>&1; then
    fuser "$port/tcp" 2>/dev/null | tr -s ' ' '\n' | grep -E '^[0-9]+$'
  fi
}

# port_in_use <port> -> success if something is listening (or answering) on it.
port_in_use() {
  [ -n "$(port_pids "$1")" ] || is_up "$1"
}
