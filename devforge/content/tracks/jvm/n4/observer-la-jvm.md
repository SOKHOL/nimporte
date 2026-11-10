---
id: jvm.n4.observer-la-jvm
title: Observer une JVM en production
skills: [outils-jdk, jfr, options-jvm]
duration_min: 12
---

# Observer une JVM en production

Le JDK est livré avec une boîte à outils de diagnostic. Un expert sait s'en servir
**sur une application en marche**, sans la redémarrer.

## jcmd : le couteau suisse

`jcmd` liste les JVM de la machine, puis envoie des commandes à l'une d'elles par son
numéro de processus (`jcmd <pid> help` donne la liste complète).

| Commande | Usage |
|---|---|
| `jcmd <pid> Thread.print` | l'état de tous les threads (le *thread dump*) |
| `jcmd <pid> GC.heap_info` | l'occupation du tas |
| `jcmd <pid> GC.heap_dump /tmp/tas.hprof` | un instantané du tas, à analyser dans Eclipse MAT |
| `jcmd <pid> VM.flags` | les options réellement utilisées |
| `jcmd <pid> JFR.start duration=60s filename=rec.jfr` | lancer un enregistrement Flight Recorder |

Sorties réelles, capturées sur un petit programme qui provoque un interblocage :

```
$ jcmd 506 Thread.print
Found one Java-level deadlock:
=============================
"virement-1":
  waiting to lock monitor 0x00007fe70c003cc0 (object 0x00000000fbe00088, a Compte),
  which is held by "virement-2"
"virement-2":
  waiting to lock monitor 0x00007fe710003b80 (object 0x00000000fbe00070, a Compte),
  which is held by "virement-1"
```

```
$ jcmd 506 GC.heap_info
 garbage-first heap   total 221184K, used 56934K
  region size 1024K, 44 young (45056K), 3 survivors (3072K)
 Metaspace       used 4042K, committed 4224K, reserved 1114112K
```

Astuce : prends **plusieurs** thread dumps à quelques secondes d'intervalle. Un thread
bloqué au même endroit dans tous les dumps est suspect ; un thread qui change de place
travaille normalement.

## jstat : le GC en direct

```
$ jstat -gcutil 506 1000
  S0     S1     E      O      M     CCS    YGC     YGCT     FGC    FGCT    …    GCT
     - 100.00  31.50  13.74  95.70  83.86      2     0.009     0     0.000  …   0.009
```

Une ligne par seconde : taux d'occupation de chaque zone (E = eden, O = vieille
génération, M = metaspace), nombre et durée cumulée des collectes jeunes (YGC) et
complètes (FGC). Une colonne O qui monte sans redescendre, c'est le signe d'une fuite.

Les anciens outils `jstack` et `jmap` existent toujours ; `jcmd` les remplace avantageusement.

## Java Flight Recorder (JFR)

JFR enregistre en continu ce qui se passe dans la JVM : collectes, allocations,
verrous contestés, entrées-sorties, exceptions, méthodes chaudes… pour un coût de l'ordre
de 1 %, ce qui permet de le laisser actif en production.

```
java -XX:StartFlightRecording=duration=60s,filename=rec.jfr -jar app.jar
jfr summary rec.jfr        # combien d'événements de chaque type
jfr print --events jdk.GarbageCollection rec.jfr
```

On ouvre ensuite le fichier dans **JDK Mission Control** : graphiques, *flame graphs* des
méthodes qui consomment le CPU ou qui allouent le plus, threads bloqués. C'est souvent
la façon la plus rapide de comprendre une lenteur en production.

## Les options à connaître

| Option | Effet |
|---|---|
| `-Xms` / `-Xmx` | tailles initiale et maximale du tas |
| `-XX:MaxRAMPercentage=75` | le tas maximal en pourcentage de la mémoire disponible |
| `-XX:+HeapDumpOnOutOfMemoryError` | capture le tas automatiquement au moment du crash |
| `-Xlog:gc*:file=gc.log` | journal du ramasse-miettes |
| `-XX:+UseZGC` (ou autre) | choix du collecteur |
| `-XX:StartFlightRecording` | Flight Recorder dès le démarrage |

`jcmd <pid> VM.flags` montre ce qui est réellement appliqué : la JVM choisit beaucoup de
valeurs toute seule, selon la machine (*ergonomics*).

## À retenir

- `jcmd` : thread dumps, état du tas, heap dumps, options, enregistrements JFR.
- `jstat -gcutil` pour suivre le GC en direct ; une vieille génération qui ne redescend pas est suspecte.
- JFR coûte environ 1 % : active-le en production, analyse-le avec Mission Control.
- En production : `-XX:+HeapDumpOnOutOfMemoryError` et les logs du GC, toujours.
