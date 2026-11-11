---
id: performance.n3.un-code-efficace
title: Complexité en pratique, mémoïsation et allocations
skills: [complexite-pratique, caches-memoisation, allocations]
duration_min: 17
---

# Complexité en pratique, mémoïsation et allocations

## La complexité en pratique

La notation O (parcours Fondations et Algorithmes) devient très concrète dès que les volumes
grandissent. Mesures réelles en Python :

| Opération | Version lente | Version rapide |
|---|---|---|
| 2 000 recherches dans 20 000 identifiants | **liste** : 148 ms (O(n) par recherche) | **set** : 0,21 ms (O(1)) |
| dédoublonner 20 000 éléments | « si pas déjà vu, ajouter » avec une liste : 258 ms (O(n²)) | `dict.fromkeys` : 0,5 ms (O(n)) |

Sept cents et cinq cents fois plus rapide, avec le **même** résultat. Et l'écart grandit avec
les données : à 200 000 éléments, la version quadratique serait environ cent fois plus lente
encore.

Les pièges quadratiques les plus courants, tous invisibles sur les petits jeux de test :

- `x in liste` (ou `liste.contains(x)`) **dans une boucle** : utiliser un `Set` ;
- chercher dans une liste par identifiant à chaque tour : construire d'abord une `Map`
  (`associateBy` en Kotlin) ;
- concaténer des chaînes dans une boucle (dans certains langages) : un `StringBuilder` ou
  `joinToString` ;
- une requête SQL **par élément** d'une liste (le N+1 du parcours Bases de données) ;
- `liste.remove(0)` ou insérer en tête d'un `ArrayList` : une `ArrayDeque`.

Le bon réflexe : se demander comment le code se comporte avec **100 fois plus** de données.

## Mémoïsation et caches applicatifs

La **mémoïsation** garde le résultat d'une fonction **pure** pour ne pas le recalculer. Mesure
réelle avec la fonction de Fibonacci récursive naïve :

| | Durée de `fib(30)` |
|---|---|
| sans mémoïsation | 69 ms (plus d'un million d'appels) |
| avec `@functools.lru_cache` | 18 µs |

Environ **3 800 fois** plus rapide : chaque `fib(k)` n'est calculé qu'une fois. C'est le principe
de la **programmation dynamique**.

Plus généralement, un **cache applicatif** (en mémoire : Caffeine en Java/Kotlin, `lru_cache` en
Python ; partagé : Redis) garde des résultats coûteux : un calcul, une configuration, une réponse
d'API. Les questions à se poser, toujours les mêmes (parcours System design, niveau 2) :

- **la fonction est-elle pure** ? Sinon, quand la valeur devient-elle fausse (invalidation,
  durée de vie) ?
- **quelle taille maximale** ? Un cache sans limite est une fuite mémoire (parcours Débogage,
  niveau 4) ;
- **quel taux de réussite** (*hit rate*) ? Un cache qui sert 2 % des requêtes coûte plus qu'il
  ne rapporte ; on le **mesure** ;
- **la clé est-elle complète** ? Oublier un paramètre (la langue, le client, la devise) dans la clé
  sert la mauvaise réponse à quelqu'un.

## Allocations, boxing et pression sur le GC

Créer des objets coûte : l'allocation elle-même est rapide sur la JVM (quelques nanosecondes),
mais chaque objet occupe de la mémoire, pollue les caches du processeur, et devra être collecté.

Mesure réelle : sommer 10 millions d'entiers en Kotlin.

| Structure | Mémoire (estimation) | Durée d'une somme (après préchauffage) |
|---|---|---|
| `List<Int>` | environ 200 Mo : un objet `Integer` de 16 octets **par** valeur, plus une référence | 23 ms |
| `IntArray` | 40 Mo : 4 octets par valeur, contigus | 4 ms |

Une `List<Int>` stocke des **références** vers des objets `Integer` éparpillés en mémoire (le
**boxing**) ; un `IntArray` stocke directement les valeurs, côte à côte. Résultat : 5 fois plus
de mémoire et environ 6 fois plus lent.

Les sources d'allocations inutiles à surveiller dans le code chaud (exécuté des millions de
fois) :

- le **boxing** (collections génériques de nombres, `Int?`) : `IntArray`, `LongArray`, ou les
  collections spécialisées (fastutil, Eclipse Collections) ;
- les **objets temporaires** dans les boucles (des `Pair`, des listes intermédiaires de chaînes
  d'opérations : une `Sequence`, ou une boucle simple) ;
- le **formatage** de chaînes et la journalisation (construire un message qui ne sera pas
  écrit : niveaux de journal paresseux, `log.debug { "…" }`) ;
- les **expressions régulières** recompilées (parcours Débogage, niveau 4).

Le symptôme d'une pression d'allocation : le ramasse-miettes tourne très souvent (journaux du
GC, métriques de la JVM), et le profil des **allocations** (JFR, async-profiler) désigne les
coupables. Inutile d'optimiser les allocations hors du code chaud : la lisibilité passe avant.

## À retenir

- Complexité : set contre liste (148 ms → 0,21 ms), O(n²) contre O(n) (258 ms → 0,5 ms) ; se demander « et avec 100 fois plus de données ? ».
- Mémoïsation d'une fonction pure : `fib(30)` de 69 ms à 18 µs ; un cache a une taille maximale, une invalidation, un taux de réussite mesuré et une clé complète.
- Allocations : `List<Int>` contre `IntArray`, 5 fois plus de mémoire et environ 6 fois plus lent ; ne chasser les allocations que dans le code chaud, guidé par un profil.
