#!/usr/bin/env bash
# =============================================================================
# Start the whole RideLink stack in dependency order:
#   account -> driver -> ride -> fare-payment -> frontend
#
# Each component runs in the background from its own folder, with output in
# logs/<service>.log and its PID in logs/<service>.pid. We wait for each port
# to answer HTTP before launching the next one, and abort (exit 1) on failure.
#
# Usage: ./scripts/start-all.sh
# Env:   STARTUP_TIMEOUT (default 120s), POLL_INTERVAL (default 2s)
# Note:  MongoDB is NOT started here - run `docker compose up -d mongodb` first.
# =============================================================================
set -u
source "$(dirname "${BASH_SOURCE[0]}")/config.sh"

mkdir -p "$LOG_DIR"
STATUSES=()

# Windows workaround: since JDK 16 NIO selectors create an AF_UNIX socket in
# %TEMP%, and on some machines that fails ("Unable to establish loopback
# connection") so Tomcat can't start. Point the JVMs (Maven + the forked app,
# which inherits the env) at a private dir instead. Appends to any existing value.
if [ "$OS_FAMILY" = windows ]; then
  mkdir -p "$LOG_DIR/.jvmtmp"
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Djdk.net.unixdomain.tmpdir=$(cygpath -m "$LOG_DIR/.jvmtmp")"
fi   # per-service result for the summary table, same index as SERVICE_NAMES

# launch <index> -> starts the service in the background, writes its PID file.
launch() {
  local name="${SERVICE_NAMES[$1]}" dir="$ROOT_DIR/${SERVICE_DIRS[$1]}" type="${SERVICE_TYPES[$1]}"
  local log="$LOG_DIR/$name.log" cmd

  case "$type" in
    maven)
      # Prefer the Maven wrapper; mvnw is committed without the exec bit, so
      # fresh Linux/macOS clones fall back to a system `mvn`, then `sh mvnw`.
      if [ -x "$dir/mvnw" ]; then cmd=(./mvnw spring-boot:run)
      elif command -v mvn >/dev/null 2>&1; then cmd=(mvn spring-boot:run)
      else cmd=(sh ./mvnw spring-boot:run)
      fi
      ;;
    npm)
      if [ ! -d "$dir/node_modules" ]; then
        echo "  ${C_DIM}node_modules missing - running npm install (logged to $name.log)...${C_RESET}"
        (cd "$dir" && npm install) >"$log" 2>&1 || return 1
      fi
      cmd=(npm run dev)
      ;;
    *) echo "  ${C_RED}unknown service type '$type' for $name${C_RESET}"; return 1 ;;
  esac

  echo "  ${C_DIM}\$ (cd ${SERVICE_DIRS[$1]} && ${cmd[*]}) > logs/$name.log${C_RESET}"
  # nohup so the service survives the launching terminal; append so a preceding
  # npm install log isn't wiped.
  (cd "$dir" && exec nohup "${cmd[@]}") >>"$log" 2>&1 < /dev/null &
  echo $! >"$LOG_DIR/$name.pid"
}

# wait_for_port <port> <pid> -> polls until HTTP answers, the process dies, or timeout.
wait_for_port() {
  local port="$1" pid="$2" waited=0
  while [ "$waited" -lt "$STARTUP_TIMEOUT" ]; do
    is_up "$port" && return 0
    # Fail fast if the launcher already exited (compile error, port clash, ...).
    kill -0 "$pid" 2>/dev/null || return 1
    sleep "$POLL_INTERVAL"
    waited=$((waited + POLL_INTERVAL))
    [ -t 1 ] && printf '\r  waiting for :%s ... %ss' "$port" "$waited"
  done
  return 1
}

clear_progress() { [ -t 1 ] && printf '\r\033[K'; return 0; }

print_summary() {
  echo
  echo "${C_BOLD}RideLink - startup summary${C_RESET}"
  printf '%-22s %-6s %-24s %s\n' SERVICE PORT URL STATUS
  printf '%-22s %-6s %-24s %s\n' ---------------------- ------ ------------------------ ----------------
  local i color
  for i in "${!SERVICE_NAMES[@]}"; do
    case "${STATUSES[$i]:-NOT STARTED}" in
      UP*) color=$C_GREEN ;; FAILED) color=$C_RED ;; *) color=$C_YELLOW ;;
    esac
    printf '%-22s %-6s %-24s %s\n' "${SERVICE_NAMES[$i]}" "${SERVICE_PORTS[$i]}" \
      "http://localhost:${SERVICE_PORTS[$i]}" "${color}${STATUSES[$i]:-NOT STARTED}${C_RESET}"
  done
  echo
}

echo "${C_BOLD}Starting RideLink ($OS_FAMILY) - logs in ${LOG_DIR}${C_RESET}"
for i in "${!SERVICE_NAMES[@]}"; do
  name="${SERVICE_NAMES[$i]}"; port="${SERVICE_PORTS[$i]}"
  echo
  echo "${C_BLUE}▶ $name${C_RESET} (port $port)"

  if port_in_use "$port"; then
    echo "${C_YELLOW}● $name already running on port $port - skipping${C_RESET}"
    STATUSES[$i]="UP (already running)"
    continue
  fi

  if ! launch "$i"; then
    echo "${C_RED}✖ $name failed to start — see logs/$name.log${C_RESET}"
    STATUSES[$i]=FAILED; print_summary; exit 1
  fi

  if wait_for_port "$port" "$(cat "$LOG_DIR/$name.pid")"; then
    clear_progress
    echo "${C_GREEN}✔ $name is UP on port $port${C_RESET}"
    STATUSES[$i]=UP
  else
    clear_progress
    echo "${C_RED}✖ $name failed to start — see logs/$name.log${C_RESET}"
    echo "  ${C_DIM}last log lines:${C_RESET}"
    tail -n 15 "$LOG_DIR/$name.log" 2>/dev/null | sed 's/^/    /'
    echo "  ${C_DIM}Already-started services are still running; use ./scripts/stop-all.sh to stop them.${C_RESET}"
    STATUSES[$i]=FAILED; print_summary; exit 1
  fi
done

print_summary
echo "${C_GREEN}All components are up.${C_RESET} Monitor with ./scripts/health-check.sh --watch"
