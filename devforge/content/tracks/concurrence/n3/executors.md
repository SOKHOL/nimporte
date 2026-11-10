---
id: concurrence.n3.executors
title: Pools de threads et CompletableFuture
skills: [executors, futures, threadlocal]
duration_min: 12
---

# Pools de threads et CompletableFuture

## Pourquoi un pool

Créer un thread coûte cher (environ 1 Mo de pile réservée, un appel système). En créer
un par tâche, sans limite, finit par épuiser la machine. Un **pool** garde un nombre
fixe de threads qui piochent les tâches dans une file.

```kotlin
val pool = Executors.newFixedThreadPool(8)
val futur: Future<Int> = pool.submit<Int> { calculer() }
val resultat = futur.get(2, TimeUnit.SECONDS)   // toujours un délai maximum
pool.shutdown()                                  // sinon ses threads gardent la JVM en vie
```

| Fabrique | Comportement | Attention |
|---|---|---|
| `newFixedThreadPool(n)` | n threads, file **sans limite** | sous la charge, la file grossit sans fin |
| `newCachedThreadPool()` | crée des threads à la demande, **sans limite** | un pic de trafic = des milliers de threads |
| `newScheduledThreadPool(n)` | tâches différées ou périodiques | une exception arrête la répétition, en silence |
| `ThreadPoolExecutor(…)` | tout est réglable : taille, file bornée, politique de rejet | le bon choix en production |

En production, on veut une **file bornée** et une **politique de rejet** explicite :
quand le système est saturé, mieux vaut refuser vite que s'effondrer lentement. C'est le
principe de la *backpressure*.

## Combien de threads ?

- Tâches de **calcul** : à peu près le nombre de cœurs. Davantage ne sert à rien.
- Tâches qui **attendent** (réseau, disque) : `cœurs × (1 + attente / calcul)`.
  Avec 8 cœurs, 90 ms d'attente et 10 ms de calcul par tâche : 8 × (1 + 9) = **80 threads**.
- Puis **mesure** : ces formules ne sont que des points de départ.

## CompletableFuture : composer sans bloquer

```kotlin
val prix = CompletableFuture.supplyAsync { 100 }
val remise = CompletableFuture.supplyAsync { 20 }
val total = prix.thenCombine(remise) { p, r -> p - r }   // attend les deux résultats
    .thenApply { it * 2 }                                // transforme
println(total.join())                                    // 160
```

Une erreur se propage le long de la chaîne (les étapes suivantes sont sautées) et se
rattrape avec `exceptionally` ou `handle`. Piège : l'exception reçue est souvent
enveloppée dans une `CompletionException` ; la vraie est dans `cause`.

```kotlin
val resultat = CompletableFuture.supplyAsync<Int> { throw IllegalStateException("base indisponible") }
    .thenApply { it * 2 }                         // sautée : la chaîne est en erreur
    .exceptionally { e ->
        println("Erreur : ${e.cause?.message}")   // Erreur : base indisponible
        0
    }
```

Autre piège : sans exécuteur précisé, `supplyAsync` utilise le **pool commun**
(`ForkJoinPool.commonPool()`), dimensionné pour le calcul (nombre de cœurs − 1). Y faire
des appels réseau bloquants l'épuise et ralentit **toute** l'application, y compris les
traitements parallèles qui l'utilisent aussi. Passe ton propre pool :
`supplyAsync({ appelHttp() }, poolEntreesSorties)`.

## ThreadLocal, rappel

Un `ThreadLocal` donne à chaque thread sa propre valeur (contexte de requête, formateur
non thread-safe). Dans un pool, les threads sont **réutilisés** : nettoie toujours avec
`remove()` dans un `finally`, sinon la valeur fuit vers la tâche suivante (voir le
parcours *Gestion de la mémoire*, niveau 4).

## À retenir

- Un pool borné, une file bornée, une politique de rejet, et un `shutdown()`.
- Taille : les cœurs pour le calcul, davantage pour les attentes, puis mesurer.
- `CompletableFuture` compose ; jamais d'attente bloquante dans le pool commun ; un délai sur chaque `get`.
