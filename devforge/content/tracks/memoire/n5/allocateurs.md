---
id: memoire.n5.allocateurs
title: Allocateurs et fragmentation
skills: [allocateurs, fragmentation]
duration_min: 8
---

# Allocateurs et fragmentation

Derrière `malloc`, `new` ou la création d'un objet Kotlin, il y a un **allocateur** : le
code qui choisit où placer chaque bloc de mémoire. Son travail est plus difficile qu'il
n'y paraît.

## La fragmentation

- **Fragmentation externe** : la mémoire libre est éparpillée en petits trous. Il peut
  rester 100 Mo libres au total, mais aucun bloc contigu de 10 Mo : l'allocation échoue.
- **Fragmentation interne** : l'allocateur arrondit les tailles (par exemple à 16 ou
  32 octets) ; l'espace perdu dans chaque bloc s'additionne.

Les ramasse-miettes qui **compactent** (G1, ZGC) éliminent la fragmentation externe en
déplaçant les objets. En C, on ne peut pas déplacer un bloc (des pointeurs y mènent) :
la fragmentation doit être évitée dès la conception.

## Les familles d'allocateurs

| Allocateur | Principe | Idéal pour |
|---|---|---|
| Généraliste (`malloc`) | gère des blocs de toutes tailles | le cas par défaut |
| **Arène** (*bump allocator*) | avance un pointeur à chaque allocation ; on libère toute l'arène d'un coup | tout ce qui meurt ensemble : une requête, une image de jeu, une passe de compilateur |
| **Pool** | des blocs de taille fixe, recyclés | beaucoup d'objets identiques (connexions, particules) |
| **Slab** | un pool par taille d'objet courante | les noyaux de systèmes d'exploitation |

## Et sur la JVM ?

La JVM utilise une arène pour chaque thread : la **TLAB** (*Thread-Local Allocation
Buffer*), une portion de la jeune génération réservée à un thread. Allouer un objet, c'est
avancer un pointeur dans sa TLAB : quelques instructions, sans verrou. C'est pour ça que
créer un objet sur la JVM est **très** rapide… et que le vrai coût d'une allocation est
payé plus tard, par le ramasse-miettes.

Conséquence pratique : réduire les allocations d'objets éphémères dans une boucle très
chaude diminue la pression sur le GC. Mais ne recycle pas tes objets à la main par
principe : la plupart du temps, le ramasse-miettes générationnel fait mieux, et un pool
maison ajoute bugs et contention.

## À retenir

- Fragmentation externe (trous éparpillés) et interne (arrondis).
- Arène : allouer vite, tout libérer d'un coup. Pool : recycler des objets identiques.
- La JVM alloue dans une arène par thread (TLAB) : rapide, le coût vient ensuite du GC.
