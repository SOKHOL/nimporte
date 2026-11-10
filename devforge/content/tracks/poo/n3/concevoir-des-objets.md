---
id: poo.n3.concevoir-des-objets
title: SOLID, composition et immutabilité
skills: [composition-heritage, solid, demeter, couplage-cohesion, immutabilite]
duration_min: 15
---

# SOLID, composition et immutabilité

## Préférer la composition à l'héritage

L'héritage expose les détails internes du parent : la sous-classe dépend de **comment**
le parent est implémenté, pas seulement de ce qu'il promet. C'est le problème de la
**classe de base fragile**.

```kotlin
class CompteurHashSet<E> : HashSet<E>() {
    var ajouts = 0

    override fun add(element: E): Boolean {
        ajouts++
        return super.add(element)
    }

    override fun addAll(elements: Collection<E>): Boolean {
        ajouts += elements.size
        return super.addAll(elements)
    }
}
```

Après `addAll(listOf("a", "b", "c"))`, `ajouts` vaut réellement **6**, pas 3 : en
interne, `HashSet.addAll` appelle `add` pour chaque élément, qui compte une seconde fois.
Rien dans la documentation de `addAll` ne le promettait.

La version par **composition**, avec la délégation Kotlin :

```kotlin
class CompteurSet<E>(private val interne: MutableSet<E> = HashSet()) : MutableSet<E> by interne {
    var ajouts = 0

    override fun add(element: E): Boolean {
        ajouts++
        return interne.add(element)
    }

    override fun addAll(elements: Collection<E>): Boolean {
        ajouts += elements.size
        return interne.addAll(elements)
    }
}
```

Elle compte **3**, comme prévu : elle ne dépend que du contrat de `MutableSet`.

## Les principes SOLID

| Lettre | Principe | En une phrase |
|---|---|---|
| **S** | Responsabilité unique | une classe n'a qu'une raison de changer |
| **O** | Ouvert/fermé | on ajoute un comportement sans modifier le code existant |
| **L** | Substitution de Liskov | une sous-classe peut remplacer son parent sans surprise |
| **I** | Ségrégation des interfaces | plusieurs petites interfaces plutôt qu'une énorme |
| **D** | Inversion des dépendances | le métier dépend d'abstractions, pas des détails techniques |

L'exemple classique de violation de Liskov : un `Carre` qui hérite de `Rectangle`.
Mathématiquement, un carré est un rectangle ; mais un code qui fait
`r.largeur = 4; r.hauteur = 5` puis attend une aire de 20 obtient 25 avec un carré. La
sous-classe ne respecte pas les promesses du parent : l'héritage est faux.

## Couplage et cohésion

- **Cohésion forte** : ce qui change ensemble est rangé ensemble.
- **Couplage faible** : chaque module dépend du moins de choses possible, et de choses stables.

## La loi de Déméter

« Ne parle qu'à tes amis proches. » Une méthode ne devrait appeler que les méthodes de
`this`, de ses paramètres, des objets qu'elle crée et de ses propres champs.

```kotlin
val ville = commande.client.adresse.ville      // ⚠️ un « accident de train »
val ville = commande.villeDeLivraison()        // la commande sait d'où vient la réponse
```

Le premier code dépend de la structure interne de trois classes : si `Client` change,
il casse.

## L'immutabilité

Un objet immuable ne change jamais après sa création :

- partageable entre threads sans aucune synchronisation ;
- utilisable comme clé de `Map` sans risque ;
- raisonnement plus simple : pas d'effet de bord caché.

En Kotlin : des `val`, des `data class` et `copy()` pour « modifier » en créant un nouvel
objet, des `value class` pour les petites valeurs typées (`value class Email(val valeur: String)`).

Piège : une `List` en lecture seule n'est **pas** immuable.

```kotlin
val source = mutableListOf(1, 2)
val lecture: List<Int> = source
source.add(3)
println(lecture)    // [1, 2, 3]
```

`List` interdit de modifier **à travers cette référence**, mais quelqu'un d'autre peut
modifier la liste en dessous. Pour une vraie immutabilité : copier (`toList()`) ou
utiliser les collections persistantes (`kotlinx.collections.immutable`).

## À retenir

- La composition dépend d'un contrat ; l'héritage dépend d'une implémentation.
- SOLID : une raison de changer, extensible sans modification, des sous-classes substituables, des petites interfaces, des dépendances vers les abstractions.
- Déméter : pas de chaînes `a.b.c.d`.
- L'immuable se partage sans risque ; une `List` en lecture seule n'est pas immuable.
