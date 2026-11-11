---
id: system-design.n3.distribuer
title: Messages, idempotence, partitionnement et CAP
skills: [messages, idempotence, sharding, cap]
duration_min: 21
---

# Messages, idempotence, partitionnement et CAP

## Les files de messages

Quand un service en appelle un autre de façon synchrone, il dépend de sa disponibilité et
de sa vitesse. Une **file de messages** (Kafka, RabbitMQ, SQS, Redis Streams) découple les
deux : le producteur dépose un message et continue ; le consommateur le traite à son rythme.

Ce que ça apporte :

- **absorber les pics** : 10 000 commandes en une minute s'accumulent dans la file, la
  facturation les traite à 500 par minute sans tomber ;
- **isoler les pannes** : si l'envoi d'e-mails est en panne, les commandes continuent ;
- **diffuser** un événement à plusieurs consommateurs (facturation, stock, analytique).

Deux modèles :

| | File (*queue*) | Publication / abonnement (*pub/sub*, journal) |
|---|---|---|
| Un message est lu par… | un seul consommateur parmi plusieurs | chaque groupe d'abonnés |
| Exemples | SQS, RabbitMQ | Kafka, Redis Streams, SNS |
| Usage | répartir du travail | diffuser des événements, et les relire plus tard (Kafka les conserve) |

### Les garanties de livraison

- **Au plus une fois** : on acquitte avant de traiter ; un plantage perd le message.
- **Au moins une fois** (le cas réel le plus courant) : on acquitte **après** le
  traitement ; un plantage entre les deux fait **relivrer** le message.
- **Exactement une fois** : impossible en général sur un réseau ; on l'**obtient en
  pratique** avec « au moins une fois » + un consommateur **idempotent**.

Démonstration réelle avec Redis Streams. Le consommateur A lit la commande 42 et plante
avant d'acquitter (`XACK`) :

```
XPENDING → 1 message en attente, détenu par A
XAUTOCLAIM (B reprend les messages en attente depuis plus de 1 s)
         → commande 42 montant 4990     ← le même message, relivré à B
XACK par B → 0 en attente
```

Si A avait déjà débité le client avant de planter, B le débite une seconde fois… sauf si
le traitement est idempotent.

Autres notions : la **file des messages morts** (*dead letter queue*) reçoit les messages
qui échouent après N tentatives, pour ne pas bloquer la file ; l'**ordre** n'est garanti
que dans une **partition** (Kafka) ou un groupe de messages (SQS FIFO), donc on choisit la
clé de partition (l'identifiant de commande) pour que les messages d'une même commande
restent dans l'ordre.

## L'idempotence

Une opération est **idempotente** si l'exécuter plusieurs fois a le même effet qu'une seule
fois. `mettre le solde à 100` l'est ; `ajouter 10 au solde` ne l'est pas.

Sur un réseau, tout est rejoué un jour : le client réessaie après un délai d'expiration
(alors que la première requête avait réussi), le message est relivré, l'utilisateur
double-clique. La technique standard est la **clé d'idempotence** : le client génère un
identifiant unique par opération, et le serveur refuse de l'exécuter deux fois.
Démonstration réelle avec PostgreSQL :

```sql
create table paiements(cle_idempotence text primary key, montant int);

insert into paiements(cle_idempotence, montant) values ('cmd-42-paiement', 4990)
on conflict (cle_idempotence) do nothing returning 'débité';
```

Trois essais de la même requête : seul le premier renvoie `débité` ; à la fin, `1 ligne,
total 4990`. La même chose sans clé, trois fois : `3 lignes, total 14970`. Le client a été
débité trois fois.

Stripe, PayPal et la plupart des API de paiement exigent une clé d'idempotence
(`Idempotency-Key`). Côté consommateur de messages, on enregistre les identifiants de
messages traités **dans la même transaction** que leur effet.

## Le partitionnement (sharding)

Quand une base ne suffit plus (en taille ou en écritures par seconde), on répartit les
données sur plusieurs machines : chaque **partition** (*shard*) contient une partie des
clés.

| Stratégie | Principe | Risque |
|---|---|---|
| Par **plage** (A-F, G-M…) | les clés voisines restent ensemble ; requêtes par plage faciles | points chauds (toutes les nouvelles commandes sur la même partition si la clé est une date) |
| Par **hachage** | répartition uniforme | les requêtes par plage touchent toutes les partitions |
| Par **annuaire** | une table indique où est chaque clé | l'annuaire devient un point central |

Le piège du `hash(clé) mod N` : quand on ajoute un serveur, presque **toutes** les clés
changent de place. Simulation réelle sur 100 000 clés, en passant de 10 à 11 serveurs :

| Méthode | Clés déplacées |
|---|---|
| `hash(clé) mod N` | **91,0 %** |
| hachage cohérent (100 nœuds virtuels par serveur) | **9,5 %** |

Le **hachage cohérent** place serveurs et clés sur un anneau ; une clé appartient au
premier serveur rencontré dans le sens des aiguilles d'une montre. Ajouter un serveur ne
déplace que les clés de son segment, environ `1/N`. Les **nœuds virtuels** (chaque serveur
occupe de nombreux points de l'anneau) équilibrent la charge : sur 10 serveurs, entre 8 905
et 11 047 clés chacun avec 100 nœuds virtuels, contre 92 à 26 830 sans. DynamoDB, Cassandra,
les clients Memcached et de nombreux répartiteurs l'utilisent.

Le partitionnement a un coût : les requêtes qui traversent plusieurs partitions sont lentes,
les transactions entre partitions sont difficiles, et changer de clé de partition revient à
tout migrer. Avant de partitionner : index, réplicas de lecture, cache, archivage, une
machine plus grosse.

## Le théorème CAP et la cohérence éventuelle

Le **théorème CAP** (Brewer, 2000) : lors d'une **partition réseau** (P), un système
distribué doit choisir entre la **cohérence** (C : toute lecture voit la dernière écriture)
et la **disponibilité** (A : toute requête reçoit une réponse).

Comme les partitions réseau arrivent toujours un jour, le vrai choix est :

- **CP** : en cas de coupure, refuser de répondre plutôt que de répondre faux (une banque,
  un stock de billets, la configuration de Kubernetes dans etcd) ;
- **AP** : répondre quand même, avec une donnée peut-être ancienne, et réconcilier plus
  tard (un panier, un compteur de vues, un fil d'actualité).

**PACELC** complète : s'il y a Partition, choisir A ou C ; sinon (Else), choisir entre
Latence et Cohérence. Même sans panne, attendre la confirmation de plusieurs répliques coûte
du temps.

La **cohérence éventuelle** : sans nouvelle écriture, toutes les répliques finissent par
converger. C'est ce qu'on a mesuré au niveau 2 avec la réplique PostgreSQL asynchrone.

Les systèmes sans leader règlent ce curseur avec des **quorums** : N répliques, une
écriture confirmée par W, une lecture interrogeant R. Si `R + W > N`, chaque lecture
touche au moins une réplique à jour (N = 3, W = 2, R = 2 est un réglage classique).

## À retenir

- Une file découple, absorbe les pics, isole les pannes ; « au moins une fois » est la norme, avec une file des messages morts.
- Exactement une fois = au moins une fois + idempotence ; clé d'idempotence (démontré : 1 débit au lieu de 3).
- Partitionner en dernier recours ; hachage cohérent : 9,5 % de clés déplacées au lieu de 91 %.
- CAP : en cas de coupure, cohérence ou disponibilité ; quorums `R + W > N`.
