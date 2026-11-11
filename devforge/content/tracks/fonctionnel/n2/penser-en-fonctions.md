---
id: fonctionnel.n2.penser-en-fonctions
title: Fonctions pures, composition et types algébriques
skills: [fonctions-pures, composition, types-algebriques]
duration_min: 17
---

# Fonctions pures, composition et types algébriques

## Les fonctions pures

Une fonction est **pure** si :

1. elle renvoie **toujours le même résultat pour les mêmes arguments** ;
2. elle n'a **aucun effet de bord** : elle ne modifie rien à l'extérieur, ne lit rien
   d'extérieur qui change (horloge, aléa, fichier, base, réseau, variable globale).

```kotlin
fun prixTtc(prixHt: Long, taux: Int): Long = prixHt * (100 + taux) / 100      // pure

fun prixTtcDuJour(prixHt: Long): Long =                                          // impure
    prixHt * (100 + tauxDepuisLaBase(LocalDate.now())) / 100
```

Une fonction pure a la **transparence référentielle** : on peut remplacer l'appel par son
résultat sans rien changer au programme. Ce qui permet :

- de la **tester** sans rien simuler (entrées → sortie) ;
- de la **mettre en cache** (mémoïsation) sans risque ;
- de l'appeler en **parallèle** sans verrou ;
- de **raisonner** sur elle isolément, sans connaître l'état du reste du programme.

Un programme utile a forcément des effets (afficher, enregistrer, envoyer). L'idée n'est pas
de les supprimer, mais de les **repousser aux bords** : un cœur de fonctions pures qui
**décident**, et une fine couche qui **agit** (le « cœur fonctionnel, coquille impérative » du
parcours Clean code, niveau 5 ; développé au niveau 4).

Les effets cachés les plus courants : `now()`, `random()`, une variable globale ou un
singleton modifiable, un `println` de débogage, une écriture dans un journal, un paramètre
modifié en place (`liste.sort()` au lieu de `liste.sorted()`).

## Composition et application partielle

**Composer** des fonctions, c'est brancher la sortie de l'une sur l'entrée de la suivante, pour
fabriquer une nouvelle fonction. Exemple réel, avec un opérateur `puis` écrit en une ligne :

```kotlin
infix fun <A, B, C> ((A) -> B).puis(g: (B) -> C): (A) -> C = { x -> g(this(x)) }

val nettoyer: (String) -> String = { it.trim() }
val minuscules: (String) -> String = { it.lowercase() }
val slug: (String) -> String = { it.replace(" ", "-") }

val enSlug = nettoyer puis minuscules puis slug
enSlug("  Programmation Fonctionnelle  ")      // "programmation-fonctionnelle"
```

De petites fonctions simples, chacune testable seule, assemblées en traitements plus
complexes. Les pipelines de collections (`filter`, `map`…) et les opérateurs de flux (`Flow`,
Reactor) sont de la composition.

L'**application partielle** fixe une partie des arguments pour obtenir une fonction plus
spécialisée ; la **curryfication** transforme une fonction à plusieurs arguments en une suite
de fonctions à un argument :

```kotlin
val ajouter = { a: Int -> { b: Int -> a + b } }   // curryfiée
val plus10 = ajouter(10)
listOf(1, 2, 3).map(plus10)                        // [11, 12, 13]
```

C'est une forme d'injection de dépendances très légère : `val calculerTva = prixTtc(taux = 20)`
(en Kotlin, on l'écrit avec une lambda : `{ ht: Long -> prixTtc(ht, 20) }`).

## Les types algébriques

Les types fonctionnels combinent deux constructions :

- le **type produit** : « A **et** B » (une `data class` : un nom **et** un e-mail) ;
- le **type somme** : « A **ou** B » (une `sealed interface` : une carte **ou** un virement
  **ou** des espèces).

```kotlin
sealed interface Paiement
data class Carte(val derniers: String) : Paiement
data class Virement(val iban: String) : Paiement
data object Especes : Paiement

fun libelle(p: Paiement): String = when (p) {          // exhaustif : pas de else
    is Carte -> "carte •••• ${p.derniers}"
    is Virement -> "virement ${p.iban.take(4)}…"
    Especes -> "espèces"
}
// sortie réelle : carte •••• 4242 / espèces / virement FR76…
```

Le `when` est **exhaustif** : si l'on ajoute `data class Crypto(…) : Paiement`, chaque `when`
qui ne le traite pas **ne compile plus**. Le compilateur dresse la liste des endroits à mettre à
jour, au lieu d'un `else` qui avalerait silencieusement le nouveau cas.

L'idée clé : **rendre les états impossibles impossibles à représenter**. Plutôt que

```kotlin
data class EtatChargement(val enCours: Boolean, val donnees: List<Article>?, val erreur: String?)
// que veut dire enCours = true ET erreur != null ?
```

on écrit

```kotlin
sealed interface EtatChargement {
    data object EnCours : EtatChargement
    data class Succes(val articles: List<Article>) : EtatChargement
    data class Echec(val message: String) : EtatChargement
}
```

Il n'existe plus de combinaison incohérente : le type le garantit.

## À retenir

- Pure = même entrée, même sortie, aucun effet de bord ; testable, cachable, parallélisable ; les effets vont aux bords.
- Composer de petites fonctions (`nettoyer puis minuscules puis slug`) ; application partielle et curryfication pour spécialiser.
- Types produits (data class) et sommes (sealed) ; `when` exhaustif ; rendre les états impossibles impossibles à représenter.
