---
id: donnees.n4.le-temps-reel
title: Streaming, fenêtres temporelles, livraison et capture des changements
skills: [streaming, fenetres, livraison, cdc]
duration_min: 22
---

# Streaming, fenêtres temporelles, livraison et capture des changements

## Le streaming

Un traitement par lots répond « que s'est-il passé hier ? ». Le **streaming** traite les
événements **en continu**, à mesure qu'ils arrivent : détection de fraude, tableaux de bord en
direct, alertes, recommandations. Deux briques :

**Un journal d'événements distribué : Kafka.** Un **topic** est un journal où l'on **ajoute** des
messages à la fin, conservés pendant une durée choisie (jours, semaines, ou pour toujours). Il est
découpé en **partitions** réparties sur les machines ; chaque message d'une partition a un
numéro, l'**offset**. Les points essentiels :

- l'ordre n'est garanti **qu'à l'intérieur d'une partition** ; les messages de même **clé** (le
  même client, la même commande) vont toujours dans la même partition, donc restent ordonnés ;
- un **groupe de consommateurs** se partage les partitions (une partition n'est lue que par un
  membre du groupe) : le parallélisme maximal est le nombre de partitions ;
- chaque consommateur **mémorise son offset** : il peut s'arrêter, reprendre, ou **relire**
  l'historique depuis le début. Un même topic sert plusieurs consommateurs indépendants
  (entrepôt, moteur de recherche, cache), chacun à son rythme.

**Un moteur de traitement : Flink** (ou Kafka Streams, Spark Structured Streaming). Il lit des
topics, joint, agrège, détecte des motifs, et maintient un **état** (des compteurs, des
fenêtres ouvertes) sauvegardé régulièrement par des **points de contrôle** (*checkpoints*) pour
reprendre après une panne sans perdre ni compter deux fois.

## Les fenêtres temporelles

Agréger un flux infini exige de le découper en **fenêtres** : **fixes** (*tumbling* : chaque
minute), **glissantes** (*sliding* : les 10 dernières minutes, recalculées chaque minute), ou de
**session** (une rafale d'activité séparée par 30 minutes d'inactivité).

