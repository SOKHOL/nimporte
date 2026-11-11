---
id: ordinateur.n5.au-plus-pres-du-silicium
title: Pipeline, exécution spéculative, SIMD, multicœur et GPU
skills: [pipeline-speculation, simd, multicoeur-gpu]
duration_min: 18
---

# Pipeline, exécution spéculative, SIMD, multicœur et GPU

La fréquence des processeurs stagne autour de 3 à 5 GHz depuis le milieu des années 2000 (la
chaleur dégagée limite). Les gains sont venus d'ailleurs : faire **plus de travail par cycle**
et **plus de cœurs**.

## Le pipeline et l'exécution spéculative

Un processeur moderne découpe chaque instruction en étapes (chercher, décoder, exécuter, accéder
à la mémoire, écrire le résultat) et traite plusieurs instructions **en même temps**, chacune à
une étape différente, comme une chaîne de montage : c'est le **pipeline**. Il va plus loin :

- il est **superscalaire** : plusieurs unités de calcul, plusieurs instructions par cycle ;
- il exécute **dans le désordre** (*out-of-order*) : il réordonne les instructions pour ne pas
  attendre une donnée lente de la mémoire ;
- il fait de l'**exécution spéculative** : devant un branchement dont il ne connaît pas encore
  l'issue, il **devine** (prédiction de branchement) et exécute à l'avance la branche probable ;
  s'il s'est trompé, il annule le travail (parcours Performance, niveau 5 : une branche
  imprévisible rendait une boucle 3,7 à 4,3 fois plus lente).

L'exécution spéculative a une face sombre, révélée en 2018 par **Spectre** et **Meltdown** : le
travail annulé laisse quand même des **traces** dans les caches. En mesurant le temps d'accès à
la mémoire, un programme malveillant pouvait en déduire des données qu'il n'avait pas le droit
de lire (la mémoire du noyau, ou d'un autre site dans le navigateur). Les correctifs (dans les
processeurs, les noyaux, les compilateurs et les navigateurs) ont coûté de quelques pour cent à
bien plus de performance selon les charges ; et ce type d'attaque par **canal auxiliaire** reste
un domaine de recherche actif.

## SIMD : le calcul vectoriel

Les instructions **SIMD** (*Single Instruction, Multiple Data*) appliquent une même opération à
plusieurs données à la fois : SSE (128 bits), **AVX2** (256 bits, soit 8 nombres flottants),
AVX-512, NEON et SVE sur ARM. Mesure réelle en C : calculer `c[i] = a[i] * b[i] + c[i]` sur
4 096 flottants, 200 000 fois.

| Compilation | Durée |
|---|---|
| `-O3 -fno-tree-vectorize` (une valeur à la fois) | 324 ms |
| `-O3 -march=native` (vectorisation AVX) | 47 ms |

**Près de 7 fois plus rapide**, sans changer une ligne du programme. Le code généré contient
réellement des instructions `vmulps` sur des registres `ymm` (256 bits : 8 flottants par
instruction) ; la version scalaire utilise des instructions à une seule valeur.

Pour que le compilateur **vectorise automatiquement**, il faut des boucles simples sur des
tableaux contigus, sans dépendance d'un tour sur le précédent ni appels de fonctions opaques
(et `-march=native`, ou une cible précise, pour qu'il ait le droit d'utiliser AVX). Les
bibliothèques de calcul (NumPy, BLAS, les moteurs de bases analytiques, l'API Vector de Java)
s'appuient massivement sur le SIMD : c'est l'une des raisons pour lesquelles une opération
NumPy sur un tableau entier est des dizaines de fois plus rapide qu'une boucle Python.

## Multicœur, NUMA et GPU

**Plusieurs cœurs.** La machine de test a 4 cœurs ; un serveur en a des dizaines à plus d'une
centaine. Chaque cœur a ses caches L1 et L2 ; ils se partagent le L3 et la mémoire. Les caches
sont maintenus **cohérents** par un protocole matériel : quand un cœur écrit une ligne, les copies
des autres sont invalidées. D'où le coût des données partagées en écriture entre threads (le
faux partage, parcours Mémoire, niveau 5), et l'intérêt de travailler sur des données propres à
chaque thread puis de combiner.

**NUMA** (*Non-Uniform Memory Access*). Sur les gros serveurs à plusieurs processeurs, chaque
processeur a **sa** mémoire : y accéder est rapide, accéder à celle de l'autre processeur passe
par une liaison et coûte plus cher. La machine de test n'a qu'un nœud NUMA (`NUMA node(s): 1`) ;
sur un serveur à deux nœuds, une base de données ou une JVM mal placées peuvent perdre une partie
importante de leurs performances, d'où les options d'affinité (`numactl`, `-XX:+UseNUMA`).

**Les GPU.** Un processeur graphique contient des **milliers** de petits cœurs simples, conçus
pour appliquer le même calcul à d'énormes quantités de données en parallèle : les pixels d'une
image, et surtout les **multiplications de matrices** des réseaux de neurones. Un CPU est
optimisé pour la **latence** d'un fil d'exécution complexe ; un GPU pour le **débit** de
calculs massivement parallèles et réguliers. C'est pourquoi l'apprentissage et l'exécution des
modèles d'IA reposent sur les GPU (et sur des puces spécialisées comme les TPU). Le coût caché :
**transférer** les données entre la mémoire principale et celle du GPU, qui peut dominer pour de
petits calculs.

## À retenir

- Pipeline, exécution superscalaire, dans le désordre et spéculative ; Spectre et Meltdown ont montré que la spéculation laisse des traces exploitables.
- SIMD : une instruction, plusieurs données ; vectorisation automatique mesurée à près de ×7 (324 → 47 ms, `vmulps` sur `ymm`) ; boucles simples sur tableaux contigus.
- Multicœur : caches cohérents, coût des écritures partagées ; NUMA : la mémoire « locale » est plus rapide ; GPU : des milliers de cœurs pour le débit, au prix des transferts.
