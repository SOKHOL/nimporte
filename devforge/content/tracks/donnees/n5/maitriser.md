---
id: donnees.n5.maitriser
title: Lakehouse, évolution des schémas, gouvernance et coûts
skills: [lakehouse, evolution-schemas, gouvernance, couts]
duration_min: 23
---

# Lakehouse, évolution des schémas, gouvernance et coûts

## Le lakehouse

Un **lac de données** (*data lake*) range des fichiers (Parquet, JSON…) dans un stockage objet
bon marché (S3, GCS, ADLS) : souple et peu coûteux, mais sans transactions, sans schéma
garanti, sans mise à jour ni suppression simples. Un **entrepôt** offre tout cela, mais enferme
les données dans un système propriétaire. Le **lakehouse** ajoute à des fichiers Parquet ouverts
une couche de **format de table** (Delta Lake, Apache Iceberg, Apache Hudi) : un **journal de
transactions** qui décrit quels fichiers forment la table à chaque version.

Expérience réelle avec Delta Lake (bibliothèque `deltalake`) : une écriture de 3 ventes, un ajout,
puis une suppression des ventes de Lyon :

```
version courante : 2 ; lignes : 3
version 0 (voyage dans le temps) : ['Paris', 'Lyon', 'Lille']
  historique : 0 WRITE
  historique : 1 WRITE
  historique : 2 DELETE
fichiers : 3 Parquet, ['00000000000000000000.json', '00000000000000000001.json', '00000000000000000002.json']
  journal v2 : add part-00000-08807f0f-…
  journal v2 : remove part-00000-4d80bfec-…
```

Le mécanisme : un fichier Parquet n'est **jamais modifié**. Supprimer Lyon a écrit un **nouveau**
fichier sans Lyon, et la version 2 du journal dit « **ajouter** celui-ci, **retirer** l'ancien ».
Une version est l'ensemble des fichiers ajoutés et non retirés. D'où :

- des **transactions** : une version apparaît d'un coup (l'écriture du fichier JSON de journal est
  atomique), un lecteur ne voit jamais une écriture à moitié faite ;
- le **voyage dans le temps** : relire la version 0 (audit, reproduire un ancien calcul,
  annuler une erreur) ;
- le **contrôle du schéma**, réel : écrire `"cinq"` dans une colonne entière est refusé
  (`Cast error: Cannot cast string 'cinq' to value of Int64 type`) ;
- un **nettoyage** explicite : après `VACUUM`, les fichiers retirés sont effacés, et la version 0
  n'est plus lisible (réel : `FileNotFoundError`). La durée de rétention fixe jusqu'où l'on peut
  remonter, et le coût de stockage.

Et plusieurs moteurs (Spark, Trino, DuckDB, Flink, Snowflake) lisent les **mêmes** tables : on ne
copie plus les données d'un système à l'autre.

## L'évolution des schémas

Les schémas changent : nouveaux champs, renommages, types élargis. Sans discipline, chaque
changement casse un consommateur. Avro formalise les règles : on lit avec le **schéma du lecteur**
des données écrites avec le **schéma de l'écrivain**, et la bibliothèque les réconcilie. Réel :
des données écrites en version 1 (`id: int, nom: string`), relues avec cinq versions :

```
v2 : ajoute pays avec défaut      → [{'id': 1, 'nom': 'Ada', 'pays': 'FR'}]
v3 : ajoute pays sans défaut      → SchemaResolutionError: No default value for field pays in Client
v4 : id passe de int à long       → [{'id': 1, 'nom': 'Ada'}]
v5 : renomme nom en nom_complet   → SchemaResolutionError: No default value for field nom_complet in Client
v6 : nom_complet avec alias nom   → [{'id': 1, 'nom_complet': 'Ada'}]
```

Les règles qui en découlent :

| Changement | Compatible ? |
|---|---|
| ajouter un champ **avec valeur par défaut** | oui |
| ajouter un champ **obligatoire** | non : les anciennes données n'en ont pas |
| supprimer un champ qui avait un défaut | oui pour les nouveaux lecteurs |
| élargir un type (`int` → `long`, `float` → `double`) | oui |
| renommer un champ | non, sauf avec un **alias** |
| changer le sens d'un champ (centimes → euros) | **jamais** détecté par un outil : créer un nouveau champ |

