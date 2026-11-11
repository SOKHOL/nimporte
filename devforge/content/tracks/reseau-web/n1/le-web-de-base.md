---
id: reseau-web.n1.le-web-de-base
title: Client, serveur, HTTP, DNS et JSON
skills: [client-serveur, http, dns, json]
duration_min: 17
---

# Client, serveur, HTTP, DNS et JSON

Les échanges de cette leçon ont été capturés avec `curl` sur un petit serveur local.

## Client, serveur et URL

Un **client** (navigateur, application mobile, autre service) envoie une **requête** ; un
**serveur** renvoie une **réponse**. Le serveur ne contacte jamais le client de lui-même en
HTTP classique : c'est toujours le client qui demande.

Une URL décrit où et comment joindre une ressource :

```
https://api.exemple.com:443/produits/42?devise=EUR&lang=fr#avis
└─┬─┘   └──────┬──────┘└┬┘└────┬─────┘└────────┬────────┘└┬┘
schéma        hôte    port  chemin        paramètres    fragment
```

- Le **schéma** dit le protocole (`https` : HTTP chiffré, port 443 par défaut).
- Le **fragment** (`#avis`) n'est jamais envoyé au serveur : il reste dans le navigateur.
- Les caractères spéciaux sont **encodés** : un espace devient `%20`, un `&` dans une valeur
  `%26`.

## HTTP

Une requête et sa réponse, réelles :

```
> GET /produits/42 HTTP/1.1
> Host: 127.0.0.1:8081
> User-Agent: curl/8.5.0
> Accept: */*

< HTTP/1.1 200 OK
< Content-Type: application/json
< ETag: "019d34d2a31a0556"
< Cache-Control: max-age=60
< Content-Length: 42

{"id": 42, "nom": "Clavier", "prix": 49.9}
```

Une ligne de requête (méthode, chemin, version), des **en-têtes**, une ligne vide, puis un
corps éventuel. HTTP/1.1 est un protocole **texte** et **sans état** : chaque requête se suffit
à elle-même.

### Les méthodes

| Méthode | Sens | Sûre (ne modifie rien) | Idempotente |
|---|---|---|---|
| `GET` | lire | oui | oui |
| `HEAD` | lire seulement les en-têtes | oui | oui |
| `POST` | créer, déclencher une action | non | **non** |
| `PUT` | remplacer entièrement | non | oui |
| `PATCH` | modifier en partie | non | pas forcément |
| `DELETE` | supprimer | non | oui |

**Idempotente** : la répéter a le même effet qu'une seule fois (supprimer deux fois la
ressource 42 la laisse supprimée). C'est ce qui permet aux clients, proxys et bibliothèques de
**réessayer** sans danger. Un `GET` qui modifie des données est un piège : des robots, des
préchargements de navigateur ou des caches peuvent l'appeler sans prévenir.

### Les codes de réponse

| Famille | Sens | Exemples |
|---|---|---|
| 2xx | succès | `200 OK`, `201 Created`, `204 No Content` |
| 3xx | redirection, ou « utilise ta copie » | `301`, `302`, `304 Not Modified` |
| 4xx | erreur **du client** (ne pas réessayer tel quel) | `400`, `401` (pas authentifié), `403` (pas autorisé), `404`, `409` (conflit), `422`, `429` (trop de requêtes) |
| 5xx | erreur **du serveur** (on peut réessayer, prudemment) | `500`, `502` (passerelle), `503` (indisponible), `504` (délai dépassé) |

`401` veut dire « je ne sais pas qui tu es », `403` « je sais qui tu es, et c'est non ».
Répondre `200` avec `{"erreur": "…"}` dans le corps casse les caches, la supervision et les
réessais : le code doit dire la vérité.

### Les en-têtes utiles

`Content-Type` (le format du corps), `Accept` (ce que le client sait lire), `Authorization`
(les identifiants), `Cache-Control` et `ETag` (le cache, niveau 2), `User-Agent`, et
`Location` (où se trouve la ressource créée, avec un `201`).

## DNS

Les machines communiquent avec des **adresses IP** ; les humains utilisent des **noms**. Le
**DNS** traduit les uns en les autres. Réponse réelle pour `github.com` :

```
github.com A TTL=60 : 140.82.112.3
```

Le chemin d'une résolution : le cache local, puis le **résolveur** (celui du fournisseur
d'accès, 8.8.8.8, 1.1.1.1), qui interroge si besoin les serveurs **racine**, puis ceux du
domaine de premier niveau (`.com`), puis les serveurs **faisant autorité** pour le domaine.

Les enregistrements principaux :

| Type | Contient |
|---|---|
| `A` / `AAAA` | une adresse IPv4 / IPv6 |
| `CNAME` | un alias vers un autre nom |
| `MX` | les serveurs de courrier |
| `TXT` | du texte (vérifications de domaine, SPF, DKIM) |
| `NS` | les serveurs qui font autorité pour le domaine |

Le **TTL** (ici 60 secondes) dit combien de temps une réponse peut être gardée en cache. Un
TTL long allège les serveurs ; un TTL court permet de changer d'adresse vite. Avant une
migration, on baisse le TTL **plusieurs jours à l'avance**, sinon les caches gardent
l'ancienne adresse pendant tout l'ancien TTL. Et quand « une partie des utilisateurs ne voit
pas le changement », c'est souvent le DNS.

## JSON et les formats d'échange

**JSON** est le format d'échange dominant des API web : lisible, simple, compris partout.

```json
{"id": 42, "nom": "Clavier", "prix": 49.9, "tags": ["usb", "azerty"], "promo": null}
```

Ses pièges :

- **Pas de type date** : on utilise des chaînes au format ISO 8601 (`"2026-11-11T10:30:00Z"`).
- **Les nombres** : beaucoup de bibliothèques les lisent comme des flottants 64 bits.
  JavaScript ne représente exactement les entiers que jusqu'à 2⁵³ − 1 : un identifiant de 64
  bits peut être corrompu silencieusement. On transmet les grands identifiants **en chaîne**.
- **L'argent** : `49.9` en flottant n'est pas exact ; on préfère les centimes en entier
  (`4990`) ou une chaîne décimale.
- **Champ absent ou `null`** : à définir clairement dans le contrat de l'API.

Alternatives : **Protocol Buffers** (binaire, compact, typé, utilisé par gRPC), XML (encore
présent dans les systèmes plus anciens), MessagePack, CBOR.

## À retenir

- URL : schéma, hôte, port, chemin, paramètres ; le fragment ne part pas au serveur.
- HTTP : méthode, en-têtes, corps ; GET sûr, PUT et DELETE idempotents, POST non ; le code de réponse dit la vérité (4xx client, 5xx serveur).
- DNS : nom → IP, avec un TTL ; baisser le TTL avant une migration.
- JSON : dates en ISO 8601, grands identifiants en chaîne, argent en centimes.
