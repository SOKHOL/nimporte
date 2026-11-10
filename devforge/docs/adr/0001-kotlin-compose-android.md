# ADR 0001 — Kotlin et Jetpack Compose pour l'app

- **Statut :** accepté
- **Date :** 2026-11-10

## Contexte

DevForge doit tourner sur Android en priorité. Le projet a deux objectifs : être une
bonne app d'apprentissage, et être lui-même un terrain d'apprentissage (POO,
patterns, architecture, tests, DevOps).

## Décision

L'app est écrite en **Kotlin** avec **Jetpack Compose**. Les modules `:core:model` et
`:core:domain` sont en **Kotlin pur**, sans dépendance Android. Le backend sera aussi
en Kotlin (Ktor).

## Conséquences

- ✅ Un seul langage à maîtriser en profondeur, objet et fonctionnel.
- ✅ L'écosystème Android oblige à pratiquer MVVM, l'injection de dépendances et la modularisation.
- ✅ Le domaine en Kotlin pur se teste vite et pourra passer en Kotlin Multiplatform (iOS, web).
- ⚠️ iOS n'est pas couvert au début.

## Alternatives écartées

- **Flutter** : multiplateforme immédiat, mais Dart est peu utilisé ailleurs.
- **React Native** : TypeScript utile, mais architecture moins structurante pour apprendre.
- **PWA** : la plus portable, gardée comme plan B pour une version web.
