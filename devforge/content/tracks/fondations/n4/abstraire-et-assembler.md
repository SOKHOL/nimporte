---
id: fondations.n4.abstraire-et-assembler
title: Génériques, variance, compilation et modules
skills: [generiques, variance, compilation-execution, modules]
duration_min: 14
---

# Génériques, variance, compilation et modules

## Les génériques

Un générique écrit **une fois** du code qui fonctionne pour plusieurs types, tout en
restant vérifié par le compilateur.

```kotlin
fun <T : Comparable<T>> plusGrand(a: T, b: T): T = if (a >= b) a else b

println(plusGrand(3, 7))               // 7
println(plusGrand("kotlin", "java"))   // kotlin
```

`T : Comparable<T>` est une **borne** : `T` peut être n'importe quel type qu'on sait
comparer. Sans elle, `a >= b` ne compilerait pas. Sorties réelles : `7` et `kotlin`.

## La variance : out et in

Un `Chien` est un `Animal`. Mais une `MutableList<Chien>` est-elle une
`MutableList<Animal>` ?

```kotlin
val chiens: MutableList<Chien> = mutableListOf(Chien())
val animaux: MutableList<Animal> = chiens   // ⛔ refusé par le compilateur
animaux.add(Chat())                          // sinon, un chat entrerait chez les chiens !
```

Le compilateur répond réellement : *initializer type mismatch: expected
MutableList<Animal>, actual MutableList<Chien>*. Il a raison : on pourrait ensuite
ajouter un `Chat` dans la liste de chiens.

En revanche, une `List<Chien>` **en lecture seule** peut servir de `List<Animal>` : on ne
peut qu'en lire des chiens, qui sont bien des animaux.

```kotlin
fun noms(animaux: List<Animal>) = animaux.joinToString { it.nom }
println(noms(listOf(Chien("Rex"), Chien("Médor"))))   // Rex, Médor
```

C'est la **variance** :

| Mot-clé | Sens | Exemple | Règle |
|---|---|---|---|
| `out T` | covariant : on **produit** des T | `List<out E>`, `Flow<out T>` | `Producteur<Chien>` est un `Producteur<Animal>` |
| `in T` | contravariant : on **consomme** des T | `Comparator<in T>` | `Consommateur<Animal>` est un `Consommateur<Chien>` |
| (rien) | invariant : on lit **et** on écrit | `MutableList<E>` | aucune substitution |

Moyen mnémotechnique : *Producer Extends, Consumer Super* (PECS) en Java, `out` pour
produire et `in` pour consommer en Kotlin.

## Compilation et exécution

| Approche | Principe | Exemples |
|---|---|---|
| Compilé à l'avance | traduit en code machine avant l'exécution | C, Rust, Go, images natives |
| Bytecode + machine virtuelle | compilé vers un code intermédiaire, exécuté par une VM avec JIT | Kotlin, Java, C# |
| Interprété | lu et exécuté au fur et à mesure | Python (CPython), Bash |
| Transpilé | traduit vers un autre langage | TypeScript → JavaScript |

Les frontières sont floues : Python compile en bytecode, JavaScript est compilé à la volée
par les navigateurs. Voir le parcours *JVM* pour le détail.

## Modules, paquets et dépendances

- Un **paquet** (`package dev.devforge.core`) range les classes et évite les conflits de noms.
- Un **module** (un module Gradle, comme `:core:domain` dans cette app) est une unité de
  compilation avec ses propres dépendances ; `internal` limite la visibilité au module.
- Les **dépendances** externes sont déclarées avec une version (`kotlinx-serialization-json:1.7.3`).
  Le **versionnage sémantique** (`MAJEUR.MINEUR.CORRECTIF`) promet : une version mineure
  n'apporte que des ajouts compatibles, une version majeure peut tout casser.

Découper en modules force les dépendances dans un seul sens : dans cette app, le domaine
ne peut pas utiliser Android, car `:core:domain` ne dépend pas d'Android.

## À retenir

- Génériques : un code pour plusieurs types, vérifié ; les bornes (`T : Comparable<T>`) précisent ce qu'on peut faire.
- `out` pour ce qu'on produit, `in` pour ce qu'on consomme ; une liste modifiable est invariante.
- Compilé, bytecode + VM, interprété, transpilé : des compromis entre démarrage, performance et portabilité.
- Paquets, modules, versions sémantiques : l'architecture commence par les dépendances.
