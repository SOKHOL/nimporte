---
id: sre.n3.mesurer-la-fiabilite
title: Traces distribuées, RED et USE, SLI, SLO, SLA et budget d'erreur
skills: [traces-distribuees, red-use, sli-slo, budget-erreur]
duration_min: 21
---

# Traces distribuées, RED et USE, SLI, SLO, SLA et budget d'erreur

## Les traces distribuées

Une requête utilisateur traverse souvent une dizaine de services. Une **trace** la suit de
bout en bout. Elle est faite de **spans** : une opération (une requête HTTP, une requête SQL,
un appel à un autre service), avec un début, une fin, des attributs, et un **parent**.

Trace réelle produite avec **OpenTelemetry** (le standard ouvert d'instrumentation, soutenu
par tous les outils) :

```
SELECT stock     trace=f319cabbc652…  span=458689885f57686d  parent=83dc209802516a95  durée=12 ms
appel paiement   trace=f319cabbc652…  span=151b95fd833cea05  parent=83dc209802516a95  durée=340 ms
POST /commandes  trace=f319cabbc652…  span=83dc209802516a95  parent=—                 durée=353 ms
```

Les trois spans partagent le même identifiant de trace ; les deux premiers ont pour parent la
requête `POST /commandes`. En les affichant en cascade, on voit immédiatement que 340 des
353 ms se passent dans le service de paiement.

Le contexte se **propage** d'un service à l'autre dans un en-tête standard (W3C Trace
Context). Celui réellement envoyé au service de paiement :

```
traceparent: 00-f319cabbc65237529c67eb7fec7e1534-151b95fd833cea05-03
             version  identifiant de trace       span parent      drapeaux (échantillonnage)
```

Le service de paiement crée ses propres spans comme enfants de `151b95fd833cea05` : la trace
continue chez lui. Pour les files de messages, le contexte voyage dans les métadonnées du
message.

À grande échelle, on **échantillonne** : garder toutes les traces coûterait autant que le
trafic lui-même. L'échantillonnage **en tête** décide au début (1 % des requêtes) ;
l'échantillonnage **en queue** décide à la fin, et peut garder toutes les traces lentes ou en
erreur, les plus utiles.

## Les méthodes RED et USE

Deux grilles pour savoir quoi mesurer :

**RED** (Tom Wilkie), pour chaque **service** qui reçoit des requêtes :

- **R**ate : le nombre de requêtes par seconde ;
- **E**rrors : le nombre (ou la part) de requêtes en erreur ;
- **D**uration : la distribution des durées (percentiles).

**USE** (Brendan Gregg, déjà vue dans le parcours Linux), pour chaque **ressource** (CPU,
mémoire, disque, réseau, pool de connexions, file) :

- **U**tilization : le temps où elle est occupée ;
- **S**aturation : le travail qui attend (file d'attente) ;
- **E**rrors : les erreurs.

RED dit **si** les utilisateurs souffrent ; USE aide à trouver **pourquoi**.

## SLI, SLO et SLA

- Un **SLI** (*Service Level Indicator*) est une **mesure** de la qualité vue par
  l'utilisateur, en général un ratio : « part des requêtes de paiement réussies en moins de
  500 ms ».
- Un **SLO** (*Service Level Objective*) est l'**objectif** interne sur ce SLI, sur une
  période : « 99,9 % sur 30 jours glissants ».
- Un **SLA** (*Service Level Agreement*) est un **engagement contractuel** avec des
  conséquences (remboursements, pénalités), toujours **moins strict** que le SLO, pour garder
  une marge.

Bien choisir ses SLI :

- mesurés au plus près de l'utilisateur (répartiteur de charge, ou sondes et mesures dans
  l'application cliente) plutôt que sur les serveurs ;
- peu nombreux, sur les **parcours qui comptent** (se connecter, chercher, payer) ;
- avec des seuils qui correspondent à une vraie gêne (une page à 3 s est un échec, même si
  elle finit par s'afficher).

**100 % n'est jamais le bon objectif** : c'est impossible (le réseau de l'utilisateur, son
téléphone, les fournisseurs tombent aussi), infiniment coûteux, et ça interdit tout
changement. Chaque « 9 » en plus coûte environ dix fois plus cher.

## Le budget d'erreur

Un SLO de 99,9 % autorise **0,1 %** d'échecs : c'est le **budget d'erreur**. Sur 30 jours :

| SLO | Budget sur 30 jours | En requêtes, pour 10 millions par mois |
|---|---|---|
| 99 % | 7 h 12 min | 100 000 |
| 99,9 % | 43 min 12 s | 10 000 |
| 99,99 % | 4 min 19 s | 1 000 |

Le budget change la conversation entre développement et exploitation :

- **tant qu'il reste du budget**, l'équipe livre, expérimente, prend des risques mesurés ;
- **quand il est épuisé**, on gèle les fonctionnalités risquées et on investit dans la
  fiabilité, selon une politique décidée **à l'avance** avec le produit.

La fiabilité devient une décision métier chiffrée, et non plus une dispute entre « il faut
livrer » et « il ne faut rien toucher ».

**Les alertes sur le taux de consommation** (*burn rate*) : au lieu d'alerter sur un seuil
instantané, on alerte quand le budget est consommé **trop vite**. Un taux de consommation de 1
épuise le budget en exactement 30 jours ; un taux de **14,4** consomme **2 %** du budget mensuel
en **une heure** (14,4 × 1/720 du mois), ce qui mérite un réveil. On combine une fenêtre longue
(1 h) pour la fiabilité et une fenêtre courte (5 min) pour que l'alerte s'arrête vite une fois
le problème réglé. Un taux de 1 à 3 sur plusieurs jours crée simplement un ticket.

## À retenir

- Traces : des spans avec parent, un identifiant commun, propagés par `traceparent` ; échantillonnage en tête ou en queue.
- RED pour les services (débit, erreurs, durée), USE pour les ressources (utilisation, saturation, erreurs).
- SLI = mesure vue par l'utilisateur, SLO = objectif interne, SLA = contrat moins strict ; jamais 100 %.
- Budget d'erreur : 99,9 % = 43 min sur 30 jours ; il arbitre entre livrer et fiabiliser ; alertes sur le taux de consommation (14,4 = 2 % en 1 h).
