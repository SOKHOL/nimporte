---
id: sre.n5.fiabilite-a-grande-echelle
title: Pannes en cascade, chaos engineering et réduction du toil
skills: [pannes-en-cascade, chaos, toil]
duration_min: 19
---

# Pannes en cascade, chaos engineering et réduction du toil

## Les pannes en cascade et les tempêtes de réessais

Les grandes pannes sont rarement causées par un seul composant : un petit problème
déclenche des réactions **automatiques** (réessais, basculements, redémarrages) qui
l'**amplifient**.

**La tempête de réessais.** Un service ralentit ; ses clients dépassent leur délai et
réessaient ; le service reçoit deux, trois, quatre fois plus de requêtes, dont beaucoup pour
des clients qui ont **déjà abandonné** : il travaille pour rien et ralentit encore.

Simulation (simplifiée) d'un service de capacité 100 requêtes/s qui reçoit 80 nouvelles
requêtes/s, avec des clients qui attendent 1 s puis réessaient jusqu'à 3 fois. Entre 10 et
20 s, une panne divise la capacité par deux ; après 20 s, la capacité est **revenue à 100**.

| Stratégie des clients | Réponses utiles avant | pendant la panne | 40 s **après** la panne | File à la fin |
|---|---|---|---|---|
| réessai immédiat | 100 % | 99 % | **2 %** | 2 864 requêtes |
| délai croissant aléatoire + budget de réessais (10 %) | 100 % | 99 % | **89 %** | 0 |

Avec les réessais immédiats, le système reste **à terre** alors que la cause a disparu : la
file est pleine de requêtes déjà abandonnées, qui provoquent de nouveaux réessais. C'est une
**panne métastable** : un état dégradé qui s'auto-entretient. Il faut souvent couper le trafic
pour en sortir.

Les protections :

- **Réessais limités et espacés** : délai **croissant** (*exponential backoff*) et
  **aléatoire** (*jitter*) pour ne pas resynchroniser tous les clients, et un **budget** de
  réessais (au plus 10 % du trafic, par exemple) ;
- réessayer à **un seul niveau** de la pile : si chacun des 4 niveaux réessaie 3 fois, une
  requête peut devenir 3⁴ = 81 requêtes au fond ;
- des **délais** qui se propagent (*deadline propagation*) : un service n'entame pas un
  travail dont l'appelant n'attend plus la réponse ;
- des **disjoncteurs** (*circuit breakers*) qui cessent d'appeler une dépendance en échec ;
- le **délestage** (*load shedding*) : refuser vite, en priorité les requêtes les moins
  importantes, plutôt que de tout servir trop tard ;
- des **cloisons** (*bulkheads*) : un pool de threads ou de connexions par dépendance, pour
  qu'une seule dépendance lente n'épuise pas tout.

D'autres cascades classiques : la panne d'une zone qui reporte toute sa charge sur les
autres zones, déjà pleines ; le **troupeau** (*thundering herd*) de milliers de clients qui se
reconnectent tous à la même seconde après une coupure ; le cache vidé dont la charge tombe
d'un coup sur la base.

## Le chaos engineering

Plutôt que d'attendre qu'une panne révèle une faiblesse un vendredi soir, on **provoque des
pannes volontairement**, de façon contrôlée, pour vérifier que le système y survit. Netflix l'a
popularisé avec *Chaos Monkey*, qui éteint des serveurs au hasard en production pendant les
heures de bureau.

La méthode, comme une expérience scientifique :

1. Définir l'**état stable** mesurable (les SLI : taux de succès, latence).
2. Formuler une **hypothèse** : « si une instance de la base tombe, le basculement prend moins
   de 30 s et le taux de succès reste au-dessus de 99 % ».
3. Injecter la panne : arrêter une instance, ajouter de la latence réseau, saturer le CPU ou
   le disque, couper une zone, rendre une dépendance lente (plus dangereux qu'en panne nette).
4. **Limiter le rayon d'impact** : commencer en préproduction, puis sur une petite part du
   trafic, avec un bouton d'arrêt immédiat.
5. Observer, conclure, corriger ce qui a cassé.

Outils : Chaos Mesh et Litmus (Kubernetes), AWS Fault Injection Service, Gremlin, Toxiproxy
(pannes réseau dans les tests). Les **journées de jeu** (*game days*) rejouent un scénario de
panne avec les équipes, pour tester aussi les procédures, les alertes et les humains : une
procédure de reprise jamais exécutée ne fonctionne pas le jour où on en a besoin.

## Réduire le travail répétitif (toil)

Le **toil** est le travail d'exploitation **manuel**, **répétitif**, **automatisable**, sans
valeur durable, et qui **grandit avec le service** : redémarrer un service à la main chaque
semaine, créer des comptes sur demande, purger un disque, appliquer la même correction de
données, répondre aux mêmes alertes.

Chez Google, les équipes SRE plafonnent le toil à **50 %** de leur temps ; le reste va à
l'ingénierie qui supprime le toil et améliore la fiabilité. Sans plafond, le toil grandit avec
le trafic jusqu'à occuper toute l'équipe, qui ne peut plus rien améliorer.

Comment le réduire :

- **Le mesurer** : noter les interruptions et les tâches manuelles pendant quelques semaines ;
  on est souvent surpris.
- **Corriger la cause** plutôt qu'automatiser le symptôme : un service qu'il faut redémarrer
  chaque semaine a une fuite à corriger.
- **Automatiser** ce qui reste, par étapes : une procédure écrite, puis un script, puis un
  outil en libre-service, puis une action automatique (auto-réparation, auto-scaling).
- **Supprimer** les alertes et tâches qui n'apportent rien.

Attention à l'excès inverse : une automatisation mal surveillée peut appliquer une erreur
partout à la vitesse de la machine. Les automatisations ont elles aussi besoin de limites
(pas plus de N actions par heure), de journaux et d'un bouton d'arrêt.

## À retenir

- Les réactions automatiques amplifient les pannes : tempêtes de réessais, pannes métastables (simulées : 2 % utiles après la panne avec réessais immédiats, 89 % avec délai croissant et budget).
- Réessais espacés, aléatoires, budgétés, à un seul niveau ; délais propagés, disjoncteurs, délestage, cloisons.
- Chaos engineering : état stable, hypothèse, panne injectée, rayon limité ; journées de jeu.
- Toil : le mesurer, corriger les causes, automatiser par étapes, le plafonner (50 % chez Google).
