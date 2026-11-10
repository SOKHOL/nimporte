# ADR 0007 — Injection de dépendances à la main

- **Statut :** accepté
- **Date :** 2026-11-10

## Contexte

L'app a peu d'objets à assembler : un chargeur de contenu, un magasin de progression,
une horloge, et quelques ViewModels.

## Décision

Un `AppContainer` créé par l'`Application` construit et relie tout. Les ViewModels sont
créés avec `viewModelFactory { initializer { … } }`. Pas de Hilt ni de Koin pour l'instant.

## Conséquences

- ✅ Rien de magique : on voit exactement qui dépend de qui. C'est la meilleure façon
  de comprendre ce que fait un framework d'injection avant d'en utiliser un.
- ✅ Pas de génération de code, des builds plus rapides.
- ⚠️ À remplacer par Hilt quand le graphe d'objets grossira (backend, synchronisation,
  plusieurs sources de contenu).
