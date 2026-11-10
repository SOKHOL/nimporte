# DevForge 🔨

> L'app qui forge des ingénieurs logiciels complets : programmation, POO, design
> patterns, architecture, DevOps, cloud, SRE, sécurité. Avec des niveaux, des
> exercices, des challenges et un arbre de compétences.

**Statut :** phase 1 — l'app Android fonctionne hors-ligne avec le contenu pilote.

## Installer l'app sur ton téléphone

1. Ouvre la page **[DevForge — dernière version](https://github.com/SOKHOL/nimporte/releases/tag/devforge-latest)**
   depuis ton téléphone Android.
2. Télécharge `DevForge.apk`, ouvre-le, et autorise l'installation depuis cette source
   quand Android le demande.
3. Chaque push sur la branche reconstruit l'APK et le teste sur un émulateur Android
   avant de le publier. Une nouvelle version s'installe par-dessus l'ancienne, sans
   perdre ta progression.

Ce que fait l'app : carte des 31 parcours, leçons, sessions d'exercices (9 types),
boss chronométrés qui valident un niveau, révisions espacées (boîtes de Leitner),
défi du jour, XP et série de jours, profil avec radar de compétences.

## L'idée en 30 secondes

- **Un arbre de compétences** façon jeu de rôle : 31 parcours, 5 rangs chacun
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
| [02 — Curriculum](docs/02-curriculum.md) | 31 parcours × 5 niveaux, prérequis, projets, ordre conseillé |
| [03 — Architecture technique](docs/03-architecture-technique.md) | stack, modules, moteur d'exercices, backend, DevOps |
| [04 — Plan d'action](docs/04-plan-d-action.md) | phases, backlog, rythme, ressources, risques |
| [05 — Format du contenu](docs/05-format-du-contenu.md) | spécification YAML des parcours et des exercices |
| [06 — Du novice à l'expert](docs/06-novice-vers-expert.md) | ce qui distingue vraiment un expert, ordres de grandeur, questions réflexes |
| [ADR](docs/adr/) | décisions d'architecture |

## Ce qui existe déjà

- La carte des 31 parcours et de leurs prérequis : [`content/curriculum.yaml`](content/curriculum.yaml).
- 9 parcours pilotes détaillés compétence par compétence : POO, Design patterns,
  Architecture, System design, DevOps, **Gestion de la mémoire, JVM, Threads et
  concurrence, Métaprogrammation et AOP**.
- **20 leçons et 91 exercices**, couvrant 9 types d'exercices, et 5 boss :
  - mémoire : pile et tas, ramasse-miettes, fuites ;
  - JVM : bytecode, zones mémoire, cache des `Integer`, chargement des classes, ordre
    d'initialisation, compilateur JIT, stack traces, G1 et ZGC ;
  - concurrence : threads, conditions de course, `volatile`, interblocages, modèle
    mémoire Java, pools de threads, `CompletableFuture`, coroutines, threads virtuels ;
  - métaprogrammation : annotations, réflexion, proxies dynamiques, AOP, `@Transactional` ;
  - et les bases en POO, design patterns, architecture, system design et DevOps.

  Tous les extraits Kotlin ont été compilés et exécutés pour vérifier les réponses attendues.
- Un validateur (schémas + cohérence) avec ses tests, lancé par GitHub Actions.

## Structure

```
devforge/
├── docs/                      # vision, curriculum, architecture, plan, ADR
├── content/
│   ├── curriculum.yaml        # parcours et prérequis
│   ├── schema/                # JSON Schema du contenu
│   └── tracks/<parcours>/     # track.yaml, leçons (.md), exercices (.yaml)
├── android/                   # l'app (Kotlin + Jetpack Compose)
│   ├── app/                   # écrans, navigation, ViewModels
│   └── core/                  # model, markdown, domain, data — Kotlin pur, testé
└── tools/
    ├── validate_content.py    # validateur du contenu
    └── test_validate_content.py
```

## Compiler l'app

Avec Android Studio : ouvre le dossier `devforge/android`. En ligne de commande (JDK 17
et SDK Android installés) :

```bash
cd devforge/android
./gradlew :core:domain:test :core:data:test   # tests du cœur
./gradlew :app:assembleDebug                  # APK dans app/build/outputs/apk/debug/
```

## Valider le contenu

```bash
pip install -r devforge/tools/requirements.txt
python devforge/tools/validate_content.py
python -m unittest discover -s devforge/tools -v
```

## Prochaine étape

Phase 2 : rédiger beaucoup plus de contenu (une leçon et ~8 exercices par compétence),
puis notifications et widget pour le défi du jour. Voir le
[plan d'action](docs/04-plan-d-action.md).
