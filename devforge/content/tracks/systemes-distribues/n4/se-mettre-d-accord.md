---
id: systemes-distribues.n4.se-mettre-d-accord
title: Consensus, transactions distribuées et « exactement une fois »
skills: [consensus, transactions-distribuees, exactement-une-fois]
duration_min: 24
---

# Consensus, transactions distribuées et « exactement une fois »

## Consensus et élection de leader

Le **consensus** : plusieurs nœuds doivent se mettre d'accord sur une valeur (qui est le
leader, quelle est la prochaine entrée du journal), même si certains tombent. C'est le cœur
d'etcd (donc de Kubernetes), de ZooKeeper, de Consul, de CockroachDB et des contrôleurs de Kafka
(KRaft). Les deux algorithmes de référence sont **Paxos** (Lamport, 1989-1998), réputé difficile,
et **Raft** (Ongaro et Ousterhout, 2014), conçu pour être compréhensible.

L'élection de Raft, en bref :

- chaque nœud est **suiveur**, **candidat** ou **leader** ; le temps est découpé en **mandats**
  (*terms*) numérotés ;
- un suiveur qui ne reçoit plus de battement de cœur du leader pendant un délai **aléatoire**
  devient candidat, incrémente le mandat, vote pour lui et demande les votes des autres ;
- chacun vote **au plus une fois par mandat** ; un candidat qui obtient la **majorité** devient
  leader ; tout message portant un mandat plus récent fait redescendre au rang de suiveur.

Simulation réelle de cinq nœuds (délais aléatoires de 150 à 300 ms, messages de 5 à 15 ms) :

```
t= 166 ms  nœud 2 candidat, mandat 1
t= 180 ms  nœud 4 candidat, mandat 1
t= 184 ms  nœud 2 ÉLU leader du mandat 1 (3 voix sur 5)
--- le nœud 2 tombe à t=1000 ms
t=1180 ms  nœud 3 candidat, mandat 2
t=1187 ms  nœud 0 candidat, mandat 2
t=1205 ms  nœud 3 ÉLU leader du mandat 2 (3 voix sur 5)
```

Deux candidats en même temps, et pourtant un seul gagnant : il faut 3 voix sur 5, et chacun ne
vote qu'une fois. Pourquoi des délais **aléatoires** ? Même simulation, 200 essais :

```
délais fixes 150 ms          : leader élu dans 0/200 essais
délais aléatoires 150-300 ms : leader élu dans 200/200 essais, en 190 ms (médiane)
```

Avec des délais identiques, tous deviennent candidats ensemble, votent pour eux-mêmes, et
recommencent ensemble, indéfiniment (*split vote*). L'aléa casse la symétrie.

Pendant une **partition** {2, 0} | {1, 3, 4}, réel : l'ancien leader 2 (mandat 1) se croit
toujours leader, et le côté majoritaire élit le nœud 3 (mandat 2). Deux « leaders » coexistent,
mais seul le 3 peut **valider** quoi que ce soit, car une entrée n'est validée que lorsqu'une
majorité l'a enregistrée, et deux majorités se croisent toujours (niveau 3). C'est ce qui rend
Raft sûr ; le niveau 5 montre ce qui arrive aux systèmes qui ne font pas ce raisonnement.

