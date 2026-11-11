---
id: algo.n4.graphes-et-dp
title: Graphes et programmation dynamique
skills: [graphes, plus-court-chemin, tri-topologique, programmation-dynamique, glouton]
duration_min: 16
---

# Graphes et programmation dynamique

## Les graphes

Un graphe, ce sont des **nœuds** reliés par des **arêtes** : un réseau social, des routes,
des dépendances entre modules, des pages web. On le représente le plus souvent par une
**liste d'adjacence** : `Map<Noeud, List<Voisin>>`.

### Parcours en largeur (BFS)

On explore niveau par niveau, avec une **file**. Dans un graphe non pondéré, BFS donne les
plus courts chemins en nombre d'étapes.

```kotlin
fun bfs(graphe: Map<String, List<String>>, depart: String): Map<String, Int> {
    val distances = mutableMapOf(depart to 0)
    val file = ArrayDeque(listOf(depart))
    while (file.isNotEmpty()) {
        val courant = file.removeFirst()
        for (voisin in graphe[courant].orEmpty()) {
            if (voisin !in distances) {
                distances[voisin] = distances.getValue(courant) + 1
                file.addLast(voisin)
            }
        }
    }
    return distances
}
```

Avec Ada → Bob, Cy ; Bob → Dan ; Cy → Dan ; Dan → Eve, résultat réel :
`{Ada=0, Bob=1, Cy=1, Dan=2, Eve=3}` (les « degrés de séparation »).

Le **parcours en profondeur** (DFS) utilise une pile (ou la récursion) : il sert à détecter
des cycles, trouver des composantes connexes, explorer un labyrinthe.

### Plus court chemin pondéré : Dijkstra

Quand les arêtes ont des longueurs, on utilise une **file de priorité** : on traite toujours
le nœud le plus proche connu.

Routes A→B (4), A→C (1), C→B (2), C→D (5), B→D (1) : Dijkstra donne réellement
`{A=0, B=3, C=1, D=4}`. Le chemin vers B passe par C (1 + 2 = 3 < 4), et D par B (3 + 1).
Complexité : O((V + E) log V). Il ne fonctionne pas avec des poids négatifs.

### Tri topologique

Ordonner des tâches qui dépendent les unes des autres (modules à compiler, cours à suivre) :
on traite d'abord celles sans dépendance restante. Pour les modules de cette app
(model → domain → data → app, markdown → app), un tri topologique donne réellement
`[markdown, model, domain, data, app]`. C'est ce que fait Gradle. S'il reste des tâches à la
fin, le graphe contient un **cycle**.

## La programmation dynamique

Quand un problème se découpe en **sous-problèmes qui se répètent**, on mémorise leurs
résultats au lieu de les recalculer.

```kotlin
fun fibNaif(n: Int): Long = if (n < 2) n.toLong() else fibNaif(n - 1) + fibNaif(n - 2)

fun fibMemo(n: Int, memo: MutableMap<Int, Long> = mutableMapOf()): Long {
    if (n < 2) return n.toLong()
    return memo.getOrPut(n) { fibMemo(n - 1, memo) + fibMemo(n - 2, memo) }
}
```

Mesuré pour ce cours, `fib(30)` = 832 040 : **2 692 537 appels** en version naïve
(exponentielle), **59 appels** avec mémorisation (linéaire).

La démarche : 1) définir le sous-problème (« le meilleur résultat pour un montant m ») ;
2) écrire la relation de récurrence ; 3) mémoriser (de haut en bas) ou remplir un tableau
(de bas en haut).

## Les algorithmes gloutons

Un algorithme glouton fait à chaque étape le choix qui **semble** le meilleur, sans revenir
en arrière. Rapide, parfois optimal… parfois faux.

Rendre 6 avec des pièces de 1, 3 et 4 :

- glouton (toujours la plus grosse pièce possible) : 4 + 1 + 1 = **3 pièces** ;
- programmation dynamique : 3 + 3 = **2 pièces**.

Résultats réels. Le glouton marche pour les euros (1, 2, 5, 10…), pas pour n'importe quel
système. Il faut **prouver** qu'un glouton est optimal avant de lui faire confiance.

## À retenir

- Graphe = nœuds + arêtes ; liste d'adjacence.
- BFS (file) : plus courts chemins en nombre d'étapes. DFS (pile) : cycles, composantes.
- Dijkstra : plus courts chemins pondérés, avec une file de priorité.
- Tri topologique : ordonner des dépendances, détecter les cycles.
- Programmation dynamique : mémoriser les sous-problèmes (2 692 537 appels → 59).
- Glouton : rapide, mais à prouver.
