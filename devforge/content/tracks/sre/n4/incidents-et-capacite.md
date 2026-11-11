---
id: sre.n4.incidents-et-capacite
title: Gérer un incident, post-mortems, capacité et tests de charge
skills: [gestion-incident, post-mortem, capacite, tests-charge]
duration_min: 21
---

# Gérer un incident, post-mortems, capacité et tests de charge

## Gérer un incident

Un incident sérieux mal coordonné tourne vite au chaos : dix personnes tapent des commandes
en production, personne ne sait qui décide, les clients n'ont aucune nouvelle. Une
organisation simple, inspirée des pompiers (*Incident Command System*) :

| Rôle | Responsabilité |
|---|---|
| **Responsable d'incident** (*incident commander*) | coordonne, décide, délègue ; ne tape pas de commandes lui-même |
| **Opérations** | agit sur les systèmes, propose et applique les actions |
| **Communication** | tient informés la direction, le support et les clients (page de statut) à intervalles réguliers |
| **Scribe** | tient la chronologie : qui a fait quoi, quand, avec quel résultat |

Les principes :

- **Déclarer tôt** : ouvrir un incident pour rien coûte peu ; le déclarer trop tard coûte cher.
- **Une gravité** (SEV1 : panne majeure pour beaucoup d'utilisateurs ; SEV3 : gêne limitée)
  qui fixe qui mobiliser et à quelle fréquence communiquer.
- **Rétablir d'abord, comprendre ensuite** : annuler le dernier déploiement, désactiver un
  feature flag, basculer sur l'autre région, ajouter de la capacité. On cherche la cause
  profonde **après**.
- **Un seul canal** dédié, et les décisions annoncées à voix haute.
- **Passer le relais** explicitement quand l'incident dure : la fatigue fait faire des erreurs.

## Les post-mortems sans reproche

Après chaque incident significatif, un **post-mortem** écrit, sans chercher de coupable
(parcours DevOps, niveau 1) :

1. **Résumé** et **impact** chiffré (durée, utilisateurs touchés, budget d'erreur consommé).
2. **Chronologie** précise, à partir du journal du scribe.
3. **Facteurs contributifs** : il y en a presque toujours plusieurs (le déploiement fautif,
   **et** l'absence de test sur ce cas, **et** l'alerte qui a sonné tard, **et** la procédure
   de retour arrière qui ne marchait pas).
4. **Ce qui a bien fonctionné** (à garder) et où on a eu de la chance (à ne pas compter dessus).
5. **Actions** concrètes, avec un responsable et une date, suivies jusqu'au bout.

Le piège des « 5 pourquoi » appliqués mécaniquement : ils conduisent à **une** cause racine,
souvent une personne (« il n'a pas vérifié »). Les systèmes complexes tombent par des
combinaisons de facteurs ; la question utile est « comment le système a-t-il rendu cette
erreur possible et si longue à détecter ? ».

## Planifier la capacité et la file d'attente

La latence d'un service ne reste **pas** constante jusqu'à 100 % de charge. Mesure réelle :
un serveur qui traite une requête à la fois, en 10 ms (capacité mesurée : 94 requêtes/s),
soumis à des requêtes qui arrivent **au hasard** (comme de vrais utilisateurs) :

| Charge | Débit | Latence médiane | Latence p99 |
|---|---|---|---|
| 30 % | 28 req/s | 11 ms | 52 ms |
| 60 % | 56 req/s | 17 ms | 79 ms |
| 80 % | 75 req/s | 25 ms | 111 ms |
| 90 % | 84 req/s | 39 ms | 132 ms |
| 95 % | 89 req/s | 55 ms | 185 ms |

Le temps de traitement n'a pas changé (10 ms) : tout le reste est de l'**attente**. Quand les
arrivées sont irrégulières, des rafales se forment, et plus le serveur est occupé, moins il a
de temps pour résorber la file. La théorie des files d'attente le prévoit : le temps d'attente
croît comme `ρ / (1 − ρ)` (ρ = taux d'occupation), donc il explose à l'approche de 100 %.

Conséquences pratiques :

- on ne dimensionne pas pour tourner à 95 % : on garde une **marge** (souvent viser 50 à 70 %
  d'occupation au pic sur la ressource limitante) ;
- on dimensionne pour le **pic** (et le pic du pic : soldes, campagne, échéance fiscale), et
  pour la perte d'une zone ou d'une instance (N+1) ;
- on suit la croissance et on prévoit les achats ou l'auto-scaling **avant** d'en avoir
  besoin ; l'auto-scaling met des minutes à réagir, une rafale non ;
- quand on est plein, mieux vaut **refuser vite** une partie des requêtes (*load shedding*,
  `503` immédiat) que de toutes les servir lentement.

Autre mesure réelle, sur le même serveur avant correction : sa **file d'attente de connexions**
n'avait que 5 places (la valeur par défaut du serveur Python). Au-delà de 80 % de charge,
des connexions étaient refusées, le client les retentait après **1 seconde**, et le p99
passait à **1 046 ms**. Avec une file de 1 024 places : 111 ms. Les limites cachées (files de
connexions, pools, descripteurs de fichiers) sont souvent les premières atteintes.

## Les tests de charge

Types de tests :

| Test | Question |
|---|---|
| **Charge** (*load*) | tient-on la charge attendue au pic, avec les objectifs de latence ? |
| **Stress** | où et comment casse-t-on, au-delà ? (et revient-on à la normale ensuite ?) |
| **Endurance** (*soak*) | tient-on des heures ? (fuites mémoire, connexions, disques qui se remplissent) |
| **Pic** (*spike*) | que se passe-t-il si le trafic est multiplié par 10 en une minute ? |

Outils : k6, Gatling, Locust, JMeter, wrk2, Vegeta.

Les pièges, dont deux mesurés ici :

- **Un trafic trop régulier ment.** La première version de la mesure ci-dessus envoyait les
  requêtes à intervalles parfaitement réguliers : p99 de **12 ms** même à 95 % de charge.
  Avec des arrivées aléatoires, réalistes : **185 ms**. Un test qui ne reproduit pas les
  rafales sous-estime la latence.
- **Le modèle fermé** (N utilisateurs virtuels qui attendent chaque réponse avant d'envoyer la
  suivante) ralentit l'envoi quand le serveur ralentit, et cache l'attente : c'est l'**omission
  coordonnée** (Gil Tene). On préfère un modèle **ouvert** : un débit d'arrivée fixé,
  indépendant des réponses (k6 `constant-arrival-rate`, wrk2).
- Tester dans un environnement **représentatif** (volume de données, configuration, mêmes
  limites), avec des **scénarios réalistes** (mélange de requêtes, cache froid et chaud), et
  sans viser par erreur un service tiers ou la production d'un autre.

## À retenir

- Incident : rôles (responsable, opérations, communication, scribe), déclarer tôt, rétablir d'abord, communiquer régulièrement.
- Post-mortem : impact, chronologie, facteurs contributifs multiples, actions suivies, sans coupable.
- Capacité : la latence explose avant 100 % (médiane 11 → 55 ms mesurée de 30 % à 95 %) ; marge, pic, N+1, refuser vite ; surveiller les limites cachées (file de 5 connexions : p99 à 1 s).
- Tests de charge : charge, stress, endurance, pic ; arrivées aléatoires et modèle ouvert, sinon les résultats sont trop beaux.
