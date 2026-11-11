---
id: bdd.n3.performance-et-transactions
title: Index, plans d'exécution, transactions, isolation et N+1
skills: [index, explain, transactions, isolation, orm-n-plus-1]
duration_min: 22
---

# Index, plans d'exécution, transactions, isolation et N+1

Mesures réelles sur PostgreSQL 16, avec une table `commandes` d'un million de lignes et une
table `acheteurs` de 100 000 lignes.

## Les index

Un index est une structure triée (un **B-tree** par défaut) qui permet de trouver des lignes
sans lire toute la table, comme l'index d'un livre.

```sql
explain analyze select * from commandes where acheteur_id = 4242;
```

Sans index (sortie réelle, résumée) :

```
Gather
  ->  Parallel Seq Scan on commandes
        Filter: (acheteur_id = 4242)
        Rows Removed by Filter: 333330
Execution Time: 22.462 ms
```

Après `create index commandes_acheteur_idx on commandes(acheteur_id)` :

```
Bitmap Heap Scan on commandes
  ->  Bitmap Index Scan on commandes_acheteur_idx
        Index Cond: (acheteur_id = 4242)
Execution Time: 0.429 ms
```

Environ **50 fois** plus rapide, et l'écart grandit avec la table. Le prix : l'index occupe de
la place (9 Mo pour une table de 63 Mo ici) et **chaque écriture** doit le mettre à jour. On
indexe donc ce qui sert vraiment : les clés étrangères, les colonnes des `where` fréquents,
des `join` et des `order by`.

Quand un index **n'est pas utilisé** :

- **Une fonction sur la colonne** : `where lower(email) = '…'` ne peut pas utiliser un index
  sur `email`. Réel : 21,2 ms (lecture séquentielle) contre 0,13 ms avec `where email = '…'`.
  Solution : un index sur l'expression, `create index on acheteurs (lower(email))`.
- **Un index composite dans le mauvais ordre** : un index sur `(pays, email)` sert pour
  `pays = …` et pour `pays = … and email = …`, mais en général pas pour `email = …` seul
  (règle du préfixe gauche ; PostgreSQL 18 sait parfois « sauter » la première colonne
  quand elle a très peu de valeurs différentes, sans que ce soit aussi efficace).
- **Une condition peu sélective** : pour `montant > 20`, qui garde 99 % des lignes, lire
  toute la table est plus rapide que de sauter d'index en table. Réel : la base choisit une
  lecture séquentielle parallèle (45 ms) ; c'est le bon choix.
- **Des statistiques périmées** : après un gros chargement, `analyze` pour que l'optimiseur
  connaisse la distribution des données.

## Lire un plan d'exécution

`explain` montre le plan choisi ; `explain analyze` **exécute** la requête et ajoute les
temps et nombres de lignes réels. Ce qu'on cherche :

- `Seq Scan` sur une grosse table avec un filtre très sélectif : un index manque ;
- un grand écart entre les lignes **estimées** et **réelles** : statistiques fausses, plan
  probablement mauvais ;
- `Rows Removed by Filter` énorme : on lit beaucoup pour garder peu ;
- les types de jointure : `Nested Loop` (bien pour peu de lignes avec un index), `Hash Join`
  (gros volumes), `Merge Join` (entrées triées).

Attention : `explain analyze` d'un `delete` ou d'un `update` les exécute vraiment. On
l'encadre par `begin` … `rollback`.

## Les transactions et ACID

Une **transaction** regroupe plusieurs opérations en un tout :

| | Garantie |
|---|---|
| **A**tomicité | tout ou rien : un virement débite et crédite, ou ne fait rien |
| **C**ohérence | les contraintes (clés, `check`) sont respectées à la fin |
| **I**solation | les transactions concurrentes ne se perturbent pas (dans une certaine mesure, voir ci-dessous) |
| **D**urabilité | une fois validée (`commit`), la donnée survit à un plantage (grâce au journal, le WAL) |

```sql
begin;
update comptes set solde = solde - 100 where id = 1;
update comptes set solde = solde + 100 where id = 2;
commit;     -- ou rollback;
```

