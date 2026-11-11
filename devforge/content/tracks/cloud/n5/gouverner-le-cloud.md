---
id: cloud.n5.gouverner-le-cloud
title: Landing zones, garde-fous, Well-Architected et multi-cloud
skills: [landing-zone, gouvernance, well-architected, multi-cloud]
duration_min: 18
---

# Landing zones, garde-fous, Well-Architected et multi-cloud

Quand une entreprise passe de trois développeurs sur un compte cloud à des dizaines d'équipes,
le problème change : comment laisser chacun avancer vite **sans** créer de failles, de coûts
incontrôlés ni de chaos ?

## Les landing zones et l'organisation des comptes

Un seul compte cloud partagé par tout le monde mélange la production et les essais, les droits
et les coûts. La pratique recommandée : **beaucoup de comptes** (AWS) ou de projets et
abonnements (Google Cloud, Azure), organisés en arbre.

Une **landing zone** est l'environnement de départ, prêt à l'emploi et conforme, dans lequel
les équipes « atterrissent » :

- une **organisation** avec des unités (sécurité, infrastructure partagée, production,
  hors-production, bacs à sable) ;
- un compte (ou projet) **par application et par environnement** : une erreur dans le compte
  de test ne peut pas toucher la production, et les coûts se lisent naturellement ;
- des comptes **dédiés** : journaux centralisés (immuables, hors d'atteinte des équipes),
  sécurité (outils de détection), réseau partagé (passerelle de transit, connexion aux
  bureaux) ;
- l'**authentification unique** (SSO) avec les groupes de l'annuaire de l'entreprise, et des
  rôles standard dans chaque compte ;
- la création d'un nouveau compte **en libre-service**, déjà configuré (réseau, journaux,
  garde-fous, budget), en quelques minutes : c'est un « chemin balisé » de la plateforme
  (parcours DevOps, niveau 5).

Outils : AWS Control Tower et Organizations, Azure Landing Zones, Google Cloud Foundation
Fabric, souvent pilotés par Terraform.

## Les garde-fous et la conformité

Plutôt que de relire chaque changement à la main, on pose des **garde-fous** automatiques, de
deux sortes :

- **préventifs** : ce qui est impossible. Des politiques au niveau de l'organisation (AWS
  *Service Control Policies*, Azure Policy, Google Organization Policies) interdisent par
  exemple de créer des ressources hors des régions européennes, de désactiver les journaux
  d'audit, de rendre un stockage public, de quitter l'organisation ;
- **détectifs** : ce qui est signalé et corrigé. Des règles de conformité (AWS Config, Security
  Hub, Microsoft Defender for Cloud, Security Command Center) détectent un disque non chiffré,
  un groupe de sécurité ouvert à tout Internet sur le port 22, une clé d'accès vieille de deux
  ans, et peuvent corriger automatiquement.

On ajoute des contrôles **en amont**, dans la CI : vérifier le code Terraform avant même qu'il
soit appliqué (Checkov, tfsec, OPA/Conftest). Une erreur trouvée dans une pull request coûte
infiniment moins cher qu'en production.

La **conformité** (RGPD, santé avec l'hébergement de données de santé, banque, ISO 27001,
SOC 2) devient alors en grande partie **vérifiable automatiquement** : les preuves sont les
règles et leurs rapports, pas des captures d'écran rassemblées une fois par an.

## Les cadres Well-Architected

Chaque grand fournisseur publie un cadre pour évaluer une architecture (AWS Well-Architected
Framework, Azure Well-Architected, Google Cloud Architecture Framework). Celui d'AWS a six
**piliers** :

| Pilier | Question centrale |
|---|---|
| **Excellence opérationnelle** | sait-on déployer, observer et améliorer en continu ? |
| **Sécurité** | identités, données, détection et réponse sont-elles maîtrisées ? |
| **Fiabilité** | le système résiste-t-il aux pannes et se rétablit-il ? |
| **Efficacité des performances** | utilise-t-on les bonnes ressources, au bon dimensionnement ? |
| **Optimisation des coûts** | paie-t-on seulement ce qui apporte de la valeur ? |
| **Durabilité** | minimise-t-on l'énergie et le matériel consommés ? |

Une **revue** Well-Architected parcourt des questions par pilier et produit une liste de
risques élevés et moyens à traiter. Son intérêt : une grille commune, qui force à regarder
tous les piliers, et pas seulement celui qu'on aime (souvent la performance). Les piliers
entrent en tension (la fiabilité multi-région coûte cher) : on arbitre explicitement.

## Multi-cloud et dépendance au fournisseur

« Être multi-cloud » recouvre des réalités très différentes :

- **utiliser plusieurs fournisseurs pour des usages différents** (l'analytique chez l'un, les
  applications chez l'autre, des SaaS partout) : courant et raisonnable ;
- **pouvoir déplacer une application** d'un fournisseur à l'autre si nécessaire : demande de
  s'appuyer sur des standards (conteneurs, Kubernetes, PostgreSQL, Terraform, OpenTelemetry) ;
- **faire tourner la même application sur deux fournisseurs en même temps** : très coûteux,
  complexe, et rarement justifié.

Le multi-cloud actif se paie cher : on se limite au plus petit dénominateur commun des
services, on double les compétences, les outils et la sécurité, et le trafic entre
fournisseurs est facturé. Les raisons valables existent (exigences réglementaires, négociation
commerciale, un service unique chez un fournisseur, souveraineté), mais « éviter la dépendance
au fournisseur » ne suffit pas toujours à les justifier.

Une approche pragmatique : **accepter une dépendance consciente** là où un service géré
apporte beaucoup, **limiter le coût de sortie** là où c'est bon marché (formats ouverts, IaC,
conteneurs, données exportables), et **documenter** ce qu'il faudrait pour partir (un ADR,
parcours Architecture).

## À retenir

- Landing zone : une organisation de comptes par application et environnement, des comptes de journaux et de sécurité dédiés, SSO, création de comptes en libre-service.
- Garde-fous préventifs (politiques d'organisation) et détectifs (règles de conformité), plus des contrôles d'IaC dans la CI ; la conformité devient vérifiable.
- Well-Architected : six piliers (excellence opérationnelle, sécurité, fiabilité, performance, coûts, durabilité) et des arbitrages explicites.
- Multi-cloud : des usages différents chez plusieurs fournisseurs, oui ; la même application partout, rarement ; une dépendance consciente et un coût de sortie maîtrisé.
