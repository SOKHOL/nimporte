---
id: performance.n2.les-outils
title: Profileurs, microbenchmarks et flame graphs
skills: [profileurs, microbenchmarks, flame-graphs]
duration_min: 17
---

# Profileurs, microbenchmarks et flame graphs

## Les profileurs

Un **profileur** mesure où un programme passe son temps (ou alloue sa mémoire). Sortie réelle de
**cProfile**, le profileur intégré à Python, sur une fonction `rapport()` qui dédoublonne une
liste puis fait quelques calculs :

```
   ncalls  tottime  cumtime  filename:lineno(function)
        1    0.000    0.084  mesures1.py:26(rapport)
        1    0.062    0.063  mesures1.py:13(doublons_lent)
        1    0.000    0.021  {built-in method builtins.sum}
 109455/5    0.021    0.021  mesures1.py:(fib)
```

- **`cumtime`** (temps cumulé) : le temps passé dans la fonction **et dans tout ce qu'elle
  appelle**. On descend l'arbre par là.
- **`tottime`** (temps propre) : le temps passé **dans le corps** de la fonction elle-même. C'est
  là qu'on trouve le code coupable.
- **`ncalls`** : `109455/5` veut dire 109 455 appels au total, dont 5 « primitifs » (non
  récursifs) : la fonction récursive `fib` est appelée des dizaines de milliers de fois.

Lecture : `doublons_lent` prend 62 ms sur 84, soit les trois quarts du temps, dans son propre
code. C'est elle qu'il faut regarder en premier (niveau 3 : elle est en O(n²)).

Deux familles de profileurs :

| | Par **instrumentation** | Par **échantillonnage** |
|---|---|---|
| Principe | ajoute du code à chaque entrée et sortie de fonction | regarde périodiquement ce que fait chaque thread |
| Précision | compte exact des appels | statistique |
| Surcoût | élevé, et il **déforme** les petites fonctions | faible (quelques %), utilisable en production |
| Exemples | cProfile, profileurs « tracing » des IDE | JFR, async-profiler, py-spy, perf, pprof |

Le profileur par échantillonnage de JFR a désigné, dans le parcours Débogage, une `Regex`
recompilée à chaque appel (×6,4 après correction). Penser aussi à profiler les **allocations**
(qui crée le plus d'objets ?) et les **attentes** (verrous, entrées-sorties) : un programme lent
n'est pas forcément un programme qui calcule.

## Les microbenchmarks et leurs pièges

Un **microbenchmark** mesure un petit morceau de code isolé. Sur la JVM, c'est plein de pièges.
Mesure réelle : sommer 10 millions d'entiers, six fois de suite, dans une `List<Int>` et dans un
`IntArray`.

```
tour 1 : List<Int> 37 ms, IntArray 9 ms
tour 2 : List<Int> 37 ms, IntArray 5 ms
tour 3 : List<Int> 45 ms, IntArray 8 ms
tour 4 : List<Int> 37 ms, IntArray 5 ms
tour 5 : List<Int> 23 ms, IntArray 4 ms
tour 6 : List<Int> 23 ms, IntArray 4 ms
```

Les premiers tours sont plus lents : le code passe de l'interpréteur aux compilateurs JIT (C1
puis C2) au fil des exécutions, et les mesures varient (le tour 3 a peut-être subi une pause du
GC). Une mesure unique, ou les premiers tours seulement, aurait donné un résultat faux.

Les pièges classiques :

- **pas de préchauffage** : on mesure l'interpréteur au lieu du code optimisé ;
- **code mort** : si le résultat n'est pas utilisé, le JIT supprime le calcul et on mesure
  une boucle vide ;
- **constantes repliées** : des entrées connues à la compilation sont précalculées ;
- **une seule exécution** : pas de distribution, pas d'idée du bruit ;
- **un environnement non représentatif** : portable sur batterie, autres programmes actifs,
  données trop petites (tout tient dans le cache du processeur) ;
- `System.currentTimeMillis()` pour mesurer (résolution grossière, horloge qui peut sauter) au
  lieu de `System.nanoTime()`.

Sur la JVM, **JMH** gère tout cela (préchauffage, plusieurs *forks*, `Blackhole` pour consommer
les résultats, statistiques). En Python, `timeit` ; en Go, `testing.B` ; en Rust, `criterion` ;
en JavaScript, `tinybench`. Et un microbenchmark ne dit pas si la différence **compte** dans
l'application : 30 ms gagnés sur une opération faite une fois par jour n'intéressent personne.

## Lire un flame graph

Un **flame graph** (Brendan Gregg) affiche des milliers de piles d'appels échantillonnées en une
seule image :

```
[            main                                                         ]
[        traiterRequete                              ][  journaliser   ]
[ validerEmail                 ][ calculerPrix     ]
[ Regex.<init>  ][ Regex.matches]
[Pattern.compile]
```

- chaque **rectangle** est une fonction, posé **sur** celle qui l'a appelée (la base est en bas
  ou en haut selon les outils) ;
- la **largeur** est la part du temps où la fonction était présente dans la pile (elle-même et
  ce qu'elle appelle) ; l'axe horizontal **n'est pas** le temps qui passe, les fonctions sont
  rangées par ordre alphabétique ;
- on cherche les **plateaux larges** au sommet : une fonction qui occupe beaucoup de largeur
  **sans** enfants au-dessus passe son temps **elle-même**. Ici, `Pattern.compile` sous
  `validerEmail`.

Les variantes : le flame graph des **allocations**, celui des temps **hors CPU** (*off-CPU* :
attentes de verrous, de disque, de réseau), et le graphe **différentiel** qui compare deux profils
(avant et après un déploiement). async-profiler, JDK Mission Control, Pyroscope, Speedscope,
Firefox Profiler et l'onglet Performance de Chrome les produisent.

## À retenir

- Profileur : `cumtime` pour descendre l'arbre, `tottime` pour trouver le coupable (62 ms sur 84 dans `doublons_lent`) ; échantillonnage en production ; profiler aussi allocations et attentes.
- Microbenchmarks : préchauffage (37 ms puis 23 ms mesurés), code mort, constantes, répétitions, environnement ; JMH sur la JVM ; et vérifier que le gain compte.
- Flame graph : la largeur est la part du temps, l'axe horizontal n'est pas le temps ; chercher les plateaux larges au sommet.
