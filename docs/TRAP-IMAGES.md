# Images des pièges vides

Source : https://skylanders.fandom.com/wiki/Trap#Gallery

Depuis la racine du projet :

```sh
python3 tools/import_wiki_images.py --traps --browser
```

Ouvrir le lien local affiché dans le navigateur habituel et lancer le téléchargement.
Laisser le terminal ouvert jusqu'à la fin. La même commande reprend un import interrompu.
Le mode `--traps --apply` permet le téléchargement direct si le CDN l'autorise.

Au 12 septembre 2026, 41 des 59 pièges du catalogue correspondent à une image
individuelle de cette galerie. Les 18 absents sont listés dans le terminal et dans
`images/trap-wiki-images.json` (`withoutPage`). Aucune image de prototype ou d'un
autre modèle n'est utilisée pour combler les absences. Les variantes Legendary
et Ultimate restent distinctes.

Les correspondances de noms sont explicites dans `tools/trap_wiki_images.py`.
L'import retire le redimensionnement des miniatures, installe les images sous les
identifiants du catalogue et sauvegarde les anciennes dans `images/.wiki-backup/`.
Les pièges vides utilisent ces images dans la vue Pièges ; les pièges occupés
conservent l'affichage du méchant prévu par l'application. Cet import ne récupère
aucune image de méchant et ne modifie aucun fichier de sauvegarde.
