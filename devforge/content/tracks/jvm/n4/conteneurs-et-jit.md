---
id: jvm.n4.conteneurs-et-jit
title: La JVM en conteneur et le JIT en pratique
skills: [jvm-conteneurs, optimisations-jit]
duration_min: 10
---

# La JVM en conteneur et le JIT en pratique

## La JVM dans un conteneur

Depuis Java 10, la JVM sait qu'elle tourne dans un conteneur et lit ses limites de
mémoire et de CPU (cgroups). Mais ses choix par défaut surprennent :

- **Le tas maximal vaut 25 % de la mémoire disponible.** Vérifié sur une machine de 16 Go :
  `MaxHeapSize = 3586129920` (3,3 Go) et `MaxRAMPercentage = 25`. Dans un conteneur
  limité à 2 Go, ton application n'aura que 512 Mo de tas, même si elle ne fait rien
  d'autre. Règle courante : `-XX:MaxRAMPercentage=75`.
- **Le tas n'est pas toute la mémoire** : piles des threads, metaspace, code cache, buffers
  directs, mémoire du GC… Un tas réglé à 100 % de la limite du conteneur provoque un
  **OOMKilled** (le noyau tue le processus, sans aucune trace Java).
- **Le nombre de CPU** visibles détermine le choix du collecteur, le nombre de threads du
  GC et du JIT, la taille du pool commun. Avec moins de 2 CPU et 1792 Mo, la JVM choisit
  même le collecteur Serial.

Pour savoir ce que la JVM a décidé : `java -XX:+PrintFlagsFinal -version` dans le
conteneur, ou `jcmd <pid> VM.flags`.

## L'escape analysis, mesurée

```kotlin
class Point(val x: Int, val y: Int)

fun distanceManhattan(n: Int): Long {
    var total = 0L
    for (i in 0 until n) {
        val p = Point(i, i + 1)        // un objet par tour de boucle ?
        total += p.x + p.y
    }
    return total
}
```

Mesuré pour ce cours (100 millions de tours, après préchauffage) :

| Configuration | Durée | Pauses du GC |
|---|---|---|
| Par défaut | **31 ms** | **0** |
| `-XX:-DoEscapeAnalysis` | 343 ms | 18 |

Avec l'escape analysis, le JIT voit que `p` ne sort jamais de la boucle : il ne crée
**aucun objet** et garde `x` et `y` dans des registres. Sans elle, 100 millions d'objets
sont alloués et le GC doit passer 18 fois.

Leçon d'expert : un objet temporaire dans une méthode courte ne coûte souvent **rien**.
Ne déforme pas ton code pour éviter des allocations sans avoir mesuré.

## Ce qui empêche le JIT d'optimiser

- **Les appels mégamorphes** : un appel d'interface qui voit 1 ou 2 implémentations est
  optimisé (et souvent inliné) ; au-delà, il devient un appel indirect, plus lent.
- **Les méthodes énormes** : au-delà d'une certaine taille de bytecode, le JIT refuse de
  les inliner, voire de les compiler.
- **Un objet qui s'échappe** (stocké dans un champ, passé à une méthode non inlinée) :
  l'escape analysis ne peut plus l'éliminer.

Pour voir les décisions du JIT : `-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining`.
Pour mesurer : JMH, toujours.

## À retenir

- En conteneur, la JVM ne prend que 25 % de la mémoire pour le tas par défaut ; et le
  tas n'est pas toute la mémoire du processus.
- L'escape analysis supprime les objets qui ne s'échappent pas : 0 pause GC au lieu de 18.
- Petites méthodes, appels monomorphes, objets locaux : le code que le JIT aime.
