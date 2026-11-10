# 01 — Idées et fonctionnalités

## 1. Le concept

**DevForge** = la régularité de **Duolingo** + les exercices de **Exercism / Codewars**
+ la carte complète du métier de **roadmap.sh** + les pannes réelles de **SadServers**
+ un **mentor** qui t'explique tes erreurs.

Trois principes pédagogiques guident tout le reste :

1. **Actif, pas passif.** Une leçon dure 3 à 6 minutes et elle est toujours suivie
   d'exercices. On apprend en répondant, pas en lisant.
2. **Petit et fréquent.** 20 minutes par jour valent mieux que 3 heures le dimanche.
   Une session = 5 à 10 exercices.
3. **Ancré dans la durée.** Répétition espacée, exercices mélangés entre compétences,
   et mise en pratique sur de vrais projets.

Et un principe de projet : **tu construis l'app toi-même**. La construire mobilise
presque tout le curriculum (voir [le plan d'action](04-plan-d-action.md)).

## 2. Progression

### L'arbre de compétences

- **31 parcours** (POO, Design patterns, JVM, Mémoire, Concurrence, AOP, DevOps, System design…) reliés par des
  **prérequis**. Exemple : *Design patterns* se débloque une fois *POO niveau 2* validé.
- Chaque parcours a **5 niveaux**, qui sont aussi des rangs :
  **Novice → Apprenti → Compagnon → Expert → Maître**.
- Chaque niveau contient des **compétences** (« encapsulation », « Strategy »…),
  chaque compétence a ses **leçons** et ses **exercices**.
- Un niveau se valide en battant son **boss** : un examen chronométré qui mélange
  toutes les compétences du niveau.

### La maîtrise

Chaque compétence a une jauge de maîtrise (0 à 100 %) calculée à partir des
dernières tentatives et de la répétition espacée. Une compétence non pratiquée
redescend doucement : c'est le signal qu'il faut la réviser.

Le profil affiche un **radar** sur 8 axes : Code, Conception, Architecture, Systèmes,
Ops, Data, Sécurité, Collaboration.

## 3. Types d'exercices

Pensés d'abord pour un écran de téléphone : on touche, on glisse, on tape peu.

| Type | Ce que tu fais | Exemple | Phase |
|---|---|---|---|
| `qcm` | choisir une ou plusieurs réponses | « Quel principe SOLID est violé ? » | 1 |
| `predict-output` | prédire ce qu'affiche un programme | égalité vs identité en Kotlin | 1 |
| `find-bug` | toucher la ligne fautive | collection modifiable exposée, secret dans un Dockerfile | 1 |
| `parsons` | remettre des lignes de code dans l'ordre (avec des pièges) | écrire un Singleton thread-safe | 1 |
| `fill-blanks` | compléter des trous avec une banque de mots | Dockerfile multi-étapes | 1 |
| `match` | relier des paires | pattern ↔ problème résolu | 2 |
| `code-review` | signaler les problèmes d'un code et leur catégorie | injection SQL, God class, requêtes N+1 | 2 |
| `scenario` | prendre des décisions dans un incident ramifié | « le pod redémarre en boucle à 3 h du matin » | 2 |
| `design-challenge` | concevoir une architecture, puis s'évaluer avec une grille | raccourcisseur d'URL | 2 |
| `code` | écrire du vrai code, validé par des tests cachés | katas FizzBuzz → Bowling → Gilded Rose | 3 |
| `lab` | travailler dans un vrai terminal distant | réparer un nginx qui ne démarre pas | 4 |
| `explain` | expliquer un concept avec ses mots, corrigé par l'IA | « explique CAP à un junior » | 4 |

Des exemples réels de chaque type des phases 1-2 sont dans [`content/tracks/`](../content/tracks/).

## 4. Challenges

- **Défi du jour** : un exercice sur tes compétences en cours + une révision.
  Bonus d'XP, notification et widget sur l'écran d'accueil.
- **Boss de fin de niveau** : 10 à 15 exercices mélangés, chronométrés, 80 % requis.
  Rejouable, avec des variantes tirées au hasard.
- **Katas** : exercices de code classiques à refaire régulièrement, avec des
  contraintes (en TDD, sans `if`, en moins de 10 minutes, *object calisthenics*).
- **Katas de refactoring** : Gilded Rose, Tennis, Trivia. Du code legacy à
  assainir sans casser les tests.
- **Astreinte** (incidents de prod) : scénario ramifié avec logs, métriques et
  décisions. Le score dépend de la justesse et du nombre d'étapes. Il se termine
  par un post-mortem à rédiger.
- **System design** : exigences, estimation de charge, choix des composants,
  grille d'évaluation, solution de référence commentée.
- **Chasse aux bugs** : série de snippets contre la montre, avec combo.
- **Mode entretien** : simulation d'entretien technique (algo + design +
  questions comportementales) avec minuteur.
- **Projets fil rouge** : un projet par rang, à réaliser hors de l'app sur GitHub,
  avec une checklist d'acceptation (voir [le curriculum](02-curriculum.md#projets-fil-rouge)).

## 5. Gamification

- **XP** selon la difficulté (10 à 80), bonus au premier essai, malus quand on
  utilise des indices.
- **Série quotidienne** (streak) avec des « gels » à gagner pour les jours sans.
- **Badges** : *Gang of Four* (23 patterns maîtrisés), *Pompier* (10 incidents
  résolus), *Zéro N+1*, *Monsieur Propre* (5 katas de refactoring),
  *Rollback éclair* (incident résolu en 2 étapes)…
- **Statistiques** : temps passé, taux de réussite, compétences fragiles à revoir.
- **Ligues hebdomadaires** entre amis (phase 5, nécessite le backend).

## 6. Pédagogie

- **Répétition espacée** (SM-2, puis FSRS) : chaque compétence et chaque exercice
  raté revient au bon moment, juste avant l'oubli.
- **Indices progressifs** : une piste, puis plus précis, puis presque la solution.
  Chacun coûte de l'XP.
- **Explication systématique** après chaque réponse, juste ou fausse.
- **Fiches mémo** par compétence, consultables hors-ligne.
- **Exercices mélangés** (*interleaving*) dans les révisions : plus difficile sur le
  moment, bien plus efficace à long terme.
- **Journal d'apprentissage** : noter ce que tu as compris et ce qui bloque.

## 7. Mentor IA (phase 4)

Via l'API Claude, toujours appelée par le backend (jamais directement depuis l'app,
sinon la clé d'API fuiterait) :

- **indices contextuels** qui guident sans donner la solution ;
- **revue de code** des exercices `code` et des projets fil rouge ;
- **correction des réponses ouvertes** (`explain`, `design-challenge`) avec la grille ;
- **exercices de révision** générés sur tes points faibles, relus avant d'entrer
  dans le contenu officiel.

Garde-fous : pas de solution complète tant que tu n'as pas essayé, quota d'appels,
aucune donnée personnelle envoyée.

## 8. Idées bonus

- **Lecture de code réel** : extraits commentés de projets open source (OkHttp,
  Retrofit, Kubernetes…), avec la question « quel pattern reconnais-tu ? ».
- **Flashcards de commandes** : git, docker, kubectl, linux.
- **Carnet d'ADR** : rédiger tes propres décisions d'architecture sur tes projets.
- **Mode « explique-moi à 3 niveaux »** : le même concept pour un débutant, un
  développeur confirmé et un expert.
- **Contenu contributif** : le contenu est du YAML dans Git, donc des amis peuvent
  proposer des exercices par pull request, validés automatiquement par la CI.
- **Hors-ligne complet** : tout le contenu est embarqué dans l'app.
