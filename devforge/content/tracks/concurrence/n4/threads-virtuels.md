---
id: concurrence.n4.threads-virtuels
title: Les threads virtuels
skills: [threads-virtuels]
duration_min: 7
---

# Les threads virtuels

Un thread classique (« de plateforme ») correspond à un thread du système : environ 1 Mo
de pile réservée, une création coûteuse, quelques milliers au maximum. Un **thread
virtuel** (Java 21) est géré par la JVM : il coûte quelques centaines d'octets, et on
peut en créer des millions.

Quand un thread virtuel fait une opération bloquante (réseau, `sleep`, attente d'un
verrou), la JVM le **décroche** de son thread porteur (*carrier*), qui part exécuter un
autre thread virtuel. On écrit du code bloquant tout simple, et il passe à l'échelle
comme du code asynchrone.

```kotlin
Executors.newVirtualThreadPerTaskExecutor().use { executor ->
    repeat(10_000) { executor.submit { Thread.sleep(1_000) } }
}   // close() attend la fin de toutes les tâches
```

Mesuré pour ce cours : 10 000 tâches qui dorment chacune 1 seconde se terminent en
**1,08 seconde** au total. Avec un pool de 200 threads classiques, il faudrait 50 secondes.

## Quand les utiliser

- ✅ Les serveurs « un thread par requête » qui passent leur temps à attendre (HTTP,
  base de données).
- ❌ Le calcul pur : le nombre de cœurs ne change pas, aucun gain.
- ❌ Les mettre dans un pool : ils sont faits pour être créés et jetés, un par tâche.

## Les pièges

- **L'épinglage** (*pinning*) : jusqu'à Java 23, un thread virtuel qui bloque à
  l'intérieur d'un `synchronized` reste accroché à son porteur. Java 24 a levé cette
  limite ; sur les versions précédentes, préfère `ReentrantLock` dans le code très sollicité.
- **ThreadLocal** : un million de threads virtuels, c'est un million de copies.
- **Les ressources limitées** : un million de threads peuvent ouvrir un million de
  connexions, mais la base de données n'en supporte que quelques centaines. Limite avec
  un sémaphore ou un pool de connexions.

## Threads virtuels ou coroutines ?

Les deux résolvent le même problème : beaucoup d'attentes en parallèle. Les coroutines
apportent en plus la concurrence structurée et l'annulation, et fonctionnent partout où
Kotlin tourne, Android compris. Les threads virtuels rendent scalable le code Java
bloquant existant, sans le réécrire.
