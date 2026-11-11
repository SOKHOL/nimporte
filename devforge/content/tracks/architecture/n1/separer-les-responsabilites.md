---
id: architecture.n1.separer-les-responsabilites
title: Couches, responsabilités et MVC
skills: [couches, separation-responsabilites, mvc]
duration_min: 14
---

# Couches, responsabilités et MVC

L'architecture, c'est l'ensemble des décisions **difficiles à changer plus tard** : comment
le code est découpé, qui a le droit de dépendre de qui, où vivent les règles métier. Une
bonne architecture rend les changements fréquents faciles et localisés.

## Séparer les responsabilités

Le symptôme d'un code mal séparé : pour ajouter un champ « date de naissance », il faut
toucher l'écran, la requête SQL, la validation, l'export CSV et trois classes qui n'ont
rien à voir, parce que tout est mélangé dans les mêmes fonctions.

Le principe : regrouper ce qui **change pour la même raison**, séparer ce qui change pour
des raisons différentes. On distingue presque toujours trois préoccupations :

| Préoccupation | Question | Change quand… |
|---|---|---|
| Présentation | Comment montrer et recevoir les données ? | le design ou le canal change (web, mobile, API) |
| Métier (domaine) | Quelles sont les règles ? | les règles de l'entreprise changent |
| Données (infrastructure) | Où et comment stocker, appeler les autres systèmes ? | la base, le fournisseur ou le format change |

## L'architecture en couches

La forme la plus répandue empile ces préoccupations :

```
Présentation   (contrôleurs, écrans)
     ↓
Métier         (services, règles)
     ↓
Données        (dépôts, clients HTTP, SQL)

les dépendances vont vers le bas, uniquement
```

Règles :

- Une couche n'appelle **que la couche du dessous** (et jamais celle du dessus).
- La présentation ne parle pas SQL ; la couche données ne connaît pas les écrans.
- Les objets qui traversent les frontières sont simples (des DTO, des objets du domaine),
  pas des `ResultSet` ni des objets de l'interface.

Exemple Spring : `CommandeController` (présentation) → `CommandeService` (métier) →
`CommandeRepository` (données).

L'anti-pattern classique est le **contrôleur obèse** : la validation, les calculs, les
règles et les requêtes SQL écrits directement dans le contrôleur. Il est impossible à
tester sans démarrer un serveur, et la même règle finit copiée dans le contrôleur web, le
traitement par lots et l'API mobile, avec des différences.

Le défaut des couches classiques : la couche métier **dépend** de la couche données. Le
cœur de l'application dépend donc de la base de données. Le niveau 3 inverse cette
dépendance (architecture hexagonale).

## MVC

**Modèle-Vue-Contrôleur** sépare l'interface en trois rôles :

| Rôle | Responsabilité |
|---|---|
| **Modèle** | les données et les règles (sans rien savoir de l'affichage) |
| **Vue** | l'affichage du modèle |
| **Contrôleur** | reçoit les actions de l'utilisateur, appelle le modèle, choisit la vue |

Dans le web (Spring MVC, Rails, Django, Laravel), le contrôleur reçoit la requête HTTP, le
modèle est l'objet métier ou ses services, et la vue est un gabarit HTML ou un JSON.

Le piège a même un nom, le **Massive View Controller** (iOS) : tout atterrit dans le
contrôleur parce que c'est le seul endroit évident. Le remède est toujours le même :
sortir les règles vers des classes métier testables, ne garder dans le contrôleur que la
traduction « requête → appel métier → réponse ».

```kotlin
@PostMapping("/commandes")
fun creer(@RequestBody demande: DemandeCommande): ResponseEntity<CommandeDto> {
    val commande = service.passerCommande(demande.client, demande.lignes)   // le métier
    return ResponseEntity.status(201).body(CommandeDto.depuis(commande))   // la traduction
}
```

## À retenir

- L'architecture, ce sont les décisions coûteuses à changer : on les prend pour rendre les changements fréquents faciles.
- Regrouper ce qui change pour la même raison ; présentation, métier, données.
- Couches : dépendances vers le bas uniquement ; pas de SQL dans un contrôleur.
- MVC : le contrôleur traduit, le modèle décide, la vue affiche. Un contrôleur obèse est un signal d'alarme.