Les transactions doivent être **courtes** : une transaction ouverte garde des verrous et
empêche le nettoyage des anciennes versions de lignes (niveau 5). Jamais d'appel HTTP ni
d'attente d'un utilisateur au milieu d'une transaction.

## L'isolation et ses anomalies

Démonstrations réelles avec deux connexions simultanées.

**La mise à jour perdue.** Deux ventes du même clavier (stock 10), en lisant puis en
écrivant la valeur calculée par l'application, au niveau par défaut `READ COMMITTED` :

```
t1 lit 10      t2 lit 10
t1 écrit 9     t2 écrit 9
→ deux ventes, quantité finale : 9        (au lieu de 8)
```

Trois corrections :

1. **Une mise à jour atomique** : `update stock set quantite = quantite - 1 …` ; la base
   relit la valeur à jour sous verrou. Réel : quantité finale **8**.
2. **Un verrou explicite** : `select … for update` bloque la ligne jusqu'à la fin de la
   transaction.
3. **Un niveau d'isolation plus strict** : en `REPEATABLE READ`, la seconde écriture échoue
   (réel : `could not serialize access due to concurrent update`) et l'application doit
   **réessayer** la transaction.

**Le write skew.** Deux médecins de garde, la règle : au moins un doit rester de garde.
Chacun vérifie qu'ils sont deux, puis se retire.

| Niveau | Résultat réel |
|---|---|
| `REPEATABLE READ` | les deux transactions sont validées ; **0** médecin de garde |
| `SERIALIZABLE` | la seconde est refusée (`could not serialize access due to read/write dependencies…`) ; 1 médecin reste |

Les niveaux, du plus permissif au plus strict :

| Niveau | Anomalies évitées |
|---|---|
| `READ UNCOMMITTED` | (en PostgreSQL, se comporte comme `READ COMMITTED`) |
| `READ COMMITTED` (défaut PostgreSQL, Oracle) | lectures sales |
| `REPEATABLE READ` (défaut MySQL InnoDB) | + lectures non répétables, mises à jour perdues (PostgreSQL) |
| `SERIALIZABLE` | toutes : le résultat est celui d'une exécution l'une après l'autre |

Plus le niveau est strict, plus il y a de transactions à **réessayer** : le code doit gérer
les erreurs de sérialisation (code SQL `40001`) par une nouvelle tentative.

## L'ORM et le problème N+1

Un ORM (Hibernate, JPA, Exposed, SQLAlchemy, Django) charge les relations **à la demande** :

```kotlin
val acheteurs = repo.findByPays("BE")                  // 1 requête
acheteurs.forEach { println(it.commandes.size) }       // + 1 requête par acheteur
```

Mesure réelle, pour 100 acheteurs et leurs 1 000 commandes, base sur la même machine :

| Approche | Requêtes | Durée |
|---|---|---|
| N+1 | 101 | 5,8 ms |
| une seule requête | 1 | 0,3 ms |

Vingt fois plus lent **sans réseau**. Avec un aller-retour réseau de 0,5 ms vers la base,
les 101 requêtes coûtent au moins 50 ms de plus, et 1 000 acheteurs une demi-seconde. Remèdes :
`join fetch` ou *entity graph* (JPA), chargement groupé (`@BatchSize`, `select … where id in
(…)`), ou une requête écrite à la main pour les écrans critiques. Pour le détecter : compter
les requêtes par requête HTTP dans les tests, ou activer leur journalisation.

## À retenir

- Index : 22,5 ms → 0,43 ms mesurés ; coût en place et en écritures ; pas d'index avec une fonction sur la colonne ni sur une condition peu sélective.
- `explain analyze` : seq scan, lignes estimées contre réelles, type de jointure ; attention, il exécute.
- ACID ; transactions courtes, sans appel externe.
- Mise à jour perdue : écriture atomique, `for update` ou isolation plus stricte avec réessai ; write skew : `SERIALIZABLE`.
- N+1 : 101 requêtes au lieu d'une ; `join fetch`, chargement groupé, et compter les requêtes.
