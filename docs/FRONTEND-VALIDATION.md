# Validation de la refonte — 12 septembre 2026

## Vérifications effectuées

- `npm run build --prefix server/frontend` : réussi (Vue/Vite, aucune dépendance ajoutée).
- `git diff --check` : réussi.
- Navigateur Chromium, viewport desktop 1440 × 1000 et mobile 390 × 844.
- API locale réelle : 523 figurines au catalogue, aucune figurine reçue ou débloquée dans cet environnement de test. Aucun import ni changement de ces données.
- Recherche sans résultat puis réinitialisation : état vide visible puis retour des 523 cartes.
- Filtre Trap Team + catégorie Personnages : 61 résultats, catégories conformes.
- Classement : 15 lignes, tri sur le nom confirmé par `aria-sort=ascending`.
- Ouverture d’une fiche, focus sur sa fermeture, boucle Tab, fermeture Échap et restitution du focus au bouton déclencheur.
- Chargement des vues Pièges et Vilains (46 entrées), ouverture et fermeture du panneau portail ; aucune mutation de disposition effectuée pendant ces tests.
- Mobile : deux colonnes de 173 px, largeur du document 390 px, aucun débordement horizontal global.
- Rendus clair/sombre inspectés ; jetons Forge, Crypte et Arcane disponibles.
- Aucune erreur ou alerte console dans les parcours vérifiés.

## Données simulées uniquement côté navigateur

Dans un contexte de test isolé, remplacement temporaire des fonctions de lecture de l’API par des réponses en mémoire : Gusto débloqué avec une date de sauvegarde. Vérification de sa première position dans la grille et de sa présence dans l’aperçu. Attente de plus de 15 secondes pour vérifier l’actualisation automatique. Simulation d’une erreur de lecture puis rétablissement : erreur affichée, reprise confirmée.

Les fonctions originales ont été restaurées. Aucune donnée simulée n’a été envoyée au serveur ; les fichiers `.sky` n’ont pas été ouverts ni modifiés.

## Limites

Pas de test d’une partie Cemu réelle, de connexion au portail ou d’écriture des sauvegardes : le contrôle Cemu est uniquement planifié dans `CEMU-PORTAL-PLAN.md`. Pas d’audit exhaustif lecteur d’écran, ni de matrice de navigateurs complète. L’actualisation automatique concerne la collection, le classement, les pièges, les vilains et l’aperçu ; une fiche de détail déjà ouverte conserve son chargement initial.

Les évolutions backend et portail déjà présentes dans le répertoire de travail ont été conservées. Aucune modification métier backend n’a été nécessaire pour cette refonte.
