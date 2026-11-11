---
id: bdd.n1.interroger-une-base
title: Tables, SELECT, modifications et NULL
skills: [tables-types, select, modifier, valeurs-null]
duration_min: 16
---

# Tables, SELECT, modifications et NULL

Toutes les requêtes de ce parcours ont été exécutées sur PostgreSQL 16 ; les résultats
cités sont réels.

## Tables, types et clés primaires

Une base relationnelle range les données dans des **tables** : des lignes qui ont toutes
les mêmes colonnes, chacune avec un **type**.

```sql
create table clients (
    id         bigint generated always as identity primary key,
    email      text not null unique,
    nom        text not null,
    ville      text,                       -- peut être NULL (inconnue)
    solde      numeric(12, 2) not null default 0,
    cree_le    timestamptz not null default now()
);
```

- La **clé primaire** identifie chaque ligne de façon unique et ne change jamais. Une clé
  **technique** (un identifiant généré) est plus sûre qu'une clé « naturelle » comme
  l'e-mail, qui finit toujours par changer.
- Les **contraintes** (`not null`, `unique`, `check (solde >= 0)`) sont des règles que la
  base garantit, quel que soit le programme qui écrit. C'est la dernière ligne de défense de
  la qualité des données.
- Les types comptent : `numeric` pour l'argent (jamais `float`, qui arrondit), `timestamptz`
  pour les dates avec fuseau, `text` plutôt que `varchar(255)` en PostgreSQL.

## Lire : SELECT

```sql
select nom, ville            -- les colonnes voulues (éviter select * dans le code)
from clients
where ville = 'Lyon'         -- filtrer les lignes
  and solde > 100
order by nom                 -- trier (sans order by, l'ordre n'est PAS garanti)
limit 20 offset 40;          -- paginer
```

L'ordre **d'écriture** n'est pas l'ordre **d'exécution** : la base évalue `from`, puis
`where`, puis `group by`, `having`, `select`, `order by` et enfin `limit`. C'est pour ça
qu'un alias défini dans le `select` n'est pas utilisable dans le `where`.

Sans `order by`, une base peut renvoyer les lignes dans n'importe quel ordre, et cet ordre
peut changer d'une exécution à l'autre (après une mise à jour, un `VACUUM`, une requête
parallèle). Un test qui « marche » sans `order by` est un test fragile.

## Modifier : INSERT, UPDATE, DELETE

```sql
insert into clients(email, nom, ville) values ('ada@exemple.com', 'Ada', 'Lyon')
returning id;                                    -- récupérer l'identifiant généré

update clients set ville = 'Paris' where id = 42;
delete from clients where id = 42;

insert into clients(email, nom) values ('ada@exemple.com', 'Ada')
on conflict (email) do update set nom = excluded.nom;   -- « upsert »
```

Le réflexe vital : un `update` ou un `delete` **sans `where`** touche toute la table. En
production, on écrit d'abord le `select` avec le même `where`, on vérifie le nombre de
lignes, et on travaille dans une transaction (`begin` … `commit`, ou `rollback` si le
nombre de lignes modifiées est inattendu).

## NULL : la logique à trois valeurs

`NULL` signifie « valeur inconnue ». Toute comparaison avec une inconnue donne… une
inconnue. Résultats réels :

```sql
select null = null;          -- NULL (et non true)
select null is null;         -- true
select 1 + null;             -- NULL
select 'a' || null;          -- NULL
```

Sur une table `clients` de 3 lignes (Ada à Lyon, Linus sans ville, Grace à Paris) :

| Requête | Résultat réel |
|---|---|
| `select count(*), count(ville) from clients` | `3` et `2` : `count(colonne)` ignore les NULL |
| `select nom from clients where ville <> 'Lyon'` | **Grace seulement** : pour Linus, `NULL <> 'Lyon'` est inconnu, donc la ligne est exclue |
| `… where ville is distinct from 'Lyon'` | Linus **et** Grace : la comparaison qui traite NULL comme une valeur |
| `… where id not in (select id from (values (1), (null)) t(id))` | **0 ligne** |

Le dernier piège est le plus dangereux : `x not in (1, NULL)` vaut `x <> 1 and x <> NULL`,
et la seconde partie est toujours inconnue. Si la sous-requête d'un `not in` peut contenir
un NULL, la requête ne renvoie **rien**, sans erreur. On préfère `not exists`, qui n'a pas
ce problème.

Règles pratiques : `is null` / `is not null` pour tester, `coalesce(ville, 'inconnue')`
pour remplacer, et `not null` sur toute colonne qui n'a aucune raison d'être vide.

## À retenir

- Clé primaire technique, contraintes en base, types justes (`numeric` pour l'argent).
- Ordre d'exécution : from, where, group by, having, select, order by, limit ; sans `order by`, aucun ordre garanti.
- `update`/`delete` : toujours un `where`, vérifié d'abord avec un `select`, dans une transaction.
- NULL est une inconnue : `is null`, `count(col)` l'ignore, `<>` l'exclut, `not in` avec un NULL ne renvoie rien ; préférer `not exists`.
