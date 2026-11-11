---
id: system-design.n5.systemes-distribues-avances
title: Consensus, horloges, sagas et multi-région
skills: [consensus, horloges, sagas, multi-region]
duration_min: 22
---

# Consensus, horloges, sagas et multi-région

## Le consensus

Plusieurs machines doivent se mettre d'accord sur une valeur (qui est le leader ? quelle est
la configuration ? cette écriture est-elle validée ?), alors que des machines tombent et que
des messages se perdent. C'est le problème du **consensus**.

**Raft** (2014), conçu pour être compréhensible, est utilisé par etcd (le cerveau de
Kubernetes), Consul, CockroachDB, Kafka (KRaft) :

1. **Élection** : chaque nœud est suiveur. Sans nouvelles du leader pendant un délai
   aléatoire (150 à 300 ms), il devient candidat, incrémente le **mandat** (*term*) et
   demande les votes. Celui qui obtient la **majorité** devient leader.
2. **Réplication** : le leader ajoute chaque écriture à son journal et l'envoie aux
   suiveurs. Une entrée est **validée** quand une majorité l'a enregistrée.
3. **Sécurité** : un nœud ne vote que pour un candidat dont le journal est au moins aussi à
   jour que le sien ; un leader élu a donc toutes les entrées validées.

Tout repose sur la **majorité** (le quorum) : deux majorités se chevauchent toujours, donc
deux leaders ne peuvent pas valider des choses contradictoires.

| Nœuds | Majorité | Pannes tolérées |
|---|---|---|
| 3 | 2 | 1 |
| 4 | 3 | 1 |
| 5 | 3 | 2 |
| 7 | 4 | 3 |

Un nombre **pair** n'apporte rien (4 nœuds tolèrent autant de pannes que 3) et 5 est le
réglage courant en production. Lors d'une coupure réseau, le côté minoritaire **ne peut plus
écrire** : c'est un système CP, qui évite le *split brain* (deux leaders qui acceptent des
écritures chacun de leur côté).

**Paxos** (Lamport, 1989) résout le même problème, de façon réputée difficile à comprendre
et à implémenter. Il est à la base de Chubby (Google) et de Spanner.

En pratique, on n'implémente pas le consensus soi-même : on s'appuie sur etcd, ZooKeeper,
Consul, ou une base qui l'intègre.

## Les horloges et l'ordre des événements

Sur une seule machine, l'heure dit dans quel ordre les choses se sont passées. Entre
machines, **non** : les horloges dérivent, NTP les corrige par sauts (parfois en arrière),
et la synchronisation n'est précise qu'à quelques millisecondes près, parfois beaucoup plus.
Utiliser l'heure murale pour décider « la dernière écriture gagne » (*last write wins*) peut
faire gagner l'écriture la plus **ancienne** si son serveur avance de 50 ms.

Deux règles : pour mesurer une **durée**, une horloge monotone (`System.nanoTime()`,
`time.monotonic()`) ; pour **ordonner** des événements entre machines, des horloges logiques.

**Les horloges de Lamport** (1978) : chaque processus tient un compteur.

- À chaque événement local ou envoi : `horloge += 1`, et le message porte la valeur.
- À la réception : `horloge = max(horloge, estampille reçue) + 1`.

```kotlin
a.evenementLocal(); a.evenementLocal()
val m = a.envoyer()           // A : 3, le message porte 3
b.evenementLocal()            // B : 1
val reception = b.recevoir(m) // B : max(1, 3) + 1 = 4
// Sortie réelle : A envoie avec 3, B reçoit à 4 ; A=3 B=4
```

Garantie : si un événement en a **causé** un autre, son estampille est plus petite. La
réciproque est fausse : deux estampilles ordonnées ne prouvent pas un lien de cause.

**Les horloges vectorielles** gardent un compteur par processus et détectent les événements
**concurrents** (aucun n'a pu voir l'autre) : c'est ce qui permet de signaler un conflit
plutôt que d'écraser silencieusement une écriture (Dynamo, Riak).

