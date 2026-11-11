---
id: algo.n3.arbres-et-recursion
title: Arbres, tas, backtracking et tris efficaces
skills: [arbres, tas, backtracking, tris-efficaces]
duration_min: 15
---

# Arbres, tas, backtracking et tris efficaces

## L'arbre binaire de recherche

Chaque nœud a au plus deux enfants : tout ce qui est à gauche est plus petit, tout ce qui
est à droite est plus grand. Chercher, insérer, supprimer : on descend d'un côté à chaque
étape, donc O(hauteur).

```kotlin
fun inserer(racine: Noeud?, valeur: Int): Noeud {
    if (racine == null) return Noeud(valeur)
    if (valeur < racine.valeur) racine.gauche = inserer(racine.gauche, valeur)
    else racine.droite = inserer(racine.droite, valeur)
    return racine
}

fun infixe(n: Noeud?, sortie: MutableList<Int>) {   // gauche, nœud, droite
    if (n == null) return
    infixe(n.gauche, sortie)
    sortie += n.valeur
    infixe(n.droite, sortie)
}
```

Résultats réels pour les insertions 50, 30, 70, 20, 40, 60, 80 :

- le parcours **infixe** donne `[20, 30, 40, 50, 60, 70, 80]` : un arbre de recherche
  parcouru ainsi est toujours **trié** ;
- la hauteur vaut **3** : l'arbre est équilibré, O(log n).

Mais en insérant 1, 2, 3… 7 dans l'ordre, la hauteur vaut **7** : l'arbre dégénère en liste,
et tout passe en O(n). C'est pourquoi les vraies implémentations (`TreeMap`) sont des arbres
**auto-équilibrés** (rouge-noir, AVL).

## Le tas et la file de priorité

Un **tas** garde toujours le plus petit (ou le plus grand) élément accessible en O(1), et
insère ou retire en O(log n). C'est la structure derrière `PriorityQueue` :

```kotlin
val tas = PriorityQueue(listOf(5, 1, 8, 3, 9, 2))
println(List(3) { tas.poll() })   // [1, 2, 3]
```

Usages : planifier les tâches les plus urgentes, garder les k meilleurs scores, l'algorithme
de Dijkstra (niveau 4).

## Le backtracking

Explorer toutes les possibilités en construisant une solution pas à pas, et **revenir en
arrière** dès qu'un choix mène à une impasse. Exemple simple, toutes les permutations :

```kotlin
fun permutations(elements: List<Char>): List<String> =
    if (elements.isEmpty()) listOf("")
    else elements.flatMap { e -> permutations(elements - e).map { e + it } }
```

`permutations(listOf('a', 'b', 'c'))` donne réellement
`[abc, acb, bac, bca, cab, cba]` : 3! = 6 solutions. Avec 10 éléments, 3,6 millions :
le backtracking est exponentiel, et tout l'art consiste à **élaguer** les branches sans
espoir le plus tôt possible (sudoku, placement de reines, planification).

## Les tris en O(n log n)

| Tri | Principe | Complexité | Particularité |
|---|---|---|---|
| **Tri fusion** | couper en deux, trier chaque moitié, fusionner | O(n log n) toujours | stable, mémoire supplémentaire O(n) |
| **Tri rapide** | choisir un pivot, partitionner, trier chaque côté | O(n log n) en moyenne, O(n²) au pire | en place, très rapide en pratique |
| **Tri par tas** | construire un tas, extraire le minimum n fois | O(n log n) toujours | en place, non stable |

Le pire cas du tri rapide : un pivot toujours mal choisi (par exemple le premier élément
d'une liste déjà triée). On le prévient avec un pivot aléatoire ou « médiane de trois ».
Les bibliothèques modernes combinent les techniques : Java trie les objets avec **TimSort**
(fusion + insertion, excellent sur les données partiellement triées).

## À retenir

- Arbre de recherche : parcours infixe trié ; O(log n) s'il est équilibré, O(n) s'il dégénère.
- Tas : le minimum toujours accessible ; `PriorityQueue`.
- Backtracking : explorer, revenir en arrière, élaguer ; souvent exponentiel.
- Tri fusion (stable, toujours n log n), tri rapide (rapide, pire cas n²).
