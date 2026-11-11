---
id: devops.n2.conteneurs
title: Images, couches, sécurité et Compose
skills: [docker-images, docker-couches, docker-securite, compose]
duration_min: 19
---

# Images, couches, sécurité et Compose

Toutes les mesures de cette leçon ont été faites avec Docker 29, sur la machine de test.

## Image et conteneur

- Une **image** est un modèle immuable : un système de fichiers en **couches** et des
  métadonnées (commande de démarrage, variables, utilisateur). Elle est identifiée par une
  empreinte, son **digest** (`sha256:…`).
- Un **conteneur** est un processus lancé depuis une image, avec une couche d'écriture
  temporaire par-dessus. Rappel du parcours Linux : un processus isolé par des namespaces et
  limité par des cgroups.

```dockerfile
FROM eclipse-temurin:21-jre          # image de base
WORKDIR /app
COPY build/libs/app.jar app.jar      # une couche
USER 1000                            # pas root
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Les couches et le cache

Chaque instruction `RUN`, `COPY` ou `ADD` crée une couche. Au build suivant, Docker
réutilise une couche si l'instruction **et tout ce qui la précède** n'ont pas changé. Dès
qu'une couche change, toutes les suivantes sont reconstruites.

Mesure réelle, avec une étape d'installation des dépendances qui prend 8 secondes :

```dockerfile
# Mauvais ordre                         # Bon ordre
COPY . .                                COPY dependances.txt .
RUN installer les dépendances (8 s)     RUN installer les dépendances (8 s)
                                        COPY . .
```

| | Premier build | Après une modification de `app.py` |
|---|---|---|
| Mauvais ordre | 8,7 s | **8,5 s** (tout est refait) |
| Bon ordre | 8,5 s | **0,3 s** (dépendances en cache) |

Règle : ce qui change **rarement** en haut (image de base, dépendances), ce qui change
**souvent** en bas (le code). Et un fichier `.dockerignore` exclut `.git`, `node_modules`,
les fichiers de build : sinon, le moindre changement invalide le `COPY . .` et le contexte
envoyé au démon est énorme.

## Des images petites

Mesure réelle avec **le même binaire** (un programme C compilé en statique, 785 Ko) copié
dans trois images de base :

| Image de base | Taille de l'image |
|---|---|
| `debian:12` | 183 Mo |
| `alpine:3.20` | 13,2 Mo |
| `scratch` (vide) | 1,14 Mo |

Une petite image se télécharge et démarre plus vite, et surtout contient moins de logiciels,
donc moins de failles à corriger. L'image `scratch` ne contient même pas de shell : `docker
run --entrypoint sh` échoue, un attaquant qui prend le contrôle du programme ne peut pas
lancer `sh`.

Le **build multi-étapes** sépare la compilation de l'exécution :

```dockerfile
FROM gradle:8-jdk21 AS build          # étape lourde : JDK, Gradle, code source
WORKDIR /src
COPY . .
RUN gradle installDist --no-daemon