Le leader réplique ensuite son **journal** : une entrée est validée quand une majorité l'a
écrite, et un nœud ne vote que pour un candidat dont le journal est au moins aussi à jour que le
sien (règle omise dans la simulation). Le résultat : une **machine à états répliquée**, tous les
nœuds appliquent les mêmes commandes dans le même ordre (l'ordre total du niveau 2).

La limite théorique : le résultat **FLP** (Fischer, Lynch et Paterson, 1985) prouve qu'aucun
algorithme déterministe ne peut garantir de terminer le consensus dans un système **asynchrone**
où un seul nœud peut tomber. Raft contourne le résultat avec des délais : il est toujours **sûr**
(jamais deux valeurs validées), mais il ne termine que lorsque le réseau se comporte assez bien.
En pratique : 3 ou 5 nœuds (tolérant 1 ou 2 pannes), jamais 2 ni 4 (4 tolère autant de pannes
que 3).

## Validation en deux phases et sagas

Une transaction qui touche **deux bases** (débiter la banque A, créditer la banque B) doit être
atomique : les deux ou aucune. La **validation en deux phases** (*two-phase commit*, 2PC) :

1. le coordinateur demande à chaque participant de **préparer** : tout vérifier, tout écrire de
   façon durable, garder les verrous, et promettre de pouvoir valider ;
2. si tous ont répondu « prêt », il décide de **valider** et le leur dit ; sinon il annule.

PostgreSQL implémente le côté participant (`PREPARE TRANSACTION`). Expérience réelle : un virement
de 30 € d'Alice (base `banque_a`) vers Bob (base `banque_b`), et le coordinateur tombe en panne
entre les deux phases :

```
phase 1 : les deux banques ont répondu PRÉPARÉ
le coordinateur tombe en panne avant la phase 2
transactions préparées : [('virement-42-a', 21:09:34), ('virement-42-b', 21:09:34)]
autre client : ERROR:  canceling statement due to lock timeout
lecture du solde d'alice : 100
--- après redémarrage de PostgreSQL
virement-42-a
virement-42-b
```

Le participant **ne peut rien décider seul** : il a promis de valider si on le lui demande,
donc il ne peut pas annuler, et il ne sait pas si l'autre a dit oui, donc il ne peut pas valider.
Il garde ses **verrous** (un autre client attend puis échoue), même **après un redémarrage**.
C'est le défaut fondamental de 2PC : il est **bloquant** si le coordinateur disparaît. Le
coordinateur, revenu, termine le travail (`COMMIT PREPARED` : réel, Alice 70, Bob 30). En
production, une transaction préparée oubliée bloque des lignes et empêche le nettoyage
(VACUUM) : il faut la surveiller dans `pg_prepared_xacts`.

Entre services, on préfère souvent les **sagas** : une suite de transactions **locales**, chacune
avec une **compensation** qui l'annule sémantiquement :

```
réserver le stock   → compensation : libérer le stock
débiter la carte    → compensation : rembourser
réserver le livreur → échec : rembourser, puis libérer le stock (dans l'ordre inverse)
```

Pas de verrous entre les étapes, donc pas de blocage, mais **pas d'isolation** : un autre client
peut voir l'état intermédiaire (stock réservé, puis libéré). Les compensations doivent être
idempotentes et ne jamais échouer définitivement. Orchestrées par un coordinateur (plus lisible,
outils comme Temporal) ou chorégraphiées par des événements (plus découplé, plus difficile à
suivre).

## « Exactement une fois »

Une file de messages peut promettre au plus une fois (on peut perdre) ou **au moins une fois**
(on peut dupliquer). « Exactement une fois » au sens de la **livraison** est impossible : le
consommateur peut toujours tomber après avoir traité un message et avant d'avoir acquitté. Ce
qu'on peut obtenir, c'est un **effet** exactement une fois : livraison au moins une fois, plus
traitement **idempotent**.

Expérience réelle avec PostgreSQL : 100 messages « +10 points », et un consommateur qui tombe
avant l'acquittement dans 20 % des cas, ce qui provoque une relivraison :

```
sans déduplication : 121 livraisons pour 100 messages, total = 1210 points (attendu 1000)
avec déduplication : 121 livraisons pour 100 messages, total = 1000 points (attendu 1000)
```

La déduplication : une table `messages_traites (id primary key)`, et dans **la même
transaction** que l'effet, `insert … on conflict do nothing` ; si la ligne existait déjà, on
ignore l'effet. Le point crucial est **la même transaction** : marquer puis appliquer dans deux
transactions séparées rouvre la fenêtre de panne entre les deux.

C'est ainsi que fonctionnent les garanties « exactly-once » de Kafka (producteur idempotent avec
numéros de séquence, plus transactions qui lient la lecture, l'écriture et le déplacement de
l'offset) : elles valent **à l'intérieur** de Kafka. Dès qu'un effet sort du système (un e-mail,
un appel d'API de paiement), il faut sa propre clé d'idempotence (niveau 1).

## À retenir

- Raft : mandats, un vote par mandat, majorité ; délais aléatoires (0/200 élections avec des délais fixes, 200/200 avec l'aléa) ; sûr toujours, vivant seulement si le réseau le permet (FLP).
- 2PC est bloquant : participants préparés, verrous gardés même après redémarrage, tant que le coordinateur n'a pas décidé ; les sagas évitent le blocage au prix de l'isolation.
- « Exactement une fois » = au moins une fois + déduplication dans la même transaction (1210 points contre 1000).
