---
id: reseau-web.n4.authentifier-et-concevoir
title: OAuth 2, OpenID Connect, JWT, conception d'API, gRPC et GraphQL
skills: [oauth2-oidc, jwt, conception-api, grpc-graphql]
duration_min: 23
---

# OAuth 2, OpenID Connect, JWT, conception d'API, gRPC et GraphQL

## OAuth 2 : déléguer un accès

Une application de photos veut publier sur ton compte d'un réseau social. Lui donner ton mot
de passe serait une catastrophe : elle aurait tous les droits, pour toujours. **OAuth 2**
permet à une application d'obtenir un **jeton d'accès limité** (certaines permissions, ou
*scopes*, pendant un temps limité), délivré par un **serveur d'autorisation** après ton
accord.

Le flux recommandé pour les applications web et mobiles est le **code d'autorisation avec
PKCE** :

1. L'application crée un secret aléatoire (`code_verifier`) et envoie son empreinte
   (`code_challenge`) en redirigeant l'utilisateur vers le serveur d'autorisation.
2. L'utilisateur s'authentifie **chez le serveur d'autorisation** (jamais dans l'application)
   et accepte les permissions demandées.
3. Le serveur redirige vers l'application avec un **code** à usage unique.
4. L'application échange ce code, avec le `code_verifier`, contre un **jeton d'accès** (et
   souvent un **jeton de rafraîchissement**).
5. Elle appelle l'API avec `Authorization: Bearer <jeton>`.

