# Images des méchants

Les 46 titres de `villains.json` figurent dans les liens de
https://skylanders.fandom.com/wiki/Villains#List_of_Villains (vérifié le 12 septembre 2026).
L'import utilise la première image de l'infobox de chaque page exacte, y compris
les suffixes `(villain)` et `(character)`, sans deviner une URL à partir du nom.

```sh
python3 tools/import_wiki_images.py --villains --browser
```

Ouvrir le lien local et lancer le téléchargement. Garder le terminal ouvert.
La même commande reprend l'import ; `--villains --apply` est le mode direct si
le CDN l'autorise. Rapport : `images/villain-wiki-images.json`.
Images : `images/villain_<nom normalisé>.<extension>` ; anciennes images sauvegardées
dans `images/.wiki-backup/`. Les illustrations des figurines et des pièges restent distinctes.

La collection charge le contenu actuel des pièges avec les cartes, puis le rafraîchit
au rythme habituel (15 secondes lorsque l'onglet est visible). Un piège occupé affiche
son méchant, un piège vide retrouve son image. La fiche et l'écran Pièges utilisent la
même sélection d'image ; la liste des méchants utilise les mêmes fichiers par nom.

L'identifiant brut du méchant doit être associé à son nom dans l'écran Pièges pour
qu'il soit reconnu. Un identifiant encore inconnu garde une image de remplacement,
sans lui attribuer arbitrairement un personnage. Cette association est conservée
pour les captures suivantes. L'agent et le backend doivent fonctionner pour recevoir
les changements des fichiers Cemu.
