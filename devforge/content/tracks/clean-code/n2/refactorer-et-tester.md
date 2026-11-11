---
id: clean-code.n2.refactorer-et-tester
title: Odeurs du code, refactorings et tests unitaires
skills: [code-smells, refactorings, tests-unitaires]
duration_min: 18
---

# Odeurs du code, refactorings et tests unitaires

## Les mauvaises odeurs du code

Une **odeur** (*code smell*, Martin Fowler et Kent Beck) n'est pas un bug : c'est un signe
qu'un endroit du code sera difficile à faire évoluer. Les plus courantes :

| Odeur | Signe | Remède habituel |
|---|---|---|
| **Fonction trop longue** | on fait défiler, on ne la décrit qu'avec des « et puis » | extraire des fonctions |
| **Classe trop grosse** (*God class*) | `GestionnaireCommande` de 3 000 lignes qui fait tout | découper selon les responsabilités |
| **Code dupliqué** | une règle copiée à plusieurs endroits | extraire et réutiliser |
| **Obsession des primitives** | des `String` pour les e-mails, des `Double` pour l'argent | des objets-valeurs (`Email`, `Montant`) |
| **Longue liste de paramètres** | `creer(nom, prenom, rue, ville, cp, pays, tel, …)` | regrouper en objets |
| **Envie de fonctionnalité** (*feature envy*) | une méthode qui passe son temps sur les données d'une autre classe | la déplacer dans cette classe |
| **Chirurgie au fusil** (*shotgun surgery*) | un petit changement oblige à toucher dix fichiers | rapprocher ce qui change ensemble |
| **`when` qui se répètent** sur un même type | chaque nouveau cas modifie cinq `when` | polymorphisme, ou une sealed class avec un seul `when` exhaustif |
| **Commentaires-déodorants** | un commentaire qui explique un code obscur | renommer, extraire |

Le meilleur indicateur reste la **douleur** : l'endroit qu'on touche souvent et qui casse
souvent est celui qui mérite d'être amélioré en premier.

## Les refactorings de base

**Refactorer**, c'est changer la **structure** du code **sans changer son comportement**, par
petites étapes sûres, en relançant les tests entre chaque étape. Ce n'est pas réécrire.

Les refactorings du quotidien (tous automatisés dans IntelliJ et Android Studio, qui les
appliquent sans erreur) :

| Refactoring | Raccourci typique |
|---|---|
| Renommer | Maj+F6 |
| Extraire une fonction | Ctrl+Alt+M (Cmd+Alt+M sur Mac) |
| Extraire une variable | Ctrl+Alt+V |
| Mettre en ligne (*inline*) | Ctrl+Alt+N |
| Changer la signature | Ctrl+F6 |
| Déplacer | F6 |
| Introduire un objet paramètre | menu Refactor |

Exemple : extraire une fonction.

```kotlin
// Avant
fun imprimerFacture(f: Facture) {
    println("Facture ${f.numero}")
    var total = 0
    for (l in f.lignes) total += l.prix * l.quantite
    if (f.client.estProfessionnel) total = total * 100 / 120
    println("Total : $total")
}

// Après
fun imprimerFacture(f: Facture) {
    println("Facture ${f.numero}")
    println("Total : ${montantAFacturer(f)}")
}

fun montantAFacturer(f: Facture): Int {
    val brut = f.lignes.sumOf { it.prix * it.quantite }
    return if (f.client.estProfessionnel) horsTaxes(brut) else brut
}
```

La fonction extraite a un nom, se teste seule, et se réutilise ailleurs.

Règles d'hygiène :

- **jamais refactorer et changer le comportement dans le même commit** : deux commits, un
  pour la structure (les tests ne changent pas), un pour la fonctionnalité ;
- refactorer **avant** d'ajouter une fonctionnalité qui serait pénible à ajouter (« rendre le
  changement facile, puis faire le changement facile », Kent Beck) ;
- la **règle du scout** : laisser le code un peu plus propre qu'on ne l'a trouvé ;
- sans tests, un refactoring n'est qu'un changement risqué (niveau 4 : le code existant).

## Des tests unitaires utiles

Un **test unitaire** vérifie un petit morceau de comportement, vite et sans dépendance
externe (ni base, ni réseau, ni horloge réelle).

La structure **Arrange / Act / Assert** (préparer, agir, vérifier) rend chaque test lisible.
Test réel en Kotlin (JUnit, kotlin.test), sur un panier qui devrait accorder 5 % de remise
dès 100 € :

```kotlin
@Test
fun `5 pour cent de remise des 100 euros`() {
    // Arrange
    val panier = Panier()
    panier.ajouter("clavier", 6_000)
    panier.ajouter("souris", 4_000)
    // Act
    val total = panier.total()
    // Assert
    assertEquals(9_500, total, "un panier de 100 € pile doit avoir la remise")
}
```

Le code testé contient un bug (`>` au lieu de `>=`). Les deux messages d'échec réels :

```
1) test vague(PanierTest)
java.lang.AssertionError: Expected value to be true.

2) 5 pour cent de remise des 100 euros(PanierTest)
java.lang.AssertionError: un panier de 100 € pile doit avoir la remise expected:<9500> but was:<10000>
```

Le premier test (`assertTrue(panier.total() == 9_500)`) dit seulement « faux ». Le second dit
**quoi**, **combien attendu**, **combien obtenu**, et **pourquoi c'est important** : le bug se
comprend sans ouvrir de débogueur.

Ce qui fait un bon test (les principes **FIRST**) :

- **F**ast : des millisecondes, pour qu'on les lance tout le temps ;
- **I**ndependent : pas d'ordre imposé, pas d'état partagé entre tests ;
- **R**epeatable : même résultat partout (pas d'horloge réelle, d'aléa non maîtrisé, de réseau) ;
- **S**elf-validating : il passe ou il échoue, sans lecture humaine des journaux ;
- **T**imely : écrit en même temps que le code (ou avant, niveau 3).

Et aussi : un nom qui décrit le **comportement** attendu (pas `test1` ni `testTotal`), un seul
comportement par test, des cas **aux limites** (0, vide, 100 € pile, le maximum), et des tests
qui vérifient le **résultat observable**, pas les détails internes : un test qui casse à chaque
refactoring alors que le comportement n'a pas changé est un frein, pas une protection.

## À retenir

- Les odeurs signalent du code difficile à faire évoluer ; commencer par l'endroit qui fait souffrir.
- Refactorer = changer la structure sans changer le comportement, par petites étapes, avec les outils de l'IDE, dans des commits séparés.
- Tests unitaires : Arrange/Act/Assert, FIRST, des noms qui décrivent un comportement, des cas limites, et des messages d'échec qui expliquent (`expected:<9500> but was:<10000>`).