Mais **quelle heure** ? Celle où l'événement s'est **produit** (temps d'événement) ou celle où il
est **arrivé** (temps de traitement) ? Elles diffèrent : réseau lent, téléphone hors ligne,
reprise après panne. Simulation réelle : onze clics, dont deux arrivent très en retard (un
téléphone passé hors ligne), comptés par fenêtres d'une minute :

```
temps de traitement : {0: 4, 60: 5, 120: 2}
  t=101s : filigrane 70s → émission de [0, 60[ = 5
  t=170s : événement de 55s en retard, fenêtre [0, 60[ déjà émise → écarté
temps d'événement   : {0: 5, 60: 4, 120: 1}
réalité             : {0: 6, 60: 4, 120: 1}
```

- En temps de traitement, les comptes sont **faux** : les clics de la première minute arrivés
  tard sont attribués aux minutes suivantes. Simple et immédiat, mais le résultat dépend des
  aléas du réseau et change si l'on rejoue le flux.
- En temps d'événement, chaque clic est rangé dans **sa** minute. Mais quand émettre la fenêtre
  [0, 60[, puisqu'un retardataire peut toujours arriver ? Le **filigrane** (*watermark*) est une
  estimation : « je ne m'attends plus à des événements antérieurs à X » (ici, le plus récent vu
  moins 30 secondes). Quand le filigrane dépasse la fin d'une fenêtre, on l'émet.
- L'événement de 55 s, arrivé à 170 s, est **trop tard** : la fenêtre est déjà émise, il est
  écarté (5 au lieu de 6). On peut aussi l'envoyer dans une sortie séparée, ou **réémettre** une
  correction.

C'est un compromis à régler : un filigrane **prudent** (attendre longtemps) donne des résultats
justes mais tardifs ; un filigrane **agressif** donne des résultats rapides mais incomplets.

## Les sémantiques de livraison

Le parcours Systèmes distribués (niveau 4) l'a démontré : « exactement une fois » au sens de la
livraison est impossible ; ce qu'on obtient, c'est un **effet** exactement une fois, avec une
livraison **au moins une fois** et un traitement **idempotent** (121 livraisons pour 100
messages, mais 1000 points et non 1210 grâce à la déduplication).

Dans un moteur de streaming, cela prend la forme de **points de contrôle cohérents** : Flink
sauvegarde ensemble l'état des opérateurs **et** les offsets lus. Après une panne, il repart du
dernier point de contrôle : l'état et la position de lecture sont cohérents, donc aucun
événement n'est compté deux fois **dans l'état**. Pour les **sorties** vers l'extérieur, il faut
soit un puits transactionnel (écrire dans Kafka avec des transactions, validées au point de
contrôle), soit un puits idempotent (un `upsert` sur une clé). Kafka Streams fait de même avec
`processing.guarantee=exactly_once_v2`.

## La capture des changements (CDC)

Comment alimenter l'entrepôt ou un flux à partir de la base de production, sans la surcharger
de requêtes et sans rien manquer ? Lire **son journal**. Toute base écrit ses modifications dans
un journal (le WAL de PostgreSQL, le binlog de MySQL) ; la **capture des changements** (*Change
Data Capture*) le décode en événements. Réel, avec PostgreSQL 16 en `wal_level = logical` et le
module de démonstration `test_decoding` :

```sql
insert into produits values (1, 'Clavier', 49.90), (2, 'Souris', 19.90);
update produits set prix = 44.90 where id = 1;
begin; delete from produits where id = 2; insert into produits values (3, 'Écran', 189.00); commit;
begin; update produits set prix = 0 where id = 3; rollback;
```

```
BEGIN 28400
table public.produits: INSERT: id[integer]:1 nom[text]:'Clavier' prix[numeric]:49.90
table public.produits: INSERT: id[integer]:2 nom[text]:'Souris' prix[numeric]:19.90
COMMIT 28400
BEGIN 28401
table public.produits: UPDATE: id[integer]:1 nom[text]:'Clavier' prix[numeric]:44.90
COMMIT 28401
BEGIN 28402
table public.produits: DELETE: id[integer]:2
table public.produits: INSERT: id[integer]:3 nom[text]:'Écran' prix[numeric]:189.00
COMMIT 28402
```

Chaque modification **validée** apparaît, dans l'ordre, regroupée par transaction ; la
transaction annulée n'apparaît **pas**. Comparé à une requête périodique
(`where modifie_le > :derniere_fois`), le CDC voit les **suppressions**, ne rate pas les
modifications intermédiaires, et ne charge pas la base. En production, on utilise **Debezium**,
qui publie ces événements dans Kafka, un topic par table.

Le piège réel du CDC : la base garde son journal tant que le **slot de réplication** ne l'a pas
consommé. Un consommateur arrêté (ou oublié), et le disque se remplit. Mesure réelle après
300 000 insertions avec un slot inactif :

```
 slot_name  | active | wal_retenu
------------+--------+------------
 cdc_oublie | f      | 77 MB
```

À surveiller (alerte sur la taille retenue, `max_slot_wal_keep_size` pour plafonner) : c'est
une cause classique de base de production arrêtée faute de disque.

Le CDC permet aussi le **patron outbox** (parcours Architecture) : l'application écrit dans la
même transaction ses données **et** un événement dans une table `outbox`, que le CDC publie. Plus
de « double écriture » base + Kafka qui peut échouer à moitié.

## À retenir

- Kafka : topics partitionnés, ordre par partition donc par clé, offsets mémorisés et relecture possible ; Flink garde un état sauvegardé par points de contrôle.
- Fenêtres en temps d'événement avec filigrane (réel : 5 au lieu de 6, un retardataire écarté) contre temps de traitement (comptes faussés par les retards).
- Effet exactement une fois = points de contrôle cohérents + puits transactionnel ou idempotent ; le CDC lit le journal de la base (réel), mais un slot oublié retient le journal (77 Mo mesurés).
