---
id: architecture.n3.le-domaine-au-centre
title: Hexagonale, Clean Architecture et DDD tactique
skills: [hexagonale, clean-architecture, ddd-tactique]
duration_min: 18
---

# Hexagonale, Clean Architecture et DDD tactique

Dans une architecture en couches classique, le métier dépend de la base de données. Les
architectures de ce niveau **inversent** cette dépendance : le métier est au centre et ne
dépend de rien d'extérieur.

## L'architecture hexagonale (ports et adaptateurs)

Alistair Cockburn (2005) décrit l'application comme un hexagone :

- au centre, le **domaine** : les règles et les cas d'usage, en code ordinaire ;
- sur les bords, des **ports** : des interfaces que le domaine **définit** selon ses
  besoins (« enregistrer une commande », « envoyer une notification ») ;
- à l'extérieur, des **adaptateurs** qui branchent la technique sur ces ports.

```kotlin
// Dans le domaine : le port, exprimé dans le vocabulaire du métier
interface Commandes {
    fun enregistrer(commande: Commande)
    fun trouver(id: CommandeId): Commande?
}

// Dans l'infrastructure : un adaptateur qui implémente le port
class CommandesPostgres(private val jdbc: JdbcTemplate) : Commandes { /* SQL */ }

// Dans les tests : un autre adaptateur, en mémoire
class CommandesEnMemoire : Commandes {
    private val donnees = mutableMapOf<CommandeId, Commande>()
    override fun enregistrer(commande: Commande) { donnees[commande.id] = commande }
    override fun trouver(id: CommandeId) = donnees[id]
}
```

Il y a deux sortes d'adaptateurs :

| Adaptateurs **primaires** (ils pilotent l'application) | Adaptateurs **secondaires** (pilotés par l'application) |
|---|---|
| contrôleur REST, écran, consommateur de messages, tâche planifiée, test | base de données, client HTTP, envoi d'e-mails, file de messages |

Ce que ça apporte : tout le métier se teste en millisecondes, sans base ni serveur ; on
change de base, de framework ou de fournisseur sans toucher aux règles ; et le code métier
se lit sans être noyé dans la plomberie.

## La Clean Architecture

Robert C. Martin (2012) résume la même idée en cercles concentriques : **entités** au centre,
puis **cas d'usage**, puis **adaptateurs d'interface** (contrôleurs, présentateurs,
passerelles), puis **frameworks et pilotes** (web, base, interface).

La seule règle, la **règle de dépendance** : le code source ne dépend que **vers
l'intérieur**. Un cercle intérieur ne connaît aucun nom (classe, fonction, annotation) d'un
cercle extérieur. Quand le centre doit appeler l'extérieur, il définit une interface
(inversion de dépendance).

Hexagonale, Clean, *Onion* : trois noms, une même idée. Le danger est d'en faire une
cérémonie : quatre couches et six classes de *mapping* pour un CRUD qui n'a aucune règle
métier. Ces architectures paient quand le métier est riche ; pour un simple formulaire
vers une table, les couches classiques suffisent.

## DDD tactique : les briques du domaine

Le *Domain-Driven Design* (Eric Evans, 2003) donne des briques pour écrire le centre de
l'hexagone.

**Les objets-valeurs** (*value objects*) : définis par leurs valeurs, sans identité,
immuables, et **toujours valides**. Sortie réelle :

```kotlin
@JvmInline
value class Email(val valeur: String) {
    init { require("@" in valeur) { "e-mail invalide : $valeur" } }
}

data class Montant(val centimes: Long, val devise: String) {
    operator fun plus(autre: Montant): Montant {
        require(devise == autre.devise) { "devises différentes : $devise et ${autre.devise}" }
        return Montant(centimes + autre.centimes, devise)
    }
}

Montant(1050, "EUR") + Montant(250, "EUR")    // Montant(centimes=1300, devise=EUR)
Montant(100, "EUR") == Montant(100, "EUR")    // true
Montant(100, "EUR") + Montant(100, "USD")     // devises différentes : EUR et USD
Email("ada.exemple.com")                       // e-mail invalide : ada.exemple.com
```

Un `Montant` remplace un `Long` nu : impossible d'additionner des euros et des dollars, ou
de passer un prix là où on attend une quantité. C'est le remède à l'**obsession des
primitives**.

**Les entités** : elles ont une **identité** qui reste la même quand leurs attributs
changent (une commande reste la même commande quand on ajoute une ligne). Deux entités sont
égales si leurs identifiants le sont.

**Les agrégats** : un groupe d'objets modifiés ensemble, avec une **racine** par laquelle
tout passe. La racine garantit les **invariants** du groupe.

```kotlin
class Commande(val id: CommandeId, private val client: ClientId) {
    private val _lignes = mutableListOf<Ligne>()
    val lignes: List<Ligne> get() = _lignes.toList()
    var statut = Statut.BROUILLON
        private set

    fun ajouter(ligne: Ligne) {
        check(statut == Statut.BROUILLON) { "commande déjà validée" }
        require(_lignes.size < 50) { "50 lignes maximum" }
        _lignes += ligne
    }

    fun valider(): CommandeValidee {
        check(_lignes.isNotEmpty()) { "commande vide" }
        statut = Statut.VALIDEE
        return CommandeValidee(id, client, total())       // un événement du domaine
    }

    fun total(): Montant = _lignes.map { it.sousTotal() }.reduce(Montant::plus)
}
```

Règles des agrégats :

- On ne modifie un agrégat **que par sa racine** (jamais `commande.lignes.add(...)`).
- **Un agrégat = une transaction** : on n'en modifie pas deux dans la même transaction ; on
  passe par un événement.
- Entre agrégats, on se référence **par identifiant** (`ClientId`), pas par objet.
- Petits agrégats : un agrégat « Client » qui contient toutes ses commandes se charge
  entièrement à chaque modification et crée des conflits permanents.

**Les événements du domaine** (`CommandeValidee`) décrivent un fait passé, nommé au passé.
D'autres parties du système y réagissent (facturation, stock, e-mail) sans que la commande
les connaisse.

**Les dépôts** (*repositories*) : l'illusion d'une collection d'agrégats en mémoire
(`trouver`, `enregistrer`). Un dépôt par agrégat, et c'est un port de l'hexagone.

**Les services du domaine** : une règle qui n'appartient naturellement à aucun objet (un
virement entre deux comptes, un calcul de tarif qui combine plusieurs agrégats).

## À retenir

- Hexagonale : le domaine définit des ports, les adaptateurs (primaires et secondaires) les branchent.
- Clean Architecture : les dépendances pointent vers l'intérieur, jamais l'inverse. Pas de cérémonie pour un simple CRUD.
- Objets-valeurs immuables et toujours valides ; entités avec une identité.
- Agrégat : une racine, des invariants, une transaction, des références par identifiant.
- Événements du domaine au passé ; un dépôt par agrégat.
