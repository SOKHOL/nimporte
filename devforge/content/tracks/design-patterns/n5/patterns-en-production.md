---
id: design-patterns.n5.patterns-en-production
title: Les patterns en situation réelle
skills: [patterns-frameworks, integration-patterns, resilience-patterns, sur-ingenierie]
duration_min: 15
---

# Les patterns en situation réelle

## Les patterns cachés dans tes outils

| Outil | Pattern | Où |
|---|---|---|
| OkHttp | Chain of Responsibility | les intercepteurs |
| OkHttp, Retrofit | Builder | `OkHttpClient.Builder()`, `Retrofit.Builder()` |
| Retrofit | Proxy | l'implémentation de ton interface d'API |
| Android | Adapter | `RecyclerView.Adapter` relie tes données aux vues |
| Android | Observer | `StateFlow`, `LiveData` |
| Android | Factory | `ViewModelProvider.Factory` |
| Spring | Proxy | `@Transactional`, `@Cacheable` |
| Spring | Template Method | `JdbcTemplate`, `RestTemplate` |
| Java I/O | Decorator | `BufferedReader(InputStreamReader(…))` |

Reconnaître ces patterns permet de comprendre une bibliothèque en quelques minutes, et
de deviner où elle est extensible.

## Les patterns de résilience

Dans un système distribué, **tout finit par tomber en panne** : le réseau, une base, un
service voisin. Ces patterns évitent qu'une panne locale devienne une panne générale.

- **Timeout** : ne jamais attendre sans limite. Un appel sans délai maximum peut bloquer
  un thread pour toujours, puis tous les threads, puis tout le service.
- **Retry avec backoff exponentiel et jitter** : réessayer, mais en attendant de plus en
  plus longtemps (100 ms, 200 ms, 400 ms…) avec une part aléatoire, pour ne pas que tous
  les clients réessaient en même temps et achèvent le service qui se relevait. Seulement
  pour les opérations **idempotentes**.
- **Circuit breaker** (disjoncteur) : après trop d'échecs, on arrête d'appeler le service
  malade pendant un moment, et on répond tout de suite (erreur ou solution de repli).
- **Bulkhead** (cloison) : des pools séparés par dépendance ; si un service est lent, il
  n'épuise que son propre pool.
- **Fallback** : une réponse dégradée plutôt qu'une erreur (un cache, une valeur par défaut).

Un disjoncteur minimal :

```kotlin
class Disjoncteur(private val seuil: Int) {
    var etat = "fermé"
        private set
    private var echecs = 0

    fun appeler(action: () -> String): String {
        if (etat == "ouvert") return "refusé"
        return try {
            val resultat = action()
            echecs = 0
            resultat
        } catch (e: Exception) {
            echecs++
            if (echecs >= seuil) etat = "ouvert"
            "échec"
        }
    }
}
```

Avec un seuil de 2 : deux appels qui échouent, puis un appel qui aurait réussi, affichent
réellement `échec`, `échec`, `refusé` ; l'état final est `ouvert`. Un vrai disjoncteur
ajoute un état **semi-ouvert** : après un délai, il laisse passer un appel d'essai, et se
referme s'il réussit. Bibliothèque de référence : Resilience4j.

## Les patterns d'intégration (EIP)

Quand des systèmes communiquent par messages, ces patterns reviennent sans cesse :

| Pattern | Rôle |
|---|---|
| Canal de messages | une file ou un sujet (Kafka, RabbitMQ) |
| Routeur | envoie chaque message vers la bonne destination selon son contenu |
| Traducteur | convertit un format de message en un autre |
| Agrégateur | regroupe plusieurs messages en un seul |
| File des lettres mortes | met de côté les messages impossibles à traiter |
| Récepteur idempotent | traite deux fois le même message sans effet en double |
| Outbox transactionnelle | écrit en base et publie le message de façon fiable, sans les perdre ni les dupliquer |

## Éviter la sur-ingénierie

Un expert connaît tous ces patterns… et en utilise **peu**.

- **YAGNI** (*You Aren't Gonna Need It*) : n'ajoute pas une abstraction pour un besoin
  hypothétique.
- **Règle de trois** : on abstrait à la troisième répétition, pas à la première.
- Une interface avec **une seule** implémentation, et aucune prévue, est souvent du bruit
  (sauf aux frontières de l'application, pour les tests).
- Le code le plus maintenable est souvent le plus **ennuyeux**.

## À retenir

- Les bibliothèques sont faites de patterns : les reconnaître, c'est les comprendre.
- Résilience : timeout, retry avec backoff et jitter, disjoncteur, cloisons, solution de repli.
- Messages : routeur, traducteur, agrégateur, lettres mortes, idempotence, outbox.
- Un pattern se mérite : YAGNI et règle de trois.
