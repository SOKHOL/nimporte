---
id: fondations.n3.ce-que-coute-le-code
title: Complexité, nullabilité et typage
skills: [complexite, structures-adaptees, nullabilite, typage]
duration_min: 13
---

# Complexité, nullabilité et typage

## La notation grand O

La complexité décrit comment le temps (ou la mémoire) **grandit** quand les données
grandissent. On ignore les constantes : on regarde la forme de la courbe.

| Complexité | Nom | Exemple | n = 1 000 → n = 1 000 000 |
|---|---|---|---|
| O(1) | constante | lire `liste[i]`, chercher dans un `HashSet` | identique |
| O(log n) | logarithmique | recherche dichotomique dans une liste triée | ×2 environ |
| O(n) | linéaire | parcourir une liste, `liste.contains(x)` | ×1 000 |
| O(n log n) | quasi linéaire | un bon tri | ×2 000 environ |
| O(n²) | quadratique | deux boucles imbriquées sur les mêmes données | ×1 000 000 |

Règles rapides : une boucle sur n éléments → O(n) ; deux boucles imbriquées → O(n²) ;
diviser le problème par deux à chaque étape → O(log n).

## La bonne structure change tout

```kotlin
val liste = (0 until 100_000).toList()
val ensemble = liste.toHashSet()
// 10 000 recherches dans chacune
```

Mesuré pour ce cours : **765 ms** avec la liste, **1 ms** avec l'ensemble. `liste.contains`
parcourt les éléments un par un (O(n)) ; `ensemble.contains` calcule directement où
chercher (O(1)). Même code, même résultat, 700 fois plus rapide.

C'est la différence la plus fréquente entre un code qui « marche en test » et un code qui
s'effondre en production : une recherche dans une liste à l'intérieur d'une boucle
(O(n²) au total) passe inaperçue avec 100 éléments, et prend des heures avec un million.

## La nullabilité

`null` signifie « pas de valeur ». Son inventeur, Tony Hoare, l'a appelé son « erreur à un
milliard de dollars » : en Java, n'importe quelle référence peut être `null`, et la
`NullPointerException` est la plus courante des erreurs.

Kotlin l'intègre au **type** : `String` ne peut pas être `null`, `String?` peut l'être, et
le compilateur t'oblige à gérer ce cas.

```kotlin
data class Adresse(val ville: String?)
data class Client(val adresse: Adresse?)

println(a.adresse?.ville ?: "inconnue")       // ?. : continue seulement si non null
println(c.adresse?.ville?.length ?: -1)       // ?: : valeur par défaut si null
```

Avec `Client(Adresse("Lyon"))`, `Client(null)` et `Client(Adresse(null))`, les sorties
réelles sont `Lyon`, `inconnue` et `-1`. L'opérateur `!!` (« je sais que ce n'est pas
null ») réintroduit la `NullPointerException` : à éviter.

## Typage statique ou dynamique

| | Statique (Kotlin, Java, Rust, TypeScript) | Dynamique (Python, JavaScript, Ruby) |
|---|---|---|
| Vérification des types | à la compilation | à l'exécution |
| Erreurs de type | avant de lancer le programme | en production, si le chemin est exécuté |
| Écriture | plus de rigueur | plus rapide pour des scripts |
| Outils (autocomplétion, refactoring) | excellents | plus limités |

Le typage **fort** ou **faible** est une autre question : JavaScript convertit
silencieusement (`"5" * 2` vaut 10), Python refuse (`"5" * 2` vaut `"55"`, mais `"5" + 2`
est une erreur).

## À retenir

- O(1), O(log n), O(n), O(n log n), O(n²) : connais leurs formes.
- Chercher dans un `Set` ou une `Map` : O(1) ; dans une `List` : O(n). Mesuré : 1 ms contre 765 ms.
- Kotlin met `null` dans le type : `?.`, `?:`, et jamais `!!` par facilité.
- Statique : erreurs à la compilation ; dynamique : erreurs à l'exécution.
