---
id: poo.n2.heritage-polymorphisme
title: Héritage, polymorphisme et égalité
skills: [heritage, polymorphisme, abstraction, interfaces, surcharge-redefinition, equals-hashcode]
duration_min: 14
---

# Héritage, polymorphisme et égalité

## L'héritage en Kotlin

En Kotlin, une classe est **fermée** par défaut : pour en hériter, il faut l'ouvrir avec
`open`, et chaque méthode redéfinissable doit l'être aussi. C'est un choix délibéré :
l'héritage crée un couplage fort, il doit être voulu.

```kotlin
open class Animal {
    open fun cri() = "..."
    fun respirer() = "inspire, expire"     // final : impossible à redéfinir
}

class Chien : Animal() {
    override fun cri() = "Ouaf"
}
```

## Le polymorphisme

Une variable de type `Animal` peut contenir un `Chien`. Quand on appelle `cri()`, c'est
la méthode de **l'objet réel** qui s'exécute, choisie **à l'exécution** : c'est la
**liaison dynamique**. Le code client manipule des `Animal` sans connaître leurs classes
concrètes, et l'on peut ajouter de nouveaux animaux sans le modifier.

## Classe abstraite ou interface ?

| | Classe abstraite | Interface |
|---|---|---|
| État (propriétés avec valeur) | oui | non (seulement des propriétés abstraites ou calculées) |
| Constructeur | oui | non |
| Implémentations par défaut | oui | oui |
| Héritage multiple | non : une seule classe parente | oui : plusieurs interfaces |
| Sens | « est une sorte de », avec du code commun | « sait faire », un contrat |

Règle pratique : commence par une **interface** ; passe à une classe abstraite seulement
si plusieurs implémentations partagent vraiment de l'état ou du code.

## Le piège : surcharge ou redéfinition ?

```kotlin
fun decrire(a: Animal) = "un animal"
fun decrire(c: Chien) = "un chien"

fun main() {
    val a: Animal = Chien()
    println(a.cri())         // Ouaf
    println(decrire(a))      // un animal
}
```

Sortie réelle : `Ouaf`, puis `un animal`. Deux mécanismes différents :

- La **redéfinition** (`override`) est choisie **à l'exécution**, selon l'objet réel : `Ouaf`.
- La **surcharge** (deux fonctions de même nom, paramètres différents) est choisie **à la
  compilation**, selon le type **déclaré** de la variable : ici `Animal`.

## Le contrat equals / hashCode

Deux objets égaux selon `equals` **doivent** avoir le même `hashCode`. Sinon, les
collections à base de hachage (`HashSet`, `HashMap`) ne fonctionnent plus :

```kotlin
class Email(val valeur: String) {
    override fun equals(other: Any?) = other is Email && other.valeur == valeur
    // ⚠️ hashCode n'est pas redéfini
}

val ensemble = hashSetOf(Email("ada@devforge.dev"))
println(Email("ada@devforge.dev") == Email("ada@devforge.dev"))   // true
println(Email("ada@devforge.dev") in ensemble)                     // false !
```

Le `HashSet` cherche d'abord dans la case correspondant au `hashCode` : avec le
`hashCode` par défaut (lié à l'identité de l'objet), il cherche au mauvais endroit.
Une `data class` génère les deux de façon cohérente.

Autre piège : **modifier une clé** déjà rangée dans un `HashSet`.

```kotlin
data class Point(var x: Int, var y: Int)

val p = Point(1, 2)
val ensemble = hashSetOf(p)
p.x = 10
println(p in ensemble)    // false : son hashCode a changé
println(ensemble.size)    // 1 : il est toujours là, mais introuvable
```

Les clés de `Set` et de `Map` doivent être **immuables**.

## À retenir

- Kotlin ferme les classes par défaut : `open` pour hériter, `override` pour redéfinir.
- Redéfinition : choisie à l'exécution. Surcharge : choisie à la compilation, selon le type déclaré.
- Interface d'abord ; classe abstraite si l'on partage vraiment de l'état.
- `equals` et `hashCode` vont ensemble, et les clés de collections sont immuables.
