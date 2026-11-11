---
id: donnees.n3.les-traitements
title: Traitement par lots, orchestration et qualité des données
skills: [traitement-par-lots, orchestration, qualite]
duration_min: 21
---

# Traitement par lots, orchestration et qualité des données

## Le traitement par lots

Quand les données ne tiennent plus sur une machine, on les **découpe en partitions** traitées en
parallèle par un cluster : c'est le modèle de **Spark** (et avant lui de MapReduce). Les idées
clés se retrouvent dans tous les moteurs modernes, y compris sur une seule machine (Polars,
DuckDB).

**1. L'évaluation paresseuse et le plan.** On décrit une suite de transformations ; rien ne
s'exécute avant une **action** (écrire, compter, collecter). Le moteur peut alors **optimiser le
plan entier**. Plan réel de Polars pour « chiffre d'affaires de Lyon par code postal » :

```python
plan = (pl.scan_parquet("ventes.parquet")
          .filter(pl.col("ville") == "Lyon")
          .group_by("code_postal").agg(pl.col("montant").sum())
          .sort("code_postal"))
print(plan.explain())
```

```
SORT BY [col("code_postal")]
  AGGREGATE [col("montant").sum()] BY [col("code_postal")]
    simple π 2/2 ["code_postal", "montant"]
      Parquet SCAN [ventes.parquet]
      PROJECT 3/5 COLUMNS
      SELECTION: col("ville") == "Lyon"
```

Le filtre écrit **après** la lecture a été **poussé dans la lecture** (*predicate pushdown*), et
seules 3 colonnes sur 5 sont lues (*projection pushdown*). Spark fait la même chose (son
optimiseur s'appelle Catalyst), et `df.explain()` y montre le plan.

**2. Transformations étroites et larges.** Un `filter` ou un `map` travaille partition par
partition, sans échange : transformation **étroite**. Un `groupBy`, un `join` ou un `orderBy`
doit réunir toutes les lignes d'une même clé sur la même machine : transformation **large**,
qui provoque un **shuffle** (redistribution des données par le réseau). Le shuffle est presque
toujours l'étape la plus coûteuse : on cherche à filtrer **avant**, à réduire les colonnes, et à
diffuser la petite table d'une jointure à toutes les machines (*broadcast join*) plutôt que de
redistribuer la grande.

**3. Le déséquilibre** (*skew*). Si une clé concentre une grande partie des lignes (un client
géant, la valeur `NULL`, le pays « FR »), la partition qui la reçoit travaille seule pendant que
les autres attendent : un traitement de 10 minutes en dure 2 heures. Parades : saler la clé
(la découper artificiellement), traiter la clé géante à part, ou laisser l'exécution adaptative
de Spark 3 (AQE) découper les partitions trop grosses.

Le conseil pratique : avant Spark, essayez une seule machine. DuckDB ou Polars traitent des
centaines de millions de lignes sur un ordinateur portable ; un cluster ajoute des coûts et des
modes de panne qu'il faut justifier par le volume.

## L'orchestration

Un pipeline réel enchaîne des dizaines de tâches dépendantes, chaque jour, avec des échecs. Un
**orchestrateur** (Airflow, Dagster, Prefect) les décrit comme un **DAG** (graphe orienté sans
cycle), les lance dans l'ordre, les réessaie, et garde l'historique. Un mini-orchestrateur
réel, avec une tâche qui échoue une fois le 2 mars :

```python
DAG = {"nettoyer": {"extraire"}, "agreger": {"nettoyer"}, "publier": {"agreger"}}
ordre = list(TopologicalSorter(DAG).static_order())
```

```
ordre : extraire → nettoyer → agreger → publier
  2026-03-02 nettoyer : essai 1 échoué (source momentanément indisponible), nouvel essai
jour=2026-03-01 {"jour": "2026-03-01", "ventes": 4, "ca": 180.47}
jour=2026-03-02 {"jour": "2026-03-02", "ventes": 4, "ca": 253.65}
jour=2026-03-03 {"jour": "2026-03-03", "ventes": 5, "ca": 214.51}
```

Le 1er et le 2 mars ont été traités **deux fois** (un premier passage, un rattrapage de trois
jours, une relance du 2) et pourtant chaque partition contient un seul résultat : la tâche
`publier` **écrase sa partition** (`jour=2026-03-02/`). La même expérience avec une écriture en
**ajout**, réel :

```
entrepot_ajout/jour=2026-03-01 : 2 lignes
entrepot_ajout/jour=2026-03-02 : 2 lignes
entrepot_ajout/jour=2026-03-03 : 1 lignes
```

Des doublons à chaque relance. Les règles d'or de l'orchestration :

- chaque exécution traite un **intervalle de données** explicite (le « jour logique » d'Airflow),
  pas « maintenant » : relancer le 2 mars demain doit traiter le 2 mars ;
- chaque tâche est **idempotente** : elle remplace sa partition (ou fait un `MERGE` sur une clé) ;
- le **rattrapage** (*backfill*) d'une période devient alors une simple boucle ;
- l'orchestrateur **ordonne**, il ne **calcule** pas : les traitements lourds tournent ailleurs
  (entrepôt, Spark), l'orchestrateur ne fait que les déclencher et les surveiller.

## La qualité des données

Un pipeline qui tourne sans erreur peut produire des données fausses : la qualité se **teste**,
comme le code, mais sur les données, à chaque exécution. Des contrôles réels sur un lot
volontairement abîmé :

```
ÉCHEC aucun id en double           (1 ligne(s) en cause)
ÉCHEC client renseigné             (7 ligne(s) en cause)
ÉCHEC montant entre 0 et 10 000    (2 ligne(s) en cause)
ÉCHEC données du jour (fraîcheur)  (3 ligne(s) en cause)
ÉCHEC volume ≥ 80 % de la veille   (641 lignes contre 1000 la veille)
```

Chaque contrôle est une requête SQL qui compte les lignes fautives. Les grandes familles :
**unicité**, **complétude** (pas de `NULL` là où il n'en faut pas), **validité** (plages,
formats, valeurs autorisées), **intégrité référentielle** (chaque vente a un magasin connu),
**fraîcheur** (les données sont-elles récentes ?) et **volume** (une chute brutale signale
souvent une source cassée en amont, l'erreur la plus fréquente et la plus silencieuse).

Que faire quand un contrôle échoue ? **Bloquer** la publication (mieux vaut un tableau de bord
d'hier qu'un tableau de bord faux), **mettre en quarantaine** les lignes fautives, ou seulement
**alerter** pour les contrôles moins critiques. Les outils : les tests de dbt (`unique`,
`not_null`, `relationships`, `accepted_values`), Great Expectations, Soda. Et les **contrats de
données** : un accord écrit entre l'équipe qui produit une donnée et celles qui la consomment
(schéma, sens des champs, fraîcheur garantie), vérifié automatiquement.

## À retenir

- Lots : évaluation paresseuse et plan optimisé (filtre et colonnes poussés dans la lecture, réel) ; le shuffle des transformations larges coûte le plus ; attention au déséquilibre.
- Orchestration : un DAG, des réessais, un intervalle de données explicite et des tâches idempotentes (écraser la partition : 1 ligne ; ajouter : 2 lignes après relance).
- Qualité : unicité, complétude, validité, intégrité, fraîcheur, volume, vérifiées à chaque exécution ; bloquer plutôt que publier faux.
