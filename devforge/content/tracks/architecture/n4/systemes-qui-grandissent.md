---
id: architecture.n4.systemes-qui-grandissent
title: DDD stratégique, CQRS, microservices et ADR
skills: [ddd-strategique, cqrs-event-sourcing, monolithe-microservices, adr]
duration_min: 19
---

# DDD stratégique, CQRS, microservices et ADR

## DDD stratégique : découper selon le métier

Dans une grande entreprise, le mot « client » n'a pas le même sens partout. Pour les
ventes, c'est un prospect avec un historique d'achats ; pour la comptabilité, un débiteur
avec des conditions de paiement ; pour le support, une personne avec des tickets. Un modèle
unique « Client » qui satisfait tout le monde devient une classe de 80 champs que personne
n'ose modifier.

Le DDD stratégique découpe le système en **contextes délimités** (*bounded contexts*) :

- chaque contexte a **son propre modèle** et son **langage omniprésent** (*ubiquitous
  language*) : les mots du code sont ceux des experts métier de ce contexte ;
- le même mot peut désigner des choses différentes dans deux contextes, et c'est normal ;
- un contexte appartient idéalement à **une équipe**.

La **carte des contextes** (*context map*) décrit leurs relations :

| Relation | Sens |
|---|---|
| **Client / fournisseur** | l'amont tient compte des besoins de l'aval |
| **Conformiste** | l'aval adopte le modèle de l'amont tel quel (souvent un grand fournisseur externe) |
| **Couche anticorruption** (ACL) | l'aval traduit le modèle de l'amont pour protéger le sien (indispensable face à un vieux système) |
| **Noyau partagé** | deux contextes partagent un petit morceau de modèle, modifié d'un commun accord |
| **Service hôte ouvert** et **langage publié** | l'amont expose une API stable et documentée pour tous |

On distingue aussi le **domaine cœur** (ce qui différencie l'entreprise, où l'on met les
meilleurs développeurs), les domaines **de support** et les domaines **génériques**
(facturation, authentification), qu'on achète plutôt que de les réécrire.

## CQRS : séparer l'écriture de la lecture

