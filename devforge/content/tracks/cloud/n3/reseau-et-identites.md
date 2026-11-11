---
id: cloud.n3.reseau-et-identites
title: VPC, IAM et chiffrement avec un KMS
skills: [vpc, iam, chiffrement-kms]
duration_min: 20
---

# VPC, IAM et chiffrement avec un KMS

## Le réseau virtuel (VPC)

Un **VPC** (*Virtual Private Cloud*) est ton réseau privé dans le cloud, isolé de celui des
autres clients. On lui donne une plage d'adresses en notation **CIDR**. Calculs réels avec le
module `ipaddress` de Python :

```
VPC 10.0.0.0/16 : 65536 adresses
découpé en /24 : 256 sous-réseaux de 256 adresses ; les 3 premiers : 10.0.0.0/24, 10.0.1.0/24, 10.0.2.0/24
10.0.0.0/24 : première 10.0.0.0, dernière 10.0.0.255, utilisables sur AWS : 251 (5 réservées)
10.0.0.0/16 et 10.0.128.0/17 se chevauchent : True
```

Le nombre après la barre est le nombre de bits fixés : un `/16` laisse 16 bits libres, soit
2¹⁶ = 65 536 adresses ; un `/24`, 256. AWS réserve 5 adresses par sous-réseau (le réseau, le
routeur, le DNS, une réservée, la diffusion). Choisir des plages qui **ne se chevauchent pas**
avec celles des autres VPC, des bureaux et des partenaires : il faudra un jour les relier, et
deux réseaux qui se chevauchent ne peuvent pas être routés ensemble.

L'architecture classique, répétée dans **chaque zone de disponibilité** :

| Sous-réseau | Contient | Accès |
|---|---|---|
| **public** | répartiteur de charge, passerelle NAT, bastion éventuel | route vers Internet via une **passerelle Internet** |
| **privé** (applications) | serveurs, conteneurs | pas d'adresse publique ; sortie vers Internet via la **passerelle NAT** |
| **privé** (données) | bases, caches | aucune route vers Internet ; seulement depuis les applications |

Les contrôles d'accès réseau :

- les **groupes de sécurité** (*security groups*) : un pare-feu **par ressource**, avec état,
  en liste blanche : « la base accepte le port 5432 depuis le groupe des applications » ;
  référencer d'autres groupes plutôt que des adresses IP ;
- les **ACL réseau** : un filtre sans état par sous-réseau, plus rarement utile ;
- les **points de terminaison privés** (*VPC endpoints*, *Private Link*) : joindre S3 ou
  d'autres services du fournisseur sans passer par Internet ni par la passerelle NAT (qui est
  facturée au volume : un gros transfert vers S3 par la NAT peut coûter très cher).

Pour relier plusieurs réseaux : l'**appairage** (*peering*) de VPC, une **passerelle de
transit** pour beaucoup de VPC, et un **VPN** ou une **liaison dédiée** vers les bureaux et
datacenters.

## Les identités et le moindre privilège (IAM)

L'**IAM** (*Identity and Access Management*) décide **qui** peut faire **quoi** sur **quelle
ressource**. Une politique AWS ressemble à ceci :

```json
{"Effect": "Allow", "Action": ["s3:GetObject", "s3:PutObject"], "Resource": "arn:aws:s3:::factures/*"}
```

La logique d'évaluation, reproduite dans un modèle simplifié en Python avec trois politiques
(une autorisant lecture et écriture sur `factures/*`, une autorisant `s3:*` sur `factures/*`,
un refus de `s3:DeleteObject` sur `factures/archives/*`) :

```
s3:GetObject           factures/2026/f-001.pdf            → autorisé
s3:DeleteObject        factures/2026/f-001.pdf            → autorisé
s3:DeleteObject        factures/archives/2019/f-042.pdf   → refusé (refus explicite)
s3:GetObject           paie/2026/bulletin.pdf             → refusé (par défaut)
ec2:TerminateInstances instance i-0abc                    → refusé (par défaut)
```

Trois règles en ressortent :

1. **Tout est refusé par défaut** : sans autorisation explicite, rien n'est permis.
2. **Un refus explicite l'emporte toujours** sur une autorisation.
3. **Les jokers sont dangereux** : la politique `s3:*`, ajoutée « pour que ça marche », autorise
   aussi la **suppression** des factures, ce que personne n'avait demandé.

Les bonnes pratiques :

- des **rôles** avec des **identifiants temporaires** plutôt que des clés d'accès permanentes :
  une machine, un conteneur, une fonction ou la CI (par OIDC) **endosse un rôle** et reçoit des
  identifiants valables quelques heures ;
- le **moindre privilège** : les actions et ressources précises, et l'outillage pour le
  vérifier (IAM Access Analyzer propose les droits réellement utilisés) ;
- pas d'usage quotidien du compte racine, protégé par une clé matérielle ;
- l'authentification des humains par le fournisseur d'identité de l'entreprise (SSO), avec MFA.

## Le chiffrement et la gestion des clés (KMS)

Les services gérés chiffrent les données au repos, souvent par défaut ; la vraie question est
**qui contrôle les clés**. Un **KMS** (*Key Management Service*) garde les clés maîtresses dans
des modules matériels (HSM) dont elles ne sortent jamais, et journalise chaque usage.

Le **chiffrement d'enveloppe**, démontré en Python :

1. On génère une **clé de données** aléatoire et on chiffre le fichier avec (localement, vite).
2. On envoie au KMS **seulement la clé de données** pour qu'il la chiffre avec la clé
   maîtresse.
3. On stocke le fichier chiffré **et** la clé de données chiffrée côte à côte, puis on oublie la
   clé en clair.

Résultat réel : un fichier chiffré de 22 028 octets, accompagné d'une clé de données chiffrée
de **60 octets**. Pour lire, un seul petit appel au KMS déchiffre la clé de données, qui
déchiffre le fichier. Quelle que soit la taille du fichier, seules 32 octets de clé
transitent par le KMS. Et changer la clé maîtresse (rotation) demande de rechiffrer ces 60
octets, pas des téraoctets de données.

Ce qu'apporte le KMS : les droits d'**utiliser** une clé sont séparés des droits sur les
données (un administrateur de stockage peut gérer les fichiers sans pouvoir les lire),
**chaque déchiffrement est journalisé**, et **désactiver une clé** rend instantanément
illisibles toutes les données qu'elle protège.

## À retenir

- VPC : des plages CIDR qui ne se chevauchent pas (un /16 = 65 536 adresses, un /24 = 256 dont 251 utilisables sur AWS) ; sous-réseaux publics et privés dans chaque zone ; groupes de sécurité en liste blanche ; points de terminaison privés.
- IAM : refus par défaut, refus explicite prioritaire, attention aux jokers (`s3:*` autorise la suppression) ; rôles et identifiants temporaires ; moindre privilège outillé.
- KMS : clés maîtresses qui ne sortent pas, chiffrement d'enveloppe (60 octets à rechiffrer pour une rotation), usage journalisé et séparé des droits sur les données.
