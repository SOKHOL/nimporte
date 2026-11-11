---
id: reseau-web.n5.performance-et-bord-du-reseau
title: Performance réseau, proxys, passerelles et edge
skills: [performance-reseau, proxys, edge]
duration_min: 19
---

# Performance réseau, proxys, passerelles et edge

## La latence, unité de base

Sur un vrai réseau, c'est presque toujours le nombre d'**allers-retours** (RTT) qui domine,
pas le débit. Avec 100 ms d'aller-retour (un mobile en 4G loin du serveur), une première
requête HTTPS coûte :

| Étape | Allers-retours |
|---|---|
| Résolution DNS (si pas en cache) | 1 |
| Poignée de main TCP | 1 |
| Poignée de main TLS 1.3 | 1 |
| La requête HTTP elle-même | 1 |
| **Total avant le premier octet** | **4, soit environ 400 ms** |

Sur une connexion **déjà ouverte**, il ne reste qu'un aller-retour. Mesures réelles en local
(sans distance) : 2,34 ms par requête sur une nouvelle connexion HTTPS, 0,17 ms sur une
connexion réutilisée. Leviers, par ordre d'impact :

1. **Réutiliser les connexions** (keep-alive, pools de connexions HTTP, HTTP/2 qui multiplexe
   tout sur une connexion).
2. **Réduire le nombre de requêtes en série** : une page qui charge un script qui charge un
   JSON qui déclenche une image fait trois allers-retours de suite. On regroupe, on précharge
   (`<link rel="preload">`, `preconnect`), on évite les cascades.
3. **Se rapprocher de l'utilisateur** : CDN et edge (plus bas).
4. **Envoyer moins d'octets** : compression, images modernes (WebP, AVIF), champs inutiles.

## La compression

Mesure réelle sur une réponse JSON de 2 000 produits :

| | Taille |
|---|---|
| JSON brut | 90 890 octets |
| gzip (niveau 9) | 5 139 octets |

Dix-sept fois moins, parce qu'un JSON répète les mêmes noms de champs. Brotli et zstd
compressent encore un peu mieux. Le client annonce `Accept-Encoding: gzip, br, zstd`, le
serveur répond avec `Content-Encoding`. On ne compresse pas ce qui l'est déjà (images, vidéos,
archives), et on n'insère pas de secret dans une réponse compressée qui contient aussi des
données contrôlées par un attaquant (attaques BREACH).

## Le démarrage lent et les petits détails de TCP

- Une nouvelle connexion TCP commence avec une **fenêtre de congestion** d'environ 10
  segments (environ 14 Ko) : la première réponse est envoyée par petites vagues, avec un
  aller-retour entre chacune. Un HTML initial de moins de 14 Ko arrive en une seule vague.
- L'algorithme de **Nagle** combiné à l'**accusé de réception retardé** peut ajouter environ
  40 ms à chaque requête (mesuré au niveau 3 : 44 ms → 0,17 ms avec `TCP_NODELAY`).
- Les **délais d'expiration** se règlent explicitement à chaque appel (connexion, lecture,
  total) : sans eux, un service lent bloque des threads ou des connexions indéfiniment, et la
  lenteur se propage à tous ceux qui l'appellent.

## Les proxys inverses

Un **proxy inverse** (*reverse proxy* : Nginx, HAProxy, Envoy, Traefik, Caddy) se place
devant les serveurs d'application. Les clients ne parlent qu'à lui. Il prend en charge :

- la **terminaison TLS** (les certificats à un seul endroit) ;
- la **répartition de charge** et les sondes de santé (parcours System design, niveau 2) ;
- le **routage** par nom de domaine et par chemin ;
- la compression, le cache, les en-têtes de sécurité, les limites de taille de requête ;
- les **délais d'expiration** et les **réessais** vers les serveurs (seulement pour les
  requêtes idempotentes, sinon une commande peut être passée deux fois).

Points d'attention : l'application voit l'IP du proxy, pas celle du client ; l'IP réelle passe
par l'en-tête `X-Forwarded-For` (ou `Forwarded`), qu'il ne faut croire **que** s'il vient d'un
proxy de confiance, sinon n'importe qui peut prétendre venir de n'importe où.

Un **proxy direct** (*forward proxy*), à l'inverse, est utilisé par les **clients** pour
sortir (un proxy d'entreprise, ou celui qui filtre l'accès réseau d'un environnement isolé).

## Les passerelles d'API

Une **passerelle d'API** (*API gateway* : Kong, AWS API Gateway, Apigee, Envoy Gateway) est un
proxy inverse spécialisé pour les API :

- **authentification** centralisée (vérification des jetons) ;
- **limitation de débit** et quotas par client ou par offre ;
- **routage** vers les microservices, et parfois agrégation de plusieurs appels en un ;
- transformation (versions, formats), journalisation et métriques uniformes.

Le risque : y mettre de la **logique métier**. La passerelle devient alors un monolithe que
toutes les équipes doivent modifier ensemble. Elle reste une couche technique ; le métier
reste dans les services. Variante courante : un **BFF** (*Backend for Frontend*), une petite
couche propre à chaque interface (web, mobile) qui assemble les appels dont elle a besoin.

Un **service mesh** (Istio, Linkerd) applique les mêmes fonctions (TLS mutuel, réessais,
délais, métriques) **entre** les services, avec un proxy à côté de chacun.

## Le CDN et l'edge computing

Un **CDN** garde des copies des contenus dans des centaines de villes (parcours System design,
niveau 2) : l'utilisateur de Sydney est servi depuis Sydney. Il **termine aussi le TLS** près
de l'utilisateur, et garde des connexions ouvertes et « chaudes » vers le serveur d'origine :
même pour une réponse non cachable, les allers-retours longs sont réduits.

L'**edge computing** (Cloudflare Workers, Fastly Compute, Vercel et Netlify Edge Functions,
Lambda@Edge) exécute **du code** dans ces points de présence :

- redirections, tests A/B, choix de langue ou de pays ;
- vérification d'un jeton avant d'atteindre l'origine ;
- personnalisation d'une page mise en cache ;
- réponses complètes pour des données répliquées près des utilisateurs (clé-valeur distribué).

Les limites : peu de temps CPU et de mémoire par requête, des environnements restreints (pas
toutes les bibliothèques), et surtout les **données** : si la fonction doit interroger une base
à l'autre bout du monde, elle refait le long aller-retour qu'on voulait éviter. L'edge brille
pour ce qui ne dépend que de la requête ou de données répliquées.

## À retenir

- La latence se compte en allers-retours : 4 RTT pour une première requête HTTPS, 1 sur une connexion réutilisée.
- Réutiliser les connexions, éviter les cascades, compresser (90 890 → 5 139 octets mesurés), régler les délais d'expiration.
- Proxy inverse : TLS, répartition, routage, réessais idempotents seulement ; `X-Forwarded-For` uniquement depuis un proxy de confiance.
- Passerelle d'API : authentification, quotas, routage, sans logique métier ; BFF par interface.
- CDN et edge : rapprocher contenu et code de l'utilisateur ; attention aux données lointaines.
