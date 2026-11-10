---
id: concurrence.n5.sans-verrou-et-acteurs
title: Sans verrou, acteurs et conception concurrente
skills: [sans-verrou, barrieres-memoire, acteurs, tester-concurrence, concevoir-concurrent]
duration_min: 15
---

# Sans verrou, acteurs et conception concurrente

## Le compare-and-swap (CAS)

Le processeur fournit une instruction atomique : « remplace la valeur par B **seulement
si** elle vaut encore A ». C'est le **CAS**, la base de toutes les variables atomiques et
des algorithmes *sans verrou* (*lock-free*) : au lieu de bloquer les autres, on tente,
et on recommence si quelqu'un est passé avant.

```kotlin
class PileSansVerrou<T> {
    private class Noeud<T>(val valeur: T, val suivant: Noeud<T>?)
    private val sommet = AtomicReference<Noeud<T>?>(null)

    fun empiler(valeur: T) {
        while (true) {
            val actuel = sommet.get()
            val nouveau = Noeud(valeur, actuel)
            if (sommet.compareAndSet(actuel, nouveau)) return   // sinon, on recommence
        }
    }

    fun depiler(): T? {
        while (true) {
            val actuel = sommet.get() ?: return null
            if (sommet.compareAndSet(actuel, actuel.suivant)) return actuel.valeur
        }
    }
}
```

C'est la **pile de Treiber**. Testée pour ce cours : 4 threads empilent 100 000
éléments chacun, et les 400 000 se retrouvent bien dans la pile, sans un seul verrou.

- ✅ Aucun thread ne peut bloquer les autres (pas d'interblocage, pas de thread endormi
  qui tient un verrou).
- ❌ Sous forte contention, les tentatives ratées se multiplient.
- ❌ Ces algorithmes sont **très** difficiles à écrire correctement : utilise ceux du JDK
  (`ConcurrentLinkedQueue`, `ConcurrentHashMap`, les atomiques) plutôt que les tiens.

## Le problème ABA

Un thread lit A, est interrompu ; un autre change A en B puis de nouveau en A. Le premier
fait son CAS : la valeur vaut A, le CAS réussit… alors que la structure a changé entre-temps.
En C/C++, quand des nœuds sont recyclés, c'est un vrai danger. Sur la JVM, le ramasse-miettes
empêche qu'un nœud encore référencé soit réutilisé, ce qui évite ce problème pour les
références ; il reste possible avec des valeurs (des entiers, des index). Parade :
`AtomicStampedReference`, qui ajoute un numéro de version à la valeur.

## Quand la contention coûte cher

Un `AtomicLong` incrémenté par beaucoup de threads devient un point chaud : tous se
disputent la même ligne de cache et les CAS échouent en boucle. `LongAdder` répartit le
compteur en plusieurs cellules et ne fait la somme qu'à la lecture.

Mesuré pour ce cours, 4 threads × 20 millions d'incréments : **2 149 ms** avec
`AtomicLong`, **245 ms** avec `LongAdder`. Le bon outil pour des statistiques souvent
écrites et rarement lues.

## Barrières mémoire

Derrière `@Volatile`, les verrous et les CAS, la JVM place des **barrières mémoire** :
des instructions qui interdisent au processeur et au compilateur de déplacer certaines
lectures et écritures de part et d'autre. C'est leur coût qui rend une écriture volatile
plus chère qu'une écriture ordinaire. `VarHandle` permet de choisir des garanties plus
faibles (*acquire/release*, *opaque*) pour les structures très optimisées : un domaine
réservé aux auteurs de bibliothèques.

## Le modèle d'acteurs

Plutôt que de protéger un état partagé, on ne le partage pas : un **acteur** possède son
état, reçoit des **messages** dans une boîte aux lettres, et les traite **un par un**.
C'est le modèle d'Erlang, d'Akka, et il s'écrit très bien avec les coroutines :

```kotlin
sealed interface Message {
    data object Incrementer : Message
    class Lire(val reponse: CompletableDeferred<Int>) : Message
}

val boiteAuxLettres = Channel<Message>(capacity = Channel.UNLIMITED)
val acteur = launch {
    var compteur = 0                      // l'état n'appartient qu'à l'acteur
    for (message in boiteAuxLettres) {
        when (message) {
            Message.Incrementer -> compteur++
            is Message.Lire -> message.reponse.complete(compteur)
        }
    }
}
```

Testé pour ce cours : 1 000 coroutines sur plusieurs threads envoient 100 messages
chacune, et le compteur vaut exactement 100 000, sans verrou ni variable atomique.

## Tester du code concurrent

Un test qui passe ne prouve rien (rappel : une exécution sur cinq a perdu des incréments
dans le niveau 1). Les outils des experts :

- **jcstress** (OpenJDK) : exécute des millions de fois de petits scénarios
  concurrents et rapporte tous les résultats observés, y compris les « impossibles ».
- **Lincheck** (JetBrains) : vérifie automatiquement qu'une structure concurrente se
  comporte comme sa version séquentielle, en explorant les entrelacements.
- Les **tests de charge** et l'injection de délais aléatoires pour faire sortir les courses.

## Concevoir pour la concurrence

L'ordre de préférence d'un expert :

1. **Ne rien partager** : chaque thread ou coroutine a ses données, on combine à la fin.
2. **Partager de l'immuable** : des `val`, des collections immuables.
3. **Passer des messages** : files, canaux, acteurs.
4. Utiliser des **structures concurrentes** du JDK.
5. En dernier recours, des **verrous**, avec un ordre global documenté.

## À retenir

- Le CAS est la brique des algorithmes sans verrou ; utilise ceux du JDK.
- ABA : un risque réel avec des valeurs recyclées ; `AtomicStampedReference` le corrige.
- `LongAdder` bat `AtomicLong` sous contention (245 ms contre 2 149 ms, mesuré).
- Acteurs : un état, une boîte aux lettres, un message à la fois.
- On teste la concurrence avec jcstress, Lincheck et la charge ; et on la conçoit en partageant le moins possible.
