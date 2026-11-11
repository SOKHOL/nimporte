---
id: systemes-distribues.n2.le-temps
title: Horloges physiques, horloges logiques et ordre causal
skills: [horloges-physiques, horloges-logiques, ordre-causal]
duration_min: 20
---

# Horloges physiques, horloges logiques et ordre causal

## Les horloges physiques

Chaque machine a sa propre horloge, un quartz qui **dérive** (typiquement de 10 à 100 µs par
seconde, soit quelques secondes par jour). Le protocole **NTP** la recale régulièrement sur des
serveurs de référence, avec une précision de l'ordre de la milliseconde sur un réseau local, et
bien pire sur Internet. Deux horloges de machines différentes ne sont donc **jamais**
exactement synchronisées, et une horloge peut **reculer** quand NTP la corrige.

Il y a deux horloges dans chaque système, et il faut savoir laquelle utiliser. Réel (Linux,
Python) :

```
time      ajustable=True  résolution=1e-09 source=clock_gettime(CLOCK_REALTIME)
monotonic ajustable=False résolution=1e-09 source=clock_gettime(CLOCK_MONOTONIC)
```

| Horloge | Garantie | Usage |
|---|---|---|
| **murale** (`time.time()`, `System.currentTimeMillis()`, `Instant.now()`) | proche de l'heure réelle, mais peut sauter en avant ou en arrière | afficher et enregistrer une date |
| **monotone** (`time.monotonic()`, `System.nanoTime()`) | ne recule jamais, sans lien avec l'heure réelle, propre à la machine | mesurer une **durée**, un délai d'attente |

Mesurer une durée avec l'horloge murale, c'est risquer une durée négative le jour d'une
correction NTP (ou d'une seconde intercalaire : le 30 juin 2012, la seconde intercalaire a fait
planter des serveurs Linux, Reddit et Mozilla compris).

Le piège le plus courant est la stratégie **« la dernière écriture gagne »** (*last write
wins*, LWW) fondée sur l'horloge murale, utilisée par défaut par Cassandra et de nombreux
caches. Simulation réelle : l'horloge de B retarde de 50 ms, et B écrit 20 ms **après** A :

```
écritures : (1000, 'adresse = 3 rue des Lilas') (970, 'adresse = 8 avenue Foch')
dernière écriture gagnante garde : adresse = 3 rue des Lilas
```

La nouvelle adresse est **perdue en silence**, sans erreur ni conflit signalé. Une horloge
physique ne peut pas ordonner des événements plus proches que l'écart entre les horloges.

Google Spanner contourne le problème avec **TrueTime** : des horloges atomiques et GPS dans
chaque centre de données, et une API qui renvoie un **intervalle** `[au plus tôt, au plus tard]`
(quelques millisecondes). Avant de valider une transaction, Spanner **attend** que l'intervalle
soit passé (*commit wait*) : l'ordre des horodatages devient alors l'ordre réel.

## Horloges de Lamport et horloges vectorielles

Leslie Lamport (1978) a remplacé la question « quelle heure était-il ? » par « qu'est-ce qui a
pu **influencer** quoi ? ». La relation **arrivé-avant** (→) : a → b si a et b sont sur le même
processus dans cet ordre, ou si a est l'envoi d'un message et b sa réception, ou par
transitivité. Si ni a → b ni b → a, les événements sont **concurrents** : aucun n'a pu
influencer l'autre.

**Horloge de Lamport** : un compteur par processus, incrémenté à chaque événement ; chaque
message emporte le compteur, et le récepteur prend `max(le sien, celui reçu) + 1`.
**Horloge vectorielle** : un compteur **par processus**, fusionné composante par composante.
Simulation réelle avec trois processus :

```
A: A publie un message          Lamport=1  vecteur=[1, 0, 0]
B: B reçoit le message          Lamport=2  vecteur=[1, 1, 0]
B: B répond                     Lamport=3  vecteur=[1, 2, 0]
C: C publie autre chose         Lamport=1  vecteur=[0, 0, 1]
A: A reçoit la réponse          Lamport=4  vecteur=[2, 2, 0]

réponse de B vs publication de C : concurrents
message de A vs réponse reçue    : a → b
```

La différence est essentielle :

- Lamport garantit : si a → b, alors L(a) < L(b). Mais **pas la réciproque** : la publication de
  C a L = 1 et la réponse de B L = 3, et pourtant elles sont concurrentes. Lamport donne un ordre
  **total** cohérent avec la causalité (en départageant par le nom du processus), utile pour que
  tout le monde se mette d'accord sur un ordre, mais il ne détecte pas la concurrence ;
- le vecteur la détecte : a → b si et seulement si chaque composante de V(a) est ≤ celle de
  V(b) (et V(a) ≠ V(b)). `[1, 2, 0]` et `[0, 0, 1]` sont incomparables : concurrents.

Le prix des vecteurs : une composante par participant. Les **versions vectorielles** de Dynamo
et de Riak servent exactement à cela : détecter que deux écritures sont concurrentes pour
garder **les deux** (des « frères ») au lieu d'en perdre une comme LWW.

## Ordre causal et concurrence

Le réseau peut livrer les messages dans le désordre. Dans une discussion de groupe, voir la
réponse avant la question est absurde. La **livraison causale** retient un message tant que
ceux qui l'ont causé ne sont pas arrivés : on livre le message de P quand c'est le suivant
attendu de P, et que tout ce qu'il a vu des autres a déjà été livré. Simulation réelle chez C :

```
ordre d'arrivée : ["B : oui, d'accord !", 'A : on déjeune à midi ?']
ordre causal    : ['A : on déjeune à midi ?', "B : oui, d'accord !"]
```

Les niveaux de garantie d'ordre, du plus faible au plus fort :

| Ordre | Garantie | Coût |
|---|---|---|
| aucun | rien | gratuit |
| **FIFO** | les messages d'un même émetteur arrivent dans l'ordre d'envoi (TCP, une partition Kafka) | faible |
| **causal** | si a → b, tout le monde voit a avant b ; les concurrents dans n'importe quel ordre | métadonnées (vecteurs) |
| **total** | tout le monde voit **tous** les messages dans le même ordre | un consensus ou un séquenceur unique (niveau 4) |

L'ordre causal est le plus fort qu'on puisse obtenir **sans coordination** et en restant
disponible pendant une partition réseau. C'est pourquoi il est au cœur des CRDT (niveau 5).

## À retenir

- Horloge murale (date, peut reculer) contre monotone (durées) ; « la dernière écriture gagne » sur l'horloge murale perd des écritures en silence (démontré avec 50 ms de décalage).
- Lamport : a → b implique L(a) < L(b), sans la réciproque ; les vecteurs détectent la concurrence ([1, 2, 0] contre [0, 0, 1]).
- Livraison causale : la réponse attend la question ; FIFO < causal < total, et le total exige un consensus.
