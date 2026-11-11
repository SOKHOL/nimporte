---
id: reseau-web.n3.sous-http
title: TCP, UDP, TLS, HTTP/2, HTTP/3 et WebSockets
skills: [tcp-udp, tls, http2-http3, websockets]
duration_min: 21
---

# TCP, UDP, TLS, HTTP/2, HTTP/3 et WebSockets

## Les couches

Une requête HTTPS traverse plusieurs couches, chacune avec son rôle :

| Couche | Protocole | Rôle |
|---|---|---|
| Application | HTTP | le sens de l'échange (méthode, ressource, en-têtes) |
| Sécurité | TLS | chiffrement, intégrité, authentification du serveur |
| Transport | TCP ou UDP | acheminer les données entre deux **ports** de deux machines |
| Réseau | IP | acheminer des paquets d'une **adresse** à une autre, de routeur en routeur |
| Liaison | Ethernet, Wi-Fi | transmettre sur le support physique |

## TCP et UDP

**TCP** fournit un flux d'octets **fiable et ordonné** :

- une **poignée de main en trois temps** ouvre la connexion (SYN, SYN-ACK, ACK) : un
  aller-retour avant de pouvoir envoyer quoi que ce soit ;
- chaque segment est numéroté et acquitté ; ce qui est perdu est **retransmis** ;
- le **contrôle de flux** et le **contrôle de congestion** adaptent le débit (démarrage lent,
  ou *slow start* : une nouvelle connexion n'envoie que quelques segments avant d'accélérer).

**UDP** envoie des datagrammes indépendants, **sans garantie** de livraison ni d'ordre, mais
sans poignée de main ni retransmission : DNS, vidéo et voix en direct, jeux, et QUIC (HTTP/3),
qui reconstruit sa propre fiabilité par-dessus.

Un **port** identifie un service sur une machine : 80 (HTTP), 443 (HTTPS), 22 (SSH), 5432
(PostgreSQL). Une connexion TCP est identifiée par le quadruplet (IP source, port source,
IP destination, port destination).

