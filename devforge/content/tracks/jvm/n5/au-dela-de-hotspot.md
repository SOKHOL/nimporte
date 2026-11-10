---
id: jvm.n5.au-dela-de-hotspot
title: Au-delà de HotSpot
skills: [graalvm, ffm, specification-jvm, langages-jvm]
duration_min: 12
---

# Au-delà de HotSpot

## Un bytecode, plusieurs langages

La JVM n'exécute ni du Java ni du Kotlin : elle exécute du **bytecode**, décrit par la
*Java Virtual Machine Specification*. Kotlin, Scala, Clojure ou Groovy compilent tous vers
ce bytecode, d'où l'interopérabilité : une classe Kotlin s'utilise depuis Java, et
inversement.

Ce que Kotlin génère pour une seule ligne, `data class Point(val x: Int, val y: Int)`
(sortie réelle de `javap -p`) :

```
public final class Point {
  private final int x;
  private final int y;
  public Point(int, int);
  public final int getX();
  public final int getY();
  public final int component1();
  public final int component2();
  public final Point copy(int, int);
  public static Point copy$default(Point, int, int, int, java.lang.Object);
  public java.lang.String toString();
  public int hashCode();
  public boolean equals(java.lang.Object);
}
```

Classe `final` par défaut, champs privés et getters, `componentN()` pour la
déstructuration, `copy` (et `copy$default` pour les paramètres par défaut), `equals`,
`hashCode`, `toString`. Lire ce que génère son langage, c'est comprendre ses
performances et son interopérabilité.

## GraalVM et la compilation native

**GraalVM** propose deux choses :

1. **Graal**, un compilateur JIT écrit en Java, qui peut remplacer C2.
2. **Native Image** : compiler toute l'application **à l'avance** (*AOT*) en un
   exécutable natif, sans JVM.

| | JVM classique | Image native |
|---|---|---|
| Démarrage | de l'ordre de la seconde (plus avec un gros framework) | de l'ordre de quelques dizaines de millisecondes |
| Mémoire | plus élevée | plus faible |
| Performance de pointe | excellente après le temps de chauffe | souvent un peu inférieure (pas d'optimisation guidée par le profil réel) |
| Réflexion, chargement dynamique | libres | à déclarer à l'avance (« monde fermé ») |
| Temps de build | rapide | long (plusieurs minutes) |

L'image native brille pour les fonctions serverless, les outils en ligne de commande et
les micro-services qui démarrent et s'arrêtent souvent. Son prix : l'hypothèse du
**monde fermé**. Tout le code doit être connu au build ; la réflexion, les proxies
dynamiques et les ressources doivent être déclarés dans des fichiers de configuration.
C'est une raison de plus pour préférer la génération de code à la réflexion.

## Appeler du code natif : de JNI à FFM

Pour appeler une bibliothèque C, Java avait **JNI** : du code C « colle » à écrire et à
compiler pour chaque plateforme, des plantages difficiles à diagnostiquer. Depuis Java 22,
l'API **Foreign Function & Memory** (FFM) permet d'appeler du C directement depuis Java ou
Kotlin, et de manipuler de la mémoire hors du tas en toute sécurité :

```java
Linker linker = Linker.nativeLinker();
MethodHandle strlen = linker.downcallHandle(
    linker.defaultLookup().find("strlen").orElseThrow(),
    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

try (Arena arena = Arena.ofConfined()) {               // mémoire libérée à la fin du bloc
    MemorySegment chaine = arena.allocateFrom("DevForge");
    long longueur = (long) strlen.invokeExact(chaine);  // 8
}
```

Remarque l'`Arena` : c'est exactement le principe de l'arène et du RAII vus dans le
parcours *Gestion de la mémoire*, appliqué à la mémoire native.

## Lire la spécification

Quand deux experts ne sont pas d'accord sur un comportement de la JVM, ils ouvrent la
**spécification** : la *Java Language Specification* (le langage) et la *Java Virtual
Machine Specification* (le bytecode, le chargement des classes, le modèle mémoire au
chapitre 17 de la JLS). Pour Kotlin, la *Kotlin Language Specification*. Lire une
spécification est une compétence en soi : précise, aride, mais c'est la source de vérité.

## À retenir

- La JVM exécute du bytecode : plusieurs langages, une plateforme, une interopérabilité totale.
- Native Image : démarrage quasi instantané et moins de mémoire, au prix du « monde fermé ».
- FFM remplace JNI pour appeler du C, avec des arènes pour gérer la mémoire native.
- En cas de doute, la spécification fait foi.
