#!/usr/bin/env bash
#
# install-service.sh — installe l'agent comme service utilisateur systemd.
#
# L'agent démarre alors avec la session et tourne en continu : plus besoin d'y
# penser avant de jouer. Il ne consomme rien au repos — un scan sans changement
# prend 0,0 s et la surveillance sonde le dossier toutes les 2 s.
#
#   ./agent/install-service.sh            installe et démarre
#   ./agent/install-service.sh --status   état et journal récent
#   ./agent/install-service.sh --remove    arrête, désactive et supprime
#
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UNIT_NAME="skylanders-agent.service"
UNIT_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
UNIT="$UNIT_DIR/$UNIT_NAME"
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/skylanders-agent"

case "${1:-install}" in
  --status)
    systemctl --user status "$UNIT_NAME" --no-pager || true
    echo
    journalctl --user -u "$UNIT_NAME" -n 20 --no-pager || true
    exit 0
    ;;
  --remove)
    systemctl --user disable --now "$UNIT_NAME" 2>/dev/null || true
    rm -f "$UNIT"
    systemctl --user daemon-reload
    echo "Service retiré. L'agent redevient un lancement manuel."
    exit 0
    ;;
  install) ;;
  *) echo "Option inconnue : $1" >&2; exit 2 ;;
esac

CONFIG="$REPO/agent/agent.yaml"
if [ ! -f "$CONFIG" ]; then
  echo "ERREUR : $CONFIG absent." >&2
  echo "        Copier agent/agent.yaml.example et l'adapter avant d'installer." >&2
  exit 1
fi

PYTHON="$(command -v python3)"
if [ -z "$PYTHON" ]; then
  echo "ERREUR : python3 introuvable." >&2
  exit 1
fi

# Le cache doit exister AVANT le démarrage : le service tourne avec le reste du
# HOME en lecture seule, il ne pourrait pas créer le dossier lui-même.
mkdir -p "$CACHE" "$UNIT_DIR"

sed -e "s|@PYTHON@|$PYTHON|g" \
    -e "s|@AGENT@|$REPO/agent/skylanders_agent.py|g" \
    -e "s|@CONFIG@|$CONFIG|g" \
    -e "s|@CACHE@|$CACHE|g" \
    "$REPO/agent/skylanders-agent.service.in" > "$UNIT"

systemctl --user daemon-reload
systemctl --user enable "$UNIT_NAME"
# `enable --now` ne relance pas un service deja actif : sans restart explicite,
# une reinstallation laisserait tourner l'ancienne definition.
systemctl --user restart "$UNIT_NAME"
sleep 2

echo "Installé : $UNIT"
echo
systemctl --user --no-pager status "$UNIT_NAME" | head -12 || true
echo
echo "L'agent démarrera désormais avec ta session."
echo "  journal   : journalctl --user -u $UNIT_NAME -f"
echo "  arrêter   : systemctl --user stop $UNIT_NAME"
echo "  désinstaller : ./agent/install-service.sh --remove"

# Pour que le service survive à la fermeture de session (utile si tu joues via
# un autre utilisateur ou en console), il faut activer le « lingering ».
if ! loginctl show-user "$USER" -p Linger --value 2>/dev/null | grep -qx yes; then
  echo
  echo "Note : le service s'arrête à la fermeture de session."
  echo "       Pour qu'il tourne aussi hors session : sudo loginctl enable-linger $USER"
fi
