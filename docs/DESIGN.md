# Identité du dashboard Skylanders

La collection est l’écran d’entrée. Le vocabulaire visuel évoque le repaire d’un Maître du Portail : encre, lumière menthe, anneaux du portail et accents élémentaires. Les images existantes du projet restent la matière principale des cartes ; leurs absences sont prises en charge par le serveur et, en cas d’échec réseau, par un repli typographique sur les cartes.

## Système visuel

- Palette sombre : fond `#0d151b`, surface `#131f27`, séparation `#2a3a44`, texte `#edf4f3`, secondaire `#9bafb8`, accent `#79d9be`.
- Palette claire : fond `#eef3f2`, surface blanche, texte `#183029`, accent `#167159`. Les thèmes Forge, Crypte et Arcane sont conservés.
- Typographie système, sans téléchargement : titres compacts, nombres tabulaires, métadonnées discrètes. Hiérarchie de 10–14 px pour les légendes et contrôles, 24–36 px pour les titres.
- Cartes à rayon 12 px, aperçu à 16 px, contrôles à 8 px. Espacement principal de 16, 24 et 32 px.
- Les couleurs élémentaires ne sont pas seules porteuses de sens : le nom de l’élément est affiché. Le déblocage est également nommé dans le libellé accessible.
- Mouvement limité au survol et aux transitions d’état, désactivé par `prefers-reduced-motion`.

## Composition

L’aperçu de collection présente des comptes globaux, sans les faire passer pour les résultats filtrés. Il apparaît seulement dans la vue Collection. La dernière sauvegarde est issue des figurines débloquées qui portent une date ; aucune activité, progression ou niveau n’est inventé.

Les vues Collection, Classement, Pièges et Vilains sont séparées des filtres. Les filtres jeu et élément s’affichent seulement là où ils s’appliquent. La collection place les figurines débloquées en premier. Le classement conserve ses colonnes et sa pagination, avec commandes de tri accessibles au clavier.

Sur mobile, le rail d’éléments devient horizontal, la grille conserve deux colonnes et le portail s’ouvre en panneau latéral. Le panneau de détail capture le focus clavier, se ferme avec Échap et restitue le focus à son déclencheur.

L’actualisation automatique interroge les données toutes les 15 secondes tant que l’onglet est visible. Ce n’est pas un indicateur de connexion à l’agent ou à Cemu. Le bouton d’actualisation reste disponible. Le portail porte explicitement le statut « Disposition locale ».

## Audit après modification

Scores de revue manuelle, indicatifs ; ils ne constituent pas une certification d’accessibilité.

| Dimension | /10 | Résultat et point de contrôle |
| --- | --- | --- |
| Couleurs | 8 | Jetons partagés dans `server/frontend/src/style.css`, couleurs élémentaires explicites dans `ToyCard.vue` |
| Typographie | 8 | Titres, légendes et métriques distingués dans `App.vue` et `CollectionOverview.vue` |
| Espacements | 8 | Grille et sections aérées ; quelques tailles historiques conservées dans les composants secondaires |
| Cohérence | 8 | Cartes, tableau, pièges et détails reprennent les mêmes surfaces et contrôles |
| Responsive | 8 | Contrôle navigateur desktop et mobile, aucune largeur de document excessive à 390 px |
| Thèmes | 8 | Jetons clair/sombre et thèmes élémentaires conservés ; pas de dépendance distante |
| Mouvement | 7 | Survol discret et respect de la réduction des animations |
| Accessibilité | 8 | Tri clavier, libellés des filtres, focus visible, lien d’évitement et gestion du focus des détails ; audit lecteur d’écran complet restant |
| Densité | 8 | Tableau compact ; aperçu réservé à la collection ; navigation mobile horizontalement défilable |
| Finition | 8 | États vides, erreurs, reprise, images de secours et clarification du portail |

Les labels les plus petits et les cibles historiques de certains composants secondaires restent des candidats à une passe d’accessibilité dédiée. L’identité visuelle conserve les données et les comportements métier existants.
