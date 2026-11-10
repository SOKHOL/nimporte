---
id: concurrence.n2.synchronisation
title: Synchroniser sans se bloquer
skills: [synchronized, visibilite-volatile, atomiques, interblocage, collections-concurrentes]
duration_min: 12
---

# Synchroniser sans se bloquer

## Exclusion mutuelle : synchronized

Un verrou garantit qu'**un seul thread à la fois** exécute une section de code.

```kotlin
val verrou = Any()
var total = 0

fun incrementer() {
    synchronized(verrou) {   // un seul thread à la fois ici
        total++
    }
}
```

Tout objet peut servir de verrou (*moniteur*). `synchronized` garantit aussi la
**visibilité** : ce qu'un thread a écrit avant de relâcher le verrou est visible par
le thread qui le prend ensuite.

## Le problème de visibilité

Sans synchronisation, un thread peut **ne jamais voir** une écriture faite par un autre :
les valeurs peuvent rester dans un cache du processeur, et le compilateur JIT a le droit
de réordonner ou de sortir une lecture d'une boucle.

```kotlin
var arreter = false            // ⚠️ sans @Volatile

fun travailler() {
    while (!arreter) { /* … */ }   // peut tourner indéfiniment
}
```

`@Volatile` garantit que chaque lecture voit la dernière écriture :

```kotlin
@Volatile var arreter = false
```

Mais `@Volatile` ne rend **pas** `compteur++` atomique : lire, ajouter, écrire restent
trois étapes. Volatile, c'est la **visibilité**, pas l'**atomicité**.

## Les variables atomiques

`AtomicInteger`, `AtomicLong`, `AtomicReference` font des opérations lire-modifier-écrire
atomiques **sans verrou**, grâce à une instruction du processeur : le *compare-and-swap* (CAS).

```kotlin
val compteur = AtomicInteger()
compteur.incrementAndGet()   // atomique
```

Vérifié pour ce cours : avec deux threads et 100 000 incréments chacun, `AtomicInteger`
et `synchronized` donnent toujours 200 000, contrairement à `compteur++` nu.

## Les collections concurrentes

| Besoin | Outil |
|---|---|
| Map partagée | `ConcurrentHashMap` (et ses opérations atomiques `merge`, `compute`) |
| Liste lue souvent, modifiée rarement | `CopyOnWriteArrayList` |
| File producteur → consommateur | `LinkedBlockingQueue`, `ArrayBlockingQueue` |

```kotlin
val compteurs = ConcurrentHashMap<String, Int>()

fun enregistrerVisite(page: String) {
    compteurs.merge(page, 1, Int::plus)   // atomique, sans verrou explicite
}
```

Attention : `if (!map.containsKey(k)) map.put(k, v)` n'est **pas** atomique, même sur
une `ConcurrentHashMap`. Deux opérations sûres ne font pas une opération sûre :
utilise `putIfAbsent`, `computeIfAbsent` ou `merge`.

## L'interblocage (deadlock)

```kotlin
fun virer(de: Compte, vers: Compte, montant: Long) {
    synchronized(de) {
        synchronized(vers) {
            de.solde -= montant
            vers.solde += montant
        }
    }
}
```

Thread 1 : `virer(a, b)` prend le verrou de `a` et attend `b`.
Thread 2 : `virer(b, a)` prend le verrou de `b` et attend `a`.
Les deux attendent pour toujours. En préparant ce cours, ce code s'est bloqué en moins
de 5 secondes.

Un interblocage demande quatre conditions : exclusion mutuelle, détention et attente,
pas de préemption, **attente circulaire**. Il suffit d'en casser une. Le plus simple :
**toujours prendre les verrous dans le même ordre global**.

```kotlin
fun virer(de: Compte, vers: Compte, montant: Long) {
    val (premier, second) = if (de.id < vers.id) de to vers else vers to de
    synchronized(premier) {
        synchronized(second) {
            de.solde -= montant
            vers.solde += montant
        }
    }
}
```

Pour diagnostiquer un blocage en production : `jstack <pid>` ou `jcmd <pid> Thread.print`.
La JVM détecte les interblocages de moniteurs et affiche `Found one Java-level deadlock`.

## À retenir

- `synchronized` : exclusion mutuelle **et** visibilité.
- `@Volatile` : visibilité seulement, pas d'atomicité.
- Atomiques et collections concurrentes : sûrs pour **une** opération ; deux opérations
  sûres enchaînées ne le sont pas forcément.
- Interblocage : prends toujours les verrous dans le même ordre.
- Le meilleur verrou reste celui dont on n'a pas besoin : immutabilité et confinement.
