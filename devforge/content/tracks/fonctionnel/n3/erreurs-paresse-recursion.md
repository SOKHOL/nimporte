---
id: fonctionnel.n3.erreurs-paresse-recursion
title: Option, Result, évaluation paresseuse et récursion terminale
skills: [erreurs-fonctionnelles, paresse, recursion-terminale]
duration_min: 18
---

# Option, Result, évaluation paresseuse et récursion terminale

## Les erreurs comme des valeurs

Une exception est un `goto` invisible : la signature de la fonction ne dit pas qu'elle peut
échouer, et l'appelant oublie facilement de la traiter. La programmation fonctionnelle
représente l'absence et l'échec par des **valeurs**, visibles dans le type :

| Type | Sens | Kotlin | Ailleurs |
|---|---|---|---|
| **Option** | une valeur, ou rien | le type nullable `T?` | `Optional` (Java), `Option` (Scala, Rust), `Maybe` (Haskell) |
| **Result** | une valeur, ou une erreur | `Result<T>` (erreur = exception), ou une sealed interface maison | `Result<T, E>` (Rust), `Either` (Scala, Haskell, Arrow) |

Avec un `Result`, on enchaîne les étapes ; à la première erreur, les étapes suivantes sont
sautées (« programmation en voie ferrée », *railway oriented programming*). Sortie réelle :

```kotlin
fun lireAge(texte: String): Result<Int> = runCatching { texte.trim().toInt() }
    .mapCatching { require(it in 0..150) { "âge impossible : $it" }; it }

listOf(" 42 ", "abc", "200").forEach { entree ->
    println(lireAge(entree).fold(onSuccess = { "ok $it" }, onFailure = { "erreur : ${it::class.simpleName}" }))
}
// ok 42
// erreur : NumberFormatException
// erreur : IllegalArgumentException
```

Pour les erreurs **métier** attendues, on préfère souvent un type somme explicite, que le
compilateur oblige à traiter :

```kotlin
sealed interface ResultatInscription {
    data class Inscrit(val id: UtilisateurId) : ResultatInscription
    data object EmailDejaUtilise : ResultatInscription
    data class MotDePasseTropFaible(val raisons: List<String>) : ResultatInscription
}
```

La règle pratique : **exceptions pour l'imprévu** (bug, base indisponible), **valeurs pour les
échecs prévus** du métier (e-mail déjà pris, solde insuffisant), qui font partie du contrat de
la fonction. Et en Kotlin, attention : `runCatching` attrape **tout**, y compris
`CancellationException` des coroutines, qu'il faut laisser passer.

## L'évaluation paresseuse

Une évaluation **stricte** calcule tout, tout de suite ; une évaluation **paresseuse** ne
calcule que ce qui est demandé, au moment où c'est demandé. Sortie réelle, avec des `println`
dans chaque étape :

```kotlin
listOf(1, 2, 3, 4).map { println("map $it"); it * 10 }.filter { println("filter $it"); it > 15 }.first()
// map 1, map 2, map 3, map 4, filter 10, filter 20, filter 30, filter 40   → 20

sequenceOf(1, 2, 3, 4).map { println("map $it"); it * 10 }.filter { println("filter $it"); it > 15 }.first()
// map 1, filter 10, map 2, filter 20                                         → 20
```

La `List` applique chaque étape à **tous** les éléments et crée une liste intermédiaire à chaque
étape. La `Sequence` fait passer **un élément à la fois** dans tout le pipeline, et s'arrête dès
que `first()` est satisfait : 4 appels au lieu de 8.

Ce que permet la paresse :

- de grosses collections sans listes intermédiaires (moins d'allocations) ;
- des **sources infinies** : `generateSequence(1) { it * 2 }.take(10)` ;
- de ne pas calculer ce qui ne servira pas (`by lazy`, `Lazy<T>`).

Ses pièges : un calcul qui s'exécute **plus tard** que prévu (et éventuellement plusieurs fois
si la séquence est reparcourue), des exceptions qui surgissent loin de leur cause, et pour de
petites collections, une séquence n'est pas plus rapide qu'une liste. Haskell est paresseux par
défaut ; la plupart des autres langages sont stricts, avec des outils pour la paresse
(`Sequence`, `Stream` en Java, générateurs Python, itérateurs Rust).

## La récursion et la récursion terminale

Sans boucles modifiant des variables, on itère par **récursion**. Mais chaque appel occupe une
**frame** sur la pile. Mesures réelles sur la JVM (pile par défaut) :

```kotlin
fun somme(n: Long): Long = if (n == 0L) 0 else n + somme(n - 1)
somme(1_000_000)            // StackOverflowError ; la pile a tenu environ 12 000 appels

tailrec fun sommeTerminale(n: Long, acc: Long = 0): Long =
    if (n == 0L) acc else sommeTerminale(n - 1, acc + n)
sommeTerminale(1_000_000)   // 500000500000
```

Un appel est **terminal** s'il est la **dernière** chose que fait la fonction : rien ne reste à
calculer après son retour. Dans `somme`, il reste l'addition `n + …` après l'appel ; dans
`sommeTerminale`, le résultat partiel voyage dans l'**accumulateur** `acc`. Le mot-clé `tailrec`
demande au compilateur de transformer cette récursion en boucle : plus de frames empilées.

La JVM n'optimise pas d'elle-même les appels terminaux (Scala a `@tailrec`, Kotlin `tailrec`) ;
Scheme, Haskell et la plupart des langages fonctionnels le font. En pratique, on utilise
surtout les fonctions de haut niveau (`fold`, `map`) qui encapsulent l'itération, et on garde la
récursion pour les structures récursives (arbres, expressions, analyses syntaxiques).

## À retenir

- Option et Result rendent l'absence et l'échec visibles dans le type ; enchaînement « voie ferrée » ; exceptions pour l'imprévu, valeurs pour les échecs métier.
- Paresse : un élément à la fois, rien d'inutile (4 appels au lieu de 8 mesurés) ; sources infinies ; attention au moment où le calcul a lieu.
- Récursion terminale : l'appel est la dernière opération, l'état voyage dans un accumulateur ; `tailrec` évite le StackOverflowError (12 000 appels environ sans).