FROM eclipse-temurin:21-jre           # étape légère : seulement le JRE et le résultat
COPY --from=build /src/build/install/app/ /app/
USER 1000
ENTRYPOINT ["/app/bin/app"]
```

Seul le résultat de la compilation est copié : le compilateur, les sources et les caches
ne finissent pas dans l'image livrée. Pour Java, les images *distroless* ou *jlink* (un JRE
réduit aux modules utilisés) vont plus loin.

## La sécurité des images

**1. Ne pas tourner en root.** Par défaut, si l'image ne dit rien, c'est root. Réel :

```
$ docker run --rm alpine:3.20 id
uid=0(root) gid=0(root) groups=0(root),…
$ docker run --rm --user 1000:1000 alpine:3.20 id
uid=1000 gid=1000 groups=1000
```

Un processus root dans un conteneur qui s'échappe (faille du noyau, montage trop
permissif) est root sur la machine. `USER 1000` dans le Dockerfile.

**2. Ne jamais mettre de secret dans une couche.** Démonstration réelle :

```dockerfile
FROM alpine:3.20
COPY .env /app/.env
RUN echo "configuration..." && rm /app/.env
```

Dans le conteneur, `/app` est vide. Mais l'image contient toujours la couche du `COPY` : en
exportant l'image (`docker save`) et en ouvrant ses couches, on retrouve
`DB_PASSWORD=Sup3rS3cret`. Chaque couche est une archive, et `rm` dans une couche suivante
ne fait que **masquer** le fichier. Les secrets se passent à l'exécution (variables
d'environnement, secrets Kubernetes, gestionnaire de secrets) ou, pendant le build, avec
`RUN --mount=type=secret`, qui ne laisse aucune trace.

**3. Figer les versions.** `FROM node:latest` change sans prévenir ; `FROM node:22.11-alpine`,
ou mieux une empreinte `@sha256:…`, garantit le même résultat.

**4. Scanner les images** (Trivy, Grype, Docker Scout) et les reconstruire régulièrement
pour recevoir les correctifs de l'image de base.

## Le PID 1 et les signaux

Le processus principal d'un conteneur a le PID 1, et le noyau le traite à part : un signal
pour lequel il n'a **pas installé de gestionnaire est ignoré**. `docker stop` envoie SIGTERM,
attend 10 secondes, puis envoie SIGKILL. Mesure réelle avec `sleep 1000` comme processus
principal :

| | Durée de `docker stop` | Code de sortie |
|---|---|---|
| sans init | **10,2 s** | 137 (tué par SIGKILL) |
| avec `--init` (tini comme PID 1) | **0,1 s** | 143 (arrêté par SIGTERM) |

Sans gestionnaire, chaque arrêt et chaque déploiement attend 10 secondes (30 s dans
Kubernetes), puis tue brutalement le processus, sans qu'il puisse finir ses requêtes.
Solutions : un petit init (`docker run --init`, `tini`), ou une application qui gère
SIGTERM (la JVM, Node et Go le font). Et toujours la **forme exec** (`ENTRYPOINT ["java",
"-jar", "app.jar"]`) plutôt que la forme shell (`ENTRYPOINT java -jar app.jar`), qui
intercale un `/bin/sh` qui ne transmet pas les signaux.

## Docker Compose

Compose décrit plusieurs conteneurs qui fonctionnent ensemble, pour le développement et
les tests :

```yaml
services:
  api:
    build: .
    ports: ["8080:8080"]
    environment:
      DATABASE_URL: postgres://app:app@db:5432/app
    depends_on:
      db:
        condition: service_healthy     # attendre que la base réponde, pas seulement qu'elle démarre
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: app
      POSTGRES_PASSWORD: app
    volumes: ["donnees:/var/lib/postgresql/data"]
    healthcheck:
      test: ["CMD", "pg_isready", "-U", "app"]
      interval: 2s
      retries: 15
volumes:
  donnees:
```

- Les services se joignent **par leur nom** (`db`) sur un réseau créé par Compose.
- `depends_on` seul attend que le conteneur **démarre**, pas que la base soit **prête** :
  sans `condition: service_healthy`, l'API plante au premier démarrage.
- Les **volumes** gardent les données au-delà de la vie des conteneurs.
- `docker compose up -d`, `logs -f api`, `down` (et `down -v` pour effacer les volumes).

## À retenir

- Ce qui change rarement en haut du Dockerfile, ce qui change souvent en bas (mesuré : 8,5 s contre 0,3 s) ; un `.dockerignore`.
- Petites images (183 Mo → 13,2 Mo → 1,14 Mo pour le même binaire) et build multi-étapes.
- `USER` non root ; aucun secret dans une couche (un `rm` ne l'efface pas) ; versions figées ; scans.
- PID 1 : `--init` ou gestion de SIGTERM, forme exec (sinon 10 s d'attente puis SIGKILL, code 137).
- Compose : noms de service, `depends_on` avec `service_healthy`, volumes.
