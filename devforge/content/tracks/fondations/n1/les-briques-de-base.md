---
id: fondations.n1.les-briques-de-base
title: Les briques de base
skills: [variables-types, operateurs, conditions, boucles, fonctions]
duration_min: 12
---

# Les briques de base

Tous les programmes, du plus simple au plus complexe, sont faits des mêmes briques.
Les exemples sont en Kotlin, mais ces notions existent dans tous les langages.

## Variables et types

```kotlin
val nom = "Ada"          // val : ne change plus (à préférer)
var score = 0            // var : peut changer
score += 10

val age: Int = 36        // le type peut être écrit…
val taille = 1.68        // …ou déduit : ici Double
val actif = true         // Boolean
```

Préfère `val` : une valeur qui ne change pas est plus facile à comprendre.

| Type | Contient | Exemple |
|---|---|---|
| `Int` | entier sur 32 bits | `42` |
| `Long` | entier sur 64 bits | `42L` |
| `Double` | nombre à virgule | `3.14` |
| `Boolean` | vrai ou faux | `true` |
| `String` | texte | `"bonjour"` |

## Les calculs et leurs pièges

```kotlin
println(7 / 2)       // 3   : division entière entre deux Int
println(7 % 2)       // 1   : le reste (modulo)
println(7 / 2.0)     // 3.5 : dès qu'un Double est impliqué
println(-7 / 2)      // -3  : Kotlin tronque vers zéro
```

Deux pièges que même les développeurs confirmés oublient :

```kotlin
println(0.1 + 0.2)          // 0.30000000000000004
println(0.1 + 0.2 == 0.3)   // false
```

Les nombres à virgule sont stockés en binaire, et 0,1 n'a pas d'écriture binaire exacte
(comme 1/3 en décimal). Ne compare jamais deux `Double` avec `==`, et n'utilise jamais
`Double` pour de l'argent (des centimes en `Long`, ou `BigDecimal`).

```kotlin
val max = Int.MAX_VALUE
println(max)        // 2147483647
println(max + 1)    // -2147483648 : débordement, sans aucune erreur !
```

Un `Int` qui dépasse sa limite repart silencieusement dans les négatifs.

## Conditions

En Kotlin, `if` et `when` sont des **expressions** : ils produisent une valeur.

```kotlin
val mention = if (note >= 10) "reçu" else "ajourné"

val categorie = when {
    age < 13 -> "enfant"
    age < 18 -> "ado"
    else -> "adulte"
}
```

`when` teste les conditions **dans l'ordre** et s'arrête à la première vraie : l'ordre
des branches compte.

## Boucles

```kotlin
for (i in 1..10 step 3) print("$i ")   // 1 4 7 10
for (i in 0 until 3) print("$i ")      // 0 1 2   (until exclut la borne)
for (i in 3 downTo 1) print("$i ")     // 3 2 1

var n = 0
while (n < 3) n++
```

## Fonctions

```kotlin
fun aire(largeur: Double, hauteur: Double = 1.0): Double = largeur * hauteur

aire(3.0, 2.0)           // 6.0
aire(3.0)                // 3.0 : valeur par défaut
aire(hauteur = 2.0, largeur = 5.0)   // arguments nommés
```

Une bonne fonction fait **une** chose, porte un nom qui dit laquelle, et tient sur un écran.

## À retenir

- `val` par défaut, `var` si nécessaire.
- Division entière entre deux `Int` ; les flottants ne sont jamais exacts ; un `Int` peut déborder.
- `if` et `when` produisent une valeur ; `when` s'arrête à la première branche vraie.
- `..` inclut la borne, `until` l'exclut.
