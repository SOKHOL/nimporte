# ADR 0004 — Un rendu Markdown écrit à la main

- **Statut :** accepté
- **Date :** 2026-11-10

## Contexte

Les leçons, consignes et explications sont en Markdown. Il faut les afficher dans
Compose, avec du code coloré.

## Décision

Un petit analyseur Markdown en Kotlin pur (`:core:markdown`) qui ne gère que ce que le
contenu utilise : titres, paragraphes, gras, italique, code, liens, listes, citations,
tableaux, blocs de code. Une coloration syntaxique minimale (mots-clés, chaînes,
commentaires, nombres, annotations) l'accompagne. Les diagrammes Mermaid sont affichés
comme du code.

## Conséquences

- ✅ Aucune dépendance, testé sur la JVM en quelques millisecondes.
- ✅ Un bon exercice de parsing (voir le parcours *Langages et compilateurs*).
- ⚠️ Pas d'images ni de Markdown imbriqué complexe ; les diagrammes ne sont pas dessinés.

## Alternatives écartées

- Une bibliothèque Markdown pour Compose : plus complète, mais une dépendance de plus et
  moins de contrôle sur le rendu du code.
