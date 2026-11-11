---
id: memoire.n4.hors-tas
title: La mémoire hors du tas
skills: [hors-tas]
duration_min: 12
---

# La mémoire hors du tas

Un *heap dump* ne montre que le tas. Or un processus Java consomme bien plus : quand un
conteneur est tué pour manque de mémoire alors que le tas est à moitié vide, la réponse est
presque toujours **hors du tas**.

## Ce qu'une JVM consomme vraiment

Mesure réelle avec le *Native Memory Tracking* (`-XX:NativeMemoryTracking=summary`, puis
`jcmd PID VM.native_memory summary`), sur un petit programme lancé avec `-Xmx256m` et qui
alloue un buffer direct de 100 Mo :

| Zone | Mémoire engagée |
|---|---|
| Java Heap (le tas) | 221 184 KB |
| GC (structures du ramasse-miettes) | 46 086 KB |
| Other (dont le buffer direct de 100 Mo) | 102 400 KB |
| Code (code compilé par le JIT) | 7 593 KB |
| Thread (piles des threads) | 829 KB |
| Class (metaspace) | 202 KB |
| **Total** | **394 596 KB** |

Le tas est limité à 256 Mo, mais le processus engage environ **385 Mo**. Sur une vraie
application, s'ajoutent des centaines de threads (environ 1 Mo de pile réservée chacun), une
metaspace de 100 Mo ou plus (Spring, Hibernate), et les buffers des bibliothèques réseau.

## Les buffers directs

`ByteBuffer.allocateDirect` alloue de la mémoire **en dehors du tas**, que le système
d'exploitation peut lire et écrire directement. C'est la base des E/S rapides en Java :
Netty, les drivers de bases de données, Kafka, gRPC. Pas de copie entre le tas et le noyau,
et pas d'objet que le GC doit déplacer.

Mesure réelle :

```java
ByteBuffer b1 = ByteBuffer.allocateDirect(200 * 1024 * 1024);
// tas utilisé : 2 Mo
ByteBuffer b2 = ByteBuffer.allocateDirect(200 * 1024 * 1024);
```

Avec `-XX:MaxDirectMemorySize=256m`, la seconde allocation échoue :

```
java.lang.OutOfMemoryError: Cannot reserve 209715200 bytes of direct buffer memory
  (allocated: 209715200, limit: 268435456)
```

Points d'expert :

- 200 Mo alloués, **2 Mo de tas utilisés** : un heap dump ne montrerait qu'un petit objet
  `DirectByteBuffer`.
- La mémoire d'un buffer direct n'est rendue que lorsque le GC collecte l'objet
  `DirectByteBuffer` qui la référence. Un tas peu sollicité peut donc garder longtemps des
  centaines de Mo hors tas.
- Par défaut, la limite des buffers directs est égale à `-Xmx` : avec un tas de 2 Go, on
  peut consommer jusqu'à 2 Go **en plus** hors tas.
- Les bibliothèques comme Netty gardent des **pools** de buffers directs : la mémoire ne
  redescend jamais, c'est voulu.

## Les autres usages hors tas

- **Fichiers projetés en mémoire** (`FileChannel.map`, `mmap`) : le fichier est vu comme de
  la mémoire, chargé page par page par le noyau. Les bases (Lucene, Kafka, Chronicle)
  l'utilisent massivement.
- **L'API FFM** (Java 22) : `Arena.ofConfined()` alloue de la mémoire native avec une
  libération déterministe à la fermeture de l'arène, sans dépendre du GC.
- **Les caches hors tas** (Ehcache, Chronicle Map, Caffeine combiné à un stockage natif) :
  des gigaoctets de données sans allonger les pauses du GC.

## Diagnostiquer

1. Comparer la **RSS** du processus (`ps -o rss`, mémoire du conteneur) au tas utilisé.
2. Si l'écart est grand : lancer avec `-XX:NativeMemoryTracking=summary` (coût de 5 à 10 %)
   et comparer deux `jcmd PID VM.native_memory summary.diff` à quelques minutes d'écart pour
   voir quelle zone grossit.
3. Buffers directs : le MBean `java.nio:type=BufferPool,name=direct` (dans JFR, VisualVM ou
   les métriques Micrometer `jvm.buffer.memory.used`).
4. Si NMT ne voit rien qui grossit, la fuite est dans du code natif (une bibliothèque JNI,
   `malloc` qui fragmente) : outils `jemalloc` et `pmap`.

## À retenir

- Mémoire du processus = tas + metaspace + piles + code JIT + GC + buffers directs + natif.
- `allocateDirect` vit hors tas, invisible dans un heap dump ; libérée seulement quand le GC collecte le buffer.
- Fixer `MaxDirectMemorySize`, laisser de la marge entre `-Xmx` et la limite du conteneur.
- Diagnostic : RSS contre tas, puis Native Memory Tracking et ses `diff`.