**Les horloges hybrides** (HLC) combinent heure physique et compteur logique (CockroachDB,
YugabyteDB). Google Spanner va plus loin avec **TrueTime** : des horloges atomiques et GPS
donnent un intervalle d'incertitude de quelques millisecondes, et chaque validation
**attend** la fin de l'intervalle.

## Les transactions distribuées et les sagas

Une commande doit réserver le stock, débiter le client et réserver un transporteur, dans
trois services qui ont chacun leur base. Pas de transaction ACID commune possible.

Le **commit à deux phases** (2PC) existe : un coordinateur demande à chacun « prêt ? », puis
« validez ». Mais si le coordinateur tombe entre les deux, les participants restent
**bloqués** avec des verrous. Il est rarement utilisé entre microservices.

La **saga** découpe l'opération en transactions locales, chacune avec une **compensation**
qui l'annule sémantiquement (rembourser, libérer le stock). En cas d'échec, on exécute les
compensations des étapes déjà faites, **dans l'ordre inverse**. Sortie réelle :

```
ok : réserver le stock
ok : débiter le client
échec : aucun transporteur disponible
annuler : débiter le client
annuler : réserver le stock
```

Deux styles :

| | Orchestration | Chorégraphie |
|---|---|---|
| Principe | un orchestrateur appelle chaque étape et décide de la suite | chaque service réagit aux événements des autres |
| Avantage | le déroulé est lisible à un seul endroit | aucun point central |
| Défaut | l'orchestrateur concentre la logique | le déroulé est éparpillé, difficile à suivre |
| Outils | Temporal, AWS Step Functions, Camunda | événements dans Kafka |

Pièges : une compensation peut échouer aussi (on la réessaie : elle doit être
**idempotente**) ; pendant la saga, d'autres voient un état intermédiaire (absence
d'isolation) ; certaines actions ne se compensent pas (un e-mail envoyé), on les met donc
**en dernier**.

Le **pattern outbox** évite de perdre un événement : on écrit le changement **et**
l'événement à publier dans la même transaction locale (une table `outbox`), puis un relais
publie la table dans la file. Plus de cas où la base est modifiée mais l'événement perdu.

## Le multi-région

Déployer dans plusieurs régions sert à survivre à la perte d'une région entière et à
rapprocher le service des utilisateurs. Une donnée de départ : un aller-retour Paris ↔
New York prend 70 à 80 ms, Paris ↔ Sydney 250 à 300 ms.

| Modèle | Principe | Compromis |
|---|---|---|
| **Actif-passif** | une région sert tout, l'autre reçoit les données et attend | simple ; bascule de quelques minutes ; données récentes perdues si la réplication est asynchrone |
| **Actif-actif, données partitionnées** | chaque utilisateur a une région « maison » | écritures locales et rapides ; un utilisateur en voyage passe par sa région |
| **Actif-actif, écritures partout** | toutes les régions acceptent toutes les écritures | latence minimale ; conflits à résoudre (dernière écriture, CRDT, logique métier) |
| **Consensus entre régions** | chaque écriture est validée par une majorité de régions | cohérence forte ; chaque écriture paie au moins un aller-retour entre régions |

Deux indicateurs fixent le choix : le **RPO** (combien de données on accepte de perdre) et
le **RTO** (combien de temps pour repartir). Un RPO de zéro entre régions impose une
réplication synchrone, donc des écritures plus lentes.

Ne pas oublier : la **résidence des données** (le RGPD et d'autres lois imposent parfois
de garder des données dans une région), le coût du trafic entre régions, et surtout les
**exercices de bascule** : une bascule jamais testée ne fonctionne pas le jour où on en a
besoin.

## À retenir

- Consensus (Raft) : élection et réplication à la majorité ; 5 nœuds tolèrent 2 pannes ; la minorité n'écrit plus.
- L'heure murale n'ordonne pas les événements entre machines ; Lamport et horloges vectorielles le font.
- Saga : transactions locales + compensations idempotentes, en ordre inverse ; outbox pour ne perdre aucun événement.
- Multi-région : actif-passif, actif-actif partitionné ou non ; RPO, RTO, et exercices de bascule.