Un **registre de schémas** (Confluent Schema Registry, Apicurio) stocke les versions de chaque
schéma et **refuse** à l'enregistrement un schéma incompatible, selon le mode choisi :
**backward** (les nouveaux lecteurs lisent les anciennes données, le plus courant), **forward**
(les anciens lecteurs lisent les nouvelles données) ou **full** (les deux). Le contrôle se fait
avant le déploiement, plutôt qu'en production à 3 heures du matin. Les mêmes idées s'appliquent
à Protobuf (numéros de champs à ne jamais réutiliser) et aux formats de table du lakehouse
(Iceberg suit les colonnes par identifiant, ce qui rend les renommages sûrs).

## La gouvernance

Plus il y a de données et d'équipes, plus il faut répondre à des questions simples :

- **D'où vient ce chiffre ?** Le **lignage** (*lineage*) trace chaque table jusqu'à ses sources
  et ses transformations. Quand un chiffre est faux, il dit où chercher ; quand une source
  change, il dit qui sera touché. dbt le produit automatiquement, OpenLineage le standardise.
- **Que signifie cette colonne, et qui en est responsable ?** Un **catalogue** (DataHub,
  OpenMetadata, Unity Catalog) documente les tables, leurs propriétaires et leur fraîcheur.
- **Qui a le droit de voir quoi ?** Les **données personnelles** (RGPD) exigent : minimisation
  (ne collecter que le nécessaire), contrôle d'accès par colonne et par ligne, masquage ou
  **pseudonymisation** (remplacer l'e-mail par un hachage avec sel), durées de conservation, et
  le **droit à l'effacement**. Ce dernier est difficile dans un lac où les fichiers sont
  immuables : il faut réécrire les fichiers concernés puis faire un `VACUUM` (vu plus haut),
  sinon la donnée « supprimée » reste lisible dans les anciennes versions.

L'approche **data mesh** confie chaque domaine de données à l'équipe métier qui le connaît,
traité comme un **produit** (propriétaire, documentation, contrat, qualité mesurée), avec une
plateforme commune et des règles fédérées. C'est d'abord une organisation, pas un outil.

## Les coûts

Dans le nuage, une requête mal écrite **coûte** directement : les entrepôts facturent souvent au
volume analysé (BigQuery à la demande : quelques dollars par téraoctet lu) ou au temps de calcul
(Snowflake). Les leviers, mesurés sur le million de ventes **partitionné par mois** :

```
tout le jeu : 8.95 Mo, 10 partitions ; partition de mars : 0.93 Mo
colonnes de mars (octets compressés) : {'id': 274658, 'jour': 70122, 'ville': 43739,
                                        'code_postal': 29859, 'montant': 492874}
select sum(montant) where mois = '2026-03' lit ≈ 0.49 Mo, soit 5.5 % du jeu
```

Plan réel de DuckDB : `File Filters: mois = '2026-03'`, `Scanning Files: 1/10`,
`Projections: montant`. Le **partitionnement** élimine 9 fichiers sur 10, le **stockage en
colonnes** 4 colonnes sur 5 : on lit 5,5 % des données. Les mêmes requêtes sans filtre de
partition, ou avec un `select *`, lisent tout et coûtent 18 fois plus.

Autre mesure réelle : le jeu partitionné pèse **8,95 Mo** contre **5,2 Mo** en un seul fichier.
Des fichiers plus petits se compressent moins bien et multiplient les métadonnées : trop de
partitions (par jour **et** par ville **et** par produit) produisent des milliers de petits
fichiers, ralentissent tout et coûtent plus. On partitionne selon les **filtres les plus
fréquents** (souvent la date), on **compacte** les petits fichiers, et l'on trie ou regroupe les
données (*clustering*, Z-order) pour que les statistiques min/max sautent des blocs.

Le reste du travail sur les coûts : des **quotas** et des alertes de budget par équipe, des
tables agrégées pour les tableaux de bord consultés cent fois par jour, l'arrêt automatique des
entrepôts inactifs, l'archivage des données froides, et la question systématique : « qui lit
encore cette table ? » (le lignage y répond).

## À retenir

- Lakehouse : des fichiers Parquet immuables + un journal de transactions (add, remove) ; versions, voyage dans le temps, schéma contrôlé, VACUUM (réel).
- Évolution des schémas : ajouter avec défaut, élargir, alias pour renommer ; un registre refuse les changements incompatibles (cinq cas réels).
- Gouvernance : lignage, catalogue, données personnelles et droit à l'effacement ; coûts : partitions et colonnes ne lisent que 5,5 % du jeu, mais trop de petites partitions coûtent (8,95 Mo contre 5,2 Mo).
