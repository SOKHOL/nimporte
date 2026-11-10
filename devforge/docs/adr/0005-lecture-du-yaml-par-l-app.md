# ADR 0005 — L'app lit directement le YAML

- **Statut :** accepté (précise l'ADR 0002)
- **Date :** 2026-11-10

## Contexte

L'ADR 0002 prévoyait de compiler le contenu YAML en un pack JSON pendant le build.
C'est une tâche Gradle de plus à écrire et à maintenir, alors que le contenu est petit.

## Décision

Les fichiers de `devforge/content/` sont copiés tels quels dans les *assets* de l'APK
(`sourceSets["main"].assets.srcDir("../../content")`). Au démarrage, `YamlContentLoader`
(module `:core:data`) les lit avec snakeyaml-engine, convertit l'arbre YAML en JSON, puis
le décode avec kotlinx.serialization dans les classes du modèle.

## Conséquences

- ✅ Une seule source de vérité, aucun outil de build spécifique.
- ✅ Le même chargeur est testé sur la JVM avec le vrai contenu du dépôt : chaque exercice
  doit être réussi avec sa propre solution (`RealContentTest`).
- ⚠️ Le contenu est analysé à chaque démarrage, en arrière-plan. Avec des milliers
  d'exercices, il faudra revenir au pack JSON précompilé de l'ADR 0002.
