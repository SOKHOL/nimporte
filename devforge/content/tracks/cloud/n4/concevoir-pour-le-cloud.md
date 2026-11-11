---
id: cloud.n4.concevoir-pour-le-cloud
title: Haute disponibilité, reprise après sinistre et FinOps
skills: [haute-disponibilite, reprise-sinistre, finops]
duration_min: 20
---

# Haute disponibilité, reprise après sinistre et FinOps

## La haute disponibilité multi-zone

Dans le cloud, tout finit par tomber : une machine, un disque, une zone entière. Concevoir
pour la panne plutôt que l'espérer improbable (« everything fails, all the time », Werner
Vogels).

Calcul réel, en supposant des pannes indépendantes : une seule instance garantie à 99,5 %
peut être indisponible environ **216 minutes par mois** ; deux instances dans deux zones,
dont l'une suffit, atteignent **99,9975 %**, soit environ **1 minute** par mois.

L'architecture type, sur au moins deux zones (souvent trois) :

- un **répartiteur de charge** régional, qui couvre toutes les zones et écarte les instances
  en mauvaise santé ;
- des **instances ou conteneurs sans état** dans chaque zone, gérés par un groupe
  d'auto-scaling qui recrée ce qui disparaît ;
- une **base multi-zone** : un primaire et une réplique synchrone dans une autre zone, avec
  basculement automatique (de l'ordre d'une à deux minutes pendant lesquelles les écritures
  échouent : l'application doit **réessayer** et se **reconnecter**) ;
- le **stockage objet**, déjà réparti sur plusieurs zones ;
- de la **capacité de réserve** : avec trois zones, il faut pouvoir perdre une zone et
  absorber sa charge sur les deux autres (donc tourner à 66 % au plus au pic, idéalement moins).

Les pièges qui annulent tout : une dépendance unique cachée (une passerelle NAT dans une seule
zone, un serveur de cache, un service interne, un certificat), un basculement jamais testé, et
des clients qui gardent en cache l'adresse d'une instance morte.

## La reprise après sinistre (RPO, RTO)

Une région entière peut devenir indisponible (panne majeure, erreur de configuration globale,
incendie) ; des données peuvent être détruites (erreur humaine, rançongiciel). Deux nombres
cadrent le plan :

- le **RPO** (*Recovery Point Objective*) : combien de **données** on accepte de perdre
  (« au plus 5 minutes de transactions ») ;
- le **RTO** (*Recovery Time Objective*) : combien de **temps** pour repartir (« service
  rétabli en moins de 4 heures »).

Les stratégies, de la moins chère à la plus chère :

| Stratégie | Principe | RPO typique | RTO typique |
|---|---|---|---|
| **Sauvegarde et restauration** | sauvegardes copiées dans une autre région ; on reconstruit tout en cas de sinistre | heures | heures à jours |
| **Veilleuse** (*pilot light*) | les données sont répliquées en continu ; l'infrastructure de calcul est décrite (IaC) mais éteinte | minutes | dizaines de minutes à heures |
| **Secours tiède** (*warm standby*) | une version réduite tourne en permanence dans l'autre région, prête à grossir | secondes à minutes | minutes |
| **Actif-actif multi-région** | les deux régions servent du trafic en permanence | proche de zéro | proche de zéro |

Chaque cran divise le RTO et multiplie le coût et la complexité. Le bon choix dépend du **coût
d'une heure d'interruption** pour le métier, pas d'une préférence technique.

Ce qui fait qu'un plan de reprise fonctionne vraiment :

- l'**infrastructure décrite en code** (Terraform), pour reconstruire vite et à l'identique ;
- des sauvegardes **dans un autre compte** et **immuables** (voir le niveau 2) : une reprise
  après sinistre doit aussi couvrir l'attaquant qui supprime tout ;
- les **dépendances** de la reprise elles-mêmes (le DNS, l'annuaire d'identités, les secrets,
  les images) disponibles dans la région de secours ;
- des **exercices réguliers**, chronométrés : le RTO réel est celui qu'on a mesuré, pas celui
  qu'on a écrit.

## FinOps : maîtriser les coûts

Le **FinOps** applique au cloud la culture DevOps : les équipes qui créent des ressources voient
et assument leur coût. Les leviers, par ordre habituel d'impact :

1. **Rendre visible** : des **étiquettes** (*tags*) obligatoires (équipe, service,
   environnement) sur chaque ressource, des tableaux de coûts par équipe, des **budgets** et des
   alertes d'**anomalies** (une dépense qui double en une nuit).
2. **Éteindre ce qui ne sert pas** : environnements de test allumés le week-end, disques et
   instantanés orphelins, adresses IP réservées inutilisées, journaux conservés indéfiniment.
3. **Redimensionner** (*rightsizing*) : beaucoup de machines tournent à 10 % de CPU ; une taille
   en dessous divise souvent le coût par deux.
4. **Payer moins cher la même chose** : engagements (réservations, savings plans) pour la base
   stable, spot pour l'interruptible, générations de processeurs plus récentes (souvent moins
   chères à performance égale, dont les processeurs ARM).
5. **Concevoir économe** : le **trafic sortant** (*egress*) vers Internet et entre régions est
   facturé et souvent oublié ; les passerelles NAT facturées au volume ; les appels d'API
   facturés à l'unité (millions de petites lectures sur le stockage objet) ; des niveaux de
   journalisation raisonnables.

Le bon indicateur n'est pas la facture totale, qui grandit avec le succès, mais le **coût
unitaire** : le coût par commande, par utilisateur actif, par minute de vidéo. Une facture qui
double pendant que le trafic triple est une bonne nouvelle.

## À retenir

- Haute disponibilité : au moins deux zones (99,5 % → 99,9975 % calculés pour deux instances indépendantes), sans état, base multi-zone avec basculement, capacité pour perdre une zone, aucune dépendance unique cachée.
- Reprise après sinistre : RPO (données perdues) et RTO (temps de reprise) ; sauvegarde, veilleuse, secours tiède, actif-actif ; infrastructure en code, sauvegardes immuables, exercices chronométrés.
- FinOps : visibilité (tags, budgets, anomalies), éteindre, redimensionner, engagements et spot, attention au trafic sortant ; suivre le coût unitaire.
