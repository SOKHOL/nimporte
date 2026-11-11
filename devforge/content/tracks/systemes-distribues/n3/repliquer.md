---
id: systemes-distribues.n3.repliquer
title: Réplication, quorums et modèles de cohérence
skills: [replication, quorums, coherence]
duration_min: 21
---

# Réplication, quorums et modèles de cohérence

On **réplique** des données (on en garde des copies sur plusieurs machines) pour trois
raisons : survivre à la panne d'une machine, répartir les lectures, et rapprocher les données
des utilisateurs. Tout le problème est de garder les copies d'accord pendant qu'elles changent.

## Réplication avec un leader

Le schéma le plus courant (PostgreSQL, MySQL, MongoDB, Kafka) : un **leader** reçoit toutes les
écritures et envoie son journal aux **suiveurs**, qui servent des lectures. Expérience réelle
avec PostgreSQL 16 : un primaire et une réplique en flux, **sur la même machine**. On écrit sur
le primaire, puis on relit tout de suite sur la réplique, 1000 fois :

```
synchronous_commit=local        :  997 lectures périmées sur 1000, écriture moyenne 0.258 ms
synchronous_commit=on           :   12 lectures périmées sur 1000, écriture moyenne 0.543 ms
synchronous_commit=remote_apply :    0 lectures périmées sur 1000, écriture moyenne 0.588 ms
```

- **asynchrone** (`local`) : le primaire confirme sans attendre la réplique. Même sans réseau,
  la relecture immédiate est périmée **997 fois sur 1000** : la réplique a quelques centaines de
  microsecondes de retard, et c'est déjà trop. Et si le primaire meurt, les dernières écritures
  confirmées peuvent être **perdues** ;
- **synchrone** (`on`) : on attend que la réplique ait **écrit** le journal sur disque. Plus de
  perte, mais 12 lectures restent périmées : écrite n'est pas encore **appliquée** ;
- `remote_apply` : on attend que la réplique ait **appliqué** l'écriture. Zéro lecture
  périmée, pour une écriture **2,3 fois plus lente**. Entre deux centres de données, le surcoût
  serait l'aller-retour réseau, des dizaines de millisecondes. Et si la réplique tombe, les
  écritures se **bloquent**.

Avec un retard artificiel de 2 secondes (`recovery_min_apply_delay`), réel : la relecture renvoie
`ada` au lieu de `ada_lovelace`, et `pg_stat_replication` affiche `replay_lag` = 2 s. C'est le
bug classique « j'ai changé mon pseudo et l'ancien s'affiche » : l'utilisateur écrit sur le
leader puis lit sur un suiveur en retard.

Les parades, par ordre de coût : lire sur le leader ce que l'utilisateur vient de modifier
(par exemple pendant une minute après une écriture), mémoriser la position du journal de la
dernière écriture du client et attendre qu'un suiveur l'ait atteinte, ou la réplication
synchrone.

**Multi-leader** (plusieurs centres de données acceptent chacun des écritures) et **sans leader**
(Dynamo, Cassandra, Riak : le client écrit sur plusieurs répliques à la fois) donnent une
meilleure disponibilité et une latence locale, mais des **écritures concurrentes** sur la même
donnée deviennent possibles : il faut les détecter (versions vectorielles, niveau 2) et les
résoudre (dernière écriture gagne, fusion par l'application, ou CRDT au niveau 5).

## Quorums

Sans leader, avec N répliques, on considère une écriture réussie quand **W** répliques l'ont
acquittée, et une lecture interroge **R** répliques et garde la version la plus récente. Si
**W + R > N**, l'ensemble lu et l'ensemble écrit ont **au moins une réplique en commun** : la
lecture voit forcément la dernière écriture réussie. Simulation réelle, N = 3, en lisant juste
après l'écriture, avant que les répliques restantes ne la reçoivent :

```
N=3 W=1 R=1  W+R>N : False  lectures périmées :  66.5%  tolère 2 panne(s) en écriture, 2 en lecture
N=3 W=2 R=1  W+R>N : False  lectures périmées :  33.5%  tolère 1 panne(s) en écriture, 2 en lecture
N=3 W=1 R=2  W+R>N : False  lectures périmées :  34.0%  tolère 2 panne(s) en écriture, 1 en lecture
N=3 W=2 R=2  W+R>N : True   lectures périmées :   0.0%  tolère 1 panne(s) en écriture, 1 en lecture
N=3 W=3 R=1  W+R>N : True   lectures périmées :   0.0%  tolère 0 panne(s) en écriture, 2 en lecture
N=3 W=1 R=3  W+R>N : True   lectures périmées :   0.0%  tolère 2 panne(s) en écriture, 0 en lecture
```

Le choix courant est la **majorité** (W = R = 2 sur 3, ou 3 sur 5) : chaque opération tolère
une panne (deux sur 5). Les répliques en retard sont rattrapées par la **réparation à la lecture**
(le lecteur réécrit la valeur récente là où elle manque) et par une **anti-entropie** en tâche
de fond (comparaison d'arbres de Merkle).

Attention, W + R > N n'est pas une garantie absolue : des écritures concurrentes, une écriture
qui échoue après avoir atteint une seule réplique, ou un **quorum relâché** (*sloppy quorum* :
écrire sur des nœuds de secours pendant une panne) peuvent encore produire des lectures
surprenantes. Pour une vraie garantie, il faut un consensus (niveau 4).

## Modèles de cohérence

Un **modèle de cohérence** est un contrat : ce qu'un lecteur peut voir, étant données les
écritures. Du plus fort au plus faible :

| Modèle | Ce qu'il garantit | Exemples |
|---|---|---|
| **linéarisable** | tout se passe comme s'il n'y avait qu'une seule copie, et chaque opération prend effet à un instant entre son début et sa fin | etcd, ZooKeeper (écritures), Spanner, un seul PostgreSQL |
| **séquentiel** | tous voient les opérations dans le même ordre, cohérent avec l'ordre de chaque client, mais pas forcément avec le temps réel | |
| **causal** | les opérations liées causalement sont vues dans l'ordre par tous (niveau 2) | MongoDB (sessions causales) |
| **garanties de session** | lire ses propres écritures, lectures monotones (ne jamais revenir en arrière), écritures monotones | la parade au bug du pseudo |
| **à terme** (*eventual*) | si les écritures s'arrêtent, toutes les copies finissent par converger | DNS, caches, Cassandra en W = R = 1 |

Le **théorème CAP** (Brewer, 2000 ; prouvé par Gilbert et Lynch, 2002) dit qu'en cas de
**partition** réseau, un système doit choisir entre la cohérence linéarisable et la
disponibilité (que chaque nœud réponde). Sa limite : il ne parle que des partitions. **PACELC**
le complète : sinon (*Else*), on choisit entre **latence** et cohérence, et c'est ce choix qu'on
fait tous les jours, comme le montrent les 0,26 ms contre 0,59 ms mesurées plus haut.

Choisir, c'est se demander ce que coûte une lecture périmée : un compteur de vues peut être à
terme ; un solde bancaire, une réservation de la dernière place, ou un verrou, exigent la
linéarisabilité.

## À retenir

- Leader et suiveurs : en asynchrone, 997 relectures sur 1000 sont périmées même sur une seule machine ; remote_apply donne 0, pour une écriture 2,3 fois plus lente.
- Quorums : W + R > N garantit une réplique commune (0 % de lectures périmées simulées) ; majorité 2 sur 3 ; pas une garantie absolue.
- Modèles de cohérence, du linéarisable à l'à-terme ; CAP en cas de partition, PACELC le reste du temps (latence contre cohérence).
