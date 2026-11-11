---
id: poo.n5.au-dela-de-la-poo
title: Au-delà de la POO
skills: [traits-mixins, prototypes, critique-poo]
duration_min: 12
---

# Au-delà de la POO

## Traits, mixins et délégation

Un **trait** (ou *mixin*) apporte un comportement réutilisable à une classe, sans
l'enfermer dans un arbre d'héritage. Les interfaces Kotlin avec implémentation par défaut
jouent ce rôle. Mais si deux interfaces fournissent la même méthode, c'est le problème du
**diamant** : laquelle choisir ? Kotlin refuse de deviner.

```kotlin
interface Volant { fun deplacer() = "vole" }
interface Nageant { fun deplacer() = "nage" }

class Canard : Volant, Nageant {
    override fun deplacer() = super<Volant>.deplacer() + " et " + super<Nageant>.deplacer()
}
```

Sortie réelle : `vole et nage`. Sans la redéfinition, le compilateur répond
`class 'Canard' must override 'deplacer' because it inherits multiple interface methods for it`.

L'autre outil est la **délégation** (`by`, vue dans les parcours POO 3 et Métaprogrammation) :
une classe obtient un comportement en le confiant à un objet, plutôt qu'en héritant.

## Des objets sans classes : les prototypes

JavaScript a d'abord été conçu sans classes : un objet hérite **directement d'un autre
objet**, son prototype. Le mot-clé `class` de JavaScript n'est qu'une syntaxe par-dessus.

```javascript
const animal = {
  crier() {
    return `${this.nom} fait du bruit`;
  },
};

const chien = Object.create(animal);
chien.nom = "Rex";
console.log(chien.crier());          // Rex fait du bruit

animal.crier = function () {
  return `${this.nom} aboie`;
};
console.log(chien.crier());          // Rex aboie
```

Sorties réelles. Quand on modifie le prototype, **tous** les objets qui en héritent
changent immédiatement. Très souple, très dangereux : modifier le prototype d'un objet
standard (`Array.prototype`) a longtemps été une source de bugs entre bibliothèques.

## Quand ne pas utiliser la POO

La POO excelle pour modéliser un domaine riche en règles, avec des objets qui protègent
leur état. Elle est moins adaptée quand :

- **on transforme des données** (pipelines, ETL, calculs) : la programmation fonctionnelle
  (fonctions pures, `map`/`filter`/`fold`) est plus simple et plus facile à tester ;
- **la performance mémoire compte** : des millions de petits objets dispersés sur le tas
  saturent les caches ; les moteurs de jeu utilisent des tableaux contigus et le modèle
  **ECS** (entités, composants, systèmes), orienté données (voir *Gestion de la mémoire*, niveau 5) ;
- **aux frontières** du système (API, base de données, messages) : de simples structures
  de données (DTO) sont plus honnêtes qu'un faux objet métier ;
- **on invente des noms** pour tout : `GestionnaireDeFabriqueDeServices` cache souvent
  une simple fonction.

Les experts mélangent : un cœur métier objet, des transformations fonctionnelles, des
structures orientées données là où la performance l'exige.

## À retenir

- Interfaces avec implémentation par défaut = traits ; Kotlin oblige à résoudre le diamant explicitement.
- La délégation (`by`) apporte un comportement sans héritage.
- Prototypes (JavaScript) : un objet hérite d'un objet ; modifier le prototype change tous ses héritiers.
- La POO est un outil parmi d'autres : fonctionnel pour les transformations, orienté données pour la performance.
