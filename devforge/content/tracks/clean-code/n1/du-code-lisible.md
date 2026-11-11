---
id: clean-code.n1.du-code-lisible
title: Nommer, découper, commenter, et DRY, KISS, YAGNI
skills: [nommage, fonctions, commentaires, principes]
duration_min: 16
---

# Nommer, découper, commenter, et DRY, KISS, YAGNI

Le code est lu **bien plus souvent** qu'il n'est écrit : par tes collègues, par les
relecteurs, par toi dans six mois. Le temps de lecture domine le coût d'un logiciel. Du code
lisible n'est pas une question de goût : c'est ce qui permet de le modifier vite et sans
erreur.

## Bien nommer

Un bon nom dit **pourquoi** la chose existe, **ce qu'elle fait** et **comment s'en servir**,
sans qu'il faille lire son corps.

```kotlin
// Avant
fun calc(l: List<Item>, f: Boolean): Double {
    var t = 0.0
    for (i in l) t += i.p * i.q
    return if (f) t * 0.95 else t
}

// Après
fun totalPanier(lignes: List<Ligne>, clientFidele: Boolean): Montant {
    val brut = lignes.sumOf { it.prixUnitaire * it.quantite }
    return if (clientFidele) brut.avecRemise(REMISE_FIDELITE) else brut
}
```

Règles pratiques :

- des noms **du métier** (le langage des experts : `facture`, `échéance`, `relance`) plutôt
  que techniques (`data`, `info`, `manager`, `handler`, `obj`) ;
- une **longueur proportionnelle à la portée** : `i` va très bien pour une boucle de 3 lignes,
  pas pour un champ de classe ;
- les **booléens** se lisent comme une question : `estActif`, `aDesDroitsAdmin`,
  `peutEtreAnnule` ;
- les **fonctions** commencent par un verbe (`calculerTotal`, `envoyerRelance`), les classes
  sont des noms (`Facture`, `ServiceDeRelance`) ;
- pas de **nombres magiques** : `if (age >= 18)` devient `if (age >= AGE_MAJORITE)` ;
- un nom qui ment est pire qu'un nom vague : `getUtilisateur()` qui crée l'utilisateur s'il
  n'existe pas, ou `valider()` qui enregistre aussi en base.

Quand on ne trouve pas de nom simple, c'est souvent que la chose fait **trop** de choses.

## Des fonctions courtes et claires

Une fonction devrait faire **une seule chose**, à **un seul niveau d'abstraction**. Le signe
d'une fonction qui en fait trop : on ne peut la décrire qu'avec des « et puis ».

```kotlin
fun traiterCommande(demande: Demande) {
    val commande = valider(demande)      // chaque étape a un nom
    reserverLeStock(commande)
    val paiement = encaisser(commande)
    confirmerAuClient(commande, paiement)
}
```

On lit l'histoire en quatre lignes ; les détails sont dans des fonctions au nom explicite.

Autres principes :

- **peu de paramètres** (au-delà de trois, un objet qui les regroupe) ; un booléen en
  paramètre (`imprimer(rapport, true)`) cache souvent deux fonctions ;
- **pas d'effets de bord cachés** : `verifierMotDePasse()` ne doit pas aussi ouvrir une session ;
- **séparer les commandes des requêtes** : une fonction modifie l'état **ou** renvoie une
  information, pas les deux ;
- des **retours anticipés** (*guard clauses*) plutôt que des `if` imbriqués sur cinq niveaux :

```kotlin
fun prixLivraison(commande: Commande): Montant {
    if (commande.estRetraitEnMagasin) return Montant.ZERO
    if (commande.total >= SEUIL_LIVRAISON_GRATUITE) return Montant.ZERO
    return tarifs.pour(commande.poids, commande.pays)
}
```

## Des commentaires utiles

Le meilleur commentaire est souvent **un meilleur nom** :

```kotlin
// vérifie si l'utilisateur peut accéder au rapport
if (u.role == 3 && u.actif && !u.bloque) …

if (utilisateur.peutConsulterLesRapports()) …
```

Les commentaires qui valent la peine expliquent **pourquoi**, ce que le code ne peut pas dire :

```kotlin
// L'API du transporteur renvoie parfois 500 au premier appel après minuit (ticket #4521) :
// on réessaie une fois avant de déclarer l'échec.
```

Et aussi : la documentation d'une API publique, un avertissement (« cette méthode n'est pas
thread-safe »), une référence (l'algorithme, la norme, le texte de loi).

Les commentaires à supprimer : ceux qui paraphrasent le code (`i++ // incrémente i`), le code
commenté (Git s'en souvient), les journaux de modifications en tête de fichier, et surtout
les commentaires **périmés**, qui mentent depuis que le code a changé sans eux.

## DRY, KISS, YAGNI

**DRY** (*Don't Repeat Yourself*) : chaque **connaissance** du système a une seule
représentation. Si la règle « 5 % de remise au-delà de 100 € » est écrite à trois endroits,
on finira par n'en modifier que deux. Mais attention : deux bouts de code qui se
**ressemblent** ne sont pas forcément la **même** connaissance. Fusionner deux règles qui
coïncident aujourd'hui mais évolueront séparément crée un couplage pire que la duplication
(« la duplication coûte moins cher que la mauvaise abstraction », Sandi Metz). La règle de
trois : on attend la troisième occurrence pour factoriser.

**KISS** (*Keep It Simple, Stupid*) : la solution la plus simple qui répond au besoin. Une
boucle claire vaut mieux qu'une chaîne de `flatMap` illisible ; une classe vaut mieux qu'une
hiérarchie de cinq interfaces.

**YAGNI** (*You Aren't Gonna Need It*) : ne pas construire aujourd'hui ce dont on « aura
peut-être besoin un jour » : la configuration pour dix bases de données alors qu'on en a une,
le système de plugins pour un seul plugin. Le code spéculatif coûte à écrire, à tester, à
lire et à maintenir, et le besoin réel, quand il arrive, est rarement celui qu'on avait imaginé.

## À retenir

- Des noms du métier, proportionnés à leur portée, qui ne mentent pas ; pas de nombres magiques.
- Une fonction = une chose, un niveau d'abstraction, peu de paramètres, pas d'effet caché, des retours anticipés.
- Commenter le pourquoi ; supprimer les paraphrases, le code commenté et les commentaires périmés.
- DRY pour la connaissance (pas pour la ressemblance), KISS, YAGNI.
