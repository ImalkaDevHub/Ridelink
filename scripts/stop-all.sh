#!/usr/bin/env bash
# =============================================================================
# Stop every RideLink component started by start-all.sh (in reverse order).
#
# Two passes per service, because Maven and npm spawn child processes
# (spring-boot:run forks a separate JVM; `next dev` forks a server process):
#   1. Kill the saved PID from logs/<service>.pid together with its children.
#   2. Kill whatever is still listening on the service's port.
# Works whether or not the service was started by start-all.sh.
#
# Usage: ./scripts/stop-all.sh
# =============================================================================
set -u
source "$(dirname "${BASH_SOURCE[0]}")/config.sh"

# kill_tree <pid> -> kill a process and all of its descendants.
kill_tree() {
  local pid="$1" winpid child
  if [ "$OS_FAMILY" = windows ]; then
    # $! in Git Bash is an MSYS PID; taskkill needs the native Windows PID.
    winpid="$(cat "/proc/$pid/winpid" 2>/dev/null)"
    [ -n "$winpid" ] || return 1
    taskkill //PID "$winpid" //T //F >/dev/null 2>&1
    kill "$pid" 2>/dev/null   # tidy up the MSYS wrapper too, if it survived
    return 0
  fi
  kill -0 "$pid" 2>/dev/null || return 1
  # Children first so they don't get re-parented and orphaned.
  for child in $(pgrep -P "$pid" 2>/dev/null); do kill_tree "$child"; done
  kill "$pid" 2>/dev/null
}

# free_port <port> -> kill every process listening on the port; prints their PIDs.
free_port() {
  local port="$1" pids pid
  pids="$(port_pids "$port")"
  [ -n "$pids" ] || return 1
  for pid in $pids; do
    if [ "$OS_FAMILY" = windows ]; then
      taskkill //PID "$pid" //T //F >/dev/null 2>&1
    else
      kill "$pid" 2>/dev/null
    fi
  done
  if [ "$OS_FAMILY" != windows ]; then
    # Give JVMs a moment to shut down gracefully, then force anything left.
    sleep 2
    pids_left="$(port_pids "$port")"
    [ -n "$pids_left" ] && echo "$pids_left" | xargs kill -9 2>/dev/null
  fi
  echo $pids
}

echo "${C_BOLD}Stopping RideLink ($OS_FAMILY)${C_RESET}"
stopped=0

# Iterate backwards: frontend first, account-service last.
for (( i=${#SERVICE_NAMES[@]}-1; i>=0; i-- )); do
  name="${SERVICE_NAMES[$i]}"; port="${SERVICE_PORTS[$i]}"
  pidfile="$LOG_DIR/$name.pid"
  did=""

  if [ -f "$pidfile" ]; then
    pid="$(cat "$pidfile")"
    kill_tree "$pid" && did="pid $pid"
    rm -f "$pidfile"
  fi

  if port_pids_killed="$(free_port "$port")"; then
    did="${did:+$did, }port $port (pid ${port_pids_killed// /, })"
  fi

  if [ -n "$did" ]; then
    echo "${C_GREEN}■ stopped $name${C_RESET} ${C_DIM}- $did${C_RESET}"
    stopped=$((stopped + 1))
  else
    echo "${C_DIM}· $name was not running${C_RESET}"
  fi
done

# Final check: anything still answering?
sleep 1
still=()
for i in "${!SERVICE_NAMES[@]}"; do
  port_in_use "${SERVICE_PORTS[$i]}" && still+=("${SERVICE_NAMES[$i]}:${SERVICE_PORTS[$i]}")
done

echo
if [ "${#still[@]}" -eq 0 ]; then
  echo "${C_GREEN}Done - stopped $stopped component(s); all ports are free.${C_RESET}"
else
  echo "${C_RED}Still running: ${still[*]}${C_RESET}"
  exit 1
fi
