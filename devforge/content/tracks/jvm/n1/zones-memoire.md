---
id: jvm.n1.zones-memoire
title: Les zones mémoire de la JVM
skills: [zones-memoire, boxing]
duration_min: 8
---

# Les zones mémoire de la JVM

## La carte

| Zone | Une par… | Contenu | Erreur quand c'est plein |
|---|---|---|---|
| **Registre PC** | thread | l'adresse de l'instruction en cours | — |
| **Pile de la JVM** | thread | les frames : variables locales et pile d'opérandes | `StackOverflowError` |
| **Tas** (*heap*) | JVM | les objets et les tableaux, partagés | `OutOfMemoryError: Java heap space` |
| **Metaspace** | JVM | les métadonnées des classes chargées (hors du tas) | `OutOfMemoryError: Metaspace` |
| **Code cache** | JVM | le code machine produit par le JIT | le JIT s'arrête : tout ralentit |

Et quelques réglages associés :

| Option | Effet |
|---|---|
| `-Xmx2g` / `-Xms2g` | taille maximale / initiale du tas |
| `-Xss1m` | taille de la pile de chaque thread |
| `-XX:MaxMetaspaceSize=256m` | plafond de la metaspace |

Chaque thread coûte donc sa pile : 1 000 threads avec `-Xss1m` réservent jusqu'à 1 Go,
en dehors du tas. Trop de threads donne une autre erreur :
`OutOfMemoryError: unable to create native thread`.

## Lire une OutOfMemoryError

Le message après les deux-points est un diagnostic :

- `Java heap space` : trop d'objets vivants (fuite ou tas trop petit).
- `Metaspace` : trop de classes chargées. Souvent une fuite de *class loaders* après des
  redéploiements à chaud, ou une bibliothèque qui génère des classes en boucle.
- `GC overhead limit exceeded` : la JVM passe presque tout son temps à collecter pour
  presque rien récupérer.
- `unable to create native thread` : trop de threads pour le système.

## Primitifs et boxing

Kotlin cache la différence, mais la JVM la voit :

- `Int` non nullable → `int` primitif : 4 octets, directement dans la frame ou dans l'objet.
- `Int?`, ou `Int` dans une `List<Int>` → objet `java.lang.Integer` sur le tas : c'est le
  **boxing**.

Pour éviter de créer des objets en permanence, `Integer.valueOf` garde un **cache des
entiers de -128 à 127**. Conséquence surprenante :

```kotlin
fun main() {
    val a: Int? = 127
    val b: Int? = 127
    val c: Int? = 1000
    val d: Int? = 1000
    println(a === b)   // true  : même objet, issu du cache
    println(c === d)   // false : deux objets Integer distincts
    println(c == d)    // true  : même valeur
}
```

Le compilateur Kotlin avertit d'ailleurs que `===` sur des `Int?` est à proscrire. La
règle : **`==` pour comparer des valeurs**, `===` seulement quand l'identité de l'objet
compte vraiment. En Java, le même piège apparaît avec `==` sur des `Integer`.

## À retenir

- Par thread : registre PC et pile. Partagés : tas, metaspace, code cache.
- Le message d'une `OutOfMemoryError` indique la zone saturée.
- `Int?` et les collections boxent les nombres ; le cache des `Integer` rend `===`
  trompeur entre -128 et 127.
