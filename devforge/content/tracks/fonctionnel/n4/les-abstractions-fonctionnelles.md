---
id: fonctionnel.n4.les-abstractions-fonctionnelles
title: Foncteurs, monades, effets et combinateurs
skills: [foncteurs-monades, effets, combinateurs]
duration_min: 20
---

# Foncteurs, monades, effets et combinateurs

## Foncteurs et monades, sans le jargon

Tu les utilises déjà. Plusieurs types « emballent » une valeur avec un **contexte** :

| Type | Contexte |
|---|---|
| `List<T>` | plusieurs valeurs |
| `T?` (ou `Option`) | peut-être une valeur |
| `Result<T>` | une valeur, ou une erreur |
| `Deferred<T>`, `Future<T>` | une valeur plus tard |
| `Flow<T>` | des valeurs au fil du temps |

Un **foncteur** est un tel type qui a une opération `map` : appliquer une fonction **à la valeur
emballée**, sans s'occuper du contexte. Une **monade** ajoute `flatMap` (ou `bind`, `andThen`,
`then`) : enchaîner une étape qui **produit elle-même** une valeur emballée, sans obtenir un
emballage dans un emballage. Sorties réelles :

```kotlin
listOf(1, 2, 3).map { listOf(it, it * 10) }       // [[1, 10], [2, 20], [3, 30]]   emballage dans l'emballage
listOf(1, 2, 3).flatMap { listOf(it, it * 10) }   // [1, 10, 2, 20, 3, 30]         aplati
```

Avec les nullables, le `?.` de Kotlin est un `flatMap` : la chaîne s'arrête au premier `null`.

```kotlin
clients.map { it.adresse?.codePostal?.take(2) }   // [69, null, null]
clients.mapNotNull { it.adresse?.codePostal }     // [69001]
```

Même idée pour `Result.flatMap`, `Optional.flatMap`, `Promise.then` en JavaScript (qui aplatit
automatiquement une promesse renvoyée), les fonctions `suspend` qui s'enchaînent dans une
coroutine, ou `flatMapLatest` sur un `Flow`. Le motif commun : **enchaîner des étapes qui ont
un contexte**, en laissant le type gérer le contexte (l'absence, l'erreur, l'attente, la
multiplicité).

Ces types respectent des **lois** (par exemple, `map` avec la fonction identité ne change rien,
et `map(f).map(g)` équivaut à `map(f puis g)`) : c'est ce qui permet de les réorganiser et de
les optimiser sans changer le résultat. En Haskell, la notation `do` (et en Scala, le
`for`-compréhension) rend les enchaînements de `flatMap` lisibles comme du code séquentiel :
c'est exactement ce que font `async`/`await` ou les coroutines pour l'asynchrone.

## Isoler les effets

Un programme utile lit, écrit, appelle des services. L'approche fonctionnelle ne supprime pas
ces effets : elle les **sépare** de la logique, et les **rend visibles**.

**Le cœur pur, la coquille impérative** (déjà vu au parcours Clean code) : la logique décide
et renvoie une **description** de ce qu'il faut faire ; la coquille l'exécute.

```kotlin
sealed interface Action {
    data class EnvoyerEmail(val a: Email, val sujet: String) : Action
    data class Enregistrer(val commande: Commande) : Action
}

fun traiter(commande: Commande, stock: Stock): List<Action> =        // pur : facile à tester
    if (stock.suffit(commande)) listOf(Enregistrer(commande.validee()), EnvoyerEmail(commande.client, "Confirmée"))
    else listOf(EnvoyerEmail(commande.client, "Rupture de stock"))

fun executer(actions: List<Action>) = actions.forEach { … }          // effets, à la frontière
```

Les tests vérifient la **liste d'actions** renvoyée, sans aucune doublure. C'est le principe
de l'architecture Elm (et de Redux, et de MVI) : la fonction de mise à jour renvoie un nouvel
état **et** des commandes à exécuter.

Les langages et bibliothèques vont plus loin en **typant les effets** : en Haskell, une fonction
qui fait des entrées-sorties a le type `IO`, et le compilateur interdit d'en appeler une depuis
une fonction pure. En Kotlin, `suspend` joue partiellement ce rôle (on ne peut appeler une
fonction `suspend` que depuis une coroutine), et la bibliothèque Arrow apporte des outils pour
les erreurs typées (`Raise`, `Either`). Scala a ZIO et Cats Effect ; TypeScript, Effect.

## Combinateurs et DSL

Un **combinateur** est une fonction qui assemble des fonctions en fonctions plus grandes. On
peut construire un vrai **analyseur syntaxique** avec quelques combinateurs (*parser
combinators*). Exemple réel en Kotlin : un analyseur est une fonction « texte → (valeur, reste
du texte), ou échec », et une poignée de combinateurs suffit :

```kotlin
fun interface Analyseur<T> { fun lire(s: String): Pair<T, String>? }

fun car(c: Char): Analyseur<Char>                         // lit un caractère précis
val chiffres: Analyseur<Int>                              // lit un nombre
fun <A, B> Analyseur<A>.map(f: (A) -> B): Analyseur<B>    // transforme le résultat
fun <A, B> Analyseur<A>.flatMap(f: (A) -> Analyseur<B>)   // enchaîne
infix fun <A> Analyseur<A>.ou(autre: Analyseur<A>)        // essaie l'un, sinon l'autre
fun <A> Analyseur<A>.repete(): Analyseur<List<A>>         // zéro fois ou plus
```

Avec eux, une grammaire d'expressions (`expr := terme (('+' | '-') terme)*`, `terme := facteur
('*' facteur)*`, `facteur := nombre | '(' expr ')'`) s'écrit en trois lignes. Sorties réelles :

```
2+3*4      = (14, )
(2+3)*4    = (20, )
10-4-3     = (3, )
7*(1+1)*3  = (42, )
2+         = (2, +)      ← le « + » final n'a pas pu être lu : il reste dans le texte
```

La priorité de `*` sur `+` découle de la structure de la grammaire, sans aucun code
particulier. Ce style est utilisé pour les analyseurs (Parsec en Haskell, kotlin-parser,
fastparse), les validations composables (« ce champ est requis **et** un e-mail **et**
inconnu »), les générateurs de tests par propriétés, et les DSL de construction (parcours
Métaprogrammation, niveau 5). L'idée centrale : **de petites briques sûres, et des façons de
les assembler** qui conservent leurs propriétés.

## À retenir

- Foncteur = un type avec `map` ; monade = avec `flatMap` pour enchaîner des étapes qui ont un contexte (absence, erreur, attente, multiplicité) ; `?.`, `Result`, `then`, les coroutines en sont des cas.
- Isoler les effets : un cœur pur qui renvoie des décisions (nouvel état + actions), une coquille qui les exécute ; certains langages typent les effets.
- Combinateurs : de petites fonctions assemblées en grandes ; un analyseur d'expressions complet en quelques lignes (`(2+3)*4 = 20` mesuré).
