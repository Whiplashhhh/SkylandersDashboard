# Images principales du wiki Skylanders

Import explicite demandé le 12 septembre 2026. Cette décision remplace, pour cet outil manuel, l’ancienne exclusion du téléchargement automatique dans la spécification. Le serveur ne contacte pas Fandom : il continue à servir les fichiers locaux du dossier `images/`.

L’importeur utilise les titres `wiki` déjà vérifiés dans `catalog.json`, lit le premier bloc image de l’infobox de chaque fiche et conserve son URL originale. Il ignore les logos, symboles d’éléments et galeries. Deux exceptions vérifiées, Volcanic Vault et UFO Hat, utilisent leur vignette en tête d’article, avant le texte. Hood Sickle est résolu vers la fiche « Hood Sickle (character) », car son titre court mène à une homonymie. Par exemple, la fiche [Food Fight](https://skylanders.fandom.com/wiki/Food_Fight) désigne `Food_Fight_Promo.jpg`.

Les variantes qui pointent vers une même fiche partagent son image principale. Une page dédiée, comme Dark Food Fight, fournit son propre visuel. Les entrées sans fiche vérifiée restent inchangées ; elles sont listées dans le rapport, sans appariement approximatif.

## Commandes

```bash
cd /home/whiplashhh/projects/SkylandersDashboard

# Préparer les URL, sans télécharger les images
python3 tools/import_wiki_images.py --resolve-only

# Télécharger les images et reprendre les imports incomplets
python3 tools/import_wiki_images.py --apply
```

Prérequis : Python 3 et curl. Sans option, l’outil affiche uniquement les comptes. `--limit 1` permet un essai sur une fiche ; `--refresh` redemande les images déjà importées.

Le rapport de provenance et de reprise est `images/wiki-images.json`. Chaque succès produit `<toyId>_<variantId>.<format>` dans le dossier servi par l’application. Les anciennes images sont copiées dans `images/.wiki-backup/<date>/` avant remplacement. Les autres formats d’une identité ne sont retirés qu’après installation du nouveau fichier, pour éviter que le serveur continue à privilégier un ancien WebP.

Les images ne sont pas ajoutées à Git et aucun fichier `.sky` n’est lu ou modifié. Le téléchargement ne change aucun statut de déblocage : les figurines non débloquées restent grisées.

## Blocage rencontré

Les pages de l’API sont accessibles, mais le CDN `static.wikia.nocookie.net` renvoie une vérification Cloudflare (`403`, `cf-mitigated: challenge`) pour les téléchargements testés, notamment Food Fight. L’outil s’arrête sur un refus 403 ou une limitation 429 et retourne un code d’échec si l’import demandé est incomplet. Il n’essaie pas de contourner la vérification. Les associations résolues sont conservées pour une reprise quand l’accès aux images sera disponible.

Une fois les images importées dans le dossier effectivement servi par le backend, aucun redémarrage n’est nécessaire. Un rechargement forcé du navigateur peut être nécessaire : les images existantes sont mises en cache pendant six heures.

Tests : `python3 -m unittest discover -s tools/tests -p 'test_import_wiki_images.py'` vérifie la sélection de la première image d’infobox, l’absence de repli arbitraire, le domaine attendu et le rejet d’une page HTML à la place d’une image.

État après correction des trois fiches : 305 pages résolues pour 434 identités. Les téléchargements restent incomplets tant que le refus du CDN persiste. Répéter la commande ne résout pas une vérification Cloudflare.


## Mode navigateur

Quand l’image s’affiche dans le navigateur habituel mais que curl reçoit un refus, essayer :

```bash
cd /home/whiplashhh/projects/SkylandersDashboard
python3 tools/import_wiki_images.py --browser
```

Ouvrir l’adresse locale affichée dans ce même navigateur et cliquer sur **Importer les images**. Le serveur temporaire écoute uniquement sur `127.0.0.1`, à un port libre, et son adresse contient un identifiant aléatoire. Il refuse les envois d’une autre origine. Les noms des fichiers à écrire viennent du catalogue, jamais du navigateur.

La page utilise les requêtes normales du navigateur, sans récupérer de cookies ni modifier ses protections. Le CDN doit autoriser la lecture CORS des images : voir une image en navigation directe ne garantit pas cette autorisation. En cas de refus, la page affiche l’échec et un lien vers l’image ; elle n’annonce pas de réussite et ne tente pas de contourner la restriction.

Les imports réussis sont persistés au fil de l’eau. Pour reprendre, relancer la commande ; Ctrl+C arrête le serveur local. Les 89 entrées sans page associée restent inchangées. Validation : 11 tests Python (sélection, transport, reprise, sauvegarde et rejet d’entrées invalides), syntaxe du JavaScript contrôlée ; l’accès effectif aux images reste à valider dans le navigateur de l’utilisateur.
