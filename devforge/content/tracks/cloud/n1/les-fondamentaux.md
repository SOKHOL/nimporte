---
id: cloud.n1.les-fondamentaux
title: Modèles de service, régions, responsabilité partagée et élasticité
skills: [modeles-service, regions-zones, responsabilite-partagee, elasticite]
duration_min: 16
---

# Modèles de service, régions, responsabilité partagée et élasticité

Le **cloud** consiste à louer de l'informatique à la demande (serveurs, stockage, bases,
réseau, services complets) à un fournisseur, par une API, en payant à l'usage. Les trois
grands fournisseurs publics sont AWS, Microsoft Azure et Google Cloud ; d'autres (OVHcloud,
Scaleway, Oracle, Alibaba) complètent le paysage. Les exemples de ce parcours utilisent
surtout les noms d'AWS, mais les concepts sont les mêmes partout.

## IaaS, PaaS, SaaS et serverless

Plus on monte, moins on gère soi-même :

| Modèle | On loue | On gère encore | Exemples |
|---|---|---|---|
| **IaaS** (infrastructure) | des machines virtuelles, des disques, du réseau | le système, les mises à jour, l'application, la mise à l'échelle | EC2, Compute Engine, Azure VM |
| **CaaS** (conteneurs) | un orchestrateur géré | les images, les manifestes | EKS, GKE, AKS, Cloud Run, ECS |
| **PaaS** (plateforme) | un environnement d'exécution | le code et la configuration | App Engine, Heroku, Elastic Beanstalk, Azure App Service |
| **FaaS / serverless** | l'exécution de fonctions à la demande | le code des fonctions | Lambda, Cloud Functions, Azure Functions |
| **SaaS** (logiciel) | une application complète | les utilisateurs et les données | Gmail, Salesforce, Slack, GitHub |

Le **serverless** ne veut pas dire « sans serveur » : les serveurs existent, mais on ne les
voit plus. On ne paie que l'exécution (à la milliseconde), la mise à l'échelle est automatique
jusqu'à zéro, au prix de limites (durée maximale d'exécution, **démarrage à froid** de quelques
centaines de millisecondes après une période d'inactivité, état à stocker ailleurs).

## Régions et zones de disponibilité

- Une **région** est une zone géographique (Paris `eu-west-3`, Francfort, Virginie) avec
  plusieurs datacenters. Les régions sont **indépendantes** : une panne dans l'une ne touche
  normalement pas les autres.
- Une **zone de disponibilité** (*AZ*) est un ou plusieurs datacenters d'une région, avec leur
  propre alimentation, refroidissement et réseau, séparés de quelques kilomètres et reliés par
  des liaisons très rapides (environ une milliseconde).

Les règles qui en découlent :

- répartir une application sur **au moins deux zones** pour survivre à la panne d'un
  datacenter (niveau 4) ;
- choisir la région selon la **proximité** des utilisateurs (latence), la **loi** (où les
  données ont le droit d'être stockées), le **prix** (il varie d'une région à l'autre) et les
  **services** disponibles ;
- les grandes pannes publiques des fournisseurs touchent en général **une** région, parfois
  un service global : un plan pour la perte d'une région est un choix explicite (niveau 4).

## La responsabilité partagée

Le fournisseur sécurise **le cloud** ; le client sécurise **ce qu'il met dans le cloud**.

| | IaaS | PaaS / serverless | SaaS |
|---|---|---|---|
| Datacenters, matériel, hyperviseur | fournisseur | fournisseur | fournisseur |
| Système d'exploitation, correctifs | **client** | fournisseur | fournisseur |
| Application, dépendances | **client** | **client** | fournisseur |
| Configuration (réseau, accès, chiffrement) | **client** | **client** | **client** (partagé) |
| Données, identités, droits | **client** | **client** | **client** |

L'immense majorité des incidents de sécurité dans le cloud ne viennent pas d'une faille du
fournisseur, mais d'une **mauvaise configuration** du client : un compartiment de stockage
public, des droits trop larges, une clé d'accès publiée, une base exposée sur Internet.

## L'élasticité et le paiement à l'usage

On paie ce qu'on consomme, et on peut ajuster la capacité en minutes. Calcul réel pour un
service qui a besoin de 10 serveurs pendant 2 heures de pic par jour, et de 2 serveurs le
reste du temps :

| | Heures-serveur par jour |
|---|---|
| capacité fixe dimensionnée pour le pic (10 × 24) | 240 |
| capacité élastique (10 × 2 + 2 × 22) | 64 |
| **économie** | **73 %** |

Le cloud paie le plus pour les charges **variables** ou **imprévisibles**, les projets qui
démarrent, les besoins temporaires (une campagne, un calcul ponctuel). Pour une charge stable
et énorme, des engagements (instances réservées, *savings plans*) ou des serveurs à soi
peuvent coûter moins cher : des entreprises ont publié des économies importantes en
rapatriant des charges stables. Le cloud n'est pas toujours moins cher ; il est plus
**flexible**.

Le revers du paiement à l'usage : une erreur de configuration (une boucle qui crée des
ressources, des journaux trop bavards, du trafic sortant oublié) peut coûter très cher très
vite. Des alertes de budget dès le premier jour.

## À retenir

- IaaS, CaaS, PaaS, serverless, SaaS : plus on monte, moins on gère, et moins on contrôle.
- Région = zone géographique indépendante ; zone de disponibilité = datacenters séparés d'une région ; au moins deux zones.
- Responsabilité partagée : le fournisseur sécurise le cloud, le client sa configuration, ses données et ses accès.
- Élasticité : 73 % d'heures-serveur en moins dans l'exemple calculé ; plus flexible, pas toujours moins cher ; alertes de budget.
