---
id: fonctionnel.n1.les-fonctions-comme-valeurs
title: Lambdas, map, filter, reduce et immutabilité
skills: [lambdas, map-filter-reduce, immutabilite]
duration_min: 15
---

# Lambdas, map, filter, reduce et immutabilité

La programmation fonctionnelle construit les programmes en **assemblant des fonctions** qui
transforment des **valeurs**, plutôt qu'en enchaînant des instructions qui modifient un état.
Kotlin, Scala, F#, JavaScript, Python, Java (depuis 8), C#, Rust et Swift en ont tous repris
les idées ; Haskell, Elm et Clojure en font leur cœur.

## Les fonctions sont des valeurs

Une fonction peut être rangée dans une variable, passée en paramètre, renvoyée par une autre
fonction :

```kotlin
val doubler: (Int) -> Int = { x -> x * 2 }      // une lambda dans une variable
val estPair = { x: Int -> x % 2 == 0 }

fun appliquerDeuxFois(f: (Int) -> Int, x: Int) = f(f(x))   // une fonction d'ordre supérieur
appliquerDeuxFois(doubler, 5)                              // 20

fun multiplicateur(n: Int): (Int) -> Int = { x -> x * n }  // une fonction qui renvoie une fonction
val tripler = multiplicateur(3)
tripler(7)                                                 // 21
```

Une **fonction d'ordre supérieur** prend ou renvoie une fonction. La lambda renvoyée par
`multiplicateur` **capture** la variable `n` : c'est une **fermeture** (*closure*).

En Kotlin, quand le dernier paramètre est une fonction, on peut écrire la lambda après les
parenthèses, et `it` désigne son paramètre unique : `liste.filter { it > 0 }`.

## map, filter, reduce

Trois opérations couvrent l'essentiel des boucles sur des collections :

| Opération | Effet | Exemple |
|---|---|---|
| `map` | transforme chaque élément | `[1, 2, 3].map { it * 10 }` → `[10, 20, 30]` |
| `filter` | garde ceux qui vérifient une condition | `[1, 2, 3, 4].filter { it % 2 == 0 }` → `[2, 4]` |
| `reduce` / `fold` | combine tous les éléments en une valeur | `[1, 2, 3].fold(0) { acc, x -> acc + x }` → `6` |

Et leurs cousines : `flatMap` (transformer chaque élément en liste, puis aplatir), `groupBy`,
`partition`, `associate`, `zip`, `windowed`, `sumOf`, `any`, `all`, `none`, `first`,
`take`, `sortedBy`.

```kotlin
// la boucle impérative
var total = 0
for (c in commandes) {
    if (c.payee) total += c.montant
}

// la version fonctionnelle
val total = commandes.filter { it.payee }.sumOf { it.montant }
```

La seconde dit **ce qu'on veut** (la somme des montants des commandes payées) plutôt que
**comment** le calculer pas à pas. Pas de variable modifiée, pas d'indice, pas de cas
oublié. Elle se lit de gauche à droite comme un **pipeline** de transformations.

`fold` est la plus générale : `map`, `filter`, `sum` peuvent toutes s'écrire avec un `fold`.
C'est aussi le `fold` qui reconstruit un état à partir d'une suite d'événements (l'event
sourcing, parcours Architecture, niveau 4) ou applique des actions à un état (MVI).

## L'immutabilité

Une valeur **immuable** ne change jamais après sa création. Pour « modifier », on crée une
**nouvelle** valeur :

```kotlin
data class Panier(val lignes: List<Ligne>, val codePromo: String? = null)

val p1 = Panier(listOf(ligneClavier))
val p2 = p1.copy(lignes = p1.lignes + ligneSouris)     // p1 n'a pas changé
```

Pourquoi c'est précieux :

- **Pas de surprise à distance** : une valeur passée à une fonction ne peut pas être modifiée
  par elle, ni par un autre thread. Les bugs d'aliasing (deux variables qui pointent vers la
  même liste, l'une la modifie, l'autre s'en étonne) disparaissent.
- **La concurrence devient simple** : des données qui ne changent pas se partagent entre
  threads sans verrou.
- **On peut raisonner localement** : la valeur au moment où on la lit est celle de sa création.
- **Historique gratuit** : l'ancienne valeur existe encore (annuler, comparer, rejouer).

En Kotlin : `val` plutôt que `var`, `List` (lecture seule) plutôt que `MutableList`, des
`data class` avec `copy`. Attention : `List` en Kotlin est une **vue en lecture seule**, pas
une garantie d'immutabilité ; la liste sous-jacente peut être une `MutableList` modifiée par
quelqu'un d'autre (on copie avec `toList()` aux frontières, ou on utilise
`kotlinx.collections.immutable`).

Le coût de « tout copier » est moins grand qu'on ne le croit : les structures persistantes
partagent l'essentiel de leur contenu entre versions (niveau 5).

## À retenir

- Les fonctions sont des valeurs : lambdas, fonctions d'ordre supérieur, fermetures.
- map (transformer), filter (garder), fold/reduce (combiner) : dire ce qu'on veut, en pipeline.
- Immutabilité : on crée de nouvelles valeurs (`copy`) ; plus d'aliasing surprise, concurrence simplifiée ; `List` en Kotlin n'est qu'une vue en lecture seule.
