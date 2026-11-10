---
id: jvm.n3.collecteurs-hotspot
title: Les ramasse-miettes de HotSpot
skills: [collecteurs, g1, logs-gc, choisir-gc]
duration_min: 12
---

# Les ramasse-miettes de HotSpot

HotSpot, la JVM la plus utilisée (OpenJDK, Temurin, Corretto…), propose plusieurs
collecteurs. Chacun fait un compromis différent entre débit, latence et empreinte
(voir le parcours *Gestion de la mémoire*, niveau 3).

## Les collecteurs

| Collecteur | Option | Principe | Pour quoi |
|---|---|---|---|
| **Serial** | `-XX:+UseSerialGC` | un seul thread, tout s'arrête pendant la collecte | petits tas, conteneurs à 1 CPU |
| **Parallel** | `-XX:+UseParallelGC` | plusieurs threads, tout s'arrête pendant la collecte | traitements par lots : le **débit** avant tout |
| **G1** | `-XX:+UseG1GC` (défaut) | tas découpé en régions, pauses visées | usage général |
| **ZGC** | `-XX:+UseZGC` | marquage et compactage concurrents, pauses de l'ordre de la milliseconde | grands tas, **faible latence** |
| **Shenandoah** | `-XX:+UseShenandoahGC` | compactage concurrent | faible latence (selon la distribution du JDK) |

Depuis Java 9, G1 est le collecteur par défaut. ZGC existe en version générationnelle
depuis Java 21 ; c'est son mode par défaut depuis Java 23.

## G1 en détail

G1 (*Garbage First*) découpe le tas en **régions** de taille égale (1 à 32 Mo). Chaque
région joue un rôle à un instant donné : *eden* (objets neufs), *survivor*, *old*, ou
*humongous* (objets plus gros qu'une demi-région).

- **Collecte jeune** : les survivants des régions eden et survivor sont copiés ailleurs.
  Pause courte.
- **Marquage concurrent** : quand le tas se remplit, G1 marque les objets vivants de la
  vieille génération **pendant que l'application tourne**.
- **Collectes mixtes** : G1 collecte alors, en plus des jeunes régions, les vieilles
  régions qui contiennent le **plus de déchets** d'abord (d'où *Garbage First*).
- G1 vise une durée de pause (`-XX:MaxGCPauseMillis`, 200 ms par défaut) et ajuste la
  quantité de travail par pause pour la respecter.
- En dernier recours, si la mémoire manque : **Full GC**, une longue pause sur tout le tas.

## Lire les logs du GC

Active-les en production, ils coûtent très peu : `-Xlog:gc*:file=gc.log`.

```
[12.345s][info][gc] GC(42) Pause Young (Normal) (G1 Evacuation Pause) 612M->148M(1024M) 6.802ms
```

| Élément | Sens |
|---|---|
| `GC(42)` | 43ᵉ collecte depuis le démarrage |
| `Pause Young (Normal)` | collecte de la jeune génération |
| `612M->148M` | occupation du tas avant → **après** la collecte |
| `(1024M)` | taille actuelle du tas |
| `6.802ms` | durée de la pause |

Le chiffre le plus important est l'occupation **après** la collecte : c'est à peu près
la quantité d'objets vivants (le *live set*).

- Il **augmente sans cesse** d'une collecte à l'autre → fuite de mémoire probable.
- Il est **proche de la taille maximale** → le GC tourne en permanence pour presque rien,
  puis Full GC et `OutOfMemoryError`.
- Il est **stable** et confortable → le problème est ailleurs.

## Choisir et régler

1. **Commence par le défaut** (G1) et mesure : latence p99, débit, mémoire.
2. **Dimensionne le tas** : environ 2 à 3 fois le live set. Sur un serveur, fixe
   `-Xms` égal à `-Xmx` pour éviter les redimensionnements.
3. **Change de collecteur pour une raison mesurée** : pauses trop longues pour ton SLO →
   ZGC ; traitement par lots où seul le débit compte → Parallel.
4. **Dans un conteneur**, règle le tas en proportion de la mémoire du conteneur :
   `-XX:MaxRAMPercentage=75`.
5. **Ne règle pas cinquante options** : chaque option ajoutée sans mesure est une dette.

## À retenir

- G1 par défaut, ZGC pour la faible latence, Parallel pour le débit, Serial pour les tout petits.
- G1 travaille par régions et collecte d'abord celles qui contiennent le plus de déchets.
- Dans les logs, regarde l'occupation **après** collecte : elle révèle le live set et les fuites.
- On règle un GC en mesurant, jamais au hasard.
