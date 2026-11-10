# 06 — Du novice à l'expert

Un expert ne connaît pas « plus de syntaxe ». Il a un **modèle mental** plus juste de
ce qui se passe vraiment : dans la machine, dans le système, dans l'équipe. Ce document
liste ces différences ; le curriculum est construit pour les combler une par une.

## Les 10 différences de fond

| # | Le novice… | L'expert… |
|---|---|---|
| 1 | apprend la syntaxe d'un langage | comprend le **modèle d'exécution** : mémoire, threads, compilation, réseau |
| 2 | cherche **la** bonne solution | cherche les **compromis** : il n'y a que des choix avec des coûts |
| 3 | optimise au feeling | **mesure** d'abord, connaît les ordres de grandeur |
| 4 | débogue en modifiant au hasard | formule une **hypothèse**, la teste, réduit le problème |
| 5 | écrit du code qui marche | écrit du code **facile à changer**, qui échoue proprement |
| 6 | pense au cas nominal | pense aux **cas limites** : pannes, concurrence, volumes, entrées hostiles |
| 7 | utilise la magie des frameworks | sait **comment** elle marche (proxies, réflexion, bytecode) et quand elle casse |
| 8 | ajoute du code | **enlève** du code : la simplicité est un objectif |
| 9 | lit un tutoriel quand ça casse | lit le **code source**, la spécification, la RFC |
| 10 | travaille seul | rend **les autres** meilleurs : revues, documentation, ADR, mentorat |

## Domaine par domaine

| Domaine | Le novice… | L'expert… | Parcours |
|---|---|---|---|
| Mémoire | pense que le GC rend les fuites impossibles | sait qu'un objet accessible depuis une racine n'est **jamais** libéré, et traque la fuite dans un heap dump | `memoire` |
| JVM | voit la JVM comme une boîte noire | lit le bytecode, comprend le JIT et le temps de chauffe, choisit son GC selon la latence visée | `jvm` |
| Threads | met `synchronized` partout, ou nulle part | raisonne en *happens-before*, préfère l'immutabilité et le confinement, sait qu'un bug de concurrence peut ne jamais apparaître en test | `concurrence` |
| Frameworks et AOP | s'étonne que `@Transactional` n'ait aucun effet | sait que c'est un proxy, que l'appel interne le contourne, et que les classes Kotlin sont `final` par défaut | `metaprog`, `frameworks` |
| Performance | optimise la boucle qui « a l'air lente » | profile, lit un flame graph, regarde le p99 et pas la moyenne | `performance` |
| Bases de données | laisse l'ORM générer 1 000 requêtes (N+1) | lit les plans d'exécution, conçoit les index, choisit le niveau d'isolation | `bdd` |
| Architecture | met tout dans un seul fichier, ou sur-architecture tout | adapte l'architecture au problème, protège le métier, documente ses décisions | `architecture` |
| Systèmes distribués | suppose que le réseau est fiable | conçoit pour la panne partielle : timeouts, retries avec backoff, idempotence | `systemes-distribues` |
| Débogage | ajoute des `println` partout | reproduit, isole, bisecte ; lit thread dumps et heap dumps | `debogage` |
| Sécurité | fait confiance aux entrées | modélise les menaces, applique le moindre privilège et la défense en profondeur | `securite` |
| Tests | teste après, pour la couverture | teste pour concevoir, teste le comportement et non l'implémentation | `clean-code` |
| Ops | « ça marche sur ma machine » | builds reproductibles, système observable, rollback en une minute, SLO | `devops`, `sre` |
| Code | écrit du code astucieux | écrit du code évident | tous |

## Les ordres de grandeur à connaître par cœur

Approximatifs, mais un expert sait instantanément qu'un appel réseau coûte autant que
des centaines de milliers d'accès au cache.

| Opération | Ordre de grandeur |
|---|---|
| Lecture dans le cache L1 | ~1 ns |
| Lecture dans le cache L2 | ~4 ns |
| Verrou non contesté (prise + relâche) | ~20 ns |
| Lecture en mémoire vive | ~100 ns |
| Lecture aléatoire sur SSD | ~100 µs |
| Aller-retour réseau dans un même datacenter | ~0,5 ms |
| Lecture séquentielle de 1 Mo sur SSD | ~0,5 ms |
| Déplacement de tête d'un disque dur | ~5 ms |
| Aller-retour Paris ↔ New York | ~75 ms |

Et quelques raccourcis : 2¹⁰ ≈ 10³ ; un jour ≈ 10⁵ secondes ; 1 million par jour ≈ 12 par seconde.

## Les questions réflexes de l'expert

Avant de valider une conception ou une pull request :

- Que se passe-t-il si ça **échoue à mi-chemin** ? Peut-on relancer sans risque ?
- Que se passe-t-il si **deux requêtes** arrivent en même temps ?
- Et si le volume est **multiplié par 100** ?
- Et si l'entrée est **malveillante** ?
- Comment saurai-je que ça **marche en production** ? Quelle métrique, quelle alerte ?
- Comment **revient-on en arrière** ?
- Qui d'autre **dépend** de ce code ? Qu'est-ce que je casse ?
- Quel sera le **coût de maintenance** dans deux ans ?
- Est-ce qu'on a vraiment **besoin** de ça ?

## Comment DevForge t'y amène

- **Les rangs** suivent cette progression : on reconnaît (Novice), on applique (Apprenti),
  on analyse (Compagnon), on conçoit (Expert), on transmet (Maître).
- **Les parcours « sous le capot »** (`ordinateur`, `memoire`, `jvm`, `concurrence`,
  `metaprog`, `frameworks`, `compilation`, `performance`) construisent le modèle mental.
- **Les exercices de type « pourquoi »** (prédire la sortie, trouver le bug, revue de code)
  entraînent à voir ce que le code fait vraiment, et pas ce qu'il a l'air de faire.
- **Les projets d'expert** (mini-conteneur d'injection, pool de threads, interpréteur,
  mini-Redis, Raft) obligent à reconstruire la magie soi-même.
- **Le rang Maître** demande d'expliquer : on ne maîtrise vraiment que ce qu'on sait enseigner.
