# 04 — Plan d'action

## Principes

1. **Tranches verticales.** Chaque phase livre une app utilisable, pas une couche technique isolée.
2. **Le contenu d'abord.** Un bon exercice sur papier vaut plus qu'un moteur vide.
3. **Chaque fonctionnalité est une leçon.** La colonne « Tu apprends » relie le
   travail de construction au curriculum.
4. **Utilise ce que tu construis.** Dès la fin de la phase 1, tu fais ta session
   quotidienne dans ta propre app.
5. **Définition de « terminé »** pour chaque tâche : tests écrits, CI verte, code relu
   (relis ta PR le lendemain, ou demande une revue), ADR si la décision est structurante.

## Vue d'ensemble

Durées indicatives pour **8 à 10 heures par semaine**.

| Phase | Durée | Résultat | Tu apprends |
|---|---|---|---|
| 0. Fondations | 2 sem. | curriculum, format de contenu, contenu pilote | modélisation, JSON Schema, CI |
| 1. MVP Android | 6 sem. | app hors-ligne utilisable au quotidien | Kotlin, Compose, MVVM, Clean Architecture, Room, tests |
| 2. Contenu et motivation | 6 sem. | boss, défi du jour, répétition espacée, badges | algorithmes, WorkManager, patterns appliqués |
| 3. Backend et vrai code | 8 sem. | exécution de code, comptes, synchronisation | backend, SQL, REST, Docker, CI/CD, sécurité |
| 4. Ops, architecture et IA | 8 sem. | mentor IA, labs, Kubernetes, observabilité | Kubernetes, Terraform, GitOps, SRE, LLM |
| 5. Ouverture | continu | Play Store, iOS/web, communauté | produit, fiabilité, communauté |

Environ **30 semaines** pour les phases 0 à 4.

## Phase 0 — Fondations (2 semaines)

**Objectif :** figer le référentiel et le format, rédiger un premier parcours.

- Vision, curriculum, architecture et plan (ce dossier) ✅
- Format YAML, JSON Schema, validateur, tests et CI ✅
- Contenu pilote : 148 exercices, 33 leçons et 5 boss ; Mémoire, JVM, Concurrence et Métaprogrammation complets ✅
- Terminer *POO niveau 1* : une leçon par compétence, ~8 exercices par compétence, le boss
- Maquettes basse fidélité (papier ou Figma) : carte des parcours, session, correction, profil
- Choisir le nom définitif et l'icône

**Terminé quand :** le niveau 1 de POO est jouable « sur papier » de bout en bout.

## Phase 1 — MVP Android hors-ligne (6 semaines) — ✅ première version livrée

**Objectif :** une app installée sur ton téléphone, que tu utilises tous les jours.

**État :** l'app existe dans [`devforge/android/`](../android/) et la CI publie l'APK.
Elle va même plus loin que prévu : les 9 types d'exercices des phases 1 et 2, les boss,
les révisions espacées, le défi du jour et le radar sont déjà là. Écarts assumés avec
le plan : progression en JSON plutôt que Room, injection manuelle plutôt que Hilt,
YAML lu par l'app plutôt que compilé (ADR 0005 à 0007).

- Projet Android multi-module (Kotlin, Compose, Material 3, version catalog)
- `:content-compiler` : YAML → JSON, embarqué dans l'APK
- Écrans : carte des parcours, leçon, session d'exercices, correction, profil
- 5 types d'exercices : `qcm`, `predict-output`, `find-bug`, `parsons`, `fill-blanks`
- XP, niveaux, série quotidienne ; progression sauvegardée dans Room
- CI : lint, tests, build de l'APK

**Tu apprends :** Kotlin, Compose, MVVM, Clean Architecture, injection de dépendances,
Room, coroutines et Flow, tests ; patterns Strategy, Factory, State, Repository, Specification.

**Terminé quand :** tu as fait ta session quotidienne dans l'app 7 jours de suite.

## Phase 2 — Contenu et motivation (6 semaines)

- Types `match`, `code-review`, `scenario`, `design-challenge`
- Boss de fin de niveau ; défi du jour avec notification et widget
- Répétition espacée : SM-2 d'abord, FSRS ensuite (Strategy !)
- Badges, radar de compétences, statistiques
- Contenu : POO 1-3, Design patterns 1-3, Clean code 1-2, Git 1-2

**Tu apprends :** algorithmes de répétition espacée, WorkManager, notifications,
Glance, et tous les patterns de l'app appliqués pour de vrai.

## Phase 3 — Backend et vrai code (8 semaines)

- API Ktor + PostgreSQL + Flyway, conteneurisée
- Runner de code isolé : type `code` avec tests cachés, katas
- Comptes (OpenID Connect) et synchronisation de la progression
- Packs de contenu téléchargeables
- Déploiement : Docker Compose sur une petite VM, HTTPS, CI/CD complète

**Tu apprends :** backend, SQL, REST, authentification, Docker, CI/CD, Linux
serveur, sécurité (threat model du runner).

## Phase 4 — Ops, architecture et IA (8 semaines)

