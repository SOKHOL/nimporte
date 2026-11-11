---
id: system-design.n1.le-vocabulaire-de-l-echelle
title: Latence, débit, disponibilité, scalabilité et estimations
skills: [latence-debit, scalabilite, estimations]
duration_min: 18
---

# Latence, débit, disponibilité, scalabilité et estimations

## Latence et débit

- La **latence** est le temps d'une opération (une requête met 40 ms).
- Le **débit** est le nombre d'opérations par unité de temps (2 000 requêtes par seconde).

Les deux sont liés mais distincts : une autoroute à dix voies a un grand débit, sans que
chaque voiture aille plus vite. La **loi de Little** les relie : `requêtes en cours =
débit × latence`. À 2 000 requêtes/s et 50 ms chacune, il y a en moyenne 100 requêtes en
cours, donc il faut au moins 100 connexions ou threads disponibles.

### Ordres de grandeur, mesurés

Mesures réelles sur la machine de test (même machine pour tout, donc sans vrai réseau) :

| Opération | Latence médiane (p50) |
|---|---|
| lecture dans un dictionnaire en mémoire (Python, appel compris) | ~0,1 µs |
| `GET` Redis (même machine, aller-retour) | ~34 µs |
| `SELECT … WHERE id = ?` PostgreSQL (même machine) | ~54 µs |
| écrire 4 Ko puis `fsync` sur le disque | ~226 µs |

Et les ordres de grandeur classiques à connaître par cœur :

| Opération | Ordre de grandeur |
|---|---|
| accès mémoire vive | 100 ns |
| aller-retour dans le même datacenter | 0,5 ms |
| lire 1 Mo en séquence sur un SSD | 1 ms |
| aller-retour Paris ↔ New York | 70 à 80 ms |
| aller-retour Paris ↔ Sydney | 250 à 300 ms |

À retenir : la mémoire est environ **mille fois** plus rapide que le réseau local, qui est
plus de **cent fois** plus rapide qu'un aller-retour entre continents. On ne bat pas la
vitesse de la lumière : un service à Paris ne répondra jamais en 10 ms à un utilisateur
australien. C'est pour ça qu'existent les caches et les CDN.

### Les percentiles

Une **moyenne** cache les problèmes. Si 99 requêtes prennent 10 ms et une prend 5 s, la
moyenne est de 60 ms et ne dit rien à personne. On mesure des **percentiles** : le p50 (la
médiane), le p99 (99 % des requêtes sont plus rapides) et le p99,9.

Le p99 compte plus qu'on ne croit, à cause de l'**amplification de la latence de queue** :
une page qui appelle 100 services en parallèle doit attendre le plus lent. Calcul :

| Appels en parallèle | Probabilité qu'au moins un dépasse son p99 |
|---|---|
| 1 | 1,0 % |
| 10 | 9,6 % |
| 100 | 63,4 % |

Avec 100 appels, le p99 de chaque service devient la latence **habituelle** de la page
(`1 − 0,99¹⁰⁰`). D'où les techniques des grands systèmes : requêtes de couverture (*hedged
requests*, on renvoie la requête à une autre réplique si la première tarde), délais
d'expiration serrés, et chasse au p99,9.

## La disponibilité

La disponibilité est la proportion du temps où le service fonctionne. Chaque « 9 » divise
la panne autorisée par dix :

| Disponibilité | Panne autorisée par an | par mois |
|---|---|---|
| 99 % | 87,7 h | 7,3 h |
| 99,9 % | 8,8 h | 43,8 min |
| 99,99 % | 53 min | 4,4 min |
| 99,999 % | 5 min | 26 s |

Deux règles de calcul :

- **En série** (A appelle B qui appelle C), les disponibilités se **multiplient** : trois
  services à 99,9 % donnent `0,999³ = 99,70 %`. Plus il y a de dépendances synchrones, plus
  la disponibilité baisse.
- **En parallèle** (deux répliques, il suffit que l'une marche), les indisponibilités se
  multiplient : deux répliques à 99 % donnent `1 − 0,01² = 99,99 %`. La redondance est ce
  qui crée les « 9 ».

## Scalabilité verticale et horizontale

| | Verticale (*scale up*) | Horizontale (*scale out*) |
|---|---|---|
| Principe | une machine plus grosse | plus de machines |
| Avantage | aucun changement de code | pas de limite théorique, et de la redondance |
| Limite | la plus grosse machine du catalogue, et un point unique de panne | le code doit être **sans état** et les données réparties |

Une machine moderne va très loin (des centaines de cœurs, des téraoctets de RAM) : beaucoup
de systèmes n'ont jamais besoin de plus qu'une base bien dimensionnée et quelques serveurs
d'application. La mise à l'échelle horizontale des serveurs d'application sans état est
facile ; celle des **données** est le vrai problème (niveaux 2 et 3).

La **loi d'Amdahl** rappelle la limite : si 10 % du travail ne peut pas être parallélisé
(un verrou global, une base unique), le gain maximal est de ×10, quel que soit le nombre de
machines.

## Les estimations à la louche

En entretien comme dans la vraie vie, on estime avant de concevoir. Raccourcis :

- 1 jour ≈ 86 400 s ≈ **10⁵ s** ; 1 mois ≈ 2,6 × 10⁶ s.
- **1 million par jour ≈ 12 par seconde**.
- Le pic vaut souvent **2 à 5 fois** la moyenne.
- Puissances de 2 : 2¹⁰ ≈ mille (Ko), 2²⁰ ≈ un million (Mo), 2³⁰ ≈ un milliard (Go).

Exemple : 50 millions d'utilisateurs actifs par jour, chacun publie 2 messages de 300
octets et en lit 100.

- Écritures : 10⁸ / 10⁵ ≈ **1 000 messages/s** (pic ≈ 3 000/s).
- Lectures : 50 fois plus, ≈ **50 000/s** (pic ≈ 150 000/s) : un système de **lecture**,
  qui appelle des caches.
- Stockage : 10⁸ × 300 octets = 30 Go par jour, environ **11 To par an**, sans les médias
  ni les répliques.

Le but n'est pas la précision, mais l'**ordre de grandeur** : 1 000 écritures par seconde
tiennent sur une base unique ; 1 000 000 non.

## À retenir

- Latence ≠ débit ; loi de Little : en cours = débit × latence.
- Ordres de grandeur : mémoire ≪ réseau local ≪ intercontinental ; mesurer en percentiles, et craindre le p99 des appels en parallèle.
- Disponibilité : en série on multiplie (ça baisse), en parallèle on gagne des 9.
- Verticale d'abord si possible ; horizontale pour les services sans état ; les données sont le vrai défi.
- Estimer avant de concevoir : 1 million/jour ≈ 12/s, pic = 2 à 5 × la moyenne.
