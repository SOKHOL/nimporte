---
id: donnees.n1.formats-et-flux
title: Les formats de données, ETL et ELT
skills: [formats, etl-elt]
duration_min: 17
---

# Les formats de données, ETL et ELT

L'ingénierie des données consiste à **déplacer** des données (depuis les applications, les
fichiers, les API), à les **transformer** (nettoyer, joindre, agréger) et à les **servir**
(tableaux de bord, analyses, modèles d'apprentissage automatique), de façon fiable et
reproductible. Toutes les mesures de ce parcours sont réelles : un jeu d'**un million de ventes**
(identifiant, jour, ville, code postal, montant) traité avec pyarrow, DuckDB, Polars et
PostgreSQL 16.

## Les formats

Le même million de lignes, écrit dans quatre formats, puis relu pour calculer la somme des
montants :

```
ventes.csv        41.7 Mo   somme des montants lue en     43 ms
ventes.jsonl      98.7 Mo   somme des montants lue en    130 ms
ventes.avro       10.4 Mo   somme des montants lue en   1470 ms
ventes.parquet     5.2 Mo   somme des montants lue en      7 ms
```

| Format | Organisation | Schéma | Usage typique |
|---|---|---|---|
| **CSV** | texte, par lignes | aucun : tout est texte, les types sont **devinés** | échanges simples, exports pour tableur |
| **JSON** (lignes) | texte, par lignes | aucun, mais des structures imbriquées | API, journaux, documents |
| **Avro** | binaire, **par lignes** | schéma joint au fichier | messages Kafka, échanges entre systèmes (niveau 5) |
| **Parquet** | binaire, **par colonnes** | schéma et statistiques joints | analyse, entrepôts, lacs de données |

**Pourquoi Parquet est-il si petit et si rapide ?** Il range les valeurs **colonne par colonne** :
une colonne contient des valeurs du même type, souvent répétitives (6 villes, 4 codes postaux),
qui se compressent très bien (encodage par dictionnaire, puis zstd). Et pour une somme des
montants, il ne lit **que** la colonne `montant` : les quatre autres ne sont jamais décompressées.
Chaque groupe de lignes porte aussi des **statistiques** (min et max par colonne : réel, 1.0 et
500.0 pour `montant`), qui permettent de sauter des blocs entiers lors d'un filtrage.

Avro est lent **ici** parce que l'exercice est analytique : il faut décoder chaque enregistrement
complet, et la lecture en Python crée un dictionnaire par ligne. Avro est fait pour l'inverse :
écrire et lire des **enregistrements entiers**, un par un, avec un schéma qui évolue.

**Le piège du CSV**, réel : les codes postaux, écrits `"01000"` entre guillemets, sont relus
comme des **entiers**.

```
types relus depuis le CSV     : {'code_postal': 'int64', …}   → 01000 devient 1000
types relus depuis Parquet    : {'code_postal': 'string', …}
```

Le code postal de l'Ain a perdu son zéro. Les mêmes accidents frappent les numéros de téléphone,
les identifiants à zéros initiaux, les dates (`03/04/2026` : 3 avril ou 4 mars ?), les décimales
(`3,14` contre `3.14`), l'encodage (UTF-8 ou Windows-1252) et les séparateurs. Règle : un CSV se
lit avec un **schéma explicite**, jamais en laissant l'outil deviner. Et entre systèmes, préférez
un format qui transporte son schéma.

## ETL et ELT

Un **pipeline** de données a trois étapes : **extraire** (lire la source), **transformer**
(nettoyer, convertir, joindre, agréger), **charger** (écrire dans la destination). L'ordre a
changé avec le temps :

| | ETL (*Extract, Transform, Load*) | ELT (*Extract, Load, Transform*) |
|---|---|---|
| où l'on transforme | dans un outil intermédiaire, **avant** de charger | **dans** l'entrepôt, en SQL, après avoir chargé les données brutes |
| époque | entrepôts coûteux et peu puissants | entrepôts en nuage puissants (BigQuery, Snowflake, Redshift) |
| avantages | on ne charge que des données propres | les données brutes restent disponibles : on peut corriger une transformation et **tout recalculer** |
| outils | Informatica, Talend, scripts maison | Fivetran ou Airbyte pour charger, **dbt** pour transformer en SQL versionné |

L'ELT a gagné pour l'analyse, avec une organisation en couches souvent appelée **médaillon** :
**bronze** (données brutes, telles que reçues), **argent** (nettoyées, typées, dédoublonnées),
**or** (agrégées, prêtes pour les tableaux de bord). Garder le bronze, c'est garder la possibilité
de rejouer l'histoire quand une règle change ou qu'un bug est découvert.

Les qualités d'un bon pipeline, qui reviendront dans tout le parcours : **idempotent** (le
relancer donne le même résultat), **incrémental** (ne traiter que le nouveau), **observable**
(on sait ce qui a tourné, quand, combien de lignes), et **testé** (sur les données, pas
seulement sur le code).

## À retenir

- Mesuré sur un million de lignes : Parquet 5,2 Mo et 7 ms, CSV 41,7 Mo, JSON 98,7 Mo ; les colonnes se compressent et seules les colonnes utiles sont lues.
- Le CSV devine les types : « 01000 » est devenu 1000 ; toujours lire avec un schéma explicite.
- ETL transforme avant de charger, ELT après, dans l'entrepôt (dbt) ; couches bronze, argent, or ; pipelines idempotents et incrémentaux.
