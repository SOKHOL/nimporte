---
id: performance.n1.mesurer-avant-d-optimiser
title: Mesurer, percentiles et budgets de performance
skills: [mesurer, percentiles, objectifs-performance]
duration_min: 15
---

# Mesurer, percentiles et budgets de performance

## Mesurer avant d'optimiser

> « L'optimisation prématurée est la source de tous les maux (ou presque). » — Donald Knuth

La citation complète poursuit : « …mais il ne faut pas laisser passer nos chances dans ces 3 %
critiques ». Le message n'est pas « ne jamais optimiser », mais **optimiser là où ça compte**,
et ça, on ne le devine pas : on le **mesure**. Les développeurs se trompent systématiquement
sur l'endroit où leur programme passe son temps.

La démarche :

1. **Définir l'objectif** : quoi, pour qui, combien (« la page produit s'affiche en moins de
   1 s au p75 sur mobile »).
2. **Mesurer l'existant**, dans des conditions réalistes (données, volume, réseau, appareil).
3. **Trouver le goulot** avec un profileur (niveau 2), pas à l'intuition.
4. **Corriger une seule chose**, puis **remesurer**. Garder la correction seulement si le gain
   est réel et significatif.
5. **Empêcher la régression** : un test de performance ou une alerte.

Le piège classique : passer une journée à rendre deux fois plus rapide une fonction qui
représente 2 % du temps total (gain : 1 %), alors qu'une requête SQL sans index en occupe 80 %.

## Moyennes et percentiles

Distribution simulée de 10 000 temps de réponse : 99 % des requêtes autour de 40 ms, 1 % très
lentes (entre 0,8 et 2 s). Valeurs réelles calculées :

| Indicateur | Valeur |
|---|---|
| moyenne | 54 ms |
| médiane (p50) | 40 ms |
| p99 | 801 ms |
| p99,9 | 1 851 ms |
| maximum | 1 996 ms |

La **moyenne** (54 ms) ne correspond à **aucune** requête réelle : elle mélange deux
populations. Elle ne voit pas que **1 requête sur 100** prend presque une seconde, alors que
c'est exactement ce que les utilisateurs remarquent. D'où l'usage des **percentiles** : le
**p50** (expérience typique), le **p95** ou **p99** (les mauvaises expériences fréquentes), le
**p99,9** pour les services très sollicités.

Et le p99 compte plus qu'il n'en a l'air : un utilisateur qui charge une page faisant 50
requêtes, ou qui fait 20 actions dans sa session, croise le p99 très souvent (parcours System
design, niveau 1).

Deux pièges de calcul :

- on **ne moyenne pas des percentiles** (le p99 de deux serveurs n'est pas la moyenne de leurs
  p99) : on agrège les données brutes ou des histogrammes ;
- un percentile sur **trop peu de points** ne veut rien dire (un p99 sur 50 requêtes, c'est le
  maximum).

## Objectifs et budgets de performance

Quelques repères sur la perception humaine (modèle RAIL de Google) :

| Délai | Perception |
|---|---|
| moins de 100 ms | instantané (réponse à un clic, une frappe) |
| moins de 1 s | fluide, l'attention est conservée |
| plus de 10 s | l'utilisateur décroche ou part |

Pour le web, les **Core Web Vitals** mesurent l'expérience réelle des visiteurs (au 75e
percentile) : **LCP** (affichage du plus gros élément, objectif moins de 2,5 s), **INP**
(réactivité aux interactions, moins de 200 ms), **CLS** (stabilité visuelle, éléments qui ne
sautent pas). Sur mobile Android, on surveille le démarrage à froid de l'application et les
images manquées (*jank*, plus de 16 ms par image à 60 Hz).

Un **budget de performance** fixe des limites mesurables, vérifiées en continu : poids maximal
du JavaScript d'une page, nombre de requêtes, p99 d'une API, taille de l'APK, temps de
démarrage. Le budget est vérifié **dans la CI** : une régression de performance se traite
comme un test qui échoue, au lieu d'être découverte six mois plus tard, quand plus personne ne
sait quel changement l'a causée.

## À retenir

- Optimiser là où ça compte : objectif chiffré, mesure réaliste, profileur, une correction à la fois, remesurer.
- Les percentiles plutôt que la moyenne : moyenne 54 ms, p99 801 ms dans la même distribution ; ne pas moyenner des percentiles.
- Repères : 100 ms instantané, 1 s fluide ; Core Web Vitals au p75 ; des budgets vérifiés dans la CI.
