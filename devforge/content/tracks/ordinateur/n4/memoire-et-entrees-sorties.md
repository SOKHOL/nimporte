---
id: ordinateur.n4.memoire-et-entrees-sorties
title: Pagination, entrées-sorties et stockage
skills: [pagination, entrees-sorties, stockage]
duration_min: 18
---

# Pagination, entrées-sorties et stockage

## La pagination et les défauts de page

Chaque processus voit un espace d'adresses **virtuel**, découpé en **pages** de 4 Kio. Une
**table des pages**, gérée par le noyau, indique pour chaque page virtuelle la page physique
correspondante (ou son absence). Le processeur fait la traduction à chaque accès, avec un cache
dédié, le **TLB**, pour ne pas relire la table à chaque fois.

Une page n'est attribuée physiquement qu'au **premier accès** : l'accès provoque un **défaut de
page** (*page fault*), une exception que le noyau traite en attribuant une page puis en
reprenant l'instruction. Mesure réelle en Python :

```
réserver 400 Mo (mmap)          : 0 défaut de page
écrire un octet tous les 4 Kio  : 102 400 défauts de page mineurs   (400 Mo / 4 Kio = 102 400)
```

Exactement un défaut par page touchée. Les défauts **mineurs** se règlent en mémoire (attribuer
une page vide, ou retrouver une page déjà en mémoire) ; les défauts **majeurs** demandent une
lecture sur disque (page envoyée en *swap*, ou fichier projeté en mémoire pas encore lu) et
coûtent des milliers de fois plus.

Ce mécanisme permet :

- l'**isolation** : un processus ne peut adresser que ses propres pages ;
- l'**allocation paresseuse** et l'*overcommit* (parcours Linux, niveau 5) ;
- le **partage** : les bibliothèques partagées (`libc.so.6`) ne sont qu'une fois en mémoire
  physique, mappées dans tous les processus ;
- la **copie à l'écriture** (*copy-on-write*) : après un `fork`, parent et enfant partagent les
  mêmes pages, copiées seulement si l'un des deux écrit ;
- les **fichiers projetés en mémoire** (`mmap`) : lire un fichier comme un tableau, le noyau
  chargeant les pages à la demande.

Pour les très grandes mémoires, les **grandes pages** (2 Mo ou 1 Go, *huge pages*) réduisent la
taille des tables et les défauts du TLB : les bases de données et les JVM à gros tas en
profitent.

## Les entrées-sorties bloquantes et non bloquantes

Une lecture réseau ou disque prend une éternité à l'échelle du processeur. Deux façons de
l'attendre :

- **bloquante** : le thread s'endort jusqu'à ce que les données arrivent ; pour servir 10 000
  connexions, il faut 10 000 threads ;
- **non bloquante, pilotée par les événements** : un seul thread demande au noyau « préviens-moi
  quand l'une de ces 10 000 connexions est prête » (`epoll` sous Linux, `kqueue` sous BSD et
  macOS, `io_uring` pour aller plus loin), et traite chacune quand elle a des données.

Mesure réelle en Python : faire attendre 10 000 tâches en même temps.

| | Création | Mémoire maximale du processus |
|---|---|---|
| 10 000 threads | 1 280 ms | ≈ 214 Mo |
| 10 000 tâches asyncio (une boucle d'événements) | 29 ms | ≈ 28 Mo |

Chaque thread du système réserve une pile et coûte au noyau ; une tâche d'une boucle
d'événements n'est qu'un petit objet. C'est le principe de Node.js, Nginx, Netty, des
coroutines Kotlin sur un petit pool, et d'asyncio. Les **threads virtuels** de Java 21 donnent
le même avantage en gardant un code bloquant simple à lire : la JVM les suspend elle-même
pendant les attentes (parcours Concurrence, niveau 4).

Le **DMA** (*Direct Memory Access*) complète le tableau : la carte réseau ou le contrôleur de
disque copient les données directement en mémoire, sans occuper le processeur, et préviennent
par une interruption quand c'est fini.

## Disques, SSD et systèmes de fichiers

| | Disque dur (HDD) | SSD NVMe |
|---|---|---|
| Principe | plateaux tournants et tête mobile | mémoire flash |
| Latence d'un accès aléatoire | 5 à 10 ms (déplacer la tête) | 20 à 100 µs |
| Opérations aléatoires par seconde (IOPS) | une centaine | des centaines de milliers |
| Débit séquentiel | 100 à 250 Mo/s | plusieurs Go/s |

Un HDD est rapide en **séquentiel** et très lent en **aléatoire** : les bases de données et les
LSM-tree ont été conçus autour de cette contrainte. Un SSD réduit énormément l'écart, mais
garde ses particularités : il s'use à l'écriture, et réécrire une petite donnée demande d'effacer
un bloc entier (l'amplification d'écriture).

Le **système de fichiers** (ext4, XFS, Btrfs, APFS, NTFS) organise les blocs du disque en
fichiers et dossiers (inodes, parcours Linux, niveau 4). Pour survivre à une coupure de courant
pendant une écriture, il tient un **journal** de ses propres modifications, comme le WAL d'une
base de données.

Et le piège de la durabilité : un `write` réussi ne veut **pas** dire « sur le disque ». Les
données passent par le cache du noyau (le *page cache*) et les caches du disque. Seul un
**`fsync`** garantit qu'elles ont atteint le support (mesuré à environ 226 µs pour 4 Kio sur la
machine de test, parcours System design). C'est pourquoi une base valide une transaction en
faisant un `fsync` de son journal, et pourquoi les grouper (parcours Performance, niveau 4)
change tout.

## À retenir

- Pages de 4 Kio, table des pages et TLB ; un défaut de page au premier accès (102 400 pour 400 Mo mesurés) ; isolation, partage, copy-on-write, mmap, grandes pages.
- Entrées-sorties : bloquantes (un thread par attente) ou pilotées par les événements (epoll) ; 10 000 attentes : 1 280 ms et 214 Mo en threads contre 29 ms et 28 Mo en asyncio ; DMA.
- HDD lent en aléatoire, SSD rapide mais avec ses contraintes ; systèmes de fichiers journalisés ; seul `fsync` garantit la durabilité.
