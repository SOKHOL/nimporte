---
id: bdd.n5.sous-le-capot
title: MVCC, WAL, B-tree, LSM-tree et bases distribuées
skills: [mvcc-wal, structures-stockage, bases-distribuees, choisir-stockage]
duration_min: 21
---

# MVCC, WAL, B-tree, LSM-tree et bases distribuées

## MVCC : plusieurs versions de chaque ligne

Comment une base laisse-t-elle lire pendant qu'on écrit, sans verrouiller les lecteurs ?
PostgreSQL (comme Oracle, MySQL InnoDB et la plupart des bases modernes) utilise le **MVCC**
(*Multi-Version Concurrency Control*) : un `update` ne modifie pas la ligne, il en **écrit une
nouvelle version**. Chaque transaction voit les versions valides au moment où elle a commencé.

Sorties réelles, sur une table `compte` d'une ligne :

```
 xmin | xmax | ctid  | id | solde
 2866 |    0 | (0,1) |  1 |   100        ← avant
 2867 |    0 | (0,2) |  1 |    90        ← après update : nouvelle version, à un autre emplacement
```

`xmin` est la transaction qui a créé la version, `ctid` son emplacement physique.
L'ancienne version `(0,1)` existe toujours : une transaction plus ancienne peut encore la
lire. Après deux mises à jour, `pg_stat_user_tables` indique réellement 1 ligne vivante et
**2 lignes mortes**.

Conséquence mesurée : sur une table de 100 001 lignes (3 544 kB), mettre à jour toutes les
lignes **double** sa taille.

