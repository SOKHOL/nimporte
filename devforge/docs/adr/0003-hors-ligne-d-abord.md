# ADR 0003 — Hors-ligne d'abord

- **Statut :** accepté
- **Date :** 2026-11-10

## Contexte

L'app sert à des sessions courtes, souvent dans les transports. Le réseau n'est pas
toujours là, et un backend coûte du temps et de l'argent dès le premier jour.

## Décision

Jusqu'à la phase 3, l'app fonctionne **entièrement hors-ligne** : contenu embarqué,
progression stockée localement (Room). Le backend arrive ensuite pour ce qui en a
vraiment besoin : exécution de code, synchronisation entre appareils, mentor IA.
Même alors, la source de vérité de la progression reste locale, synchronisée en
arrière-plan.

## Conséquences

- ✅ App utilisable dès la phase 1, sans serveur ni coût.
- ✅ Rapide : aucune attente réseau pendant une session.
- ⚠️ Les exercices de type `code` attendent le backend (phase 3).
- ⚠️ La synchronisation devra gérer les conflits (ex. : la tentative la plus récente gagne).
