---
id: systemes-distribues.n1.penser-distribue
title: Illusions, pannes partielles et latence
skills: [illusions, pannes-partielles, latence]
duration_min: 18
---

# Illusions, pannes partielles et latence

Un système distribué, c'est plusieurs machines qui coopèrent en s'envoyant des messages. Dès
que vous appelez une API, une base de données ou un cache sur une autre machine, vous en
écrivez un. Toutes les sorties citées sont réelles (simulations en Python sur une machine).

## Les illusions de l'informatique distribuée

En 1994, Peter Deutsch et James Gosling (Sun) ont listé les **huit hypothèses fausses** que
font les développeurs qui découvrent le réseau :

1. le réseau est fiable ;
2. la latence est nulle ;
3. la bande passante est infinie ;
4. le réseau est sûr ;
5. la topologie ne change pas ;
6. il y a un seul administrateur ;
7. le transport ne coûte rien ;
8. le réseau est homogène.

Chacune a causé des pannes célèbres. Le piège commun : un appel réseau **ressemble** à un appel
de fonction (`client.virer(30)`), et les frameworks RPC renforcent cette ressemblance. Mais un
appel de fonction ne peut pas « à moitié réussir », et il est 1000 fois plus rapide. Mesures
réelles sur la même machine :

| Opération | Durée |
|---|---|
| appel de fonction Python | 58 ns |
| aller-retour TCP **sur la même machine** (médiane) | 40 µs (700 fois plus) |
| requête HTTPS vers api.github.com | 70 ms, parfois 200 ms (1 million de fois plus) |

## Pannes partielles et délais dépassés

Sur une seule machine, un programme marche ou plante en entier. Dans un système distribué, une
partie peut tomber pendant que le reste continue : c'est la **panne partielle**, et elle est la
norme. Le problème fondamental : quand une réponse n'arrive pas, **vous ne pouvez pas savoir
pourquoi**.

```
client ──requête──> serveur        trois histoires indiscernables pour le client :
                                   1. la requête s'est perdue (rien n'a été fait)
client <──réponse── serveur        2. le serveur est en panne ou très lent
                                   3. le travail est FAIT, mais la réponse s'est perdue ou traîne
```

Le seul outil est le **délai d'attente** (*timeout*). Et réessayer après un délai dépassé est
dangereux. Simulation réelle : un virement de 30 € d'Alice vers Bob, un délai de 200 ms, trois
essais au plus, et un serveur dont la première réponse traîne 500 ms :

```
serveur naïf
  essai 1 : délai dépassé, on réessaie
  essai 2 : délai dépassé, on réessaie
  réussi à l'essai 3 ; soldes : {'alice': 10, 'bob': 90}
serveur idempotent
  essai 1 : délai dépassé, on réessaie
  essai 2 : délai dépassé, on réessaie
  réussi à l'essai 3 ; soldes : {'alice': 70, 'bob': 30}
```

Le serveur naïf a exécuté le virement **trois fois** : 90 € au lieu de 30. Le deuxième essai a
lui aussi expiré, parce qu'il attendait derrière le premier. La correction : un **identifiant
de requête** choisi par le client (`virement-42`) et mémorisé par le serveur, qui ignore les
doublons. L'opération devient **idempotente** : la répéter ne change rien. C'est le rôle de
l'en-tête `Idempotency-Key` des API de paiement (Stripe), et le sujet du niveau 4.

Choisir le délai est un compromis : trop court, on abandonne des requêtes qui allaient réussir
(et on multiplie les doublons) ; trop long, l'appelant reste bloqué et ses propres appelants
expirent à leur tour. Règles pratiques : un délai sur **chaque** appel réseau (beaucoup de
clients n'en ont pas par défaut), des délais décroissants de l'extérieur vers l'intérieur, des
réessais avec **attente exponentielle et gigue**, et un budget de réessais limité (parcours SRE,
niveau 3).

## Latence et latence de queue

La moyenne ment : ce qui compte, ce sont les **percentiles** (p50, p99, p99,9). Et dans un
système distribué, une requête utilisateur en déclenche souvent des dizaines d'autres en
parallèle (*fan-out*) : elle attend la **plus lente**. Simulation réelle avec 100 000 requêtes,
chaque appel prenant 10 ms, sauf 1 % qui prennent 1 s :

```
  1 appels en parallèle :   1.0% des requêtes prennent 1 s ; p50 = 10 ms
 10 appels en parallèle :   9.6% des requêtes prennent 1 s ; p50 = 10 ms
100 appels en parallèle :  63.3% des requêtes prennent 1 s ; p50 = 1000 ms
théorie pour 100 : 63.4%
```

Avec 100 appels, la **médiane** de l'utilisateur devient le **p99** de chaque service
(1 − 0,99¹⁰⁰ = 63 %). C'est l'article *The Tail at Scale* (Dean et Barroso, Google, 2013). Les
parades :

- **requêtes de couverture** (*hedged requests*) : si la réponse n'est pas arrivée après le p95,
  envoyer la même requête à une autre réplique et garder la première réponse ;
- réduire le fan-out, et répondre **partiellement** quand une partie manque (une page de
  résultats sans le bloc « recommandations ») ;
- chasser les causes de la queue : pauses du GC, files d'attente, voisins bruyants (parcours
  Performance).

## À retenir

- Huit illusions ; un appel réseau n'est pas un appel de fonction (58 ns contre 40 µs contre 70 ms mesurés).
- Panne partielle : un délai dépassé ne dit pas si le travail est fait ; réessayer sans idempotence a exécuté le virement trois fois.
- La latence de queue s'amplifie avec le fan-out : 1 % de lenteur par appel, 63 % des requêtes lentes avec 100 appels.
