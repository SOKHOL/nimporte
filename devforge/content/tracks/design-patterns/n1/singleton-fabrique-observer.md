---
id: design-patterns.n1.premiers-patterns
title: Pourquoi des patterns, Singleton, Factory Method et Observer
skills: [pourquoi-patterns, singleton, factory-method, observer]
duration_min: 14
---

# Pourquoi des patterns, Singleton, Factory Method et Observer

## Pourquoi des patterns ?

Un *design pattern* est une **solution éprouvée à un problème de conception qui revient
souvent**, décrite assez abstraitement pour s'adapter à chaque contexte. Le livre du
« Gang of Four » (1994) en a catalogué 23, répartis en trois familles :

| Famille | Question | Exemples |
|---|---|---|
| Création | Comment créer les objets ? | Singleton, Factory Method, Builder |
| Structure | Comment assembler les objets ? | Adapter, Decorator, Composite |
| Comportement | Comment les objets collaborent ? | Strategy, Observer, Command |

Ce qu'ils apportent vraiment :

- **Un vocabulaire** : « mets un Decorator autour du client HTTP » dit en cinq mots ce qui
  prendrait un paragraphe.
- **Des compromis connus** : chaque pattern a ses coûts, documentés depuis 30 ans.
- **Des repères** pour lire du code existant : les frameworks en sont pleins.

Le piège du débutant est l'inverse : appliquer des patterns **partout**, « pour faire
propre ». Un pattern se justifie par un problème présent, pas par un problème imaginé.
Beaucoup de patterns GoF compensaient des manques de C++ et de Java de 1994 : en Kotlin, une
lambda remplace souvent une Strategy, `object` un Singleton, `by` un Decorator.

## Singleton

**Problème :** une seule instance doit exister (un cache partagé, un pool de connexions),
avec un point d'accès connu.

```kotlin
object Configuration {          // Kotlin : instance unique, créée au premier accès, thread-safe
    val urlBase = System.getenv("API_URL") ?: "https://api.exemple.com"
}
```

Le problème n'est pas l'instance unique, c'est l'**accès global** : n'importe quel code peut
appeler `Configuration`, ce qui cache les dépendances, couple le code à une classe concrète
et fait dépendre les tests les uns des autres. La version moderne : **une seule instance,
injectée** là où elle sert.

```kotlin
class ServiceCommandes(private val config: Configuration)   // la dépendance est visible
```

Les frameworks d'injection (Spring, Koin, Hilt) font exactement ça : leurs *beans*
« singleton » sont des instances uniques passées en paramètre, pas des variables globales.

## Factory Method

**Problème :** une classe sait **quand** créer un objet, mais pas **lequel** ; ce choix
appartient à ses sous-classes.

```kotlin
abstract class Service {
    abstract fun creerNotification(): Notification          // la méthode fabrique
    fun alerter(message: String) = println(creerNotification().envoyer(message))
}
class ServiceBureau : Service() { override fun creerNotification() = Email("ops@exemple.com") }
class ServiceAstreinte : Service() { override fun creerNotification() = Sms("0600000000") }
```

`alerter` contient la logique commune ; chaque sous-classe décide quel objet créer. Sortie
réelle pour les deux services : `e-mail à ops@exemple.com : disque plein`, puis `SMS au
0600000000 : disque plein`.

Dans la pratique, on appelle aussi « fabrique » une simple fonction qui choisit la classe
concrète, souvent dans un `companion object` :

```kotlin
sealed interface Paiement {
    companion object {
        fun depuis(moyen: String): Paiement = when (moyen) {
            "carte" -> Carte()
            "virement" -> Virement()
            else -> throw IllegalArgumentException("moyen inconnu : $moyen")
        }
    }
}
```

Avantages : un nom parlant (`Paiement.depuis("carte")`), la possibilité de renvoyer un objet
en cache ou une sous-classe, et un seul endroit à modifier quand un cas apparaît.

## Observer

**Problème :** quand un objet change, d'autres doivent réagir, sans que le premier connaisse
les seconds.

```kotlin
class Bouton {
    private val ecouteurs = mutableListOf<(String) -> Unit>()
    fun surClic(ecouteur: (String) -> Unit) { ecouteurs += ecouteur }
    fun cliquer() = ecouteurs.forEach { it("clic") }
}
```

Le sujet (`Bouton`) garde une liste d'observateurs et les prévient ; il ne sait rien de ce
qu'ils font. On retrouve Observer partout : écouteurs d'interface, `StateFlow` et `LiveData`
sur Android, événements du DOM, bus de messages, *webhooks*.

Pièges :

- **Fuites mémoire** : un observateur jamais désinscrit garde en vie tout ce qu'il
  référence (une activité Android entière, par exemple). D'où `collectAsStateWithLifecycle`
  et les désinscriptions automatiques.
- **Ordre et cascades** : un observateur qui modifie le sujet déclenche d'autres
  notifications ; le flux devient difficile à suivre.
- **Erreurs** : si un observateur lève une exception, les suivants sont-ils prévenus ?

## À retenir

- Un pattern = un problème récurrent + une solution + ses compromis ; jamais « pour faire propre ».
- Singleton : l'instance unique est utile, l'accès global est le problème ; injecter.
- Factory Method : la classe décide quand créer, la sous-classe (ou la fabrique) décide quoi.
- Observer : le sujet prévient sans connaître ; attention aux fuites et aux cascades.
