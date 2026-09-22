# Déploiement du dashboard sur le serveur Debian

Le serveur (API + base + interface) tourne en continu sur le homelab ; l'agent
d'ingestion et le connecteur du portail restent sur le PC de jeu et parlent au
serveur par Tailscale (SPEC.md §2). Les visuels sous copyright Activision ne sont
ni dans le dépôt ni dans l'image Docker : ils sont montés en volume (SPEC.md §10.3).

```text
PC de jeu (Omarchy)                        Serveur Debian (Docker)
  agent d'ingestion  ──── HTTP/Tailscale ──►  skylanders-server  ──►  postgres
  connecteur Cemu    ──── HTTP/Tailscale ──►  (port 8080)
```

## 1. Prérequis sur le Debian

```bash
sudo apt install -y docker.io docker-compose-v2 git
sudo usermod -aG docker "$USER"   # puis se reconnecter
```

Tailscale doit être installé et connecté au même tailnet que le PC de jeu.

## 2. Récupérer le dépôt

```bash
git clone git@github.com:Whiplashhhh/SkylandersDashboard.git
cd SkylandersDashboard
```

Seul ce dépôt est nécessaire : le dépôt Cemu ne sert qu'au PC de jeu.

## 3. Configurer les secrets

```bash
cp .env.example .env
for key in POSTGRES_PASSWORD INGEST_TOKEN PORTAL_CONNECTOR_TOKEN PORTAL_CONTROL_TOKEN; do
  printf '%s : %s\n' "$key" "$(openssl rand -hex 32)"
done
```

Reporter les quatre valeurs dans `.env`. **Trois jetons distincts, jamais la même
valeur** : l'agent, le connecteur et le navigateur ont des droits différents.
Un `INGEST_TOKEN` vide ouvrirait `/api/ingest` à tout le tailnet — le
`docker compose` refuse de démarrer si une valeur manque.

Choisir aussi l'interface d'écoute dans `.env` :

```bash
SERVER_BIND=100.x.y.z   # adresse Tailscale du serveur (tailscale ip -4)
```

`127.0.0.1` n'expose rien hors de la machine ; `0.0.0.0` ouvre au LAN entier.

## 4. Copier les visuels

Depuis le PC de jeu (53 Mo, hors dépôt) :

```bash
rsync -av --delete ~/projects/CemuSkylandersVersion/SkylandersDashboard/images/ \
  debian:~/SkylandersDashboard/images/
```

L'application fonctionne sans : chaque figurine sans visuel reçoit un badge de repli.

## 5. Démarrer

```bash
docker compose -f docker-compose.yml -f docker-compose.server.yml up -d --build
```

Le build compile le frontend Vue puis le JAR Spring Boot dans l'image ; comptez
quelques minutes la première fois. Vérifier :

```bash
docker compose -f docker-compose.yml -f docker-compose.server.yml ps
docker compose -f docker-compose.yml -f docker-compose.server.yml logs -f server
```

Le dashboard répond sur `http://<hôte-tailscale>:8080`.

Pour éviter de répéter les deux `-f`, exporter une fois pour toutes :

```bash
echo 'COMPOSE_FILE=docker-compose.yml:docker-compose.server.yml' >> .env
```

## 6. Pointer le PC de jeu vers le serveur

Trois fichiers sur le PC de jeu, dans `~/.config/skylanders/` :

| Fichier | Clé à changer | Valeur |
| --- | --- | --- |
| `agent.yaml` | `serverUrl` | `http://<hôte-tailscale>:8080` |
| `agent.yaml` | `token` | `INGEST_TOKEN` du `.env` |
| `connector.json` | `serverUrl` | `http://<hôte-tailscale>:8080` |
| `connector.json` | `token` | `PORTAL_CONNECTOR_TOKEN` du `.env` |

Le `PORTAL_CONTROL_TOKEN` ne se met dans aucun fichier : il se colle dans la page
portail du navigateur, à la première connexion.

Le PC de jeu n'a plus besoin de Docker, de PostgreSQL ni du JAR : il ne lance que
l'agent, le connecteur et Cemu (voir [DEMARRER.md](../../DEMARRER.md)).

## 7. Exploitation

```bash
# Mise à jour après un git pull
docker compose up -d --build

# Sauvegarde de la base (historique de progression)
docker compose exec -T postgres pg_dump -U skylanders skylanders | gzip > skylanders-$(date +%F).sql.gz

# Restauration
gunzip -c skylanders-2026-09-22.sql.gz | docker compose exec -T postgres psql -U skylanders skylanders
```

Les migrations Flyway s'appliquent au démarrage du conteneur `server` : aucune
étape manuelle après une mise à jour.

## 8. En cas de problème

| Symptôme | À vérifier |
| --- | --- |
| `POSTGRES_PASSWORD manquant` au `up` | `.env` absent ou incomplet à côté du `docker-compose.yml`. |
| Le conteneur `server` redémarre en boucle | `docker compose logs server` : le plus souvent la base n'est pas prête ou `DB_URL` a été surchargée à la main. |
| 401 dans le journal de l'agent | `token` de `agent.yaml` ≠ `INGEST_TOKEN` du serveur. |
| « Connecteur absent » dans l'interface | Le connecteur ne tourne pas sur le PC de jeu, ou son `token` ne correspond pas à `PORTAL_CONNECTOR_TOKEN`. |
| Toutes les figurines en badge de repli | Volume `images/` vide côté serveur : refaire le `rsync` de l'étape 4. |
| Rien ne répond hors de la machine | `SERVER_BIND` vaut `127.0.0.1` : mettre l'adresse Tailscale puis `up -d`. |
