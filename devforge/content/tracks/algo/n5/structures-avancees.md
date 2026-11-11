---
id: algo.n5.structures-avancees
title: Structures avancées
skills: [tries, probabilistes, union-find, analyse-amortie]
duration_min: 13
---

# Structures avancées

## L'arbre de préfixes (trie)

Un **trie** range des mots lettre par lettre : tous les mots qui commencent par « ko »
partagent les nœuds « k » puis « o ». Chercher tous les mots d'un préfixe coûte O(longueur
du préfixe), quel que soit le nombre de mots stockés.

```kotlin
class Trie {
    private class Noeud {
        val enfants = sortedMapOf<Char, Noeud>()
        var finDeMot = false
    }

    private val racine = Noeud()

    fun ajouter(mot: String) {
        var n = racine
        for (c in mot) n = n.enfants.getOrPut(c) { Noeud() }
        n.finDeMot = true
    }
}
```

Avec « kotlin », « kotlinx », « koin », « java », « kafka », les recherches donnent
réellement : préfixe `ko` → `[koin, kotlin, kotlinx]`, `ka` → `[kafka]`, `py` → `[]`.
Usages : autocomplétion, correcteurs orthographiques, routage d'URL, tables de routage IP.

## Les structures probabilistes

Quand les données sont gigantesques, on accepte une **petite erreur** en échange d'une
mémoire minuscule.

- **Filtre de Bloom** : « cet élément est-il dans l'ensemble ? ». Réponse « non » : certaine.
  Réponse « oui » : probable (faux positifs possibles, jamais de faux négatif). Quelques
  bits par élément. Usage : éviter un accès disque coûteux pour une clé absente (Cassandra,
  RocksDB), filtrer des URL déjà visitées.
- **HyperLogLog** : compter le nombre d'éléments **distincts** (visiteurs uniques) avec
  environ 1 % d'erreur et quelques kilo-octets, même pour des milliards d'éléments. Redis
  le propose (`PFADD`, `PFCOUNT`).
- **Count-Min Sketch** : estimer la fréquence des éléments (les sujets les plus discutés).

## Union-find

Union-find maintient des **groupes** qu'on fusionne au fil du temps, et répond à « ces deux
éléments sont-ils dans le même groupe ? ». Avec deux optimisations (compression des chemins
et union par rang), chaque opération coûte un temps quasi constant.

Usages : composantes connexes d'un réseau, détection de cycles dans l'algorithme de Kruskal
(arbre couvrant de poids minimal), regroupement de comptes en double.

## L'analyse amortie

Une `ArrayList` est un tableau qui **double** sa capacité quand il est plein. Cette copie
coûte O(n)… mais elle arrive de plus en plus rarement. Sur n ajouts, le total des copies
reste inférieur à 2n : chaque ajout coûte **O(1) amorti**.

L'analyse amortie raisonne sur une **suite** d'opérations plutôt que sur le pire cas
d'une seule. Conséquence pratique : si tu connais la taille finale, `ArrayList(capaciteInitiale)`
évite toutes les copies ; et une structure « O(1) amorti » peut quand même avoir un pic
ponctuel de latence au moment d'un agrandissement.

## À retenir

- Trie : recherche par préfixe en O(longueur du préfixe) ; autocomplétion.
- Bloom (« peut-être » ou « sûrement pas »), HyperLogLog (distincts approximés) : peu de mémoire, une petite erreur.
- Union-find : fusionner des groupes et tester l'appartenance en temps quasi constant.
- Amorti : un coût moyen sur une suite d'opérations ; l'ArrayList ajoute en O(1) amorti.
