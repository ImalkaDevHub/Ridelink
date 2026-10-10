#!/usr/bin/env bash
# =============================================================================
# Check whether every RideLink component is answering HTTP.
#
# No Spring Actuator here, so "UP" means any real HTTP response from
# http://localhost:<port>/ (200, 401, 403, 404, ...). A connection failure
# (curl code 000) means DOWN.
#
# Usage:
#   ./scripts/health-check.sh                      one-shot table; exit 0 if all UP, 1 otherwise
#   ./scripts/health-check.sh --watch              redraw every 5s, alert on UP<->DOWN changes
#   ./scripts/health-check.sh --watch --interval 10
# =============================================================================
set -u
source "$(dirname "${BASH_SOURCE[0]}")/config.sh"

WATCH=0
INTERVAL=5

usage() { sed -n '2,13p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

while [ $# -gt 0 ]; do
  case "$1" in
    -w|--watch) WATCH=1 ;;
    -i|--interval)
      [ $# -ge 2 ] || { echo "--interval needs a value" >&2; exit 2; }
      INTERVAL="$2"; shift ;;
    --interval=*) INTERVAL="${1#*=}" ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done
case "$INTERVAL" in ''|*[!0-9]*|0) echo "--interval must be a positive integer" >&2; exit 2 ;; esac

STATES=()   # latest UP/DOWN per service (same index as SERVICE_NAMES)
CODES=()    # latest HTTP code per service

# probe_all -> fills STATES/CODES; returns the number of DOWN components.
probe_all() {
  local i code down=0
  for i in "${!SERVICE_NAMES[@]}"; do
    code="$(http_code "${SERVICE_PORTS[$i]}")"
    CODES[$i]="$code"
    if [ "$code" = "000" ]; then STATES[$i]=DOWN; down=$((down + 1)); else STATES[$i]=UP; fi
  done
  return "$down"
}

print_table() {
  local i color
  printf '%-22s %-6s %-6s %s\n' SERVICE PORT STATUS HTTP
  printf '%-22s %-6s %-6s %s\n' ---------------------- ------ ------ ----
  for i in "${!SERVICE_NAMES[@]}"; do
    [ "${STATES[$i]}" = UP ] && color=$C_GREEN || color=$C_RED
    # Pad the status *before* wrapping it in color codes so columns stay aligned.
    printf '%-22s %-6s %s%-6s%s %s\n' "${SERVICE_NAMES[$i]}" "${SERVICE_PORTS[$i]}" \
      "$color" "${STATES[$i]}" "$C_RESET" "${CODES[$i]}"
  done
}

# --- One-shot mode ----------------------------------------------------------
if [ "$WATCH" -eq 0 ]; then
  probe_all; down=$?
  print_table
  echo
  if [ "$down" -eq 0 ]; then
    echo "${C_GREEN}All ${#SERVICE_NAMES[@]} components are UP.${C_RESET}"
    exit 0
  fi
  echo "${C_RED}$down of ${#SERVICE_NAMES[@]} component(s) DOWN.${C_RESET}"
  exit 1
fi

# --- Watch mode -------------------------------------------------------------
ALERTS=()       # rolling history of state-change alerts, shown under the table
MAX_ALERTS=15
PREV=()         # previous STATES, to detect transitions

on_exit() {
  [ -t 1 ] && printf '\033[?25h'   # restore cursor
  echo
  echo "Stopped watching."
  exit 0
}
trap on_exit INT TERM
[ -t 1 ] && printf '\033[?25l'     # hide cursor while redrawing

while true; do
  probe_all; down=$?
  now="$(date +%H:%M:%S)"

  # Record UP->DOWN and DOWN->UP transitions (skipped on the very first pass).
  for i in "${!SERVICE_NAMES[@]}"; do
    prev="${PREV[$i]:-}"
    if [ "$prev" = UP ] && [ "${STATES[$i]}" = DOWN ]; then
      ALERTS+=("${C_RED}[$now] ⚠ ${SERVICE_NAMES[$i]} went DOWN${C_RESET}")
    elif [ "$prev" = DOWN ] && [ "${STATES[$i]}" = UP ]; then
      ALERTS+=("${C_GREEN}[$now] ✔ ${SERVICE_NAMES[$i]} is back UP${C_RESET}")
    fi
    PREV[$i]="${STATES[$i]}"
  done
  # Keep only the most recent alerts.
  while [ "${#ALERTS[@]}" -gt "$MAX_ALERTS" ]; do ALERTS=("${ALERTS[@]:1}"); done

  # Redraw. Plain `clear` flickers in some terminals; home + clear-screen is smoother.
  if [ -t 1 ]; then printf '\033[H\033[2J'; else echo; fi
  echo "${C_BOLD}RideLink health${C_RESET} ${C_DIM}- $now - every ${INTERVAL}s - Ctrl+C to exit${C_RESET}"
  echo
  print_table
  echo
  if [ "$down" -eq 0 ]; then
    echo "${C_GREEN}All components UP${C_RESET}"
  else
    echo "${C_RED}$down component(s) DOWN${C_RESET}"
  fi
  if [ "${#ALERTS[@]}" -gt 0 ]; then
    echo
    echo "${C_BOLD}Alerts${C_RESET}"
    printf '%s\n' "${ALERTS[@]}"
  fi

  sleep "$INTERVAL"
done
