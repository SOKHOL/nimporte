---
id: cloud.n2.les-services-de-base
title: Calcul, stockage, bases et files gérées
skills: [calcul, stockage, bases-gerees]
duration_min: 18
---

# Calcul, stockage, bases et files gérées

## Le calcul

| Option | Ce qu'on fournit | Mise à l'échelle | Idéal pour |
|---|---|---|---|
| **Machines virtuelles** (EC2, Compute Engine) | une image et une configuration | groupes d'auto-scaling | logiciels qui ont besoin du système complet, charges stables |
| **Conteneurs gérés** (ECS/Fargate, Cloud Run, Azure Container Apps) | une image de conteneur | automatique, parfois jusqu'à zéro | la plupart des services web modernes |
| **Kubernetes géré** (EKS, GKE, AKS) | des manifestes | autoscalers de pods et de nœuds | beaucoup de services, besoin de contrôle fin et de portabilité |
| **Fonctions** (Lambda, Cloud Functions) | du code | par requête, jusqu'à zéro | événements, traitements courts et irréguliers |

Pour les machines, on choisit une **famille** (usage général, optimisée calcul, mémoire,
stockage, GPU), une **taille**, et un **mode de paiement** :

- **à la demande** : le plus cher à l'heure, sans engagement ;
- **réservé** ou ***savings plan*** : engagement d'un ou trois ans, remise importante (les
  fournisseurs annoncent jusqu'à environ 70 %) ;
- **spot** (*preemptible*) : la capacité inutilisée du fournisseur, avec des remises allant
  jusqu'à environ 90 %, mais la machine peut être **reprise** avec un préavis de quelques
  dizaines de secondes à deux minutes. Parfait pour les traitements par lots tolérants aux
  interruptions, les nœuds de calcul qu'on peut perdre, la CI.

Les **images** (AMI, images de VM) sont construites à l'avance (Packer) et immuables : on
remplace les machines au lieu de les modifier, et l'auto-scaling démarre de nouvelles
machines prêtes en quelques secondes.

## Le stockage objet, bloc et fichier

| Type | Ce que c'est | Exemples | Usage |
|---|---|---|---|
| **Objet** | des fichiers entiers (objets) dans des compartiments (*buckets*), accédés par HTTP | S3, Cloud Storage, Azure Blob | fichiers utilisateurs, images, sauvegardes, données analytiques, sites statiques |
| **Bloc** | un disque virtuel attaché à **une** machine | EBS, Persistent Disk, Managed Disks | système, bases de données auto-gérées |
| **Fichier** | un système de fichiers partagé en réseau (NFS, SMB) | EFS, Filestore, Azure Files | applications qui ont besoin d'un dossier partagé entre plusieurs machines |

Le **stockage objet** est la brique la plus universelle du cloud :

- capacité pratiquement illimitée, durabilité annoncée de « onze 9 » (99,999999999 %) grâce à
  des copies dans plusieurs zones ;
- on ne **modifie** pas un objet, on le **remplace** en entier ; pas de verrou ni d'écriture
  partielle comme sur un disque ;
- des **classes de stockage** selon la fréquence d'accès : standard, accès peu fréquent,
  archive (récupération en minutes ou en heures, très bon marché), avec des **règles de
  cycle de vie** (« après 90 jours, passer en archive ; après 7 ans, supprimer ») ;
- le **versionnage** et le **verrouillage d'objets** (*object lock*, immuable pendant une
  durée) protègent des suppressions accidentelles et des rançongiciels ;
- des **URL présignées** donnent un accès temporaire à un objet précis, sans rendre le
  compartiment public : le navigateur envoie le fichier directement au stockage, sans
  passer par les serveurs de l'application.

Le **disque bloc** est attaché à une seule zone : si la zone tombe, le disque est
inaccessible. Les **instantanés** (*snapshots*) sont copiés dans le stockage objet, et
permettent de recréer le disque ailleurs.

## Les bases et files gérées

Une base **gérée** (RDS, Aurora, Cloud SQL, Azure SQL, DynamoDB, Firestore) délègue au
fournisseur l'installation, les correctifs, les sauvegardes automatiques, la restauration à un
instant précis, la réplication et le basculement entre zones. On garde :

- le **schéma**, les **index** et les **requêtes** (une base gérée lente reste lente) ;
- le **dimensionnement** (taille d'instance, IOPS, stockage) et les **coûts** ;
- la **sécurité** : réseau privé, chiffrement, comptes et droits ;
- les **fenêtres de maintenance** et les montées de version majeures, à planifier.

Même logique pour les **files et flux gérés** (SQS, SNS, Pub/Sub, Kinesis, Amazon MSK pour
Kafka, Event Hubs) et les **caches gérés** (ElastiCache, Memorystore) : plus de serveurs à
maintenir, mais les concepts du parcours System design restent entiers (au moins une fois,
idempotence, ordre par partition, files de messages morts).

Le compromis général du « géré » : moins d'exploitation et plus de fiabilité, contre moins de
contrôle, un coût unitaire plus élevé, et une dépendance plus forte au fournisseur (une base
propriétaire comme DynamoDB ne se migre pas en un week-end ; un PostgreSQL géré, si).

## À retenir

- Calcul : VM, conteneurs gérés, Kubernetes géré, fonctions ; à la demande, réservé (jusqu'à ~70 %), spot (jusqu'à ~90 %, mais repris sans prévenir longtemps à l'avance).
- Stockage objet (illimité, durable, classes et cycle de vie, versionnage, URL présignées), bloc (un disque, une zone), fichier (partagé).
- Bases et files gérées : le fournisseur gère l'exploitation, on garde le schéma, les requêtes, la sécurité et les coûts ; attention à la dépendance aux services propriétaires.
