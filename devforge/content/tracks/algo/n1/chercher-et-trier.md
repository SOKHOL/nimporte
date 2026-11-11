---
id: algo.n1.chercher-et-trier
title: Chercher et trier
skills: [recherche, tris-simples, analyse-complexite]
duration_min: 13
---

# Chercher et trier

## La recherche linéaire

Pour trouver un élément dans une liste quelconque, on la parcourt du début à la fin :
O(n). Simple, et c'est parfois la seule option.

## La recherche dichotomique

Si la liste est **triée**, on fait comme dans un dictionnaire papier : on ouvre au milieu,
et on élimine la moitié qui ne peut pas contenir la réponse.

```kotlin
fun rechercheDichotomique(triee: List<Int>, cible: Int): Int {
    var bas = 0
    var haut = triee.size - 1
    while (bas <= haut) {
        val milieu = bas + (haut - bas) / 2
        when {
            triee[milieu] == cible -> return milieu
            triee[milieu] < cible -> bas = milieu + 1
            else -> haut = milieu - 1
        }
    }
    return -1
}
```

Mesuré pour ce cours sur un million d'éléments : **20 étapes** pour trouver le dernier
élément, 20 aussi pour conclure qu'un élément est absent. La recherche linéaire en
aurait fait jusqu'à un million. C'est O(log n) : doubler les données n'ajoute qu'une étape.

Deux détails d'expert :

- `bas + (haut - bas) / 2` plutôt que `(bas + haut) / 2` : la somme peut **déborder**
  sur de très grands tableaux (un bug resté vingt ans dans la bibliothèque standard de Java).
- `while (bas <= haut)` et pas `<` : avec `<`, le dernier élément candidat n'est jamais
  examiné. Testé : chercher 7 dans `[1, 3, 5, 7]` renvoie alors -1.

## Les tris simples

| Tri | Principe | Complexité |
|---|---|---|
| Sélection | chercher le plus petit, le mettre en tête, recommencer | O(n²) |
| Insertion | insérer chaque élément à sa place dans la partie déjà triée | O(n²), mais O(n) si presque trié |
| Bulles | échanger les voisins mal ordonnés, jusqu'à ce que rien ne bouge | O(n²) |

Ils sont lents sur de gros volumes, mais le tri par insertion est excellent sur de **petits**
tableaux ou des données **presque triées** : les tris de la bibliothèque standard
l'utilisent pour les petits morceaux.

En pratique, on utilise `sorted()`, `sortedBy { }` et `sortedWith(compareBy(…))` : ils sont
en O(n log n) et **stables** (deux éléments égaux gardent leur ordre d'origine).

## Analyser un algorithme

1. Compter ce qui se répète : combien de tours de boucle, en fonction de n ?
2. Boucles imbriquées : multiplier ; boucles successives : additionner et garder la plus grande.
3. Diviser le problème par deux à chaque étape : log n.
4. Penser au **pire cas** (l'élément n'est pas là) et pas seulement au cas moyen.

## À retenir

- Linéaire : O(n). Dichotomique : O(log n), mais seulement sur des données triées.
- 1 million d'éléments : 20 étapes en dichotomie.
- `bas <= haut` et `bas + (haut - bas) / 2` : les deux pièges classiques.
- Tris simples en O(n²) ; en pratique, `sorted()` (O(n log n), stable).
