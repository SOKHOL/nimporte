---
id: donnees.n2.l-entrepot
title: Entrepôts de données, modèle en étoile et SQL analytique
skills: [entrepots, modele-etoile, sql-analytique]
duration_min: 20
---

# Entrepôts de données, modèle en étoile et SQL analytique

## Les entrepôts de données

La base de production d'une application (PostgreSQL, MySQL) est optimisée pour l'**OLTP**
(*OnLine Transaction Processing*) : beaucoup de petites transactions qui lisent ou modifient
quelques lignes par clé. L'analyse est un autre métier, l'**OLAP** (*OnLine Analytical
Processing*) : peu de requêtes, mais chacune parcourt des millions de lignes sur quelques
colonnes, pour agréger.

La même requête (chiffre d'affaires par ville et par mois, sur le million de ventes), mesurée
(meilleur de trois essais) :

```
PostgreSQL 16, table de 62 Mo (stockage par lignes)       : 88 ms
DuckDB sur le fichier Parquet de 5,2 Mo (par colonnes)    : 25 ms
```

L'écart grandit avec le volume et le nombre de colonnes ignorées. Un **entrepôt de données**
(BigQuery, Snowflake, Redshift, ClickHouse ; DuckDB en local) est une base conçue pour l'OLAP :

- stockage **par colonnes** et compressé (niveau 1) ;
- exécution **vectorisée** (des blocs de valeurs traités d'un coup, en SIMD) et parallèle ;
- souvent, **séparation du stockage et du calcul** : les données dorment dans un stockage objet
  bon marché, et l'on paie la puissance de calcul à la requête ou à la seconde.

Et surtout, l'entrepôt **sépare l'analyse de la production** : une requête lourde d'un analyste
ne doit jamais ralentir les clients. On y copie donc les données de production (par lots ou par
capture des changements, niveau 4), avec leur **historique**.

## Le modèle en étoile

Ralph Kimball a popularisé la modélisation **dimensionnelle** : au centre, une table de **faits**
(des événements mesurables : une ligne par vente, avec ses **mesures** numériques) ; autour, des
tables de **dimensions** (le contexte : qui, quoi, où, quand), qui portent les attributs
descriptifs selon lesquels on filtre et on regroupe.

```
              dim_date (date_id, annee, mois, jour_semaine, week_end)
                         │
dim_magasin ──── fait_ventes (vente_id, date_id, magasin_id, montant) ──── dim_produit
(magasin_id, ville, region)
```

Construit réellement dans DuckDB à partir du million de ventes :

```
dim_magasin :  1 Bordeaux Ouest | 2 Lille Nord | 3 Lyon Sud-Est | 4 Marseille Sud-Est | 5 Nantes Ouest | 6 Paris Nord
```

Une question métier devient une jointure de la table de faits avec ses dimensions, toujours de
la même forme. Ventes de mars par région, semaine contre week-end, réel :

```sql
select m.region, d.week_end, count(*) as ventes, round(sum(f.montant)) as ca
from fait_ventes f join dim_magasin m using (magasin_id) join dim_date d using (date_id)
where d.mois = 3
group by all order by m.region, d.week_end;
```

```
Nord    false  24531  6169520      Nord    true   9956  2492028
Ouest   false  24594  6156587      Ouest   true  10091  2544132
Sud-Est false  24684  6194057      Sud-Est true  10100  2545432
```

Pourquoi ce modèle **dénormalisé** (la région est répétée pour chaque ville), à l'opposé de la
normalisation enseignée pour la production ? Parce qu'il est **simple à interroger** (une seule
forme de requête, compréhensible par les analystes et les outils de BI), **rapide** (peu de
jointures, des dimensions petites), et que les données y sont **historiques**, rarement
modifiées. Les questions de conception :

- le **grain** de la table de faits : que représente **une** ligne (une vente ? une ligne de
  commande ? un total par jour ?) ; c'est la première décision, et la plus difficile à changer ;
- les **dimensions à évolution lente** (*SCD*) : un magasin qui change de région. Type 1 :
  écraser (l'historique est réécrit) ; type 2 : ajouter une nouvelle ligne avec des dates de
  validité (l'historique est conservé, les anciennes ventes restent dans l'ancienne région) ;
- des **clés de substitution** (`magasin_id`) plutôt que les clés de production, qui peuvent
  changer ou entrer en collision entre sources.

## Le SQL analytique

Les **fonctions de fenêtre** (parcours Bases de données, niveau 2) sont l'outil central de
l'analyse : elles calculent sur un ensemble de lignes liées à la ligne courante, **sans les
regrouper**. Réel, sur les trois premiers mois :

```sql
select region, mois, ca,
       rank() over (partition by mois order by ca desc) as rang_du_mois,
       sum(ca) over (partition by region order by mois) as cumul,
       round(100.0 * (ca - lag(ca) over (partition by region order by mois))
             / lag(ca) over (partition by region order by mois), 1) as evolution_pct
from par_mois order by region, mois;
```

```
region   mois  ca         rang  cumul       evolution_pct
Nord     1     8589020.0  3     8589020.0   NULL
Nord     2     7759928.0  3     16348948.0  -9.7
Nord     3     8661548.0  3     25010496.0  11.6
Sud-Est  1     8600069.0  1     8600069.0   NULL
Sud-Est  2     7878202.0  1     16478271.0  -8.4
Sud-Est  3     8739489.0  1     25217760.0  10.9
```

`rank` classe, `sum … over (order by …)` cumule, `lag` regarde la ligne précédente. Et une
leçon d'analyse au passage : la « chute » de février (−9,7 %) n'est due qu'aux **28 jours** de
février contre 31 en janvier. Comparer des moyennes **par jour**, ou d'une année sur l'autre,
évite de tirer de fausses conclusions. Les autres outils du SQL analytique : `group by rollup` et
`cube` (sous-totaux), les percentiles (`percentile_cont`), `qualify` (filtrer sur une fonction de
fenêtre), et les dénombrements approximatifs (`approx_count_distinct`, HyperLogLog) pour les très
gros volumes.

## À retenir

- OLTP (production, par lignes) contre OLAP (analyse, par colonnes) : 88 ms contre 25 ms mesurés ; l'entrepôt sépare l'analyse de la production.
- Modèle en étoile : faits au grain choisi, dimensions dénormalisées, clés de substitution, dimensions à évolution lente (type 1 ou 2).
- Fonctions de fenêtre : rang, cumul, évolution avec lag ; comparer à périmètre égal (février a 28 jours).
