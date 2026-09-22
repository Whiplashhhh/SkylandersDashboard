#!/usr/bin/env bash
# Session de jeu complete, lancable depuis le launcher d'Omarchy.
#
# Demarre, dans cet ordre :
#   1. l'agent d'ingestion      (lecture seule sur les .sky, pousse les deltas)
#   2. Cemu patche + le connecteur du portail (start_session.py)
# et arrete l'agent quand Cemu se ferme normalement.
#
# Le serveur du dashboard n'est PAS demarre ici : il tourne en continu sur le
# serveur Debian (docs/DEPLOIEMENT.md).
#
# Configuration : ~/.config/skylanders/session.env (voir session.env.example).
set -uo pipefail

CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/skylanders"
STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/skylanders"
RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp}"
mkdir -p "$STATE_DIR"
LOG="$STATE_DIR/session.log"

# Lance sans terminal depuis le launcher : tout part dans le journal, les erreurs
# remontent en notification.
exec >>"$LOG" 2>&1
echo "=== $(date -Is) demarrage de la session ==="

fail() {
  echo "ERREUR : $*"
  command -v notify-send >/dev/null && \
    notify-send -u critical "Skylanders" "$* — voir $LOG"
  exit 1
}

# --- Configuration -----------------------------------------------------------
# Racine du dashboard : ce script vit dans <dashboard>/tools/.
DASHBOARD_DIR="$(cd -- "$(dirname -- "$(readlink -f -- "$0")")/.." && pwd)"
# Cemu patche : depot voisin, cf. AGENTS.md (« Espace de travail »).
CEMU_BIN="$(dirname -- "$DASHBOARD_DIR")/Cemu/bin/Cemu_release"
GAME_PATH=""
AGENT_CONFIG="$CONFIG_DIR/agent.yaml"
CONNECTOR_CONFIG="$CONFIG_DIR/connector.json"
OPEN_DASHBOARD=1

# shellcheck source=/dev/null
[ -f "$CONFIG_DIR/session.env" ] && . "$CONFIG_DIR/session.env"

[ -x "$CEMU_BIN" ]        || fail "Cemu introuvable ou non executable : $CEMU_BIN"
[ -f "$AGENT_CONFIG" ]    || fail "Configuration de l'agent absente : $AGENT_CONFIG"
[ -f "$CONNECTOR_CONFIG" ] || fail "Configuration du connecteur absente : $CONNECTOR_CONFIG"
if [ -n "$GAME_PATH" ] && [ ! -f "$GAME_PATH" ]; then
  fail "Jeu introuvable : $GAME_PATH"
fi

# --- Une seule session a la fois ---------------------------------------------
# start_session.py refuse deja un second pont, mais un double-clic ne doit pas
# non plus lancer deux agents.
exec 9>"$RUNTIME_DIR/skylanders-session.lock"
flock -n 9 || fail "Une session Skylanders est deja en cours."

# --- Serveur joignable ? ------------------------------------------------------
SERVER_URL="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["serverUrl"].rstrip("/"))' \
  "$CONNECTOR_CONFIG" 2>/dev/null)"
if [ -n "$SERVER_URL" ] && ! curl -fsS --max-time 5 -o /dev/null "$SERVER_URL/"; then
  echo "AVERTISSEMENT : dashboard injoignable sur $SERVER_URL"
  command -v notify-send >/dev/null && \
    notify-send -u normal "Skylanders" \
      "Dashboard injoignable ($SERVER_URL). Le jeu demarre, mais sans portail ni suivi."
fi

# --- Agent d'ingestion --------------------------------------------------------
# Si l'agent tourne deja en service utilisateur (agent/install-service.sh), on le
# laisse faire : deux agents sur le meme dossier n'apporteraient rien.
AGENT_PID=""
# `is-active` renvoie aussi 0 pour « activating » : une unite en boucle de
# redemarrage passerait pour un agent en bonne sante et on ne lancerait rien.
if [ "$(systemctl --user is-active skylanders-agent.service 2>/dev/null)" = "active" ]; then
  echo "agent deja actif en service systemd, rien a demarrer"
else
  python3 "$DASHBOARD_DIR/agent/skylanders_agent.py" --config "$AGENT_CONFIG" &
  AGENT_PID=$!
  echo "agent demarre (pid $AGENT_PID)"
fi

stop_agent() {
  # L'agent est en lecture seule : l'interrompre ne risque aucune donnee.
  if [ -n "$AGENT_PID" ] && kill -0 "$AGENT_PID" 2>/dev/null; then
    kill -TERM "$AGENT_PID" 2>/dev/null
    for _ in $(seq 10); do
      kill -0 "$AGENT_PID" 2>/dev/null || break
      sleep 0.5
    done
    kill -KILL "$AGENT_PID" 2>/dev/null
  fi
  echo "=== $(date -Is) session terminee ==="
}
trap stop_agent EXIT

# --- Dashboard dans le navigateur ---------------------------------------------
if [ "$OPEN_DASHBOARD" = "1" ] && [ -n "$SERVER_URL" ] && command -v xdg-open >/dev/null; then
  xdg-open "$SERVER_URL" >/dev/null 2>&1 &
fi

# --- Cemu + connecteur --------------------------------------------------------
# start_session.py possede Cemu et le connecteur : il rend la main a la fermeture
# de Cemu et arrete le connecteur lui-meme.
SESSION_ARGS=(--config "$CONNECTOR_CONFIG" --cemu "$CEMU_BIN")
[ -n "$GAME_PATH" ] && SESSION_ARGS+=(--game "$GAME_PATH")

python3 "$DASHBOARD_DIR/connector/start_session.py" "${SESSION_ARGS[@]}"
STATUS=$?
[ "$STATUS" -ne 0 ] && fail "Cemu s'est arrete en erreur (code $STATUS)."
exit 0
