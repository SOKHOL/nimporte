---
id: algo.n2.structures-de-base
title: Piles, files, hachage et deux pointeurs
skills: [piles-files, tables-hachage, listes-chainees, deux-pointeurs]
duration_min: 14
---

# Piles, files, hachage et deux pointeurs

## Pile et file

| Structure | Règle | Opérations | Exemples |
|---|---|---|---|
| **Pile** (*stack*) | dernier entré, premier sorti | empiler, dépiler en O(1) | « Annuler », la pile d'appels, les parenthèses |
| **File** (*queue*) | premier entré, premier sorti | enfiler, défiler en O(1) | tâches à traiter, parcours en largeur |

En Kotlin, `ArrayDeque` fait les deux. Exemple classique : vérifier que des parenthèses
sont équilibrées.

```kotlin
fun equilibre(texte: String): Boolean {
    val pile = ArrayDeque<Char>()
    val paires = mapOf(')' to '(', ']' to '[', '}' to '{')
    for (c in texte) {
        when (c) {
            '(', '[', '{' -> pile.addLast(c)
            ')', ']', '}' -> if (pile.removeLastOrNull() != paires[c]) return false
        }
    }
    return pile.isEmpty()
}
```

Pour `"(a[b]{c})"`, `"(]"`, `"(("` et `"f(x) = [1, 2]"`, les résultats réels sont
`true`, `false`, `false`, `true`.

## La table de hachage

`HashMap` et `HashSet` calculent, à partir du `hashCode` de la clé, la case où la ranger :
recherche, ajout et suppression en **O(1)** en moyenne. C'est la structure la plus utile
des entretiens techniques : beaucoup de problèmes en O(n²) passent en O(n) avec une table.

« Deux nombres dont la somme vaut une cible » : au lieu de tester toutes les paires (O(n²)),
on mémorise ce qu'on a déjà vu.

```kotlin
fun deuxSomme(nombres: List<Int>, cible: Int): Pair<Int, Int>? {
    val vus = HashMap<Int, Int>()          // valeur -> indice
    for ((i, n) in nombres.withIndex()) {
        vus[cible - n]?.let { return it to i }
        vus[n] = i
    }
    return null
}
```

`deuxSomme(listOf(2, 7, 11, 15), 18)` renvoie réellement `(1, 2)` : 7 + 11.

## La liste chaînée

Chaque élément pointe vers le suivant. Insérer ou retirer en tête : O(1). Accéder au
10 000ᵉ élément : O(n), et chaque saut d'un nœud à l'autre rate souvent le cache du
processeur. En pratique, `ArrayList` (un tableau qui s'agrandit) est presque toujours plus
rapide. Mais les listes chaînées restent un classique des entretiens (inverser une liste,
détecter un cycle avec deux pointeurs à vitesses différentes).

## Deux pointeurs et fenêtre glissante

Deux indices qui avancent dans le tableau évitent une double boucle.

**Fenêtre glissante** : la meilleure somme de k jours consécutifs. Au lieu de recalculer
chaque fenêtre (O(n·k)), on ajoute l'élément qui entre et on retire celui qui sort :

```kotlin
fun meilleureSemaine(ventes: IntArray, k: Int): Int {
    var somme = ventes.take(k).sum()
    var meilleure = somme
    for (i in k until ventes.size) {
        somme += ventes[i] - ventes[i - k]
        meilleure = maxOf(meilleure, somme)
    }
    return meilleure
}
```

Pour `[3, 1, 4, 1, 5, 9, 2, 6]` et k = 3, résultat réel : `17` (9 + 2 + 6), en O(n).

## À retenir

- Pile : dernier entré, premier sorti. File : premier entré, premier sorti. `ArrayDeque` fait les deux.
- Une table de hachage transforme beaucoup de O(n²) en O(n).
- Les listes chaînées sont surtout un sujet d'entretien ; `ArrayList` gagne en pratique.
- Deux pointeurs et fenêtre glissante : une seule passe au lieu de deux boucles.
