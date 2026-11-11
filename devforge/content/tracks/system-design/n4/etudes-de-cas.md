---
id: system-design.n4.etudes-de-cas
title: Cinq études de cas
skills: [url-shortener, news-feed, chat, rate-limiter, notifications]
duration_min: 24
---

# Cinq études de cas

Une méthode pour toute conception, en entretien comme au travail :

1. **Clarifier** les besoins fonctionnels (ce que fait le système) et non fonctionnels
   (charge, latence, disponibilité, cohérence, durée de conservation).
2. **Estimer** : requêtes par seconde (lecture et écriture), stockage, bande passante.
3. **Esquisser** l'architecture de haut niveau : clients, API, services, stockages, files.
4. **Approfondir** le chemin critique et les points difficiles.
5. **Nommer les compromis** et les points de défaillance.

## Raccourcisseur d'URL

- **Charge** : beaucoup plus de lectures (redirections) que d'écritures (créations),
  souvent 100 pour 1.
- **Codes courts** : base62 sur 7 caractères = 62⁷ ≈ 3,5 × 10¹² combinaisons. Générer
  sans collision : un compteur distribué dont chaque instance réserve des **plages**
  d'identifiants, encodés en base62 ; ou un hachage avec vérification d'unicité.
- **Chemin critique** : la redirection. Cache Redis devant une base clé-valeur ; les
  liens suivent une loi de puissance, un petit cache absorbe l'essentiel du trafic.
- **302 ou 301** : un 301 (permanent) est mis en cache par le navigateur, qui ne revient
  plus : la redirection est plus rapide, mais les clics suivants ne sont plus comptés.
- **Statistiques** : publiées dans une file, traitées à part, pour ne pas ralentir la
  redirection.

L'exercice de conception complet est dans ce niveau.

## Fil d'actualité

Afficher les publications récentes des comptes que l'on suit.

- **Fan-out à la lecture** (*pull*) : à l'ouverture, on interroge les publications de
  tous les comptes suivis et on les fusionne. Écriture bon marché, lecture coûteuse.
- **Fan-out à l'écriture** (*push*) : à la publication, on ajoute l'identifiant du post
  dans le fil précalculé (une liste dans Redis) de chaque abonné. Lecture instantanée,
  écriture coûteuse.

Le problème des **célébrités** : un compte à 50 millions d'abonnés qui publie déclenche
50 millions d'écritures. La solution des grands réseaux est **hybride** : fan-out à
l'écriture pour les comptes ordinaires, fan-out à la lecture pour les quelques comptes
géants, fusionnés au moment de l'affichage.

Autres points : on stocke des **identifiants** dans les fils (pas les publications
entières), on garde seulement les quelques centaines de plus récents, on ne précalcule pas
les fils des utilisateurs inactifs, et le classement (chronologique ou par pertinence) est
une étape séparée.

## Messagerie instantanée

- **Connexions persistantes** : WebSocket (ou long polling en secours) entre chaque client
  et un serveur de connexions. Un serveur tient des centaines de milliers de connexions ;
  il faut savoir **sur quel serveur** est chaque utilisateur (un registre de présence dans
  Redis).
- **Envoi** : le message est d'abord **enregistré** (avec un identifiant croissant par
  conversation), puis acheminé vers le serveur de connexions du destinataire. Si le
  destinataire est hors ligne, une notification push part à la place.
- **Ordre** : garanti **par conversation** (partition par identifiant de conversation),
  jamais globalement.
- **Accusés** : envoyé (le serveur l'a enregistré), distribué (l'appareil l'a reçu), lu.
- **Synchronisation multi-appareils** : chaque appareil retient le dernier identifiant
  reçu et demande la suite à la reconnexion.
- **Idempotence** : le client génère un identifiant pour chaque message, pour que les
  renvois après une coupure ne créent pas de doublons.
- **Stockage** : énormément d'écritures, lecture des messages récents par conversation ;
  une base orientée colonnes larges (Cassandra, ScyllaDB, HBase) partitionnée par
  conversation s'y prête bien.

## Rate limiter

Limiter le nombre de requêtes par client protège contre les abus, les robots et les
erreurs de boucle d'un client. Réponse : `429 Too Many Requests`, avec `Retry-After`.

| Algorithme | Principe | Défaut |
|---|---|---|
| **Fenêtre fixe** | un compteur par minute (`INCR` + `EXPIRE`) | une rafale à 59 s puis à 0 s passe le double de la limite |
| **Fenêtre glissante** | on compte sur les 60 dernières secondes (journal ou approximation pondérée) | plus coûteux |
| **Seau à jetons** (*token bucket*) | un seau de capacité C se remplit à R jetons/s ; chaque requête prend un jeton | rafales autorisées jusqu'à C, ce qui est souvent voulu |
| **Seau qui fuit** (*leaky bucket*) | une file vidée à débit constant | lisse tout, ajoute de l'attente |

Démonstration réelle de fenêtre fixe avec Redis (limite de 5 par fenêtre) :

```
requête 1 : 1 -> acceptée
…
requête 5 : 5 -> acceptée
requête 6 : 6 -> 429 Too Many Requests
```

Simulation d'un seau à jetons (capacité 10, 5 jetons/s) : une rafale de 30 requêtes à
`t = 0`, puis une requête toutes les 100 ms pendant 2 s. Résultat : **20 acceptées, 30
refusées**. La rafale initiale consomme les 10 jetons, puis le débit de 10 requêtes/s
dépasse le remplissage de 5/s.

Points d'architecture : le compteur doit être **partagé** entre toutes les instances
(Redis, avec une opération atomique ou un script Lua), le limiteur ne doit pas devenir un
point unique de panne (en cas de panne de Redis, on laisse passer plutôt que de tout
bloquer, en général), et les limites sont souvent différentes par client, par route et par
offre commerciale.

## Système de notifications

Envoyer des e-mails, SMS et notifications push (APNs pour iOS, FCM pour Android) à des
millions d'utilisateurs.

- Les services métier publient un événement (« commande expédiée ») ; un **service de
  notifications** décide quoi envoyer, à qui, par quel canal, selon les **préférences** de
  l'utilisateur (et ses heures de silence).
- Une **file par canal**, avec des workers qui appellent les fournisseurs externes. Un
  fournisseur lent ou en panne ne bloque pas les autres canaux.
- **Réessais** avec délai croissant et aléatoire, puis file des messages morts.
- **Déduplication** : une clé d'idempotence par notification, car les événements peuvent
  arriver deux fois ; recevoir deux fois « votre colis est expédié » fait mauvaise
  impression, deux fois « paiement refusé » inquiète.
- **Limites** : les fournisseurs limitent le débit, et les utilisateurs aussi (pas plus de
  N notifications marketing par jour).
- **Suivi** : envoyé, délivré, ouvert ; et désinscription en un clic, imposée par la loi
  pour les e-mails commerciaux.

## À retenir

- Clarifier, estimer, esquisser, approfondir le chemin critique, nommer les compromis.
- Raccourcisseur : base62, plages d'identifiants, cache devant la redirection, 302 pour compter.
- Fil d'actualité : push pour la majorité, pull pour les célébrités.
- Messagerie : WebSocket, enregistrer puis acheminer, ordre par conversation, identifiants côté client.
- Rate limiter : seau à jetons ou fenêtre glissante, compteur partagé et atomique.
- Notifications : files par canal, réessais, déduplication, préférences.