| Étape | Taille réelle |
|---|---|
| départ | 3 544 kB |
| après un `update` de toutes les lignes | 7 080 kB |
| après un second `update` | 10 Mo |
| après `VACUUM` | 10 Mo (les places libres sont marquées réutilisables) |
| nouvel `update` après `VACUUM` | 10 Mo (l'espace libéré est réutilisé) |
| après `VACUUM FULL` (réécrit la table, verrou exclusif) | 3 544 kB |

**VACUUM** (lancé automatiquement par l'*autovacuum*) récupère la place des versions que plus
aucune transaction ne peut voir. D'où un problème classique : une **transaction ouverte depuis
des heures** (une session oubliée, `idle in transaction`) empêche de nettoyer **toutes** les
versions créées depuis son début. Les tables gonflent, les requêtes ralentissent. Surveiller
l'âge des transactions et `n_dead_tup`, et régler l'autovacuum sur les tables très modifiées.

MySQL InnoDB fait différemment : la ligne est modifiée sur place et l'ancienne version part
dans un journal d'annulation (*undo log*). Même idée, autres compromis.

## Le WAL : la durabilité

Avant de modifier les fichiers de données, la base écrit le changement dans un **journal**
séquentiel, le **WAL** (*Write-Ahead Log*). Au `commit`, seul le journal doit être forcé sur le
disque (`fsync`, environ 226 µs mesurés au parcours System design) ; les pages de données sont
écrites plus tard, par lots. Après un plantage, la base rejoue le journal.

Mesure réelle : insérer 100 000 lignes a produit **12 Mo** de WAL pour une table de 3,5 Mo
(le journal contient aussi les pages d'index et des images complètes de pages après chaque
point de contrôle).

Le WAL sert aussi à :

- la **réplication** : les répliques rejouent le WAL du primaire (parcours System design,
  niveau 2) ;
- la **restauration à un instant précis** (PITR) : une sauvegarde complète + le WAL archivé
  permettent de revenir à 14 h 31 min 59 s, juste avant le `delete` malheureux ;
- la **capture des changements** (CDC, Debezium) : lire le flux des modifications pour
  alimenter d'autres systèmes.

Une sauvegarde n'existe que si sa **restauration** a été testée.

## B-tree et LSM-tree

Deux grandes structures de stockage s'opposent.

**Le B-tree** (PostgreSQL, MySQL, Oracle, SQL Server, SQLite) : un arbre équilibré de pages
(8 Ko en PostgreSQL), très large et peu profond (3 ou 4 niveaux pour des centaines de millions
de clés). Les données sont **modifiées sur place**, dans leur page.

- lecture d'une clé : quelques pages, rapide et prévisible ;
- écriture : une page à réécrire (et le WAL), avec des écritures aléatoires sur le disque.

**Le LSM-tree** (*Log-Structured Merge-tree* : RocksDB, Cassandra, ScyllaDB, LevelDB, HBase) :

1. les écritures vont dans une table triée **en mémoire** (et un journal) ;
2. quand elle est pleine, elle est écrite d'un bloc sur disque, en fichier trié **immuable**
   (SSTable) ;
3. en arrière-plan, la **compaction** fusionne ces fichiers et élimine les versions périmées.

- écriture : très rapide, toujours séquentielle ;
- lecture : il faut parfois regarder plusieurs fichiers (des filtres de Bloom évitent la
  plupart des lectures inutiles) ;
- la compaction consomme du disque et du CPU en arrière-plan, et peut créer des pics de
  latence.

| | B-tree | LSM-tree |
|---|---|---|
| Point fort | lectures, requêtes par plage | débit d'écriture |
| Amplification d'écriture | moyenne | élevée (compaction) |
| Amplification de lecture | faible | plus élevée |
| Usage typique | bases transactionnelles | séries temporelles, journaux, gros volumes d'écriture |

## Les bases distribuées

Quand une seule machine ne suffit plus, même avec des répliques, trois chemins :

- **partitionner soi-même** une base classique (Citus pour PostgreSQL, Vitess pour MySQL) ;
- une base **NoSQL distribuée** (Cassandra, DynamoDB) : échelle horizontale, mais cohérence
  réglable et peu de transactions entre partitions ;
- une base **NewSQL** (Google Spanner, CockroachDB, YugabyteDB, TiDB) : du SQL et des
  transactions ACID, réparties sur de nombreuses machines.

Une base NewSQL découpe les données en plages, chaque plage étant répliquée sur 3 ou 5 nœuds
avec un **consensus Raft** (parcours System design, niveau 5). Une transaction qui touche
plusieurs plages utilise une validation distribuée, et des horloges hybrides (ou TrueTime pour
Spanner) ordonnent les transactions. Le prix : chaque écriture attend la majorité de ses
répliques, donc la latence d'écriture dépend de la distance entre les nœuds ; et les
transactions entre régions éloignées sont lentes.

## Choisir son stockage

Questions à se poser, dans l'ordre :

1. **Quelles requêtes ?** Par clé, par plage, jointures, agrégations, recherche plein texte,
   parcours de graphe.
2. **Quelle cohérence ?** Une erreur coûte-t-elle de l'argent (stock, paiement) ou rien
   (compteur de vues) ?
3. **Quels volumes** en lecture, en écriture, en stockage, aujourd'hui et dans trois ans ?
4. **Qui l'exploite ?** Sauvegardes, restaurations testées, montées de version, supervision,
   compétences de l'équipe. Un service géré (RDS, Cloud SQL, Aurora) en retire une partie.

Le choix par défaut, raisonnable pour la grande majorité des applications : **PostgreSQL**, et
un cache. On ajoute un autre stockage quand un besoin mesuré le justifie, pas parce qu'il est
à la mode.

## À retenir

- MVCC : chaque `update` écrit une nouvelle version (table doublée mesurée) ; VACUUM récupère la place ; une transaction ouverte trop longtemps bloque le nettoyage.
- WAL : journal écrit avant les données ; durabilité, réplication, restauration à un instant précis, CDC ; tester les restaurations.
- B-tree pour les lectures, LSM-tree pour le débit d'écriture.
- NewSQL : SQL et ACID distribués grâce au consensus, au prix de la latence d'écriture.
- Choisir selon les requêtes, la cohérence, les volumes et l'exploitation ; PostgreSQL par défaut.