**CQRS** (*Command Query Responsibility Segregation*) sépare le modèle qui **modifie**
(commandes, avec toutes les règles) du modèle qui **lit** (requêtes, optimisées pour
l'affichage).

```
Commande ──► modèle d'écriture (agrégats, invariants) ──► événements
                                                          │
Requête ◄── modèle de lecture (tables dénormalisées, index de recherche) ◄──┘
```

Utile quand la lecture et l'écriture ont des besoins très différents : un tableau de bord
qui agrège des millions de lignes, une recherche plein texte, des lectures 1000 fois plus
fréquentes que les écritures. Le prix : le modèle de lecture est mis à jour de façon
asynchrone, donc **légèrement en retard** (cohérence éventuelle), et il y a deux modèles à
maintenir. Pour la plupart des écrans, un seul modèle suffit.

## Event sourcing : l'historique comme source de vérité

Au lieu d'enregistrer l'**état actuel** (« solde = 120 »), on enregistre la **suite des
événements** et on en déduit l'état. Sortie réelle :

```kotlin
sealed interface Evenement
data class Ouvert(val titulaire: String) : Evenement
data class Depose(val montant: Int) : Evenement
data class Retire(val montant: Int) : Evenement

fun appliquer(compte: Compte, e: Evenement): Compte = when (e) {
    is Ouvert -> compte.copy(titulaire = e.titulaire)
    is Depose -> compte.copy(solde = compte.solde + e.montant, operations = compte.operations + 1)
    is Retire -> compte.copy(solde = compte.solde - e.montant, operations = compte.operations + 1)
}

val journal = listOf(Ouvert("Ada"), Depose(100), Retire(30), Depose(50))
journal.fold(Compte(), ::appliquer)           // Compte(titulaire=Ada, solde=120, operations=3)
journal.take(2).fold(Compte(), ::appliquer)   // solde 100 : l'état à n'importe quel moment passé
```

Ce que ça apporte : un **audit complet** (la banque, la comptabilité, la santé l'exigent
souvent), la possibilité de **rejouer** l'histoire pour reconstruire un état passé ou un
nouveau modèle de lecture, et le débogage d'un problème en production en rejouant ses
événements.

Ce que ça coûte : les événements sont **éternels** (on ne modifie jamais le passé, on
versionne les formats d'événements), la reconstruction devient lente sur de longs historiques
(on ajoute des **instantanés**), et le « droit à l'oubli » du RGPD se gère par chiffrement
puis destruction de la clé. Réservé aux domaines où l'historique a une vraie valeur.

## Monolithe modulaire ou microservices

| | Monolithe modulaire | Microservices |
|---|---|---|
| Déploiement | un seul artefact | un par service, indépendants |
| Appels entre parties | appels de fonctions (nanosecondes, fiables) | réseau (millisecondes, peuvent échouer) |
| Transactions | une transaction de base de données | sagas, cohérence éventuelle |
| Mise à l'échelle | tout ensemble | service par service |
| Coût d'exploitation | faible | élevé : supervision, traces distribuées, CI/CD par service |
| Autonomie des équipes | limitée par le déploiement commun | forte, si les frontières sont bonnes |

Les microservices résolvent surtout un problème **d'organisation** : permettre à des dizaines
d'équipes de livrer sans se coordonner. Pour une équipe de cinq personnes, ils ajoutent
surtout des pannes réseau.

Le pire des deux mondes est le **monolithe distribué** : des services qui doivent être
déployés ensemble, qui partagent la même base, ou qui s'appellent en chaîne de façon
synchrone (si un tombe, tout tombe). Il est presque toujours causé par un découpage selon
les couches techniques (« service utilisateurs », « service base de données ») au lieu des
contextes délimités.

Chemin raisonnable : commencer par un **monolithe modulaire** (modules alignés sur les
contextes, frontières vérifiées, pas d'accès direct aux tables des autres modules), puis
extraire un service quand une raison concrète apparaît : une charge très différente, un
rythme de livraison différent, une équipe dédiée.

## Documenter les décisions : les ADR

Dans deux ans, quelqu'un demandera « pourquoi avoir choisi Kafka plutôt que RabbitMQ ? ».
Un **ADR** (*Architecture Decision Record*, Michael Nygard) est un court fichier Markdown,
versionné avec le code :

```markdown
# ADR 0007 : Publier les événements de commande avec le pattern outbox

## Statut
Accepté (2026-03-12)

## Contexte
Les commandes sont enregistrées en base, puis un événement est publié dans Kafka.
Si la publication échoue après l'enregistrement, la facturation ne voit jamais la commande.

## Décision
Écrire l'événement dans une table outbox, dans la même transaction que la commande ;
un processus relais le publie ensuite dans Kafka.

## Conséquences
+ Aucune commande perdue.
- Un événement peut être publié deux fois : les consommateurs doivent être idempotents.
- Un composant de plus à superviser (le relais).
```

Règles : un ADR par décision, **jamais modifié** une fois accepté (on en écrit un nouveau
qui le remplace et on change le statut de l'ancien en « remplacé par 0012 »), et surtout
les **conséquences négatives** : une décision sans inconvénient n'a pas été réfléchie.
DevForge en a dans `docs/adr`.

## À retenir

- Contextes délimités : un modèle et un langage par contexte, une équipe par contexte ; la carte des contextes décrit leurs relations (ACL face au legacy).
- CQRS : modèles d'écriture et de lecture séparés, au prix d'un léger retard de lecture.
- Event sourcing : l'état est un `fold` des événements ; audit et rejeu, au prix d'événements éternels.
- Monolithe modulaire d'abord ; microservices pour l'autonomie des équipes ; fuir le monolithe distribué.
- ADR : contexte, décision, conséquences (y compris négatives), jamais modifié.