PKCE empêche qu'un code intercepté (par une autre application sur le téléphone, par exemple)
soit échangé : il faut aussi le secret initial. Les anciens flux (implicite, mot de passe
transmis à l'application) sont déconseillés.

Pour les échanges **entre services**, sans utilisateur : le flux *client credentials*
(identifiant et secret du service, ou mieux, un certificat).

## OpenID Connect : savoir qui est l'utilisateur

OAuth 2 donne un **accès**, il ne dit pas **qui** est l'utilisateur. **OpenID Connect**
(OIDC) ajoute un **jeton d'identité** (`id_token`, un JWT) qui décrit l'utilisateur
authentifié. C'est ce qu'utilisent « Se connecter avec Google / Apple / Microsoft » et les
fournisseurs d'identité d'entreprise (Keycloak, Okta, Entra ID, Auth0). Ne jamais réinventer
l'authentification : déléguer à un fournisseur éprouvé.

## Les jetons JWT

Un **JWT** (*JSON Web Token*) est composé de trois parties en Base64URL séparées par des
points : en-tête, **charge utile** (*claims*), signature. Jeton réel, construit en Python :

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZGEiLCJyb2xlIjoibGVjdGV1ciIsImV4cCI6MTc5NDQwMDAwMH0.rolas5F2urKaEjIxIpc6F1h0rxaiVb60qagWJHI7060
```

Ce qu'il faut comprendre, démontré sur ce jeton :

- **La charge utile n'est pas chiffrée** : décodée sans aucune clé, elle donne
  `{'sub': 'ada', 'role': 'lecteur', 'exp': 1794400000}`. Jamais de secret dans un JWT.
- **La signature empêche la modification** : en remplaçant le rôle par `admin` sans connaître
  la clé, la vérification répond `signature invalide`.
- **Le piège `alg: none`** : un vérificateur naïf qui fait confiance à l'algorithme annoncé
  **par le jeton lui-même** accepte un jeton sans signature. Réel, avec un tel vérificateur :
  `{'sub': 'mallory', 'role': 'admin'}` accepté. Le serveur doit **imposer** l'algorithme
  attendu (et la clé) ; les bonnes bibliothèques le font si on le leur demande.

Autres règles :

- vérifier `exp` (expiration), `iss` (émetteur) et `aud` (le jeton est-il destiné à **cette**
  API ?) ;
- des jetons d'accès **courts** (5 à 15 minutes) et un jeton de rafraîchissement révocable,
  car un JWT ne se révoque pas avant son expiration sans liste de révocation ;
- `HS256` (secret partagé) entre un émetteur et une API ; `RS256`/`ES256` (clé publique) dès
  que plusieurs services vérifient : ils n'ont alors que la clé publique, publiée par
  l'émetteur (JWKS).

## Concevoir une API

Une API publique est un **contrat** : une fois utilisée, chaque détail devient impossible à
changer sans casser quelqu'un.

**Décrire le contrat** avec **OpenAPI** (un fichier YAML qui décrit routes, paramètres,
schémas et erreurs) : documentation, génération de clients, validation et tests de contrat
en découlent. On peut l'écrire **avant** le code (*API first*).

**Des erreurs cohérentes**, par exemple au format standard *Problem Details* (RFC 9457) :

```json
{
  "type": "https://api.exemple.com/erreurs/stock-insuffisant",
  "title": "Stock insuffisant",
  "status": 409,
  "detail": "Il reste 2 claviers, 5 demandés.",
  "instance": "/commandes/7731"
}
```

**La pagination** : par **curseur** plutôt que par numéro de page sur les grandes listes.
Avec `offset=100000`, la base doit lire et ignorer 100 000 lignes, et des éléments sautent ou
se répètent si la liste change entre deux pages. Un curseur (« après l'identifiant 8812 »)
reste rapide et stable.

**Les versions et l'évolution** : ajouter un champ ou une route est compatible ; renommer,
supprimer ou changer un type ne l'est pas. Les clients doivent **ignorer les champs
inconnus**. Pour un vrai changement incompatible : une nouvelle version (`/v2/…` ou un
en-tête), une période de coexistence annoncée (en-tête `Sunset`), et la mesure de qui utilise
encore l'ancienne.

## gRPC et GraphQL

**gRPC** (Google) : on décrit les services et messages dans un fichier `.proto`, et les
clients et serveurs sont **générés** dans chaque langage. Il transporte du **Protocol
Buffers** (binaire, compact, typé) sur HTTP/2, avec des flux dans les deux sens.

```protobuf
service Commandes {
  rpc Lire (LireCommande) returns (Commande);
  rpc Suivre (LireCommande) returns (stream EvenementCommande);
}
```

Idéal **entre services** : rapide, contrat strict, évolution maîtrisée (numéros de champs).
Moins adapté aux navigateurs (il faut gRPC-Web) et moins lisible pour déboguer.

**GraphQL** (Facebook) : un seul point d'entrée, et le **client décrit exactement les
données dont il a besoin**, éventuellement à travers plusieurs ressources :

```graphql
query {
  commande(id: 42) {
    total
    client { nom }
    lignes { produit { nom } quantite }
  }
}
```

Utile quand de nombreux clients (web, mobile, partenaires) ont des besoins différents :
plus de sur-chargement ni d'allers-retours multiples. Ses coûts : le cache HTTP ne marche
plus simplement (tout passe en POST sur une seule URL), le **problème N+1** se déplace dans le
serveur (on le résout avec des *DataLoaders* qui regroupent les requêtes), et il faut limiter
la profondeur et le coût des requêtes, sinon un client peut demander des millions d'objets.

| | REST | gRPC | GraphQL |
|---|---|---|---|
| Point fort | simple, cache HTTP, universel | performance, contrat généré, flux | flexibilité côté client |
| Usage typique | API publiques | entre services internes | API pour des interfaces variées |

## À retenir

- OAuth 2 : un accès délégué et limité ; code d'autorisation + PKCE ; OpenID Connect ajoute l'identité.
- JWT : la charge est lisible par tous, la signature empêche la modification ; imposer l'algorithme (le piège `alg: none`), vérifier `exp`, `iss`, `aud` ; jetons courts.
- API : un contrat (OpenAPI), des erreurs cohérentes, pagination par curseur, évolutions compatibles, versions annoncées.
- gRPC entre services, GraphQL pour des clients variés, REST pour le reste.
