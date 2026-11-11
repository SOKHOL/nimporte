---
id: concurrence.n4.flux-reactifs
title: Flux réactifs et contre-pression
skills: [flux-reactifs]
duration_min: 14
---

# Flux réactifs et contre-pression

Une fonction `suspend` renvoie **une** valeur. Beaucoup de données arrivent en **flux** :
des mesures de capteur, des messages Kafka, les changements d'une base, les frappes au
clavier. Les flux réactifs traitent ces séquences de façon asynchrone, et règlent le
problème central : que faire quand le producteur va plus vite que le consommateur ?

## Flow : un flux froid

En Kotlin, un `Flow` est **froid** : il ne fait rien tant que personne ne le collecte, et
chaque collecte le relance depuis le début. Sortie réelle :

```kotlin
val nombres = flow {
    println("démarrage")
    emit(1)
    emit(2)
}
println("flux créé")
nombres.collect { println("A reçoit $it") }
println(nombres.map { it * 10 }.toList())
```

```
flux créé
démarrage
A reçoit 1
A reçoit 2
démarrage
[10, 20]
```

`démarrage` s'affiche **deux fois** : chaque collecteur a sa propre exécution. Un flux froid
qui appelle une API l'appellera autant de fois qu'il y a de collecteurs.

Les flux **chauds** existent indépendamment de leurs collecteurs : `SharedFlow` (des
événements diffusés à tous les abonnés présents) et `StateFlow` (une valeur courante, comme
l'état d'un écran). `shareIn` et `stateIn` transforment un flux froid en flux chaud partagé.

Les opérateurs ressemblent à ceux des collections : `map`, `filter`, `take`, `debounce`
(attendre une pause, pour une recherche au clavier), `combine` (dernière valeur de
plusieurs flux), `flatMapLatest` (annuler la requête précédente quand une nouvelle arrive).

## La contre-pression

La **contre-pression** (*backpressure*) est la façon dont un consommateur lent freine un
producteur rapide. Sans elle, les éléments s'accumulent en mémoire jusqu'à
l'`OutOfMemoryError`.

Mesure réelle : un producteur émet une mesure toutes les 100 ms (5 mesures), le
consommateur met 300 ms à traiter chacune.

| Stratégie | Éléments traités | Durée totale | Quand l'utiliser |
|---|---|---|---|
| aucune (par défaut) | `[1, 2, 3, 4, 5]` | 2000 ms | le producteur attend le consommateur : c'est déjà de la contre-pression |
| `buffer()` | `[1, 2, 3, 4, 5]` | 1600 ms | tout traiter, mais en parallèle de la production |
| `conflate()` | `[1, 3, 5]` | 1000 ms | seule la **dernière** valeur compte (position GPS, cours de bourse) |
| `collectLatest` | `[5]` | 800 ms | un nouvel élément **annule** le traitement en cours (résultats de recherche) |

Par défaut, un `Flow` est séquentiel : `emit` suspend tant que le collecteur n'a pas fini.
Le producteur est donc freiné naturellement. `buffer` découple les deux avec une file
bornée (64 éléments par défaut) ; quand elle est pleine, le producteur est suspendu à
nouveau. `conflate` saute les valeurs intermédiaires, `collectLatest` abandonne le travail
devenu inutile.

## Reactive Streams et les autres bibliothèques

Sur la JVM, la norme **Reactive Streams** (reprise dans `java.util.concurrent.Flow`)
définit quatre interfaces : `Publisher`, `Subscriber`, `Subscription` et `Processor`. La
contre-pression y est explicite : le consommateur appelle `request(n)` pour annoncer combien
d'éléments il peut recevoir, et le producteur n'a pas le droit d'en envoyer plus.

Project Reactor (`Flux`, `Mono`, au cœur de Spring WebFlux), RxJava et Akka Streams
l'implémentent. Kotlin fait le pont avec `asFlow()` et `asPublisher()`.

| | Flow (Kotlin) | Reactor / RxJava |
|---|---|---|
| Style | code séquentiel, fonctions `suspend` | chaînes d'opérateurs |
| Contre-pression | suspension | `request(n)` |
| Débogage | stack traces lisibles | difficile sans outils dédiés |

## Les pièges

- Faire un appel bloquant dans un flux exécuté sur un petit pool : tout le pool est
  bloqué. `flowOn(Dispatchers.IO)` change le contexte du producteur.
- Oublier qu'un flux froid est relancé pour chaque collecteur (deux appels réseau au lieu
  d'un) : partager avec `shareIn`.
- Un `buffer(Channel.UNLIMITED)` supprime la contre-pression : la mémoire devient la limite.
- Avaler les erreurs : `catch` ne capture que les erreurs **en amont** de lui.

## À retenir

- `Flow` est froid : rien sans collecteur, une exécution par collecteur. `SharedFlow` et `StateFlow` sont chauds.
- Contre-pression = le consommateur lent freine le producteur. Par défaut, `emit` suspend.
- `buffer` (tout traiter, en parallèle), `conflate` (la dernière valeur), `collectLatest` (annuler l'ancien).
- Reactive Streams : `request(n)` ; Reactor et RxJava, interopérables avec Flow.
