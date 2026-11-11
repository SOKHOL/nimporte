---
id: sre.n1.observer-un-systeme
title: Journaux, métriques, traces et signaux d'or
skills: [trois-piliers, monitoring-observabilite, signaux-or]
duration_min: 15
---

# Journaux, métriques, traces et signaux d'or

Le **SRE** (*Site Reliability Engineering*) est né chez Google en 2003 : traiter
l'exploitation comme un problème d'ingénierie logicielle. Tout commence par une question
simple : **que se passe-t-il en production, en ce moment ?**

## Les trois piliers

| | Ce que c'est | Répond à | Exemple |
|---|---|---|---|
| **Journaux** (*logs*) | des événements datés, un par chose qui arrive | que s'est-il passé, en détail, pour **cette** requête ? | `paiement refusé commande=7731 raison=fonds_insuffisants` |
| **Métriques** | des nombres agrégés dans le temps | combien, à quel rythme, quelle tendance ? | 1 240 requêtes/s, p99 = 180 ms, 0,3 % d'erreurs |
| **Traces** | le parcours d'une requête à travers tous les services | **où** passe le temps, **quel** service échoue ? | API 12 ms → commande 25 ms → paiement 340 ms |

Ils se complètent :

- les **métriques** sont légères et rapides à interroger : elles servent aux tableaux de bord
  et aux alertes (« le taux d'erreur dépasse 1 % ») ;
- les **traces** disent quel composant est en cause ;
- les **journaux** donnent le détail d'un cas précis.

Un bon outillage relie les trois : depuis un pic sur un graphe, on ouvre des traces de cette
minute-là, puis les journaux de la requête lente, grâce à un **identifiant de trace** commun.

Ordres de grandeur de coût : une métrique coûte presque la même chose qu'il y ait 10 ou
10 millions de requêtes ; les journaux et les traces grossissent avec le trafic, d'où
l'**échantillonnage** des traces et le tri de ce qu'on journalise.

## Supervision et observabilité

- La **supervision** (*monitoring*) répond à des questions **connues d'avance** : le disque
  est-il plein ? le service répond-il ? le taux d'erreur dépasse-t-il le seuil ?
- L'**observabilité** est la capacité à répondre à des questions **qu'on n'avait pas
  prévues**, à partir des données que le système émet : « pourquoi les clients belges sur
  Android voient-ils des paiements lents depuis 14 h ? ».

Les systèmes distribués échouent de façons nouvelles et imprévisibles : on ne peut pas tout
prévoir dans un tableau de bord. L'observabilité demande des données **riches en contexte**
(qui, quelle version, quel pays, quel client, quelle fonctionnalité) qu'on peut découper à
volonté, et des outils pour explorer.

## Les quatre signaux d'or

Le livre SRE de Google recommande de surveiller, pour tout service exposé à des
utilisateurs, quatre **signaux d'or** :

| Signal | Question | Attention |
|---|---|---|
| **Latence** | combien de temps prennent les requêtes ? | en percentiles (p50, p99), et séparer les succès des erreurs (une erreur rapide fausse la moyenne) |
| **Trafic** | combien de demandes ? | requêtes/s, messages/s, sessions actives |
| **Erreurs** | quelle proportion échoue ? | les erreurs explicites (500) **et** implicites (un 200 avec un contenu faux, une réponse trop lente) |
| **Saturation** | à quel point le service est-il plein ? | la ressource la plus contrainte : CPU, mémoire, connexions, file d'attente ; on veut voir **avant** d'être à 100 % |

On regarde ces signaux **du point de vue de l'utilisateur** : un serveur à 30 % de CPU n'est
pas une bonne nouvelle si 20 % des pages renvoient une erreur. À l'inverse, une alerte « CPU à
90 % » n'a aucun intérêt si les utilisateurs sont bien servis.

## À retenir

- Journaux (détail d'un événement), métriques (nombres agrégés, alertes), traces (parcours d'une requête) ; reliés par un identifiant de trace.
- Supervision : questions connues ; observabilité : questions imprévues, grâce à des données riches en contexte.
- Les quatre signaux d'or : latence (en percentiles), trafic, erreurs, saturation, vus depuis l'utilisateur.
