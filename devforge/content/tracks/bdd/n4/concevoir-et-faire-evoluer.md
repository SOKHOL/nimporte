---
id: bdd.n4.concevoir-et-faire-evoluer
title: NoSQL, migrations, verrous et pools de connexions
skills: [nosql, migrations, verrous, pool-connexions]
duration_min: 19
---

# NoSQL, migrations, verrous et pools de connexions

## Les familles NoSQL

« NoSQL » regroupe des bases qui renoncent à une partie du modèle relationnel pour un besoin
précis.

| Famille | Modèle | Exemples | Taillée pour |
|---|---|---|---|
| **Clé-valeur** | une clé → une valeur opaque | Redis, DynamoDB, Memcached | caches, sessions, compteurs ; accès par clé ultra-rapide |
| **Document** | des documents JSON imbriqués | MongoDB, Couchbase, Firestore | données hiérarchiques lues d'un bloc, schéma qui évolue |
| **Colonnes larges** | lignes partitionnées, colonnes nombreuses | Cassandra, ScyllaDB, HBase | énormément d'écritures, séries temporelles, échelle horizontale |
| **Graphe** | nœuds et relations | Neo4j, Neptune | parcourir des relations (réseau social, fraude, recommandations) |
| **Recherche** | index inversé | Elasticsearch, OpenSearch | recherche plein texte, filtres à facettes |

Le compromis général : on choisit le modèle de données **selon les requêtes**. En Cassandra,
on crée une table par requête, dénormalisée ; une requête imprévue (« toutes les commandes
d'un produit ») peut être impossible sans tout relire. En relationnel, on modélise les données
et on écrit les requêtes ensuite.

« Sans schéma » ne veut pas dire sans schéma : le schéma est simplement **dans le code qui
lit**, et chaque version de ce code doit gérer les anciens documents.

Avant d'ajouter une base NoSQL, vérifier ce que PostgreSQL sait déjà faire : colonnes `jsonb`
indexables, recherche plein texte, partitionnement, extensions (PostGIS pour la géographie,
pgvector pour les vecteurs d'IA). Chaque base de plus, c'est des sauvegardes, de la
supervision, des montées de version et des compétences en plus.

## Les verrous et la contention

Les écritures posent des verrous : sur les **lignes** (deux `update` de la même ligne
s'attendent) et sur les **tables** (les changements de structure).

Un piège réel, celui qui provoque des pannes en production pendant des migrations « anodines ».
Démonstration sur PostgreSQL 16 :

```
0,0 s  une transaction de lecture reste ouverte (un rapport qui dure)
0,0 s  ALTER TABLE acheteurs ADD COLUMN telephone text : en attente du verrou…
3,5 s  fin du rapport (commit)
3,5 s  ALTER TABLE terminé
3,5 s  lecture 1 servie après 3,0 s
3,5 s  lecture 2 servie après 3,0 s
```

L'`ALTER TABLE` a besoin d'un verrou exclusif ; il attend la fin du rapport. Mais pendant
qu'il attend, il est **en file**, et toutes les requêtes suivantes, même de simples `select`,
attendent **derrière lui**. Une migration qui dure 1 ms bloque tout le site pendant la durée
de la plus longue transaction en cours.

La parade : un **délai d'attente de verrou** court, et réessayer.

```sql
set lock_timeout = '200ms';
alter table acheteurs add column telephone text;
-- réel : ERROR: canceling statement due to lock timeout   (personne n'a été bloqué)
```

Autres règles : des transactions courtes, `statement_timeout` pour les requêtes qui
s'éternisent, et des écritures dans un **ordre cohérent** pour éviter les interblocages
(*deadlocks*) que la base détecte et résout en annulant une des transactions.

## Les migrations sans interruption

Les changements de schéma sont versionnés avec le code (Flyway, Liquibase, Alembic, les
migrations de Rails ou de Django) et appliqués automatiquement au déploiement. Pour ne rien
couper :

| Opération | Risque | Façon sûre |
|---|---|---|
| Ajouter une colonne qui accepte NULL | aucun (instantané) | directement, avec `lock_timeout` |
| Ajouter une colonne `not null default …` | instantanée depuis PostgreSQL 11 ; réécrit la table sur de vieilles versions et d'autres moteurs | vérifier sa version et son moteur |
| Créer un index | `create index` bloque les écritures pendant toute la création | `create index concurrently` |
| Ajouter une contrainte (clé étrangère, `check`) | vérifie toute la table sous verrou | `… not valid`, puis `validate constraint` (sans bloquer les écritures) |
| Renommer ou supprimer une colonne | casse le code encore déployé | expand / contract en plusieurs déploiements |
| Remplir une nouvelle colonne sur 100 millions de lignes | un énorme `update` : verrous, WAL, réplication en retard | par lots (10 000 lignes à la fois), avec des pauses |

Et toujours : la migration doit être **compatible avec la version précédente du code**, qui
tourne encore pendant le déploiement (voir le parcours DevOps, niveau 3).

## Connexions et pools

Ouvrir une connexion à une base coûte cher : connexion réseau, souvent TLS,
authentification, et en PostgreSQL **un processus par connexion**. Mesure réelle, sur la
même machine, par une socket locale et sans TLS :

| | Durée par requête |
|---|---|
| nouvelle connexion pour chaque requête | 2,29 ms |
| connexion réutilisée | 0,062 ms |

Environ **37 fois** plus lent, avant même d'ajouter le réseau et TLS. D'où les **pools de
connexions** (HikariCP en Java, celui de SQLAlchemy, pgx en Go) : un ensemble de connexions
ouvertes, prêtées puis rendues.

Dimensionner le pool :

- PostgreSQL accepte `max_connections = 100` par défaut ; chaque connexion consomme de la
  mémoire. 20 instances de l'application × un pool de 20 = 400 connexions : refusées.
- **Un petit pool va plus vite qu'un gros** : la base n'a qu'un nombre limité de cœurs et de
  disques ; au-delà, les requêtes se gênent. Point de départ souvent cité : environ
  `2 × cœurs de la base`, puis mesurer.
- Quand le pool est épuisé, les requêtes **attendent** une connexion ; il faut un délai
  maximal d'attente et une métrique sur le temps d'attente.
- Avec beaucoup d'instances (ou des fonctions serverless), un **pool côté base** comme
  PgBouncer multiplexe des milliers de connexions clientes sur quelques dizaines de vraies
  connexions.

## À retenir

- NoSQL : clé-valeur, document, colonnes larges, graphe, recherche ; on modélise selon les requêtes ; PostgreSQL couvre déjà beaucoup.
- Un `ALTER TABLE` en attente de verrou bloque toutes les requêtes derrière lui (3 s mesurées) : `lock_timeout` et réessai.
- Migrations : `concurrently`, `not valid` puis `validate`, expand/contract, remplissage par lots.
- Pool de connexions : ×37 mesuré ; petit pool, délai d'attente, PgBouncer pour beaucoup de clients.
