---
id: poo.n1.classes-et-objets
title: Classes, constructeurs, propriétés et égalité
skills: [classes-objets, constructeurs, proprietes-methodes, egalite-identite]
duration_min: 14
---

# Classes, constructeurs, propriétés et égalité

## Une classe, des objets

Une **classe** est un plan : elle décrit les données (propriétés) et les comportements
(méthodes) communs. Un **objet** est une instance construite à partir de ce plan, avec
ses propres valeurs.

```kotlin
class Compte(val titulaire: String) {
    var solde = 0
        private set

    fun deposer(montant: Int) {
        require(montant > 0) { "montant invalide : $montant" }
        solde += montant
    }
}

val a = Compte("Ada")      // un objet
val b = Compte("Linus")    // un autre objet, indépendant
a.deposer(100)             // b.solde vaut toujours 0
```

Une classe regroupe ce qui change ensemble. Si une classe s'appelle `Utilitaires` ou
`Manager` et fait tout, c'est souvent qu'elle n'a pas de responsabilité claire.

## Constructeurs et ordre d'initialisation

Le **constructeur principal** est dans l'en-tête de la classe. Les blocs `init` et les
initialiseurs de propriétés s'exécutent **dans l'ordre où ils sont écrits**, puis le corps
d'un éventuel constructeur secondaire. Sortie réelle :

```kotlin
class Compte(val titulaire: String) {
    init { println("1. init : $titulaire") }
    val solde = 0.also { println("2. propriété solde") }
    init { println("3. second init") }

    constructor(titulaire: String, depot: Int) : this(titulaire) {
        println("4. constructeur secondaire : dépôt de $depot")
    }
}
// Compte("Ada", 100) affiche 1, 2, 3, puis 4.
```

En Kotlin, on a rarement besoin de constructeurs secondaires : les **valeurs par défaut**
et les **arguments nommés** couvrent la plupart des cas.

```kotlin
class Connexion(val hote: String, val port: Int = 5432, val ssl: Boolean = true)
Connexion("db.exemple.com", ssl = false)
```

Un constructeur doit produire un objet **valide** : on vérifie les invariants (`require`)
dès la construction, plutôt que d'avoir des objets à moitié remplis qu'il faut
« initialiser » ensuite.

## Propriétés et méthodes

En Kotlin, une propriété cache un champ et ses accesseurs. On peut les personnaliser :

```kotlin
class Rectangle(val largeur: Int, val hauteur: Int) {
    val aire: Int
        get() = largeur * hauteur          // propriété calculée, pas de champ

    var nom: String = "sans nom"
        set(valeur) {
            require(valeur.isNotBlank())
            field = valeur                   // field : le champ caché
        }
}
```

- `val` : lecture seule (pas de *setter*) ; `var` : modifiable.
- `private set` : lisible par tous, modifiable seulement par la classe.
- Une **propriété** décrit un état bon marché à lire ; une **méthode** décrit une action ou
  un calcul coûteux (`aire` est une propriété, `exporterEnPdf()` une méthode).

## Égalité et identité

| Opérateur | Question | Par défaut |
|---|---|---|
| `===` | Est-ce **le même objet** en mémoire ? | identité |
| `==` | Ces objets sont-ils **équivalents** ? | appelle `equals`, qui par défaut… compare l'identité |

```kotlin
class Point(val x: Int, val y: Int)
data class PointD(val x: Int, val y: Int)

Point(1, 2) == Point(1, 2)      // false : equals n'est pas redéfini
PointD(1, 2) == PointD(1, 2)    // true  : data class génère equals et hashCode
PointD(1, 2) === PointD(1, 2)   // false : deux objets distincts
```

Une `data class` génère `equals`, `hashCode`, `toString` et `copy` à partir des propriétés
du constructeur principal. Règle d'or : si on redéfinit `equals`, on redéfinit `hashCode`
de façon cohérente, sinon les `HashSet` et `HashMap` se comportent de façon absurde (le
niveau 2 le montre en détail).

## À retenir

- Classe = plan ; objet = instance avec ses propres valeurs.
- Initialisation dans l'ordre d'écriture, puis constructeur secondaire ; valeurs par défaut plutôt que surcharges.
- Un objet est valide dès sa construction (`require`).
- Propriété = état, méthode = action ; `private set` pour protéger.
- `===` identité, `==` équivalence ; `data class` pour les objets définis par leurs valeurs.
