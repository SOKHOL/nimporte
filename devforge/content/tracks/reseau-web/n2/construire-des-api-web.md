---
id: reseau-web.n2.construire-des-api-web
title: REST, cookies, CORS et cache HTTP
skills: [rest, cookies-sessions, cors, cache-http]
duration_min: 19
---

# REST, cookies, CORS et cache HTTP

## REST : des ressources et des verbes

**REST** (Roy Fielding, 2000) organise une API autour de **ressources** désignées par des URL,
manipulées avec les méthodes HTTP :

| Requête | Sens | Réponse typique |
|---|---|---|
| `GET /commandes?statut=payee&page=2` | lister, filtrer, paginer | `200` |
| `GET /commandes/42` | lire une commande | `200`, ou `404` |
| `POST /commandes` | créer | `201` + en-tête `Location: /commandes/43` |
| `PUT /commandes/42` | remplacer entièrement | `200` ou `204` |
| `PATCH /commandes/42` | modifier en partie | `200` |
| `DELETE /commandes/42` | supprimer | `204` |
| `GET /clients/7/commandes` | ressource liée | `200` |

Règles de style :

- des **noms** au pluriel, pas des verbes (`/commandes`, pas `/creerCommande`) ;
- une action qui ne rentre pas dans le CRUD devient une ressource ou une sous-ressource :
  `POST /commandes/42/annulation` ;
- les codes HTTP disent le résultat (niveau 1), et les erreurs ont un format cohérent
  (niveau 4).

**L'idempotence en pratique** : `POST` n'est pas idempotent, donc un réessai après une coupure
peut créer deux commandes. On ajoute un en-tête `Idempotency-Key` généré par le client ; le
serveur garde la réponse associée à la clé et la renvoie si la même clé revient (parcours
System design, niveau 3).

## Cookies et sessions

HTTP est sans état : pour reconnaître un utilisateur d'une requête à l'autre, le serveur lui
confie un **cookie**, que le navigateur renvoie automatiquement. Échange réel :

```
GET /connexion
< Set-Cookie: session=8f3a9c; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=3600

GET /moi                        → 401  (sans cookie)
GET /moi   Cookie: session=8f3a9c   → 200  Ada
```

Les attributs du cookie sont des protections, à mettre systématiquement :

| Attribut | Effet |
|---|---|
| `HttpOnly` | invisible pour JavaScript : un script injecté (XSS) ne peut pas le voler |
| `Secure` | envoyé seulement en HTTPS |
| `SameSite=Lax` (ou `Strict`) | pas envoyé avec les requêtes venues d'autres sites (sauf navigation simple pour `Lax`) : protège contre le CSRF |
| `Max-Age` / `Expires` | durée de vie ; sans eux, cookie de session supprimé à la fermeture du navigateur |
| `Domain`, `Path` | où le cookie est envoyé |

Deux façons de gérer une session :

- **Session côté serveur** : le cookie contient un identifiant aléatoire ; les données sont
  dans Redis ou en base. Révocation immédiate (on supprime la session), mais un stockage
  partagé est nécessaire.
- **Jeton autoportant** (JWT, niveau 4) : le jeton contient les informations, signées.
  Aucun stockage, mais révocation difficile avant l'expiration.

## CORS et la politique de même origine

Une **origine** = schéma + hôte + port. Par défaut, le navigateur interdit à une page de
`https://app.exemple.com` de **lire** la réponse d'une requête faite par JavaScript vers
`https://api.exemple.com` : c'est la **politique de même origine**. Sans elle, n'importe quel
site visité pourrait lire vos e-mails avec vos cookies.

**CORS** (*Cross-Origin Resource Sharing*) permet au serveur de l'API d'autoriser certaines
origines. Pour une requête « non simple » (méthode `PUT`/`DELETE`, en-tête `Authorization`,
JSON…), le navigateur envoie d'abord une **requête de contrôle** (*preflight*) `OPTIONS`.
Réponses réelles :

```
OPTIONS /produits/42   Origin: https://app.exemple.com   Access-Control-Request-Method: DELETE
< HTTP/1.1 204 No Content
< Access-Control-Allow-Origin: https://app.exemple.com
< Access-Control-Allow-Methods: GET, POST, DELETE
< Access-Control-Allow-Headers: Content-Type, Authorization
< Access-Control-Max-Age: 600

OPTIONS /produits/42   Origin: https://pirate.exemple
< HTTP/1.1 204 No Content
  (aucun en-tête Access-Control : le navigateur bloque la requête)
```

Points clés :

- CORS est appliqué **par le navigateur**. `curl`, un script ou un autre serveur l'ignorent :
  CORS **n'est pas** une protection de l'API contre les appels, juste une règle pour les
  pages web.
- `Access-Control-Allow-Origin: *` est interdit avec des cookies
  (`Access-Control-Allow-Credentials: true`) ; et renvoyer aveuglément l'origine reçue revient
  à autoriser n'importe quel site à lire les données de l'utilisateur connecté.
- `Access-Control-Max-Age` met le résultat du preflight en cache (ici 10 minutes) pour éviter
  un aller-retour de plus à chaque requête.
- L'erreur « CORS » dans la console masque souvent une autre erreur (un 500 sans en-têtes
  CORS) : regarder la réponse réelle dans l'onglet réseau.

## Le cache HTTP

HTTP a un système de cache complet, utilisé par les navigateurs, les proxys et les CDN.

**La fraîcheur** : `Cache-Control: max-age=60` autorise à réutiliser la réponse 60 secondes
sans rien demander. Les directives utiles :

| Directive | Sens |
|---|---|
| `max-age=N` | fraîche pendant N secondes |
| `no-cache` | on peut garder une copie, mais il faut la **revalider** avant de s'en servir |
| `no-store` | ne rien garder (données sensibles) |
| `private` | seul le navigateur peut la garder, pas un cache partagé (CDN) |
| `public, max-age=31536000, immutable` | fichiers versionnés (`app.3f9a2c.js`) |

**La revalidation** : une fois la copie périmée, le client demande « ai-je toujours la bonne
version ? » avec l'**ETag** reçu. Échange réel :

```
GET /produits/42                                     → 200, 42 octets, ETag: "019d34d2a31a0556"
GET /produits/42   If-None-Match: "019d34d2a31a0556" → 304 Not Modified, 0 octet
```

Le serveur a quand même été appelé, mais n'a rien renvoyé : sur une grosse réponse ou un
réseau mobile, l'économie est importante.

Le piège : une réponse personnalisée (le panier de l'utilisateur) mise en cache par un CDN
avec `public` peut être servie… à un autre utilisateur. Toute réponse qui dépend de
l'utilisateur est `private` ou `no-store`.

## À retenir

- REST : des ressources au pluriel, les méthodes HTTP, `201` + `Location` ; `Idempotency-Key` pour les POST.
- Cookies : `HttpOnly`, `Secure`, `SameSite` ; session serveur (révocable) ou jeton (sans stockage).
- Même origine = schéma + hôte + port ; CORS autorise des origines, appliqué par le navigateur uniquement ; preflight `OPTIONS`.
- Cache : `max-age`, `no-cache`, `no-store`, `private` ; ETag et `304` ; jamais de cache public pour une réponse personnelle.
