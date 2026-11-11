---
id: system-design.n2.les-briques-de-base
title: Répartition de charge, cache, CDN, réplication et services sans état
skills: [load-balancing, cache, cdn, replication, stateless]
duration_min: 20
---

# Répartition de charge, cache, CDN, réplication et services sans état

## Les services sans état

Un service est **sans état** (*stateless*) quand il ne garde rien entre deux requêtes : ni
session en mémoire, ni fichier sur son disque local. Toute requête peut alors aller sur
n'importe quelle instance, et on ajoute ou retire des instances à volonté.

L'état ne disparaît pas, il **déménage** : sessions dans Redis, fichiers dans un stockage
objet (S3), données en base. Pour l'authentification, un jeton signé (JWT) porte lui-même
l'identité : le serveur vérifie la signature sans rien stocker, au prix d'une révocation plus
difficile (on garde des jetons courts).

## La répartition de charge

Un **répartiteur de charge** (*load balancer*) distribue les requêtes entre plusieurs
instances et retire de la rotation celles qui ne répondent plus.

| Niveau | Voit | Exemples |
|---|---|---|
| **L4** (transport) | adresses IP et ports TCP/UDP | AWS NLB, IPVS, HAProxy en mode TCP |
| **L7** (application) | l'URL, les en-têtes, les cookies | Nginx, Envoy, HAProxy, AWS ALB |

Un L7 sait router `/api/*` vers un service et `/images/*` vers un autre, terminer le TLS,
réessayer une requête idempotente. Un L4 est plus simple et plus rapide.

Algorithmes :

- **round robin** : chacun son tour ; simple, mais ignore la charge réelle ;
- **moins de connexions** (*least connections*) : vers l'instance la moins occupée ; bien
  pour des requêtes de durées très variables ;
- **deux choix au hasard** : on tire deux instances, on prend la moins chargée ; presque
  aussi bon que l'optimal, sans état global ;
- **hachage** (de l'IP, de l'utilisateur) : le même client va toujours au même endroit,
  utile pour les caches locaux, nuisible si une instance tombe.

Les **sondes de santé** (*health checks*) appellent régulièrement `/sante`. Une sonde trop
profonde (qui vérifie aussi la base) peut sortir **toutes** les instances de la rotation
quand la base a un hoquet : la panne d'une dépendance devient une panne totale.

## Le cache

Un cache garde le résultat d'une opération coûteuse près de celui qui en a besoin. Il
sert à **réduire la latence** et à **protéger** le système de stockage.

### Les stratégies

| Stratégie | Lecture | Écriture |
|---|---|---|
| **Cache-aside** (la plus courante) | l'application lit le cache ; en cas d'absence, elle lit la base et remplit le cache | l'application écrit en base puis **supprime** l'entrée du cache |
| **Read-through** | le cache lit lui-même la base en cas d'absence | — |
| **Write-through** | — | on écrit dans le cache, qui écrit en base de façon synchrone |
| **Write-behind** (*write-back*) | — | on écrit dans le cache, qui écrit en base plus tard ; rapide, mais risque de perte |

### L'éviction

Un cache a une taille limitée. Démonstration réelle : Redis limité à 2 Mo
(`maxmemory 2mb`, politique `allkeys-lru`), dans lequel on écrit 20 000 pages de 200 octets.

```
clés gardées : 3821
evicted_keys : 16265
page:0      → absente (la plus ancienne, évincée)
page:19999  → présente (la plus récente)
```

**LRU** (*least recently used*) évince ce qui n'a pas servi depuis le plus longtemps ;
**LFU** ce qui sert le moins souvent ; un **TTL** fait expirer une entrée après un délai.

### Les pièges

- **L'invalidation** : « Il n'y a que deux choses difficiles en informatique :
  l'invalidation de cache et nommer les choses » (Phil Karlton). Après une écriture, on
  **supprime** l'entrée plutôt que de la mettre à jour (deux mises à jour concurrentes
  peuvent laisser la mauvaise valeur), et on garde un TTL comme filet de sécurité.