- Mentor IA via l'API Claude : indices, revue de code, correction de `explain` et `design-challenge`
- Type `lab` : terminal distant éphémère pour les exercices Linux et Docker
- Backend migré sur Kubernetes (k3s) + Helm + Terraform + Argo CD
- Observabilité : OpenTelemetry, Prometheus, Grafana, Loki ; SLO et alertes
- Contenu : DevOps 1-4, SRE 1-3, Architecture 1-3, System design 1-3, Sécurité 1-2

**Tu apprends :** Kubernetes, IaC, GitOps, observabilité, SRE, intégration d'un LLM.

## Phase 5 — Ouverture (en continu)

- Publication sur Google Play ; iOS et web avec Compose Multiplatform
- Ligues hebdomadaires ; contributions de contenu par pull request
- Chaos engineering sur ta propre prod ; post-mortems publics

## Backlog des phases 0 et 1

À transformer en issues GitHub, une par ligne.

| # | Tâche | Phase |
|---|---|---|
| P0-01 | Rédiger la leçon et ~8 exercices pour chacune des 5 compétences de POO niveau 1 | 0 |
| P0-02 | Compléter le boss de POO niveau 1 avec des exercices dédiés | 0 |
| P0-03 | Maquettes basse fidélité des 4 écrans principaux | 0 |
| P0-04 | ADR 0004 : choix de la bibliothèque de rendu Markdown dans Compose | 0 |
| P1-01 ✅ | Créer le projet Android multi-module (Kotlin, Compose, Material 3, version catalog) | 1 |
| P1-02 ✅ | CI GitHub Actions : tests unitaires, build de l'APK, pré-version | 1 |
| P1-03 ✅ | Lecture du contenu YAML par l'app (ADR 0005), testée sur le vrai contenu | 1 |
| P1-04 ✅ | `:core:model` : Track, Level, Skill, Lesson, Exercise (sealed) | 1 |
| P1-05 ✅ | `:core:data` : chargeur de contenu, adaptateur des assets | 1 |
| P1-06 ✅ | Progression enregistrée en JSON (ADR 0006) | 1 |
| P1-07 ✅ | Évaluateurs des 10 types d'exercices (Strategy), testés | 1 |
| P1-08 ✅ | Carte des parcours, prérequis conseillés (Specification) | 1 |
| P1-09 ✅ | Lecteur de leçons Markdown avec coloration syntaxique (ADR 0004) | 1 |
| P1-10 ✅ | Session d'exercices (machine d'état) et correction avec explication | 1 |
| P1-11 ✅ | Composants des 9 types d'exercices (Parsons au toucher, avec annulation) | 1 |
| P1-12 ✅ | XP, niveaux, boss, série quotidienne, révisions, défi du jour | 1 |
| P1-13 ✅ | Écran profil avec radar | 1 |
| P1-14 ✅ | Test de fumée Compose sur émulateur en CI (l'APK n'est publié que s'il passe) | 1 |
| P1-15 | ktlint et detekt en CI ; tests Compose de chaque type d'exercice | 1 |

## Rythme hebdomadaire conseillé

| Quand | Quoi |
|---|---|
| Chaque jour, 20 min | une session d'exercices (sur papier en phase 0, dans l'app ensuite) |
| 3 soirs × 1 h 30 | construire DevForge : une tâche du backlog à la fois |
| Le week-end, 2 h | rédiger du contenu : une compétence = une leçon + ~8 exercices |
| Fin de semaine, 15 min | rétro : ce que j'ai appris, ce qui bloque, la priorité suivante |

## Ressources

**Livres**

- *The Pragmatic Programmer* — Hunt et Thomas
- *Head First Design Patterns*, puis *Design Patterns* du Gang of Four
- *Refactoring* — Martin Fowler
- *Clean Architecture* — Robert C. Martin (à lire avec un esprit critique)
- *Domain-Driven Design Distilled* — Vaughn Vernon
- *Designing Data-Intensive Applications* — Martin Kleppmann
- *The Phoenix Project*, puis *Accelerate*
- *Site Reliability Engineering* — Google (gratuit en ligne)
- *Kotlin in Action*

**Sites**

- refactoring.guru — patterns et refactoring, illustrés et traduits en français
- roadmap.sh — cartes de compétences par métier
- Exercism et Codewars — katas
- SadServers — pannes Linux à réparer
- *The System Design Primer* (GitHub)
- 12factor.net
- OWASP Top 10
- developer.android.com — guide d'architecture et l'app exemple *Now in Android*

## Risques et parades

| Risque | Parade |
|---|---|
| Le contenu est le goulot d'étranglement (des milliers d'exercices) | rédiger d'abord la saison en cours ; gabarits ; brouillons générés par l'IA puis relus |
| Construire au lieu d'apprendre | phases 0 et 1 courtes ; utiliser l'app au plus tôt |
| Perte de motivation | série quotidienne, petits livrables, journal d'apprentissage, montrer l'app à des amis |
| Sécurité du runner de code | isolation stricte, pas de réseau, quotas, threat model avant la mise en ligne |
| Coûts du cloud et de l'IA | une seule petite VM au début, quotas d'IA, alertes de budget |
| Droits d'auteur | contenu original ; citer les sources ; ne pas recopier d'exercices existants |
