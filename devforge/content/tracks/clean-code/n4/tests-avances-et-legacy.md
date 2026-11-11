---
id: clean-code.n4.tests-avances-et-legacy
title: Code existant, tests de contrat, tests par propriétés et tests instables
skills: [legacy, tests-contrat, property-based, tests-instables]
duration_min: 20
---

# Code existant, tests de contrat, tests par propriétés et tests instables

## Travailler avec du code existant sans tests

Michael Feathers définit le *legacy code* comme du « code sans tests » : on ne peut pas le
modifier en sécurité, car rien ne dit ce qui casse. Le dilemme : pour le modifier, il faut des
tests ; pour ajouter des tests, il faut souvent le modifier.

La démarche :

1. **Repérer les points de changement** : où faut-il intervenir ?
2. **Trouver des points d'injection** (*seams*) : un endroit où l'on peut changer le
   comportement sans modifier le code autour (un paramètre, une interface, une méthode
   redéfinissable) pour isoler la partie à tester de ses dépendances (base, réseau, horloge).
3. **Écrire des tests de caractérisation** : des tests qui figent le comportement **actuel**,
   même s'il paraît faux. On appelle le code avec des entrées variées, on note ce qu'il
   renvoie, et on en fait des assertions. Le but n'est pas de dire ce qui est juste, mais de
   savoir si l'on a **changé** quelque chose.
4. **Modifier**, puis **refactorer** sous la protection de ces tests.

Techniques utiles :

- **Sprout method** (faire pousser une méthode) : écrire le nouveau comportement dans une
  **nouvelle** fonction testée, et ne faire qu'**un appel** depuis l'ancien code.
- **Wrap method** : envelopper l'ancienne fonction pour ajouter un comportement avant ou
  après, sans la toucher.
- **Approval tests** (tests d'approbation) : enregistrer une sortie complète (un rapport, un
  JSON, un PDF en texte) comme référence, et comparer à chaque exécution.
- Les **refactorings automatisés de l'IDE**, sûrs même sans tests, pour créer les points
  d'injection (extraire une méthode, introduire un paramètre).

Et un réflexe : ne pas vouloir tout réécrire. Le vieux code contient des années de corrections
de cas particuliers que personne n'a documentés.

## Les tests de contrat

Dans un système de services, le service **Commande** appelle l'API du service **Client**. Les
tests unitaires de Commande utilisent un faux Client ; ceux de Client ne savent pas ce que
Commande attend. Le jour où Client renomme un champ, tout est vert de chaque côté… et la
production casse.

Les **tests de contrat pilotés par le consommateur** (Pact, Spring Cloud Contract) :

1. Le **consommateur** (Commande) écrit, dans ses tests, les interactions qu'il attend :
   « pour `GET /clients/42`, j'ai besoin d'un `nom` texte et d'un `email` ». Cela produit un
   **contrat**.
2. Le contrat est publié (un *broker*).
3. Le **fournisseur** (Client) rejoue tous les contrats de ses consommateurs contre son vrai
   code dans sa propre CI. Si un changement casse un contrat, **son** build échoue, avant le
   déploiement.

On vérifie ainsi la compatibilité sans environnement complet où tous les services tournent en
même temps, environnement lent, coûteux et souvent cassé. Le contrat ne décrit que ce que le
consommateur **utilise** : le fournisseur reste libre de changer le reste.

## Les tests par propriétés

Un test classique vérifie des **exemples** choisis à la main. Un test **par propriétés**
énonce une **règle qui doit être vraie pour toutes les entrées**, et un outil (Hypothesis en
Python, jqwik ou Kotest en Kotlin/Java, fast-check en JavaScript) génère des centaines
d'entrées, y compris tordues.

Démonstration réelle : un encodeur par plages (`"aaab"` → `"3a1b"`) et son décodeur. Le test
par exemples passe. La propriété « décoder(encoder(x)) == x pour tout texte x » :

```python
@given(st.text())
def test_aller_retour(texte):
    assert decoder(encoder(texte)) == texte
```

Résultat réel :

```
Falsifying example: test_aller_retour(
    texte='0',
)
1 failed, 1 passed
```

Hypothesis a trouvé une entrée qui casse le code **et l'a réduite** (*shrinking*) au plus
petit contre-exemple possible : le texte `"0"`. L'encodeur produit `"10"` (« un zéro »), que
le décodeur lit comme le nombre 10 sans caractère derrière. Personne n'aurait pensé à tester
un texte contenant des chiffres.

Les propriétés classiques :

- **aller-retour** : `lire(ecrire(x)) == x` (sérialisation, encodage, compression) ;
- **invariants** : un tri renvoie une liste de même taille, ordonnée, avec les mêmes éléments ;
- **oracle** : comparer à une implémentation simple mais lente qu'on sait juste ;
- **idempotence** : `normaliser(normaliser(x)) == normaliser(x)` ;
- **ce qui ne doit jamais arriver** : aucune entrée ne fait planter l'analyseur (proche du
  fuzzing, parcours Sécurité, niveau 4).

## Les tests instables

Un test **instable** (*flaky*) passe ou échoue sans que le code ait changé. Démonstration
réelle :

```python
def fruits_uniques(panier):
    return list(set(panier))

def test_fruits_uniques():
    assert fruits_uniques(["pomme", "kiwi", "pomme", "poire"]) == ["pomme", "kiwi", "poire"]
```

Lancé 50 fois : **8 réussites, 42 échecs**. L'ordre des éléments d'un ensemble de chaînes
change d'un processus à l'autre en Python (le hachage des chaînes est rendu aléatoire à chaque
démarrage, une protection de sécurité) ; avec `PYTHONHASHSEED=0`, l'ordre est fixé et le test
devient… stable, mais toujours faux.

Les causes habituelles :

| Cause | Remède |
|---|---|
| ordre non garanti (ensembles, `select` sans `order by`, résultats parallèles) | comparer sans tenir compte de l'ordre, ou trier |
| horloge réelle, fuseaux horaires, changement d'heure | injecter l'horloge |
| attentes fixes (`sleep(2000)`) en tests asynchrones ou d'interface | attendre une **condition** (Awaitility, attentes explicites) |
| état partagé entre tests (base, fichier, singleton) | isoler : transaction annulée, données propres à chaque test |
| ordre des tests | chaque test prépare tout ce dont il a besoin |
| réseau, services externes | doublures ou conteneurs locaux |

Un test instable n'est pas un « faux » échec : il signale souvent un vrai problème (une
course entre threads, une dépendance à l'ordre). Mais il détruit la confiance dans la CI. On le
corrige tout de suite, ou on le **met en quarantaine** avec un ticket et un propriétaire,
jamais en relançant le pipeline jusqu'à ce que ça passe.

## À retenir

- Code sans tests : points d'injection, tests de caractérisation, sprout/wrap method, refactorings automatisés ; ne pas tout réécrire.
- Tests de contrat : le consommateur décrit ce qu'il utilise, le fournisseur le vérifie dans sa CI.
- Tests par propriétés : des règles vraies pour toute entrée ; Hypothesis a trouvé et réduit le contre-exemple `'0'`.
- Tests instables (42 échecs sur 50 mesurés) : ordre, horloge, attentes fixes, état partagé ; corriger ou mettre en quarantaine, jamais relancer en boucle.
