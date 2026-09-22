#!/usr/bin/env bash
#
# install-launcher.sh — ajoute « Skylanders Trap Team » au launcher d'Omarchy.
#
# Le raccourci appelle tools/skylanders-session.sh : agent d'ingestion,
# connecteur du portail et Cemu patche, en une fois, sans terminal.
#
#   ./tools/install-launcher.sh            installe
#   ./tools/install-launcher.sh --remove   retire
#
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APPS="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
ICONS="${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor/256x256/apps"
ENTRY="$APPS/skylanders-session.desktop"
ICON_NAME="skylanders-session"
ICON="$ICONS/$ICON_NAME.png"

if [ "${1:-install}" = "--remove" ]; then
  rm -f "$ENTRY" "$ICON"
  update-desktop-database "$APPS" 2>/dev/null || true
  echo "Raccourci retire."
  exit 0
fi

SCRIPT="$REPO/tools/skylanders-session.sh"
[ -x "$SCRIPT" ] || { echo "ERREUR : $SCRIPT absent ou non executable." >&2; exit 1; }

# Icone : celle de Cemu, prise dans le depot voisin. Le paquet cemu-git de la
# distribution peut etre desinstalle sans casser ce raccourci.
mkdir -p "$APPS" "$ICONS"
SOURCE_ICON="$(dirname "$REPO")/Cemu/dist/linux/info.cemu.Cemu.png"
if [ -f "$SOURCE_ICON" ]; then
  cp -f "$SOURCE_ICON" "$ICON"
else
  echo "Note : icone Cemu introuvable ($SOURCE_ICON), entree sans icone dediee."
  ICON_NAME="applications-games"
fi

CONFIG="${XDG_CONFIG_HOME:-$HOME/.config}/skylanders/session.env"
if [ ! -f "$CONFIG" ]; then
  mkdir -p "$(dirname "$CONFIG")"
  cp "$REPO/tools/session.env.example" "$CONFIG"
  echo "Configuration creee : $CONFIG (verifier le chemin du jeu)."
fi

sed -e "s|@SCRIPT@|$SCRIPT|g" -e "s|@ICON@|$ICON_NAME|g" \
    "$REPO/tools/skylanders-session.desktop.in" > "$ENTRY"
chmod 644 "$ENTRY"
update-desktop-database "$APPS" 2>/dev/null || true

echo "Installe : $ENTRY"
echo "Ouvre le launcher (SUPER + espace) et cherche « Skylanders »."
echo "  journal de session : ${XDG_STATE_HOME:-$HOME/.local/state}/skylanders/session.log"
