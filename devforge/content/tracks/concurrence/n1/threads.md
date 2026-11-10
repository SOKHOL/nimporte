---
id: concurrence.n1.threads
title: Threads et conditions de course
skills: [concurrence-parallelisme, processus-threads, creer-threads, race-condition]
duration_min: 10
---

# Threads et conditions de course

## Concurrence ou parallélisme ?

- **Concurrence** : *gérer* plusieurs tâches qui progressent en même temps, en les
  entrelaçant. Possible sur un seul cœur.
- **Parallélisme** : *exécuter* plusieurs tâches au même instant, sur plusieurs cœurs.

Un serveur qui jongle avec 10 000 connexions sur un cœur est concurrent sans être
parallèle. Un calcul matriciel découpé sur 8 cœurs est parallèle.

## Processus et threads

| | Processus | Thread |
|---|---|---|
| Mémoire | espace **isolé** | **partage** le tas du processus |
| Pile | — | une pile **par thread** |
| Création | coûteuse | moins coûteuse (mais pas gratuite : ~1 Mo de pile réservée) |
| Communication | fichiers, sockets, pipes | variables partagées… d'où les problèmes |

## Créer et attendre un thread

```kotlin
fun main() {
    val t = Thread { println("B") }
    println("A")
    t.start()      // lance le thread : le code s'exécute en parallèle
    t.join()       // attend sa fin
    println("C")   // affiche toujours A, B, C
}
```

Piège classique : `run()` n'est **pas** `start()`.

```kotlin
val t = Thread({ println(Thread.currentThread().name) }, "worker")
t.run()     // affiche "main" : simple appel de méthode, dans le thread courant
t.start()   // affiche "worker" : nouveau thread
```

## La condition de course

```kotlin
var compteur = 0

fun main() {
    val threads = List(2) { Thread { repeat(100_000) { compteur++ } } }
    threads.forEach { it.start() }
    threads.forEach { it.join() }
    println(compteur)   // 200 000 ? Pas toujours.
}
```

`compteur++` ressemble à une seule opération, mais ce sont **trois** étapes :
lire, ajouter 1, écrire. Si deux threads s'entrelacent mal :

| Thread A | Thread B | compteur en mémoire |
|---|---|---|
| lit 41 | | 41 |
| | lit 41 | 41 |
| écrit 42 | | 42 |
| | écrit 42 | 42 ← un incrément perdu |

En lançant ce programme cinq fois pour préparer ce cours, on a obtenu :
`124 927`, puis quatre fois `200 000`. **Le bug n'apparaît pas à chaque exécution.**
C'est ce qui rend les bugs de concurrence si dangereux : un test qui passe ne prouve
rien. Ils sortent en production, sous charge, sur une autre machine.

## Les trois façons de s'en sortir

1. **Ne pas partager** : chaque thread a ses propres données, on combine à la fin.
2. **Ne pas modifier** : des objets immuables se partagent sans risque.
3. **Synchroniser** : verrous, variables atomiques… (niveau 2).

Les deux premières sont les meilleures : il n'y a rien à synchroniser.

## À retenir

- Concurrence = structure, parallélisme = exécution simultanée.
- Les threads d'un processus partagent le tas ; chacun a sa pile.
- `start()` lance un thread, `run()` non. `join()` attend la fin.
- `x++` n'est pas atomique. Un état **partagé** et **modifiable** sans synchronisation
  est un bug, même si les tests passent.
