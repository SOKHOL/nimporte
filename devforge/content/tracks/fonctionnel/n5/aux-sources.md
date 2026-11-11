---
id: fonctionnel.n5.aux-sources
title: Structures persistantes, lambda-calcul, Haskell et catégories
skills: [structures-persistantes, lambda-calcul, langages-purs, categories]
duration_min: 20
---

# Structures persistantes, lambda-calcul, Haskell et catégories

## Les structures de données persistantes

Si les valeurs sont immuables, « ajouter un élément » doit créer une nouvelle collection.
Copier un million d'éléments à chaque ajout serait ruineux. Les **structures persistantes**
gardent l'ancienne version **et** la nouvelle, en **partageant** tout ce qui n'a pas changé.

L'exemple le plus simple : une liste chaînée immuable. Sortie réelle en Kotlin :

```kotlin
sealed interface Liste<out T>
data object Vide : Liste<Nothing>
data class Cellule<T>(val tete: T, val queue: Liste<T>) : Liste<T>

val a = Vide.ajouterDevant(3).ajouterDevant(2)     // [2, 3]
val b = a.ajouterDevant(1)                        // [1, 2, 3]
val c = a.ajouterDevant(9)                        // [9, 2, 3]
// tailles : 2 3 3
// (b as Cellule).queue === a && (c as Cellule).queue === a  →  true
```

`b` et `c` ne copient rien : chacune ajoute **une** cellule devant `a`, qu'elles partagent (le
`===` vérifie qu'il s'agit du **même objet** en mémoire). Trois versions coexistent pour le prix
de quatre cellules.

Pour les tableaux et dictionnaires, on utilise des **arbres très larges** (32 branches par
nœud : les *HAMT* et les vecteurs de Clojure, Scala, `kotlinx.collections.immutable`,
Immutable.js) : une modification ne recopie que le chemin de la racine à la feuille, soit 5 ou
6 nœuds pour un million d'éléments. Accès et mise à jour en « presque » temps constant, et un
historique quasi gratuit (annuler, comparer deux versions, partager entre threads).

## Le lambda-calcul

Le **lambda-calcul** (Alonzo Church, 1936) est le modèle de calcul à l'origine de la
programmation fonctionnelle. Il n'a que trois constructions : des variables, des fonctions
anonymes à un paramètre (`λx. corps`), et l'application d'une fonction à un argument. Pas de
nombres, pas de booléens, pas de boucles… et pourtant il peut tout calculer : il est
**équivalent à la machine de Turing** (thèse de Church-Turing).

Comment avoir des nombres avec seulement des fonctions ? Les **entiers de Church** : le nombre
*n* est la fonction qui applique *n* fois une fonction `f` à `x`.

```kotlin
typealias Nombre = ((Any?) -> Any?) -> (Any?) -> Any?
val zero: Nombre = { _ -> { x -> x } }                                  // f appliquée 0 fois
fun succ(n: Nombre): Nombre = { f -> { x -> f(n(f)(x)) } }              // une fois de plus
fun plus(a: Nombre, b: Nombre): Nombre = { f -> { x -> a(f)(b(f)(x)) } }
fun fois(a: Nombre, b: Nombre): Nombre = { f -> a(b(f)) }
fun enEntier(n: Nombre): Int = n { (it as Int) + 1 }(0) as Int          // compter les applications

val un = succ(zero); val deux = succ(un); val trois = plus(un, deux)
// sortie réelle : enEntier(zero), (un), (trois), (fois(trois, deux))  →  0 1 3 6
```

Les booléens se codent de la même façon (`vrai = λa. λb. a`, `faux = λa. λb. b`), et la
récursion aussi, avec le **combinateur Y**. Ce n'est pas une façon pratique de programmer,
mais c'est la preuve que les fonctions suffisent à tout, et la source de la syntaxe des lambdas
de tous les langages modernes.

## Haskell et les langages purs

**Haskell** pousse les idées fonctionnelles au bout (les exemples ci-dessous sont illustratifs :
Haskell n'est pas installé sur la machine de test) :

- **pureté par défaut** : une fonction qui fait des entrées-sorties a un type `IO`, et le
  compilateur interdit de l'appeler depuis une fonction pure ;
- **paresse par défaut** : rien n'est calculé avant d'être nécessaire ;
- **inférence de types** puissante, **classes de types** (proches des interfaces, mais
  ajoutables après coup à un type existant) ;
- **types algébriques** et pattern matching partout.

```haskell
data Forme = Cercle Double | Rectangle Double Double

aire :: Forme -> Double
aire (Cercle r)      = pi * r * r
aire (Rectangle l h) = l * h

main :: IO ()
main = mapM_ (print . aire) [Cercle 1, Rectangle 2 3]
```

D'autres langages fonctionnels, chacun avec son terrain : **Elm** (interfaces web sans erreur
à l'exécution), **OCaml** et **F#** (pragmatiques, rapides, typés), **Clojure** (Lisp sur la
JVM, données immuables), **Elixir/Erlang** (systèmes concurrents et tolérants aux pannes),
**Scala** (fonctionnel et objet sur la JVM). Apprendre l'un d'eux change la façon d'écrire du
Kotlin, du Java ou du TypeScript.

## Un aperçu de la théorie des catégories

La **théorie des catégories** est la branche des mathématiques qui étudie la **composition**.
Une catégorie, ce sont des **objets** (pour nous : des types) et des **flèches** entre eux (des
fonctions), avec deux règles : on peut composer les flèches (`f puis g`), et cette composition
est associative et possède une identité.

Quelques notions qu'on retrouve en programmation :

- un **monoïde** : une opération associative avec un élément neutre. L'addition (avec 0), la
  multiplication (avec 1), la concaténation de chaînes (avec `""`) ou de listes, le `max`.
  Comme l'opération est associative, on peut **découper** le calcul et le paralléliser
  (MapReduce, Spark, les agrégations de bases distribuées) : c'est une propriété très concrète ;
- un **foncteur** : une façon de transformer les types **et** les fonctions en conservant la
  composition (`map` du niveau 4, avec ses lois) ;
- une **monade** : un foncteur avec une façon d'aplatir les contextes imbriqués (`flatMap`).

On n'a pas besoin de la théorie pour utiliser ces outils ; elle explique **pourquoi** ils se
combinent si bien, et pourquoi des bibliothèques de langages très différents finissent par
proposer les mêmes opérations.

## À retenir

- Structures persistantes : chaque version est conservée, ce qui n'a pas changé est partagé (`queue === a` vérifié) ; arbres larges pour les vecteurs et dictionnaires.
- Lambda-calcul : variables, fonctions, application ; Turing-complet ; les entiers de Church (0 1 3 6 calculés) montrent que les fonctions suffisent.
- Haskell : pureté et paresse par défaut, effets typés (`IO`), classes de types ; Elm, OCaml, F#, Clojure, Elixir, Scala.
- Catégories : la science de la composition ; monoïdes (associativité = parallélisme), foncteurs, monades.
