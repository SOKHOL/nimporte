---
id: frameworks.n4.les-serveurs
title: Fils, threads virtuels, boucle d'événements, Ktor et réactif
skills: [serveurs-web, ktor-plugins, reactif]
duration_min: 21
---

# Fils, threads virtuels, boucle d'événements, Ktor et réactif

## Trois façons de servir des requêtes

Toutes les mesures de cette leçon utilisent le même test : **50 requêtes simultanées** vers un
point d'accès qui attend **1 seconde** (comme un appel à une base ou à une API lente), sur une
machine à 4 cœurs. Le meilleur résultat possible est un peu plus d'une seconde.

**1. Un fil par requête** (Tomcat, le modèle classique des servlets). Chaque requête occupe un
fil système du début à la fin, y compris pendant qu'il **attend**. Réel, avec un pool limité à
10 fils (`server.tomcat.threads.max=10`) :

```
50 requêtes simultanées : total 5.1 s, la plus rapide 1.1 s, la plus lente 5.1 s
exemple de fil : Thread[#38,http-nio-8090-exec-6,5,main]
```

Cinq vagues de 10 : les autres requêtes attendent un fil libre. Le pool par défaut de Tomcat
compte 200 fils ; au-delà, la latence explose. Et on ne peut pas en créer des dizaines de
milliers : chaque fil système réserve une pile (1 Mo par défaut) et coûte au noyau.

**2. Les threads virtuels** (Java 21, projet Loom). Des fils légers gérés par la JVM : quand un
thread virtuel **bloque** (sommeil, socket, JDBC), la JVM le décroche de son fil système
porteur, qui en exécute un autre. Même application, une seule propriété en plus
(`spring.threads.virtual.enabled=true`), réel :

```
50 requêtes simultanées : total 1.1 s, la plus rapide 1.1 s, la plus lente 1.1 s
exemple de fil : VirtualThread[#48,tomcat-handler-11]/runnable@ForkJoinPool-1-worker-2
```

Le code reste **bloquant et simple**, mais n'occupe plus de fil système pendant l'attente. Les
limites : un bloc `synchronized` qui attend « épingle » le thread virtuel à son porteur
(corrigé dans Java 24), et la ressource limitante se déplace ailleurs (le pool de connexions à
la base, souvent 10 par défaut avec HikariCP).

**3. La boucle d'événements** (Netty, donc Ktor et Spring WebFlux ; Node.js). Quelques fils
seulement, un par cœur, qui ne doivent **jamais bloquer** : chaque opération d'entrée-sortie est
lancée, puis le fil passe à autre chose et reprend le travail quand la réponse arrive. En
Kotlin, ce sont les fonctions `suspend`. Ktor 2.3 sur Netty, réel :

```
/suspendu (delay(1000))         : total 1.1 s, la plus lente 1.0 s,  fil eventLoopGroupProxy-4-2
/bloquant (Thread.sleep(1000))  : total 13.0 s, la plus lente 13.0 s, fil eventLoopGroupProxy-4-2
```

Avec `delay`, 50 attentes se superposent sur les mêmes fils. Avec `Thread.sleep`, chaque appel
**gèle** un des quelques fils de la boucle : les 50 requêtes passent environ 4 par 4, et
**toutes les autres** routes du serveur sont gelées aussi, même les plus rapides. C'est l'erreur
numéro un avec ces frameworks : un appel JDBC, un `File.readText()` ou un client HTTP bloquant
au milieu d'un gestionnaire. Parade : `withContext(Dispatchers.IO) { … }` pour isoler le code
bloquant sur un pool prévu pour cela, ou des bibliothèques non bloquantes (R2DBC, client HTTP de
Ktor).

| Modèle | Code | Attente | Piège |
|---|---|---|---|
| fil par requête | bloquant, simple | occupe un fil système | pool épuisé |
| threads virtuels | bloquant, simple | ne coûte presque rien | épinglage, autres ressources limitantes |
| boucle d'événements | `suspend`, réactif | ne coûte presque rien | un seul appel bloquant gèle le serveur |

## Ktor et ses plugins

Ktor (JetBrains) est un framework Kotlin **explicite** : pas d'analyse de paquets ni
d'auto-configuration, on **installe** ce dont on a besoin. Chaque fonctionnalité est un
**plugin** branché sur le **pipeline** de traitement des appels :

```kotlin
embeddedServer(Netty, port = 8091) {
    install(Chrono)                                       // notre plugin
    install(ContentNegotiation) { jackson() }             // objets ↔ JSON
    install(StatusPages) {                                // exceptions → réponses
        exception<NumberFormatException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, mapOf("erreur" to "id doit être un nombre"))
        }
    }
    routing {
        get("/produits/{id}") { call.respond(Produit(call.parameters["id"]!!.toLong(), "Clavier")) }
    }
}
```

Un plugin maison se branche sur des **points d'accroche** du pipeline :

```kotlin
val Chrono = createApplicationPlugin("Chrono") {
    onCall { call -> call.attributes.put(Debut, System.nanoTime()) }
    onCallRespond { call, _ -> println("réponse en ${…} ms") }
}
```

Sortie réelle pour `/produits/7` puis `/produits/abc` :

```
1. plugin Chrono : début de /produits/7
2. route : id = 7 [eventLoopGroupProxy-4-2]
3. plugin Chrono : réponse en 0 ms
1. plugin Chrono : début de /produits/abc
2. StatusPages : For input string: "abc"
3. plugin Chrono : réponse en 26 ms
```

Même cycle qu'avec Spring (niveau 1), mais tout est visible dans le code : c'est le compromis
de Ktor. Moins de magie, démarrage rapide, mais l'assemblage (base de données, sécurité,
configuration) est à faire soi-même.

## Les frameworks réactifs

Avant les threads virtuels et les coroutines, la boucle d'événements en Java passait par la
**programmation réactive** : Spring WebFlux avec Project Reactor (`Mono<T>` pour 0 ou 1 valeur,
`Flux<T>` pour un flux), RxJava, Vert.x. Le code devient une chaîne d'opérateurs :

```java
return clients.findById(id)
    .flatMap(c -> commandes.findByClient(c.getId()).collectList())
    .map(cs -> new Resume(id, cs.size()))
    .timeout(Duration.ofSeconds(2));
```

Les apports : la **contre-pression** (*backpressure* : le consommateur dit combien il peut
recevoir, parcours Concurrence niveau 4), la composition de flux, et l'efficacité sous forte
charge. Les coûts : des piles d'appels illisibles en cas d'erreur, un débogage difficile, une
« contamination » de tout le code (une seule étape bloquante ruine tout), et une courbe
d'apprentissage raide.

Le conseil actuel : pour un service web classique, des **threads virtuels** (code bloquant
simple) ou des **coroutines** (Ktor, ou Spring avec des fonctions `suspend`) ; le réactif garde
sa place pour les vrais **flux** (diffusion en continu, WebSocket, traitement de messages avec
contre-pression).

## À retenir

- Fil par requête : 10 fils, 50 requêtes d'1 s, 5,1 s ; threads virtuels : 1,1 s, sans changer le code.
- Boucle d'événements : delay 1,1 s, Thread.sleep 13,0 s ; un seul appel bloquant gèle le serveur (Dispatchers.IO).
- Ktor : plugins explicites sur un pipeline (onCall, onCallRespond) ; le réactif (Mono, Flux) apporte la contre-pression, au prix de la lisibilité.