Un piège réel de TCP, mesuré sur un petit serveur HTTPS en Python : chaque requête prenait
**44 ms**, alors que le serveur répond en une fraction de milliseconde. Cause : l'**algorithme
de Nagle** (TCP retient un petit envoi tant que le précédent n'est pas acquitté) combiné à
l'**accusé de réception retardé** de l'autre côté (qui attend jusqu'à 40 ms avant d'acquitter).
Le serveur écrivait les en-têtes et le corps en deux fois. Avec l'option `TCP_NODELAY` :
**0,17 ms**. Les serveurs web et bibliothèques HTTP modernes l'activent ; une latence
mystérieuse d'environ 40 ms est souvent ce phénomène.

## TLS

**TLS** (la couche de sécurité de HTTPS) apporte trois garanties :

- **confidentialité** : personne sur le chemin ne lit les données ;
- **intégrité** : personne ne les modifie sans que ça se voie ;
- **authentification** : on parle bien au serveur `exemple.com`, grâce à son **certificat**.

La poignée de main TLS 1.3 (sortie réelle d'`openssl s_client` sur le serveur de test) :

```
New, TLSv1.3, Cipher is TLS_AES_256_GCM_SHA384
subject=CN = localhost
issuer=CN = localhost
Verify return code: 18 (self-signed certificate)
```

1. Le client propose ses algorithmes et une part d'échange de clés (Diffie-Hellman sur courbe
   elliptique).
2. Le serveur choisit, envoie sa part, son **certificat**, et une signature qui prouve qu'il
   possède la clé privée correspondante.
3. Les deux côtés calculent la même clé de session ; tout le reste est chiffré
   symétriquement (ici AES-256-GCM).

TLS 1.3 ne demande **qu'un aller-retour** (contre deux en TLS 1.2), et zéro pour une reprise
de session (0-RTT, avec des précautions : les données 0-RTT peuvent être rejouées).

**Le certificat** lie un nom de domaine à une clé publique, signé par une **autorité de
certification** (AC). Le navigateur remonte la **chaîne** jusqu'à une AC racine qu'il connaît
déjà. Ici, le certificat est **auto-signé** (émetteur = sujet), donc la vérification échoue
(code 18) : acceptable en local, jamais en production. Let's Encrypt délivre des certificats
gratuits pour 90 jours, renouvelés automatiquement ; un certificat expiré est une cause
classique de panne totale.

Le coût réel, mesuré en local : connexion TCP seule **0,04 ms** ; TCP + poignée de main TLS
1.3 **2,81 ms**, surtout du calcul cryptographique. Sur un vrai réseau, s'ajoute l'aller-retour.
D'où l'importance de **réutiliser les connexions** : une requête HTTPS sur une connexion
gardée ouverte a pris 0,17 ms, contre 2,34 ms avec une nouvelle connexion.

## HTTP/2 et HTTP/3

**HTTP/1.1** ne traite qu'une requête à la fois par connexion : les navigateurs ouvrent 6
connexions par domaine, et une réponse lente bloque celles qui suivent sur la même connexion
(*head-of-line blocking*).

**HTTP/2** (2015) garde la même sémantique (méthodes, codes, en-têtes) mais change le
transport :

- un format **binaire** en trames ;
- le **multiplexage** : de nombreuses requêtes en parallèle sur **une seule** connexion ;
- la **compression des en-têtes** (HPACK) : les en-têtes répétés coûtent presque rien.

Il est négocié pendant la poignée de main TLS, par l'extension **ALPN**. Sortie réelle avec
`curl --http2` vers un serveur Node :

```
* ALPN: curl offers h2,http/1.1
* ALPN: server accepted h2
* using HTTP/2
< HTTP/2 200
{"protocole":"2.0"}
```

Le même serveur répond `{"protocole":"1.1"}` à un client qui ne propose que HTTP/1.1.

Mais HTTP/2 tourne sur TCP : si **un** paquet est perdu, TCP bloque tout le flux jusqu'à sa
retransmission, donc toutes les requêtes multiplexées attendent.

**HTTP/3** (2022) remplace TCP par **QUIC**, sur UDP :

- chaque flux est indépendant : une perte ne bloque que son flux ;
- poignée de main QUIC et TLS 1.3 combinées : un seul aller-retour, voire zéro ;
- **migration de connexion** : passer du Wi-Fi à la 4G sans recommencer la connexion.

Le gain est surtout visible sur les réseaux mobiles avec des pertes. Le serveur annonce HTTP/3
avec l'en-tête `Alt-Svc`, et le navigateur l'essaie à la connexion suivante.

## WebSockets et temps réel

HTTP est conçu pour « le client demande, le serveur répond ». Pour que le serveur **pousse**
des données (messagerie, cours de bourse, édition collaborative), plusieurs techniques :

| Technique | Principe | Usage |
|---|---|---|
| Interrogation régulière (*polling*) | le client redemande toutes les N secondes | simple, mais du trafic inutile et du retard |
| *Long polling* | le serveur garde la requête ouverte jusqu'à avoir quelque chose | compatible partout |
| **Server-Sent Events** (SSE) | une réponse HTTP qui ne se termine jamais, ligne par ligne | flux serveur → client (notifications, flux de texte d'une IA) |
| **WebSocket** | une connexion bidirectionnelle permanente | messagerie, jeux, collaboration |

Un WebSocket commence par une requête HTTP qui demande de **changer de protocole**. Échange
réel avec un serveur minimal :

```
GET /chat HTTP/1.1
Connection: Upgrade
Upgrade: websocket
Sec-WebSocket-Version: 13
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==

HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
```

Puis la connexion transporte des **trames** : ici les octets `0x81 0x07` suivis de `bonjour`
(`0x81` = trame finale de type texte, `0x07` = 7 octets). Le serveur prouve qu'il a compris la
demande en renvoyant un hachage SHA-1 de la clé et d'une constante fixée par la norme.

À l'échelle : chaque client garde une connexion ouverte (des centaines de milliers par
serveur, avec un registre pour savoir qui est connecté où), les répartiteurs de charge doivent
supporter les connexions longues, il faut des **pings** pour détecter les connexions mortes,
et le client doit se **reconnecter** avec un délai croissant et récupérer ce qu'il a manqué.

## À retenir

- TCP : fiable, ordonné, poignée de main, retransmissions ; UDP : sans garantie, sans attente. Une latence d'environ 40 ms : penser Nagle et `TCP_NODELAY` (44 ms → 0,17 ms mesurés).
- TLS 1.3 : confidentialité, intégrité, authentification par certificat ; un aller-retour ; coût mesuré 2,8 ms en local, d'où la réutilisation des connexions.
- HTTP/2 : binaire, multiplexé, négocié par ALPN ; HTTP/3 : QUIC sur UDP, sans blocage d'une perte sur tous les flux.
- Temps réel : polling, SSE, WebSocket (`101 Switching Protocols`, puis des trames) ; pings et reconnexion.
