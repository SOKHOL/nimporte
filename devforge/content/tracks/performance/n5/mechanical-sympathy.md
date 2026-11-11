---
id: performance.n5.mechanical-sympathy
title: Caches du processeur, prédiction de branchement et latence de queue
skills: [caches-cpu, branchements, latence-queue]
duration_min: 19
---

# Caches du processeur, prédiction de branchement et latence de queue

« Mechanical sympathy » (l'expression vient du pilote Jackie Stewart, reprise par Martin
Thompson) : comprendre la machine pour écrire du code qui va **dans son sens**. Ce niveau ne
concerne que le code vraiment chaud ; mais là, les écarts sont spectaculaires.

## Les caches du processeur

Le processeur ne lit pas la mémoire octet par octet : il charge des **lignes de cache** de 64
octets, et garde les plus récentes dans des caches très rapides (L1 : environ 1 ns ; L2 : 4 ns ;
L3 : 10 à 20 ns ; mémoire vive : environ 100 ns). Il **précharge** aussi les lignes suivantes
quand il détecte un parcours régulier.

Mesure réelle en C : additionner les 16 millions d'entiers d'une matrice 4 096 × 4 096,
stockée ligne par ligne, de deux façons.

| Parcours | Durée |
|---|---|
| ligne par ligne (les éléments se suivent en mémoire) | 9 à 13 ms |
| colonne par colonne (un saut de 16 Ko à chaque élément) | 159 ms |

**12 à 18 fois** plus lent pour exactement les mêmes calculs. Ligne par ligne, chaque ligne de
cache chargée sert 16 entiers et le préchargement anticipe ; colonne par colonne, chaque accès
charge une nouvelle ligne de cache pour n'en utiliser que 4 octets.

Les conséquences pratiques :

- **parcourir les données dans l'ordre où elles sont stockées** ;
- préférer des **tableaux contigus** aux structures pleines de pointeurs (listes chaînées,
  `List<Integer>` : niveau 3, 23 ms contre 4 ms) ;
- **structure de tableaux** plutôt que **tableau de structures** quand on ne lit qu'un champ :
  `IntArray` des prix plutôt qu'une liste de produits dont on ne lit que le prix (c'est ce
  qu'exploitent les bases de données **en colonnes** pour l'analytique) ;
- éviter le **faux partage** entre threads (parcours Mémoire, niveau 5).

## La prédiction de branchement

Un processeur moderne exécute les instructions **en pipeline**, plusieurs à la fois et à
l'avance. Devant un `if`, il **devine** la branche et continue ; s'il s'est trompé, il jette le
travail spéculatif (une quinzaine de cycles perdus). Quand les données sont prévisibles, il
devine presque toujours juste.

Mesure réelle en C : sommer les valeurs ≥ 128 d'un tableau de 32 768 octets aléatoires, 2 000
fois, sur les données dans le désordre puis triées.

| Compilation | Désordre | Trié | Écart |
|---|---|---|---|
| `-O0` (un vrai saut conditionnel) | 325 ms | 76 à 88 ms | **×3,7 à ×4,3** |
| `-O1`, `-O3` | 33 ms | 35 ms | aucun |

Dans le désordre, la branche est imprévisible (une chance sur deux) ; triée, elle est fausse
pendant toute la première moitié puis vraie ensuite, et le prédicteur ne se trompe presque
jamais. Avec l'optimisation, le compilateur a remplacé le saut par une instruction de
**déplacement conditionnel** (réellement : `cmovg` dans le code généré), qui calcule sans
deviner : plus de différence du tout.

La leçon va au-delà de l'astuce : **les mesures dépendent des options de compilation et du
compilateur**. Un microbenchmark en `-O0`, ou une conclusion tirée sur une version de la JVM,
peut être fausse ailleurs. Dans le code chaud, on préfère des données **prévisibles** (trier
d'abord, regrouper les éléments par type), et des calculs **sans branche** quand c'est simple.

## Réduire la latence de queue

À grande échelle, la latence d'une requête est celle de **son composant le plus lent**. Si une
page interroge 100 serveurs, une lenteur à 1 % sur chacun touche la majorité des pages (63 %,
calcul du parcours System design). Jeff Dean et Luiz Barroso (*The Tail at Scale*, 2013) ont
décrit les sources de ces lenteurs rares et les remèdes.

Les **sources** : pauses du ramasse-miettes, compactage de la base (LSM, parcours Bases de
données), tâches de fond (sauvegardes, journaux), une machine voisine bruyante, une file
d'attente momentanée (parcours SRE, niveau 4), un démarrage à froid, une connexion à rétablir.

Les **remèdes** :

- **les requêtes de couverture** (*hedged requests*) : si la réponse n'est pas arrivée après le
  p95 habituel, envoyer la même requête (idempotente) à une autre réplique, et prendre la
  première réponse ; coût : quelques pour cent de requêtes en plus, gain énorme sur le p99,9 ;
- **les délais d'expiration serrés** et la dégradation (afficher la page sans le module lent) ;
- **réduire les pauses** : GC à faible latence (ZGC, Shenandoah), moins d'allocations, tas
  dimensionné ;
- **isoler** les tâches de fond (autres machines, autres heures, priorités) ;
- **éviter les files** : marge de capacité, délestage, files courtes.

Et **mesurer correctement** : un outil de test de charge en modèle fermé sous-estime la latence
de queue (omission coordonnée, parcours SRE), et un tableau de bord qui affiche la moyenne ne la
verra jamais.

## À retenir

- Caches : lignes de 64 octets, L1 ≈ 1 ns contre mémoire ≈ 100 ns ; parcours par colonnes 12 à 18 fois plus lent mesuré ; données contiguës, structure de tableaux.
- Branchements : des données imprévisibles coûtent ×3,7 à ×4,3 (mesuré en -O0) ; le compilateur peut supprimer le saut (`cmovg`) : les mesures dépendent de la compilation.
- Latence de queue : composants lents en cascade ; requêtes de couverture, délais serrés, GC à faible latence, isolation, marges ; la mesurer correctement.
