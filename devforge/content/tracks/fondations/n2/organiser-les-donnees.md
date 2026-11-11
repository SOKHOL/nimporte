---
id: fondations.n2.organiser-les-donnees
title: Collections, chaînes, récursivité et erreurs
skills: [collections, chaines, portee, recursivite, exceptions]
duration_min: 14
---

# Collections, chaînes, récursivité et erreurs

## Les trois collections essentielles

| Collection | Quand l'utiliser | Exemple |
|---|---|---|
| `List` | une suite ordonnée, doublons permis | `listOf(12, 7, 15)` |
| `Set` | des éléments uniques, « est-ce que ça y est ? » | `setOf("kotlin", "java")` |
| `Map` | associer une clé à une valeur | `mapOf("ada" to 36)` |

Chaque collection existe en lecture seule (`List`) et modifiable (`MutableList`).

Kotlin fournit des opérations qui remplacent la plupart des boucles :

```kotlin
val notes = listOf(12, 7, 15, 9, 18, 7)
println(notes.filter { it >= 10 }.map { it * 2 }.sum())   // 90
println(notes.toSet().size)                                // 5 : le 7 en double disparaît
println(notes.groupBy { if (it >= 10) "reçu" else "ajourné" }.mapValues { it.value.size })
// {reçu=3, ajourné=3}
```

Sorties réelles. `filter` garde, `map` transforme, `sum`/`count`/`groupBy` résument.

## Les chaînes de caractères

Une `String` est **immuable** : chaque « modification » crée une nouvelle chaîne.

```kotlin
val nom = "Ada"
println("Bonjour $nom, tu as ${nom.length} lettres")   // gabarit
```

Construire une longue chaîne dans une boucle avec `+` recopie tout à chaque tour (coût
quadratique) : utilise `buildString { }` ou `StringBuilder`.

## La portée

Une variable n'existe que dans le bloc `{ }` où elle est déclarée. Déclarer une variable
du même nom dans un bloc intérieur la **masque** (*shadowing*) : légal, mais source de
confusion. Garde les portées les plus petites possible.

## La récursivité

Une fonction récursive s'appelle elle-même sur un problème plus petit, jusqu'à un **cas de
base** qui s'arrête.

```kotlin
fun sommeChiffres(n: Int): Int =
    if (n < 10) n                          // cas de base
    else n % 10 + sommeChiffres(n / 10)    // cas récursif

println(sommeChiffres(2026))   // 10 (2 + 0 + 2 + 6)
```

Sans cas de base, ou si le problème ne rapetisse pas : `StackOverflowError`.

## Gérer les erreurs

```kotlin
fun lire(): String {
    try {
        println("lecture")
        return "contenu"
    } finally {
        println("fermeture")
    }
}
println(lire())
```

Sortie réelle : `lecture`, `fermeture`, `contenu`. Le bloc `finally` s'exécute **toujours**,
même après un `return` ou une exception : c'est l'endroit pour libérer une ressource
(ou mieux, `use { }`).

En Kotlin, `try` est une expression :

```kotlin
val port = try { texte.toInt() } catch (e: NumberFormatException) { 8080 }
```

Règles : attrape des exceptions **précises**, jamais pour cacher un problème ; pour les
cas prévus (« pas trouvé »), préfère un retour `null` ou un `Result`.

## À retenir

- `List` ordonnée, `Set` sans doublons, `Map` clé-valeur ; `filter`, `map`, `groupBy` remplacent les boucles.
- Les `String` sont immuables : `buildString` pour construire dans une boucle.
- Récursivité : un cas de base, et un problème qui rapetisse.
- `finally` s'exécute toujours ; on attrape des exceptions précises.
