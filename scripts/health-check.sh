#!/usr/bin/env bash
# =============================================================================
# Check whether every RideLink component is healthy.
#
# Each component is asked GET /actuator/health first (the 4 Spring services):
#   200            -> UP       (actuator says UP)
#   503            -> DEGRADED (running, but a dependency such as MongoDB is down)
# Anything else (no actuator, e.g. the frontend; 401/404; ...) falls back to the
# old check: any real HTTP response from http://localhost:<port>/ means UP, and
# a connection failure (curl code 000) means DOWN. The CHECK column says which
# of the two produced the result.
#
# Usage:
#   ./scripts/health-check.sh                      one-shot table; exit 0 if all UP, 1 otherwise
#   ./scripts/health-check.sh --watch              redraw every 5s, alert on status changes
#   ./scripts/health-check.sh --watch --interval 10
# =============================================================================
set -u
source "$(dirname "${BASH_SOURCE[0]}")/config.sh"

WATCH=0
INTERVAL=5

usage() { sed -n '2,17p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

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

STATES=()   # latest UP/DEGRADED/DOWN per service (same index as SERVICE_NAMES)
CODES=()    # latest HTTP code per service
CHECKS=()   # which check produced it: health (/actuator/health) or http (any response)

# probe_one <index> -> fills STATES/CODES/CHECKS for that component.
probe_one() {
  local i="$1" port="${SERVICE_PORTS[$1]}" code
  code="$(http_code "$port" /actuator/health)"
  case "$code" in
    200) STATES[$i]=UP;       CODES[$i]="$code"; CHECKS[$i]=health; return ;;
    503) STATES[$i]=DEGRADED; CODES[$i]="$code"; CHECKS[$i]=health; return ;;
  esac
  # No usable health endpoint - fall back to "answers HTTP at all".
  code="$(http_code "$port")"
  CODES[$i]="$code"; CHECKS[$i]=http
  if [ "$code" = "000" ]; then STATES[$i]=DOWN; else STATES[$i]=UP; fi
}

# probe_all -> fills STATES/CODES/CHECKS; returns the number of components not UP.
probe_all() {
  local i down=0
  for i in "${!SERVICE_NAMES[@]}"; do
    probe_one "$i"
    [ "${STATES[$i]}" = UP ] || down=$((down + 1))
  done
  return "$down"
}

state_color() {
  case "$1" in
    UP) printf '%s' "$C_GREEN" ;;
    DEGRADED) printf '%s' "$C_YELLOW" ;;
    *) printf '%s' "$C_RED" ;;
  esac
}

print_table() {
  local i
  printf '%-22s %-6s %-9s %-5s %s\n' SERVICE PORT STATUS HTTP CHECK
  printf '%-22s %-6s %-9s %-5s %s\n' ---------------------- ------ --------- ----- ------
  for i in "${!SERVICE_NAMES[@]}"; do
    # Pad the status *before* wrapping it in color codes so columns stay aligned.
    printf '%-22s %-6s %s%-9s%s %-5s %s\n' "${SERVICE_NAMES[$i]}" "${SERVICE_PORTS[$i]}" \
      "$(state_color "${STATES[$i]}")" "${STATES[$i]}" "$C_RESET" "${CODES[$i]}" "${CHECKS[$i]}"
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
  echo "${C_RED}$down of ${#SERVICE_NAMES[@]} component(s) not UP (DOWN or DEGRADED).${C_RESET}"
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

  # Record every status change, e.g. UP->DEGRADED or DOWN->UP (skipped on the very first pass).
  for i in "${!SERVICE_NAMES[@]}"; do
    prev="${PREV[$i]:-}"
    if [ -n "$prev" ] && [ "$prev" != "${STATES[$i]}" ]; then
      if [ "${STATES[$i]}" = UP ]; then
        ALERTS+=("${C_GREEN}[$now] ✔ ${SERVICE_NAMES[$i]} is back UP (was $prev)${C_RESET}")
      else
        ALERTS+=("$(state_color "${STATES[$i]}")[$now] ⚠ ${SERVICE_NAMES[$i]} went ${STATES[$i]} (was $prev)${C_RESET}")
      fi
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
    echo "${C_RED}$down component(s) not UP${C_RESET}"
  fi
  if [ "${#ALERTS[@]}" -gt 0 ]; then
    echo
    echo "${C_BOLD}Alerts${C_RESET}"
    printf '%s\n' "${ALERTS[@]}"
  fi

  sleep "$INTERVAL"
done
