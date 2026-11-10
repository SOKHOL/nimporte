---
id: memoire.n5.memoire-et-processeur
title: La mémoire vue par le processeur
skills: [caches-cpu, faux-partage, disposition-objets]
duration_min: 12
---

# La mémoire vue par le processeur

Pour le processeur, la mémoire vive est **lente** : environ 100 ns par accès, le temps
d'exécuter des centaines d'instructions. Il compense avec des caches, et ton code est
rapide ou lent selon qu'il les utilise bien ou mal. C'est ce que les experts appellent la
*mechanical sympathy* : écrire du code qui va dans le sens du matériel.

## La hiérarchie des caches

| Niveau | Taille typique | Latence typique |
|---|---|---|
| Registres | quelques centaines d'octets | immédiat |
| Cache L1 | ~32–64 Ko par cœur | ~1 ns |
| Cache L2 | ~256 Ko – 2 Mo par cœur | ~4 ns |
| Cache L3 | quelques dizaines de Mo, partagé | ~10–20 ns |
| Mémoire vive | des Go | ~100 ns |

Le processeur ne charge jamais un octet seul : il charge une **ligne de cache** de
**64 octets**. Et il **anticipe** : s'il voit que tu lis des adresses qui se suivent, il
charge les lignes suivantes à l'avance (*prefetching*).

## La localité, mesurée

```kotlin
const val N = 4096

fun parLignes(m: Array<IntArray>): Long {
    var total = 0L
    for (i in 0 until N) for (j in 0 until N) total += m[i][j]
    return total
}

fun parColonnes(m: Array<IntArray>): Long {
    var total = 0L
    for (j in 0 until N) for (i in 0 until N) total += m[i][j]
    return total
}
```

Même calcul, mêmes données. Mesuré pour ce cours (après préchauffage) : **8 ms** par
lignes, **178 ms** par colonnes, plus de 20 fois plus lent. Par lignes, on lit des
entiers qui se suivent : chaque ligne de cache chargée sert 16 fois, et le prefetching
fonctionne. Par colonnes, chaque accès saute 16 Ko plus loin : presque chaque lecture
va chercher en mémoire vive.

## Le faux partage

Deux cœurs qui écrivent dans **la même ligne de cache** se la disputent, même s'ils ne
touchent pas la même variable : à chaque écriture, la ligne doit passer d'un cœur à
l'autre. C'est le **faux partage** (*false sharing*).

```kotlin
val compteurs = AtomicLongArray(64)
// thread 1 incrémente compteurs[0], thread 2 incrémente compteurs[ecart]
```

Mesuré pour ce cours, 50 millions d'incréments par thread sur 4 cœurs :
**3,1 s** quand les deux compteurs sont voisins (même ligne de cache), **0,97 s** quand
ils sont séparés de 128 octets. Trois fois plus lent, pour deux variables qui n'ont
rien à voir entre elles.

Parades : espacer les données écrites par des threads différents (rembourrage), ou
l'annotation `@Contended` du JDK. Mieux encore : que chaque thread accumule dans une
variable locale, et ne combine qu'à la fin. C'est ce que fait `LongAdder`.

## La disposition des données

Sur la JVM, une `List<Point>` est un tableau de **références** vers des objets
dispersés sur le tas. La parcourir, c'est sauter d'adresse en adresse (*pointer chasing*),
souvent hors du cache. Deux `DoubleArray` (`xs` et `ys`), c'est de la mémoire contiguë,
parfaitement adaptée au cache. Les moteurs de jeu et les bases de données analytiques
organisent leurs données ainsi : en « structure de tableaux » plutôt qu'en « tableau de
structures ».

## À retenir

- La mémoire vive est lente ; les caches travaillent par lignes de 64 octets.
- Parcourir les données dans l'ordre où elles sont rangées peut être 20 fois plus rapide.
- Faux partage : deux threads qui écrivent sur la même ligne de cache se ralentissent.
- Des données contiguës battent des objets dispersés reliés par des références.
- Les chiffres varient selon les machines ; l'ordre de grandeur, lui, reste.
