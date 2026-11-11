---
id: design-patterns.n4.patterns-entreprise
title: Patterns rares et patterns d'entreprise
skills: [visitor, memento, interpreter, repository-uow, injection-dependances, specification, null-object, anti-patterns]
duration_min: 16
---

# Patterns rares et patterns d'entreprise

## Interpreter et Visitor : travailler sur un arbre

Une expression comme `(2 + 3) × 4` se représente par un arbre. **Interpreter** donne à
chaque nœud le moyen de s'évaluer. En Kotlin, une `sealed interface` et des `when`
exhaustifs font le travail :

```kotlin
sealed interface Expr
data class Nombre(val valeur: Int) : Expr
data class Somme(val gauche: Expr, val droite: Expr) : Expr
data class Produit(val gauche: Expr, val droite: Expr) : Expr

fun evaluer(e: Expr): Int = when (e) {
    is Nombre -> e.valeur
    is Somme -> evaluer(e.gauche) + evaluer(e.droite)
    is Produit -> evaluer(e.gauche) * evaluer(e.droite)
}

fun afficher(e: Expr): String = when (e) {
    is Nombre -> e.valeur.toString()
    is Somme -> "(${afficher(e.gauche)} + ${afficher(e.droite)})"
    is Produit -> "${afficher(e.gauche)} × ${afficher(e.droite)}"
}
```

Sortie réelle pour `Produit(Somme(Nombre(2), Nombre(3)), Nombre(4))` : `(2 + 3) × 4 = 20`.

**Visitor** répond au problème inverse : ajouter de **nouvelles opérations** (évaluer,
afficher, simplifier…) sur une hiérarchie **stable**, sans modifier ses classes. En Java
classique, il passe par une double répartition (`accept(visitor)` → `visitor.visit(this)`).
En Kotlin, chaque fonction `when` sur la hiérarchie scellée **est** un visiteur, plus
simple et vérifié par le compilateur. C'est ainsi que travaillent les compilateurs et les
outils d'analyse de code.

## Memento : une photo de l'état

Sauvegarder l'état d'un objet pour pouvoir y revenir, sans exposer ses détails internes.
Avec des objets immuables, c'est trivial : garder l'ancienne instance **est** le memento.
C'est ainsi que fonctionnent l'historique d'un éditeur, les points de sauvegarde d'un jeu
ou le « voyage dans le temps » des outils de débogage d'état.

## Repository et Unit of Work

- **Repository** : une collection d'objets du domaine, cachant la base de données.
  `utilisateurs.parId(id)`, `utilisateurs.enregistrer(u)`. Le domaine ne voit ni SQL ni ORM.
- **Unit of Work** : regroupe toutes les modifications d'une opération métier et les
  enregistre ensemble, dans une transaction. Le contexte de persistance de JPA/Hibernate
  en est une implémentation.

## Injection de dépendances et inversion de contrôle

Un objet **reçoit** ses dépendances au lieu de les créer :

```kotlin
class InscriptionService(
    private val utilisateurs: UtilisateurRepository,   // reçu, pas créé
    private val email: EnvoiEmail,
)
```

On peut alors lui passer de vraies implémentations en production et des fausses en test.
Un **conteneur** (Spring, Hilt, Koin) peut se charger de l'assemblage ; mais
l'injection, c'est d'abord ce simple constructeur. Cette app la fait à la main
(`AppContainer`).

## Specification : des règles qu'on combine

Une règle métier devient un objet combinable avec `and`, `or`, `not` :
`clientPremium and commandeSuperieureA(100)`. Les prérequis des parcours de cette app
(« POO niveau 2 et Clean code niveau 1 ») sont des spécifications combinées
(`LevelRules`).

## Null Object : un « rien » qui se comporte bien

Au lieu de tester `null` partout, on fournit un objet qui ne fait rien :

```kotlin
object JournalSilencieux : Journal {
    override fun info(message: String) {}   // ne fait rien, volontairement
}
```

Kotlin, avec ses types nullables et `?.`, rend ce pattern moins nécessaire ; il reste utile
pour des valeurs par défaut (un journal, une stratégie, un écouteur).

## Les anti-patterns

| Anti-pattern | Symptôme |
|---|---|
| **God object** | une classe de 3 000 lignes qui sait tout et fait tout |
| **Code spaghetti** | un flux de contrôle impossible à suivre |
| **Golden hammer** | utiliser partout l'outil qu'on connaît (« on va le faire avec Kafka ») |
| **Lava flow** | du code mort que personne n'ose supprimer |
| **Singletonite** | des singletons partout, donc un état global partout |
| **Optimisation prématurée** | complexifier le code pour une performance jamais mesurée |
| **Copier-coller** | la même logique dupliquée, corrigée à un endroit sur trois |

## À retenir

- Interpreter et Visitor : en Kotlin, une hiérarchie scellée et des `when` exhaustifs.
- Memento : une photo de l'état ; gratuit avec des objets immuables.
- Repository cache la persistance ; Unit of Work regroupe les modifications.
- L'injection de dépendances, c'est d'abord un constructeur qui reçoit ses dépendances.
- Les anti-patterns se reconnaissent à leurs symptômes : apprends à les nommer.
