# DevForge 🔨

> L'app qui forge des ingénieurs logiciels complets : programmation, POO, design
> patterns, architecture, DevOps, cloud, SRE, sécurité. Avec des niveaux, des
> exercices, des challenges et un arbre de compétences.

**Statut :** conception (phase 0). Ce dossier contient la vision, le curriculum,
l'architecture, le plan d'action, et un premier contenu pilote validé automatiquement.

## L'idée en 30 secondes

- **Un arbre de compétences** façon jeu de rôle : 17 parcours, 5 rangs chacun
  (Novice → Apprenti → Compagnon → Expert → Maître), avec des prérequis entre parcours.
- **Des exercices courts et actifs**, pensés pour le téléphone : QCM, prédire la sortie,
  trouver le bug, remettre le code dans l'ordre, compléter le code, relier un pattern à
  son problème, revue de code, incidents de prod interactifs, défis de system design.
- **Des challenges** : défi du jour, boss de fin de niveau, katas, astreintes simulées,
  projets fil rouge.
- **La répétition espacée** pour ne rien oublier, et un **mentor IA** pour les indices
  et la revue de code (phase 4).
- **Double apprentissage** : tu utilises l'app pour apprendre… et tu la construis
  toi-même. La construire (Kotlin, Clean Architecture, patterns, backend, Docker,
  CI/CD, Kubernetes, observabilité) couvre presque tout le curriculum.

## Documents

| Document | Contenu |
|---|---|
| [01 — Idées et fonctionnalités](docs/01-idees-et-fonctionnalites.md) | concept, types d'exercices, challenges, gamification, IA |
| [02 — Curriculum](docs/02-curriculum.md) | 17 parcours × 5 niveaux, prérequis, projets fil rouge, ordre conseillé |
| [03 — Architecture technique](docs/03-architecture-technique.md) | stack, modules, moteur d'exercices, backend, DevOps |
| [04 — Plan d'action](docs/04-plan-d-action.md) | phases, backlog, rythme, ressources, risques |
| [05 — Format du contenu](docs/05-format-du-contenu.md) | spécification YAML des parcours et des exercices |
| [ADR](docs/adr/) | décisions d'architecture |

## Ce qui existe déjà

- La carte des 17 parcours et de leurs prérequis : [`content/curriculum.yaml`](content/curriculum.yaml).
- 5 parcours pilotes détaillés compétence par compétence : POO, Design patterns,
  Architecture, System design, DevOps.
- **20 exercices et 2 leçons**, couvrant 9 types d'exercices. Tous les extraits Kotlin
  ont été compilés et exécutés pour vérifier les réponses attendues.
- Un validateur (schémas + cohérence) avec ses tests, lancé par GitHub Actions.

## Structure

```
devforge/
├── docs/                      # vision, curriculum, architecture, plan, ADR
├── content/
│   ├── curriculum.yaml        # parcours et prérequis
│   ├── schema/                # JSON Schema du contenu
│   └── tracks/<parcours>/     # track.yaml, leçons (.md), exercices (.yaml)
└── tools/
    ├── validate_content.py    # validateur
    └── test_validate_content.py
```

## Valider le contenu

```bash
pip install -r devforge/tools/requirements.txt
python devforge/tools/validate_content.py
python -m unittest discover -s devforge/tools -v
```

## Prochaine étape

Terminer le niveau 1 de POO puis créer le projet Android : voir le
[backlog](docs/04-plan-d-action.md#backlog-des-phases-0-et-1).
