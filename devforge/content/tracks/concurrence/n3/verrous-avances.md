---
id: concurrence.n3.verrous-avances
title: Verrous avancés et ThreadLocal
skills: [verrous-avances, threadlocal]
duration_min: 13
---

# Verrous avancés et ThreadLocal

`synchronized` suffit dans la plupart des cas. Le paquet `java.util.concurrent.locks`
répond aux cas où il ne suffit plus.

## ReentrantLock : ce que synchronized ne sait pas faire

```kotlin
val verrou = ReentrantLock()

fun transferer() {
    if (!verrou.tryLock(200, TimeUnit.MILLISECONDS)) {
        throw TimeoutException("compte occupé, on réessaiera")   // ne pas attendre indéfiniment
    }
    try {
        // section critique
    } finally {
        verrou.unlock()                                          // TOUJOURS dans finally
    }
}
```

| Besoin | `synchronized` | `ReentrantLock` |
|---|---|---|
| Libération automatique, même en cas d'exception | oui | non : `unlock()` dans `finally` |
| Abandonner après un délai (`tryLock(délai)`) | non | oui : évite les interblocages définitifs |
| Être interrompu pendant l'attente (`lockInterruptibly`) | non | oui |
| Équité (premier arrivé, premier servi) | non | `ReentrantLock(true)`, plus lent |
| Plusieurs files d'attente (`Condition`) | une seule (`wait`/`notify`) | autant que nécessaire |

« Réentrant » signifie qu'un thread qui tient déjà le verrou peut le reprendre (une méthode
verrouillée qui en appelle une autre) ; il doit alors le libérer autant de fois.

En Kotlin, `verrou.withLock { … }` écrit le `try`/`finally` à ta place.

## Les conditions : attendre qu'un état change

Une `Condition` permet d'attendre **à l'intérieur** d'un verrou qu'une condition devienne
vraie, en libérant le verrou pendant l'attente.

```kotlin
class FileBornee<T>(private val capacite: Int) {
    private val verrou = ReentrantLock()
    private val pasVide = verrou.newCondition()
    private val pasPleine = verrou.newCondition()
    private val elements = ArrayDeque<T>()

    fun deposer(x: T) = verrou.withLock {
        while (elements.size == capacite) pasPleine.await()   // while, jamais if
        elements.addLast(x)
        pasVide.signal()
    }

    fun prendre(): T = verrou.withLock {
        while (elements.isEmpty()) pasVide.await()
        elements.removeFirst().also { pasPleine.signal() }
    }
}
```

Toujours `while`, jamais `if` : un thread peut se réveiller sans signal (*spurious
wakeup*), ou un autre consommateur peut avoir pris l'élément entre le signal et le réveil.
Après chaque réveil, on revérifie la condition. (En pratique, on utilise
`ArrayBlockingQueue`, qui est exactement ce code.)

## ReadWriteLock et StampedLock

Quand les lectures sont bien plus fréquentes que les écritures (un cache de configuration),
`ReentrantReadWriteLock` laisse **plusieurs lecteurs** entrer en même temps, mais un
écrivain seul.

`StampedLock` va plus loin avec la **lecture optimiste** : on lit sans verrou, puis on
vérifie qu'aucune écriture n'a eu lieu entre-temps.

```kotlin
var stamp = verrou.tryOptimisticRead()
var x = this.x; var y = this.y
if (!verrou.validate(stamp)) {             // une écriture a eu lieu : on relit sous verrou
    stamp = verrou.readLock()
    try { x = this.x; y = this.y } finally { verrou.unlockRead(stamp) }
}
```

Attention : `StampedLock` n'est **pas** réentrant, et ces verrous ne gagnent que si les
lectures dominent nettement. Mesurer avant de remplacer un `synchronized`.

## ThreadLocal : une valeur par thread

`ThreadLocal` donne à chaque thread **sa propre copie** d'une variable. Usages classiques :
le contexte d'une requête (utilisateur, identifiant de trace pour les journaux, le `MDC` de
SLF4J), la transaction courante de Spring, un `SimpleDateFormat` (non thread-safe) par
thread.

Le piège : dans un **pool**, les threads sont réutilisés. Une valeur jamais effacée
« fuit » vers la tâche suivante. Démonstration réelle avec un pool d'un thread :

```kotlin
val utilisateur = ThreadLocal<String>()

fun traiter(requete: String, connecte: String?) {
    if (connecte != null) utilisateur.set(connecte)
    println("$requete traitée pour ${utilisateur.get()}")
}

pool.submit { traiter("requête 1", "ada") }.get()
pool.submit { traiter("requête 2", null) }.get()
// requête 1 traitée pour ada
// requête 2 traitée pour ada      ← une requête anonyme hérite de l'identité d'Ada
```

C'est une faille de sécurité, et aussi une fuite mémoire (la valeur reste en vie tant que
le thread vit, et avec elle son *ClassLoader* lors d'un redéploiement). Règle :

```kotlin
utilisateur.set(connecte)
try { /* traitement */ } finally { utilisateur.remove() }
```

Avec les coroutines, un `ThreadLocal` ne suit pas la coroutine d'un thread à l'autre : on
utilise le contexte de coroutine (`asContextElement()`). Avec les threads virtuels (des
millions de threads), Java 25 propose les **ScopedValue** : une valeur liée à une portée,
immuable et libérée automatiquement à sa sortie.

## À retenir

- `ReentrantLock` : `tryLock` avec délai, interruption, équité, conditions ; `unlock()` dans `finally` (ou `withLock`).
- `Condition.await()` toujours dans un `while`.
- `ReadWriteLock`, `StampedLock` : seulement si les lectures dominent, et après mesure.
- `ThreadLocal` dans un pool : toujours `remove()` dans `finally` ; `ScopedValue` pour les threads virtuels.
