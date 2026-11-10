# ADR 0006 — La progression dans un fichier JSON, avant Room

- **Statut :** accepté
- **Date :** 2026-11-10

## Contexte

L'architecture prévoyait Room pour la progression. Pour l'instant, la progression est
petite (quelques centaines d'entrées) et toujours lue en entier.

## Décision

La progression est un objet immuable (`Progress`) enregistré en JSON dans
`filesDir/progress.json` par `FileProgressStore`. L'écriture passe par un fichier
temporaire renommé, pour ne jamais laisser un fichier à moitié écrit. Le domaine ne voit
qu'un port `ProgressStore`.

## Conséquences

- ✅ Simple, sans génération de code (KSP), testable sur la JVM.
- ✅ Le pattern Repository rend le passage à Room invisible pour le reste de l'app.
- ⚠️ À revoir quand il faudra des requêtes (statistiques détaillées, historique complet)
  ou une synchronisation avec un serveur.
