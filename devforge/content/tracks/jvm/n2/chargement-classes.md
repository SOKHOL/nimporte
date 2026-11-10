---
id: jvm.n2.chargement-classes
title: Chargement et initialisation des classes
skills: [chargement-classes, initialisation]
duration_min: 10
---

# Chargement et initialisation des classes

Une classe n'existe pas dans la JVM tant qu'on n'en a pas besoin. À sa **première
utilisation**, elle passe par trois étapes :

1. **Chargement** : un *class loader* trouve le fichier `.class` (dans un JAR, un
   dossier…) et le lit.
2. **Liaison** : vérification du bytecode, préparation des champs statiques (valeurs
   par défaut), résolution des références vers les autres classes.
3. **Initialisation** : exécution des initialisations statiques ; en Kotlin, le bloc
   `init` d'un `object` ou d'un `companion object`.

## Les class loaders

| Class loader | Charge |
|---|---|
| Bootstrap | le cœur du JDK (`java.lang.*`, `java.util.*`…) |
| Platform | les autres modules du JDK |
| Application | ton application et ses dépendances (le classpath) |
| Personnalisés | serveurs d'applications, plugins, rechargement à chaud… |

Ils fonctionnent par **délégation au parent** : avant de chercher lui-même, un class
loader demande à son parent. C'est pourquoi personne ne peut remplacer
`java.lang.String` par sa propre version : le bootstrap la fournit toujours en premier
(et la JVM interdit de définir des classes dans le package `java.*`).

Une classe est identifiée par **son nom et son class loader**. La même classe chargée
par deux loaders différents donne deux classes distinctes, d'où ce message déroutant,
typique des serveurs d'applications et des systèmes de plugins :
`ClassCastException: com.app.Utilisateur cannot be cast to com.app.Utilisateur`.

## Paresseux et thread-safe

```kotlin
object Config {
    init { println("Config chargée") }
    val url = "https://devforge.dev"
}

fun main() {
    println("Début")
    println(Config.url)
    println(Config.url)
}
```

Sortie réelle :

```
Début
Config chargée
https://devforge.dev
https://devforge.dev
```

L'`object` n'est initialisé qu'au premier accès, **une seule fois**, et la JVM garantit
que c'est thread-safe : si deux threads y accèdent en même temps, l'un attend que
l'autre ait terminé. Un `object` Kotlin est donc un singleton paresseux et sûr, sans une
ligne de synchronisation.

Si l'initialisation lève une exception, la classe est marquée en erreur : chaque
utilisation suivante lève `NoClassDefFoundError`. On cherche alors un problème de
classpath, alors que la vraie cause est dans le premier `ExceptionInInitializerError`,
plus haut dans les logs.

## Le piège de l'ordre d'initialisation

```kotlin
open class Parent {
    init { println("Parent : init") }
    open val nom: String = "parent"
    init { println("Parent voit : $nom") }
}

class Enfant : Parent() {
    override val nom: String = "enfant"
    init { println("Enfant : init, nom = $nom") }
}

fun main() {
    Enfant()
}
```

Sortie réelle :

```
Parent : init
Parent voit : null
Enfant : init, nom = enfant
```

`null` dans une propriété déclarée non nullable ! Le constructeur du parent s'exécute
**avant** celui de l'enfant. Quand il lit `nom`, l'appel est polymorphe : c'est le getter
de `Enfant` qui répond, et son champ n'est pas encore initialisé. Le compilateur Kotlin
prévient d'ailleurs : *accessing non-final property in constructor*.

Règle : **n'appelle jamais une méthode ou une propriété `open` depuis un constructeur
ou un bloc `init`.**

## À retenir

- Charger, lier, initialiser : à la première utilisation.
- Délégation au parent ; une classe = un nom + un class loader.
- `object` = singleton paresseux et thread-safe, garanti par la JVM.
- Le parent est construit avant l'enfant : pas d'appel `open` dans un constructeur.
