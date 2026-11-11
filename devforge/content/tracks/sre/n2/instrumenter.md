---
id: sre.n2.instrumenter
title: Journaux structurés, Prometheus, tableaux de bord et alertes
skills: [journaux-structures, metriques-prometheus, tableaux-de-bord, alertes]
duration_min: 19
---

# Journaux structurés, Prometheus, tableaux de bord et alertes

## Des journaux structurés

Deux lignes réelles, pour le même événement :

```
2026-11-11 20:21:20,106 WARNING Paiement refusé pour la commande 7731 du client 42 (fonds insuffisants) en 340 ms

{"horodatage": "2026-11-11T20:21:20Z", "niveau": "WARNING", "message": "paiement refusé",
 "service": "paiement", "commande_id": 7731, "client_id": 42, "raison": "fonds_insuffisants",
 "duree_ms": 340, "trace_id": "4bf92f3577b34da6"}
```

La première se lit bien… par un humain, une ligne à la fois. Pour répondre à « combien de
refus pour fonds insuffisants depuis 14 h, par client ? », il faut des expressions régulières
fragiles. La seconde est **structurée** : chaque information est un champ, que l'outil
(Loki, Elasticsearch, Datadog, CloudWatch) filtre, compte et agrège directement.

Bonnes pratiques :

- un **message fixe** (`paiement refusé`) et les valeurs variables dans des **champs** : on
  peut alors compter les occurrences d'un même événement ;
- toujours les champs de contexte : service, version, environnement, **identifiant de trace**
  (pour relier aux traces) ;
- des **niveaux** cohérents : `ERROR` = quelqu'un doit regarder ; `WARN` = anormal mais géré ;
  `INFO` = événements métier importants ; `DEBUG` = désactivé en production ;
- écrire sur la **sortie standard** (12 facteurs) : la plateforme collecte ;
- jamais de données sensibles (mots de passe, jetons, cartes, santé) ;
- maîtriser le **volume** : une ligne `INFO` par requête à 10 000 requêtes/s, c'est 864
  millions de lignes par jour, et une facture à la hauteur.

## Les métriques avec Prometheus

**Prometheus** est le standard libre des métriques : chaque service expose ses compteurs sur
une URL (`/metrics`), et Prometheus vient les **lire** toutes les 15 à 60 secondes (modèle
*pull*). Sortie réelle de la bibliothèque officielle Python :

```
# TYPE http_requetes_total counter
http_requetes_total{code="200",route="/commandes"} 6.0
http_requetes_total{code="500",route="/commandes"} 1.0
# TYPE http_requetes_en_cours gauge
http_requetes_en_cours 3.0
# TYPE http_duree_secondes histogram
http_duree_secondes_bucket{le="0.05",route="/commandes"} 2.0
http_duree_secondes_bucket{le="0.1",route="/commandes"} 3.0
http_duree_secondes_bucket{le="0.25",route="/commandes"} 5.0
http_duree_secondes_bucket{le="0.5",route="/commandes"} 6.0
http_duree_secondes_bucket{le="1.0",route="/commandes"} 7.0
http_duree_secondes_bucket{le="+Inf",route="/commandes"} 7.0
http_duree_secondes_count{route="/commandes"} 7.0
http_duree_secondes_sum{route="/commandes"} 1.66
```

Les trois types à connaître :

| Type | Évolution | Exemple | On en tire |
|---|---|---|---|
| **Counter** | ne fait que monter (remis à zéro au redémarrage) | requêtes traitées, erreurs | un **débit** : `rate(http_requetes_total[5m])` |
| **Gauge** | monte et descend | requêtes en cours, mémoire, taille de file | la valeur actuelle |
| **Histogram** | des compteurs par **tranche** (*bucket*) | durée des requêtes | des **percentiles** : `histogram_quantile(0.99, …)` |

Lecture de l'histogramme ci-dessus (7 requêtes de 0,03 à 0,9 s) : 2 ont duré au plus 0,05 s,
3 au plus 0,1 s, 5 au plus 0,25 s… Chaque tranche **contient** les précédentes. Prometheus
estime un percentile par interpolation dans la bonne tranche : le résultat n'est aussi précis
que les tranches choisies ; on les place autour des seuils qui comptent (si l'objectif est
300 ms, une limite à 0,3).

Les **étiquettes** (*labels*, ici `route` et `code`) découpent une métrique. Chaque combinaison
de valeurs crée une série distincte : une étiquette `client_id` avec un million de clients
crée un million de séries et fait tomber Prometheus (**cardinalité** explosive). Les
identifiants individuels vont dans les journaux et les traces, pas dans les étiquettes.

Quelques requêtes PromQL :

```promql
sum(rate(http_requetes_total{code=~"5.."}[5m])) / sum(rate(http_requetes_total[5m]))   # taux d'erreur
histogram_quantile(0.99, sum by (le) (rate(http_duree_secondes_bucket[5m])))          # p99
```

## Des tableaux de bord utiles

Un bon tableau de bord répond à une question, en quelques secondes :

- **en haut**, l'état vu par l'utilisateur : les signaux d'or du service (latence p50/p99,
  trafic, taux d'erreur, saturation), avec l'objectif de fiabilité en ligne de repère ;
- **en dessous**, les dépendances (base, cache, files, API externes) et les ressources ;
- les **déploiements** marqués sur les graphes : la première question d'un incident est
  « qu'est-ce qui a changé ? » ;
- des percentiles, pas des moyennes ; des taux, pas des compteurs bruts.

À éviter : 60 graphes que personne ne sait lire, des tableaux de bord copiés par service qui
divergent, et la conviction qu'un tableau de bord remplace une alerte (personne ne le regarde
à 3 h du matin).

## Des alertes qui méritent un réveil

Une alerte qui réveille quelqu'un doit être **urgente**, **importante**, **actionnable**, et
correspondre à un **impact réel ou imminent** pour les utilisateurs.

| Mauvaise alerte | Pourquoi | Meilleure alerte |
|---|---|---|
| CPU > 80 % | une cause possible, souvent sans impact | taux d'erreur ou latence p99 au-delà de l'objectif |
| un seul pic d'une minute | du bruit | condition maintenue (`for: 5m`), ou consommation du budget d'erreur (niveau 3) |
| « le disque est plein » à 100 % | trop tard | « le disque sera plein dans 4 h au rythme actuel » (`predict_linear`) |
| 40 alertes pour une même panne | noyade | regroupement et inhibition (Alertmanager) |

Chaque alerte renvoie à une **fiche réflexe** (*runbook*) : ce que signifie l'alerte, comment
vérifier, quoi faire. Les alertes non urgentes (un certificat qui expire dans 20 jours)
créent un **ticket**, elles ne réveillent personne. Et on revoit régulièrement les alertes :
toute alerte qui a sonné sans demander d'action est supprimée ou corrigée. La **fatigue
d'alerte** fait ignorer la seule alerte qui comptait.

## À retenir

- Journaux structurés : un message fixe, des champs, un identifiant de trace, sur la sortie standard, sans données sensibles, avec un volume maîtrisé.
- Prometheus : counter (avec `rate`), gauge, histogram (percentiles par tranches) ; attention à la cardinalité des étiquettes.
- Tableaux de bord : les signaux d'or en haut, les déploiements marqués, des percentiles.
- Alertes : sur des symptômes utilisateur, maintenues, prédictives, avec une fiche réflexe ; le reste en ticket.
