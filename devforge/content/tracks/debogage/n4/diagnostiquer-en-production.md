---
id: debogage.n4.diagnostiquer-en-production
title: Thread dumps, fuites mémoire et profilage en production
skills: [thread-dumps, fuites-memoire, profilage]
duration_min: 20
---

# Thread dumps, fuites mémoire et profilage en production

En production, pas de débogueur ni de pas à pas : on observe un système vivant, sans
l'arrêter. Trois outils couvrent les situations les plus fréquentes : le service est **bloqué**
(thread dump), il **grossit** jusqu'à mourir (heap dump), il est **lent** (profileur).

## Thread dumps et interblocages

Un **thread dump** est une photo de tous les threads d'une JVM : ce que chacun exécute, et les
verrous qu'il tient ou attend. On l'obtient sans arrêter le service : `jstack PID`,
`jcmd PID Thread.print`, ou `kill -3 PID` (le dump s'écrit sur la sortie standard).

Dump réel d'un programme où deux virements s'exécutent en sens inverse, chacun verrouillant
d'abord son compte source :

```
Found one Java-level deadlock:
=============================
"virement-A-vers-B":
  waiting to lock monitor 0x00007f2334001e30 (object 0x0000000737904298, a Compte),
  which is held by "virement-B-vers-A"
"virement-B-vers-A":
  waiting to lock monitor 0x00007f2328000fe0 (object 0x000000073781acb0, a Compte),
  which is held by "virement-A-vers-B"

"virement-A-vers-B":
	at InterblocageKt.virer(Interblocage.kt:6)
	- waiting to lock <0x0000000737904298> (a Compte)
	- locked <0x000000073781acb0> (a Compte)
Found 1 deadlock.
```

La JVM détecte elle-même l'**interblocage** : chacun tient un compte et attend l'autre. La
correction classique : verrouiller toujours dans le **même ordre** (par identifiant de compte),
ou utiliser `tryLock` avec un délai (parcours Concurrence, niveau 2).

Sans interblocage, un dump reste précieux : prendre **trois dumps à quelques secondes
d'intervalle** et comparer. Les threads qui restent sur la **même ligne** sont bloqués : sur une
requête SQL, un appel HTTP sans délai d'expiration, l'attente d'une connexion du pool (des
dizaines de threads en `WAITING` sur `getConnection`), ou un verrou très disputé.

## Fuites mémoire et heap dumps

Une fuite mémoire en Java : des objets qui restent **atteignables** alors qu'ils ne servent plus,
le plus souvent dans une collection qui ne fait que grandir (un cache sans limite, une liste
d'écouteurs jamais retirés, un `ThreadLocal` jamais vidé). Démonstration réelle : un cache de
sessions jamais purgé, sur une JVM limitée à 512 Mo, observé avec `jcmd PID GC.class_histogram`
et `GC.heap_info` :

| Instant | Objets `Session` vivants | Tas utilisé |
|---|---|---|
| ≈ 2 s | 84 000 | 109 Mo |
| ≈ 6 s | 247 000 | 301 Mo |
| ≈ 10 s | 411 000 | 495 Mo |
| puis | | `java.lang.OutOfMemoryError` |

La démarche :

1. **Confirmer** qu'il s'agit d'une fuite : la mémoire **après chaque GC complet** monte sans
   cesse (une hausse avant le GC est normale).
2. **Comparer deux histogrammes** de classes à quelques minutes d'écart : la classe dont le
   nombre d'instances ne fait que croître (ici `Session`) est la piste.
3. **Prendre un heap dump** (`jcmd PID GC.heap_dump /tmp/tas.hprof`, ou automatiquement avec
   `-XX:+HeapDumpOnOutOfMemoryError`) et l'ouvrir dans **Eclipse MAT** ou VisualVM :
   - le *dominator tree* montre quels objets retiennent le plus de mémoire ;
   - le **chemin vers les racines du GC** (*path to GC roots*) montre **qui** garde ces objets
     en vie : ici, `CacheSessions.sessions`, un champ statique.
4. **Corriger** : une limite de taille et une expiration (Caffeine), des références faibles
   quand c'est adapté, désinscrire les écouteurs, `ThreadLocal.remove()`.

Attention : un heap dump fige la JVM le temps de l'écrire (des secondes, voire des minutes pour
un gros tas), et il contient **toutes les données** en mémoire, mots de passe et données
personnelles compris : il se traite comme une donnée sensible.

## Profiler en production

Un **profileur** dit **où passe le temps**. Les profileurs **par échantillonnage** regardent
périodiquement ce que fait chaque thread (des centaines de fois par seconde), pour un coût de
quelques pour cent : utilisables en production.

Profil réel avec **Java Flight Recorder** (intégré au JDK, activé par
`-XX:StartFlightRecording`), affiché par `jfr view hot-methods`, sur un programme qui valide des
e-mails et formate des nombres :

```
Method                                                       Samples  Percent
java.util.regex.Pattern.compile()                                102   16.64%
java.util.regex.Matcher.<init>(Pattern, CharSequence)             58    9.46%
java.util.regex.Pattern$BmpCharProperty.match(...)                54    8.81%
java.util.regex.Pattern.range(Pattern$BitClass)                   52    8.48%
```

`Pattern.compile()` en tête : la fonction de validation recrée sa `Regex` à **chaque appel**.
En la compilant une seule fois (une constante), la mesure réelle passe de **2,87 millions** à
**18,3 millions** de validations par seconde, environ **6,4 fois plus**. Sans profileur, personne
n'aurait soupçonné cette ligne.

Les outils : JFR et JDK Mission Control ; **async-profiler** (CPU, allocations, verrous, en
*flame graphs*) ; `py-spy` (Python), `pprof` (Go), le profileur des navigateurs ; et le
**profilage continu** (Pyroscope, Parca, profils dans Datadog ou Grafana) qui garde un profil de
chaque service en permanence, pour comparer « avant » et « après » un déploiement. Un *flame
graph* se lit ainsi : chaque barre est une fonction, sa **largeur** est le temps passé dedans
(et dans ce qu'elle appelle) ; on cherche les **plateaux larges**.

## À retenir

- Thread dump (`jstack`, `jcmd Thread.print`) : la JVM détecte les interblocages ; trois dumps espacés révèlent les threads bloqués.
- Fuite : mémoire après GC qui monte ; histogrammes comparés (Session : 84 000 → 411 000, puis OutOfMemoryError) ; heap dump et chemin vers les racines ; c'est une donnée sensible.
- Profileur par échantillonnage (JFR, async-profiler, profilage continu) : `Pattern.compile()` à 16,6 % révèle une Regex recompilée ; ×6,4 après correction.
