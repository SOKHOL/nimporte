# ADR 0002 — Le contenu en YAML dans Git

- **Statut :** accepté
- **Date :** 2026-11-10

## Contexte

DevForge aura des milliers d'exercices. Il faut pouvoir les écrire vite, les relire,
les versionner, les valider automatiquement et accepter des contributions.

## Décision

- Les exercices sont écrits en **YAML**, les leçons en **Markdown** avec un en-tête YAML.
- Le tout vit dans **Git**, dans `content/`.
- Le format est défini par des **JSON Schema** et vérifié en CI par un validateur qui
  contrôle aussi la cohérence (identifiants, compétences, scénarios…).
- Au build, le contenu est compilé en un **pack JSON** embarqué dans l'app.

## Conséquences

- ✅ Relecture et historique via les pull requests ; contributions faciles.
- ✅ Les erreurs de contenu sont détectées avant d'arriver dans l'app.
- ✅ Les schémas donnent l'autocomplétion dans les éditeurs.
- ⚠️ Pas d'interface d'édition graphique : il faut connaître YAML (et ses pièges).
- ⚠️ Mettre à jour le contenu demande une nouvelle version de l'app, jusqu'aux packs
  téléchargeables de la phase 3.

## Alternatives écartées

- **CMS headless** : interface agréable, mais dépendance externe et pas de revue par PR.
- **Base de données** : contenu difficile à relire et à versionner.
- **JSON écrit à la main** : trop verbeux, pas de commentaires, pénible pour du texte long.
