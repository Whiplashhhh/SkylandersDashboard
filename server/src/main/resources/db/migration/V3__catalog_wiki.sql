-- Titre de la page Skylanders Fandom d'une entree du roster.
--
-- Resolu hors ligne par tools/wiki_links.py et versionne dans catalog.json, jamais devine a
-- l'execution : les noms du pack sont francises, parfois colles (« Hoodsickle » pour « Hood
-- Sickle ») et porteurs de coquilles connues (« Drill Seargeant », « Cobra Candabra »). Une
-- regle appliquee a la volee produirait des liens morts sans le dire.
--
-- NULL = aucune page connue, et c'est un etat normal : les pieges, coffres et cristaux de
-- creation n'ont pas de page individuelle sur le wiki. L'interface n'affiche alors pas de
-- bouton, plutot qu'un lien qui tomberait sur une 404.
ALTER TABLE catalog_toy
    ADD COLUMN wiki text;

COMMENT ON COLUMN catalog_toy.wiki IS
    'Titre de page wiki verifie, ou NULL si aucune page ne correspond. Alimente par catalog.json.';