- **La ruée** (*cache stampede*) : une entrée très demandée expire, et 10 000 requêtes
  recalculent la même valeur en même temps. Remèdes : un seul recalcul à la fois (verrou ou
  *single flight*), expiration anticipée aléatoire, servir l'ancienne valeur pendant le
  recalcul.
- **Le cache froid** : après un redémarrage, toute la charge tombe sur la base. Un système
  qui ne survit **qu'avec** son cache doit le savoir et le préchauffer.

## Le CDN

Un **CDN** (*Content Delivery Network* : Cloudflare, Akamai, CloudFront, Fastly) est un
cache réparti dans des centaines de villes. L'utilisateur de Sydney télécharge l'image depuis
Sydney (quelques ms) et non depuis Paris (250 à 300 ms d'aller-retour, multipliés par les
allers-retours TCP et TLS).

- On y met le contenu **statique** (images, JS, CSS, vidéos), et de plus en plus des pages
  et des réponses d'API mises en cache quelques secondes.
- On contrôle la durée avec `Cache-Control: max-age=…`. Pour les fichiers versionnés
  (`app.3f9a2c.js`), un an et `immutable` : un nouveau déploiement change le nom.
- Bonus : le CDN absorbe les pics et une partie des attaques par déni de service.

## La réplication des bases de données

Répliquer, c'est garder des copies de la base sur plusieurs machines : pour **survivre** à
la perte d'une machine, et pour **répartir les lectures**.

Le modèle le plus courant est **leader-suiveurs** (*primary-replica*) : toutes les écritures
vont au leader, qui envoie son journal (le WAL pour PostgreSQL) aux répliques.

- **Synchrone** : le leader attend la confirmation d'une réplique avant de valider. Aucune
  perte si le leader meurt, mais chaque écriture paie un aller-retour réseau.
- **Asynchrone** (le défaut) : le leader valide tout de suite. Rapide, mais les répliques
  ont du **retard**, et une panne du leader peut perdre les dernières écritures.

Démonstration réelle avec PostgreSQL 16 : un primaire et une réplique en streaming
asynchrone, **sur la même machine**. On insère une ligne sur le primaire, puis on la relit
immédiatement sur la réplique, 1 000 fois de suite :

```
994 lectures périmées sur 1000 (99%), réplique asynchrone sur la même machine
```

Même sans réseau, la réplique a presque toujours un léger retard. Avec un retard simulé de
2 secondes (`recovery_min_apply_delay`) : lecture immédiate, `0 ligne(s)` ; 2,5 s plus
tard, `1 ligne(s)`.

Conséquence : l'utilisateur qui modifie son profil puis recharge la page peut voir
l'**ancienne** version. Garantir la **lecture de ses propres écritures** (*read your
writes*) :

- lire depuis le leader pendant quelques secondes après une écriture de cet utilisateur ;
- ou lire sur une réplique qui a rattrapé la position (LSN) de l'écriture ;
- ou lire toujours sur le leader ce que l'utilisateur vient de modifier (son propre profil).

Autres modèles : **multi-leader** (plusieurs régions acceptent les écritures, avec des
conflits à résoudre) et **sans leader** (Cassandra, DynamoDB : on écrit sur W répliques, on
lit sur R, avec `R + W > N` pour lire au moins une copie à jour).

## À retenir

- Sans état : l'état déménage dans Redis, S3, la base ou un jeton signé.
- L4 ou L7 ; moins de connexions ou deux choix au hasard ; sondes de santé pas trop profondes.
- Cache-aside, éviction LRU et TTL ; supprimer après écriture ; se méfier des ruées et du cache froid.
- CDN : le contenu près de l'utilisateur, piloté par Cache-Control.
- Réplication asynchrone = retard (99 % des lectures immédiates périmées, même en local) : prévoir la lecture de ses propres écritures.
