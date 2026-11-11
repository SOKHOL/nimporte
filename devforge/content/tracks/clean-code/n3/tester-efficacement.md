---
id: clean-code.n3.tester-efficacement
title: TDD, doublures de test, pyramide et couverture
skills: [tdd, doublures, pyramide, couverture]
duration_min: 20
---

# TDD, doublures de test, pyramide et couverture

## Le développement piloté par les tests

Le **TDD** (*Test-Driven Development*, Kent Beck) inverse l'ordre habituel, en cycles de
quelques minutes :

1. **Rouge** : écrire un test pour un petit comportement qui n'existe pas encore, et le voir
   **échouer** (pour être sûr qu'il teste quelque chose).
2. **Vert** : écrire le code le plus simple qui le fait passer.
3. **Refactorer** : nettoyer le code et les tests, les tests restant verts.

```kotlin
// 1. Rouge
@Test fun `un panier vide coûte zéro`() = assertEquals(0, Panier().total())
// 2. Vert : fun total() = 0
// 3. Test suivant, rouge
@Test fun `le total additionne les lignes`() { … }
// 4. Vert : fun total() = lignes.sumOf { it.prix * it.quantite }
```

Ce que ça apporte : on ne code que ce qui est demandé (YAGNI), chaque ligne est couverte par
un test **qui a déjà échoué** une fois, et le test oblige à penser à l'**interface** (comment
on va utiliser le code) avant l'implémentation. Un code difficile à tester est un signal de
conception : trop de dépendances, des effets cachés.

Le TDD n'est pas une religion : il est très efficace pour la logique métier et les
corrections de bugs (on écrit d'abord le test qui reproduit le bug), moins pour explorer une
API inconnue ou ajuster une interface graphique.

## Les doublures de test

Pour tester une unité sans ses dépendances lentes ou imprévisibles (base, réseau, horloge,
service de paiement), on les remplace par des **doublures** (Gerard Meszaros) :

| Doublure | Rôle | Exemple |
|---|---|---|
| **Dummy** | remplit un paramètre, jamais utilisé | un journal vide passé au constructeur |
| **Stub** | renvoie des réponses préparées | `tauxDeChange.pour("USD")` renvoie toujours 1,08 |
| **Fake** | une vraie implémentation, simplifiée | un dépôt en mémoire (`CommandesEnMemoire`) |
| **Spy** | enregistre les appels pour les vérifier après | « l'e-mail a-t-il été envoyé une fois ? » |
| **Mock** | programmé avec des **attentes** sur les appels | « `debiter(4990)` doit être appelé exactement une fois » |

```kotlin
class FausseHorloge(var maintenant: Instant) : Clock { … }      // fake
val paiement = mockk<Paiement>()                                 // mock (MockK)
every { paiement.debiter(any()) } returns Accepte
service.valider(commande)
verify(exactly = 1) { paiement.debiter(Montant(4990)) }
```

Les pièges des mocks :

- **Tester l'implémentation au lieu du comportement** : un test qui vérifie que la méthode A
  appelle B puis C casse à chaque refactoring, même quand le résultat est identique.
- **Mocker ce qu'on ne possède pas** : un mock du client HTTP d'une bibliothèque reproduit ce
  qu'on **croit** qu'elle fait. On l'enveloppe dans son propre adaptateur (un port) et on teste
  cet adaptateur avec un test d'intégration.
- **Tout mocker** : un test dont tout est simulé ne vérifie plus que le scénario du mock.

Préférer, dans l'ordre : les objets réels quand ils sont rapides et déterministes, puis des
**fakes** (simples, réutilisables, ils se comportent comme le vrai), et les mocks pour vérifier
une **interaction** qui fait partie du comportement attendu (un paiement, un e-mail).

## La pyramide des tests

```
            ▲   peu nombreux, lents, fragiles, très réalistes
           ╱ ╲        tests de bout en bout (navigateur, application, API déployée)
          ╱   ╲
         ╱     ╲      tests d'intégration (vraie base, vrai Kafka, vrais appels HTTP locaux)
        ╱       ╲
       ╱_________╲    tests unitaires : nombreux, rapides, précis
```

Mesure réelle : 200 tests unitaires d'une fonction de calcul s'exécutent en **0,15 s** ; les
mêmes vérifications faites contre une vraie base PostgreSQL locale (une table temporaire par
test) prennent **0,91 s**, six fois plus, sans même compter le démarrage d'une base dans un
conteneur ni le réseau. À l'échelle de milliers de tests, l'écart décide si l'on lance les
tests à chaque modification ou une fois par jour.

Les **tests d'intégration** restent indispensables : ils trouvent ce que les doublures
cachent (une requête SQL fausse, un mapping JSON, une migration, une configuration).
**Testcontainers** démarre de vraies bases, files de messages ou navigateurs dans des
conteneurs jetables, le temps des tests.

Les **tests de bout en bout** vérifient les parcours critiques (connexion, achat, paiement)
tels qu'un utilisateur les vit. Ils sont lents et fragiles : quelques-uns, bien choisis.

Une pyramide inversée (beaucoup de tests de bout en bout, peu de tests unitaires) donne une
suite lente, instable, et des échecs difficiles à diagnostiquer. Certaines équipes préfèrent
un « trophée » qui donne plus de place aux tests d'intégration ; l'idée commune reste : des
retours rapides pour l'essentiel, des tests lents seulement là où ils apportent quelque chose
d'unique.

## La couverture de code et ses limites

La **couverture** mesure la part du code exécutée par les tests. Démonstration réelle en
Python avec deux tests :

```python
def remise(total):
    if total >= 100:
        return total * 0.95
    return total

def test_remise_appliquee(): assert remise(200) == 190
def test_pas_de_remise():    assert remise(50) == 50
```

`coverage report` : **100 %** des lignes couvertes. Pourtant, si l'on remplace `>=` par `>`
(un bug réaliste), les deux tests **passent toujours** : `2 passed`. Il manque le cas à la
limite, `remise(100) == 95` ; une fois ajouté, le même mutant donne `1 failed, 2 passed`.

La couverture dit ce qui n'est **pas** testé (une ligne jamais exécutée n'est vérifiée par
personne) ; elle ne dit pas ce qui est **bien** testé : du code exécuté sans assertion
pertinente est couvert à 100 %. Un objectif chiffré imposé (« 80 % minimum ») pousse à écrire
des tests qui exécutent du code sans rien vérifier. On s'en sert comme **outil
d'exploration** : quelles branches importantes ne sont jamais testées ? Le test de mutation
(niveau 5) mesure, lui, la qualité des tests.

## À retenir

- TDD : rouge, vert, refactorer, en cycles courts ; idéal pour la logique métier et la reproduction des bugs.
- Doublures : dummy, stub, fake, spy, mock ; préférer le réel ou un fake, mocker les interactions qui comptent, ne pas mocker ce qu'on ne possède pas.
- Pyramide : beaucoup de tests unitaires rapides (0,15 s contre 0,91 s mesurés), des tests d'intégration ciblés (Testcontainers), quelques tests de bout en bout.
- 100 % de couverture n'empêche pas un bug de passer (démontré) : la couverture montre les trous, pas la qualité.
