#!/usr/bin/env bash
#
# backup_db.sh — sauvegarde la base de progression.
#
# POURQUOI CE SCRIPT EXISTE
#
# Le dossier des .sky est sauvegardé une fois pour toutes, mais il ne contient
# que l'état COURANT de chaque figurine. Dès que Cemu réécrit un fichier, l'état
# précédent n'existe plus que dans PostgreSQL. L'historique de progression n'est
# donc pas reconstructible depuis les fichiers : c'est la seule donnée du projet
# dont la base est l'unique dépositaire.
#
# Restauration (destructive, à lancer à la main en connaissance de cause) :
#   gunzip -c <fichier.sql.gz> | docker exec -i skylanders-postgres \
#       psql -U skylanders -d skylanders
#
set -euo pipefail

CONTAINER="${SKYLANDERS_DB_CONTAINER:-skylanders-postgres}"
DB="${SKYLANDERS_DB_NAME:-skylanders}"
USER_NAME="${SKYLANDERS_DB_USER:-skylanders}"
DEST="${1:-$HOME/Backups/skylanders-db}"
KEEP="${SKYLANDERS_DB_KEEP:-14}"

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "ERREUR : conteneur « $CONTAINER » introuvable ou arrêté." >&2
  echo "        Démarrer la base avec : docker compose up -d postgres" >&2
  exit 1
fi

mkdir -p "$DEST"
STAMP="$(date +%Y-%m-%d_%H%M%S)"
FILE="$DEST/skylanders_$STAMP.sql.gz"

echo "Sauvegarde de « $DB » depuis « $CONTAINER »…"
docker exec "$CONTAINER" pg_dump -U "$USER_NAME" -d "$DB" --clean --if-exists \
  | gzip -9 > "$FILE"

# Une sauvegarde jamais relue est une sauvegarde qu'on croit avoir : on vérifie
# tout de suite que l'archive est intacte et qu'elle contient bien les données.
if ! gunzip -t "$FILE" 2>/dev/null; then
  echo "ERREUR : archive corrompue, elle est supprimée." >&2
  rm -f "$FILE"
  exit 1
fi

SNAPSHOTS="$(gunzip -c "$FILE" | grep -c '^COPY public.toy_snapshot' || true)"
if [ "$SNAPSHOTS" -eq 0 ]; then
  echo "ERREUR : la table toy_snapshot est absente du dump — sauvegarde suspecte." >&2
  echo "        Fichier conservé pour inspection : $FILE" >&2
  exit 1
fi

SIZE="$(du -h "$FILE" | cut -f1)"
echo "  $FILE  ($SIZE)"

# Rotation : on garde les KEEP plus récentes.
mapfile -t OLD < <(ls -1t "$DEST"/skylanders_*.sql.gz 2>/dev/null | tail -n "+$((KEEP + 1))")
if [ "${#OLD[@]}" -gt 0 ]; then
  printf '  rotation : %d archive(s) au-delà des %d conservées\n' "${#OLD[@]}" "$KEEP"
  printf '    %s\n' "${OLD[@]}"
  rm -f -- "${OLD[@]}"
fi

echo "Terminé. $(ls -1 "$DEST"/skylanders_*.sql.gz 2>/dev/null | wc -l) archive(s) dans $DEST"
