---
id: metaprog.n2.proxies-dynamiques
title: Proxies dynamiques et préoccupations transverses
skills: [preoccupations-transverses, proxy-dynamique, delegation-kotlin]
duration_min: 10
---

# Proxies dynamiques et préoccupations transverses

## Le problème : du code qui s'étale partout

Journaliser chaque appel, mesurer les temps, ouvrir une transaction, vérifier les
droits, mettre en cache… Ces besoins concernent **toutes** les classes, mais ne font
partie du métier d'**aucune**. On les appelle des **préoccupations transverses**.

Les écrire à la main partout donne du code **dispersé** (le même code dans 200 méthodes)
et **emmêlé** (le métier noyé dans la plomberie).

## Première solution : un décorateur

```kotlin
interface Calculatrice {
    fun ajouter(a: Int, b: Int): Int
    fun multiplier(a: Int, b: Int): Int
}

class CalculatriceJournalisee(private val cible: Calculatrice) : Calculatrice by cible {
    override fun ajouter(a: Int, b: Int): Int {
        println("ajouter($a, $b)")
        return cible.ajouter(a, b)
    }
}
```

`by cible` est la **délégation Kotlin** : le compilateur génère toutes les méthodes non
redéfinies (ici `multiplier`) en les transmettant à `cible`. Pratique, mais il faut
encore un décorateur par interface.

## Deuxième solution : le proxy dynamique

Le JDK sait fabriquer **à l'exécution** une classe qui implémente n'importe quelle
interface, et qui envoie chaque appel à un seul gestionnaire :

```kotlin
import java.lang.reflect.Proxy

interface Calculatrice {
    fun ajouter(a: Int, b: Int): Int
}

class CalculatriceSimple : Calculatrice {
    override fun ajouter(a: Int, b: Int) = a + b
}

fun main() {
    val cible = CalculatriceSimple()
    val proxy = Proxy.newProxyInstance(
        Calculatrice::class.java.classLoader,
        arrayOf(Calculatrice::class.java),
    ) { _, methode, args ->
        println("→ ${methode.name}${args.toList()}")
        val resultat = methode.invoke(cible, *args)
        println("← $resultat")
        resultat
    } as Calculatrice

    println(proxy.ajouter(2, 3))
}
```

Sortie réelle :

```
→ ajouter[2, 3]
← 5
5
```

Le même gestionnaire fonctionnerait pour **n'importe quelle** interface : la
journalisation est écrite une seule fois. La classe du proxy est générée à la volée ;
son nom ressemble à `$Proxy0`.

## Où tu en croises tous les jours

- **Spring** : `@Transactional`, `@Cacheable`, `@Async`, la sécurité… sont des proxies
  autour de tes beans.
- **Retrofit** : tu déclares une interface annotée, Retrofit renvoie un proxy qui
  transforme chaque appel en requête HTTP.
- **Mockito, MockK** : un mock est un proxy qui enregistre les appels.

## Les limites

- Un proxy du JDK n'implémente **que des interfaces**. Pour une classe, il faut générer
  une **sous-classe** à l'exécution (CGLIB, ByteBuddy)… impossible si la classe est
  `final`. Or en Kotlin, les classes sont `final` par défaut : d'où le plugin
  `kotlin-spring` (*all-open*) qui les ouvre automatiquement pour Spring.
- Chaque appel passe par la réflexion : un léger coût.
- Seuls les appels qui **passent par le proxy** sont interceptés. Un objet qui appelle
  ses propres méthodes (`this.autreMethode()`) contourne le proxy. C'est le piège n°1
  de l'AOP avec Spring (niveau 3).

## À retenir

- Préoccupations transverses : le code de plomberie qui concerne tout le monde.
- Décorateur + délégation `by` : simple, explicite, une classe par interface.
- Proxy dynamique : une seule implémentation pour toutes les interfaces, générée à l'exécution.
- Toute la « magie » des frameworks repose sur ces proxies : comprends-les, et la magie disparaît.
