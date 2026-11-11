---
id: performance.n4.le-systeme-entier
title: Entrées-sorties par lots, lois de Little et d'Amdahl, parallélisme
skills: [entrees-sorties, lois-performance, parallelisme]
duration_min: 18
---

# Entrées-sorties par lots, lois de Little et d'Amdahl, parallélisme

## Les entrées-sorties et le traitement par lots

Une opération d'entrée-sortie (une requête à la base, un appel réseau, une écriture synchronisée
sur disque) coûte un **aller-retour**, souvent bien plus cher que le travail lui-même. Grouper
les opérations amortit ce coût. Mesure réelle : insérer 20 000 lignes dans PostgreSQL, sur la
même machine.

| Méthode | Durée | Débit |
|---|---|---|
| une transaction par ligne | 5 512 ms | 3 628 lignes/s |
| une seule transaction, une requête par ligne | 1 024 ms | 19 528 lignes/s |
| des lots de 1 000 lignes par requête | 83 ms | 240 446 lignes/s |
| `COPY` (le chargement en masse de PostgreSQL) | 32 ms | 616 991 lignes/s |

**170 fois** plus rapide entre la première et la dernière, pour les mêmes données. Chaque
`commit` attend que le journal soit écrit sur le disque (un `fsync`), et chaque requête est un
aller-retour ; regrouper supprime l'essentiel de ces attentes. Sur un vrai réseau (0,5 ms
d'aller-retour vers la base), l'écart serait encore plus grand.

Le même principe partout :

- **regrouper les écritures** : requêtes par lots, `COPY`, `saveAll` avec le *batching* de
  l'ORM activé, écritures dans un fichier tamponnées (parcours Linux, niveau 5 : 100 000
  appels `write` contre 8) ;
- **regrouper les lectures** : `where id in (…)` plutôt qu'une requête par identifiant (le N+1) ;
- **regrouper les appels réseau** : des API qui acceptent des lots, le *pipelining* (Redis),
  HTTP/2 qui multiplexe, la compression ;
- **ne pas attendre en série** ce qui peut se faire **en parallèle** : trois appels
  indépendants de 100 ms prennent 300 ms l'un après l'autre, 100 ms en parallèle (coroutines,
  `CompletableFuture`).

Le compromis : un lot plus gros augmente le débit **et** la latence de chaque élément (il attend
que le lot soit plein) ; on fixe une taille **et** un délai maximal (« 1 000 éléments ou 50 ms »).

## Les lois de Little et d'Amdahl

**La loi de Little** : `éléments en cours = débit × temps de séjour`. Elle s'applique à tout
système stable (une file, un service, un magasin). Exemples :

- un service reçoit 2 000 requêtes/s et met 50 ms à répondre : il a en moyenne **100** requêtes
  en cours ; avec un pool de 50 threads, il ne peut pas tenir ;
- si la latence passe à 200 ms (une dépendance ralentit), il faut **400** requêtes en cours pour
  garder le même débit : c'est ainsi qu'une dépendance lente épuise les pools (parcours Réseau
  et web, niveau 5).

**La loi d'Amdahl** : si une fraction *s* du travail ne peut pas être parallélisée,
l'accélération maximale avec *N* processeurs vaut `1 / (s + (1 − s) / N)`, et ne dépassera
jamais `1 / s`. Mesure réelle, avec un calcul dont 10 % est séquentiel, sur une machine à 4
cœurs (meilleur de trois essais) :

| Processus | Durée | Accélération mesurée | Théorie d'Amdahl |
|---|---|---|---|
| 1 | 1,83 s | ×1,00 | ×1,00 |
| 2 | 1,03 s | ×1,78 | ×1,82 |
| 4 | 0,63 s | ×2,91 | ×3,08 |

La mesure suit la théorie, un peu en dessous (démarrage des processus, transfert des données).
Avec 10 % séquentiels, même un million de cœurs ne dépasseraient pas ×10. La partie séquentielle
(une base unique, un verrou global, une étape d'agrégation finale) finit toujours par dominer :
c'est elle qu'il faut réduire.

La **loi de Gustafson** nuance : quand on a plus de processeurs, on traite souvent **plus de
données** dans le même temps, et la part séquentielle devient relativement plus petite.

## Paralléliser avec réalisme

Avant de paralléliser, vérifier que le travail est **limité par le CPU** (profil CPU élevé) et
**découpable** en morceaux indépendants. Sinon, plus de threads n'apportent rien, ou empirent
les choses (contention sur des verrous, sur la base, sur la mémoire).

Selon le langage :

- **JVM, Go, Rust, C++** : de vrais threads qui s'exécutent en parallèle sur tous les cœurs ;
  pools dimensionnés sur le nombre de cœurs pour le calcul (`Dispatchers.Default`,
  `ForkJoinPool`), plus larges pour les attentes d'entrées-sorties (`Dispatchers.IO`, ou des
  threads virtuels) ;
- **Python** : le **GIL** empêche deux threads d'exécuter du code Python en même temps (sauf
  avec la version expérimentale sans GIL) ; pour le calcul, des **processus** (`multiprocessing`,
  comme la mesure ci-dessus) ou du code natif (NumPy) ; les threads restent utiles pour les
  attentes d'entrées-sorties ;
- **JavaScript** : un seul thread d'exécution ; des *workers* pour le calcul.

Les pièges : le coût de **coordination** (découper, envoyer, rassembler) qui dépasse le gain pour
de petites tâches ; le **faux partage** des lignes de cache (parcours Mémoire, niveau 5) ; et
oublier que le serveur de production fait **déjà** tourner d'autres requêtes en parallèle sur
ces mêmes cœurs.

## À retenir

- Grouper les entrées-sorties : 5 512 ms → 32 ms mesurés pour 20 000 insertions ; lots, `COPY`, `in (…)`, appels parallèles ; une taille et un délai de lot.
- Little : en cours = débit × durée (2 000/s × 50 ms = 100 requêtes en cours). Amdahl : ×2,91 mesuré pour ×3,08 théorique sur 4 cœurs avec 10 % séquentiel ; jamais plus de 1/s.
- Paralléliser du calcul découpable ; vrais threads (JVM, Go), processus en Python (GIL), workers en JavaScript ; attention à la coordination et à la contention.
