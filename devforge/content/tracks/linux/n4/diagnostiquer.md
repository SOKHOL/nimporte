---
id: linux.n4.diagnostiquer
title: Diagnostiquer une machine en production
skills: [charge-performance, strace-lsof, systemes-fichiers, conteneurs-linux]
duration_min: 18
---

# Diagnostiquer une machine en production

## Une méthode avant les outils

La méthode **USE** (Brendan Gregg) : pour chaque ressource (CPU, mémoire, disque, réseau),
regarder l'**U**tilisation (occupée à quel point ?), la **S**aturation (du travail
attend-il ?) et les **E**rreurs. Elle évite de tourner en rond avec `top` pendant une heure.

Les 60 premières secondes sur une machine qui va mal :

```bash
uptime            # charge moyenne sur 1, 5 et 15 minutes
dmesg -T | tail   # le noyau a-t-il tué un processus (OOM) ou vu des erreurs disque ?
vmstat 1          # CPU, mémoire, swap, attente d'E/S, seconde par seconde
free -m           # mémoire
iostat -xz 1      # disques : utilisation, file d'attente, latence
df -h ; df -i     # espace disque… et inodes
top               # qui consomme
```

## La charge et le CPU

La *load average* compte les tâches qui tournent **ou attendent** le CPU (et, sous Linux,
celles bloquées en attente de disque). Elle se lit **par rapport au nombre de cœurs** :
sur la machine de test, `nproc` donne `4` et `/proc/loadavg` donne `0.07 0.10 0.12` :
presque rien. Une charge de 8 sur 4 cœurs signifie qu'en moyenne 4 tâches attendent leur
tour.

Colonnes de `vmstat` à connaître (sortie réelle) :

```
 r  b   swpd   free   buff  cache   si   so    bi    bo   in   cs us sy id wa st
 0  0      0 15789776  15792 367448    0    0     0     4  110   97  0  0 100  0  0
```

| Colonne | Sens | Inquiétant si… |
|---|---|---|
| `r` | tâches prêtes à tourner | durablement > nombre de cœurs (saturation CPU) |
| `b` | tâches bloquées (E/S) | durablement > 0 |
| `si`/`so` | pages lues/écrites dans le swap | non nul en continu : la machine manque de RAM |
| `us`/`sy` | CPU utilisateur / noyau | `sy` élevé : trop d'appels système, de changements de contexte |
| `wa` | CPU qui attend le disque | élevé : le disque est le goulot |
| `st` | temps volé par l'hyperviseur | élevé : la machine virtuelle partage mal son hôte |

## La mémoire : « free » n'est pas « disponible »

Sortie réelle de `free -m` :

```
               total        used        free      shared  buff/cache   available
Mem:           16086         513       15419          16         374       15572
```

Linux utilise la RAM libre comme **cache disque** (`buff/cache`), qu'il rend dès qu'un
programme en a besoin. La colonne qui compte est **available**. Une machine avec `free`
à 200 Mo et `available` à 10 Go va très bien ; s'en inquiéter est une erreur de débutant.

## strace et lsof : voir ce que fait un processus

`strace` affiche chaque **appel système** (chaque demande au noyau). Réel :

```
$ strace -e trace=openat cat app.log
openat(AT_FDCWD, "/lib/x86_64-linux-gnu/libc.so.6", O_RDONLY|O_CLOEXEC) = 3
openat(AT_FDCWD, "app.log", O_RDONLY) = 3
```

Un programme qui « ne fait rien » ? `strace -p PID` montre s'il attend une socket, un
verrou, un fichier. Une config ignorée ? `strace -e trace=openat` montre quels fichiers il
ouvre **vraiment**. `strace -c ls` résume (réel : 70 appels, dont 17 `mmap` et 8 `close`).

`lsof` liste les fichiers ouverts : `lsof -p PID` pour un processus, `lsof -i :8080` pour
un port, `lsof +L1` pour les fichiers **supprimés mais encore ouverts**.

## Disques : le piège du fichier supprimé

Démonstration réelle : un processus ouvre un journal de 50 Mo, puis on supprime le
fichier.

```
$ du -sh .        →  51M
$ rm gros.log
$ du -sh .        →  4.0K
$ lsof -p 2433
sleep  2433 root  3r  REG  254,0  52428800  942906  …/gros.log (deleted)
```

`du` ne voit plus le fichier, mais **l'espace n'est pas libéré** tant qu'un processus le
garde ouvert : `rm` retire seulement le nom. D'où le grand classique : `df` dit « disque
plein à 100 % », `du` ne trouve que 40 % d'occupation. La cause : un journal supprimé à la
main pendant que l'application écrivait dedans. Solution : redémarrer le processus, ou
vider le fichier par son descripteur (`: > /proc/PID/fd/N`), et utiliser `logrotate` au
lieu de `rm`.

Autre classique : `No space left on device` alors que `df -h` montre de la place.
`df -i` répond : il ne reste plus d'**inodes**. Chaque fichier consomme un inode (la
fiche qui décrit le fichier), et des millions de petits fichiers (sessions, cache) peuvent
les épuiser avant l'espace. Ici, `df -i` donne 16 777 216 inodes, dont 265 155 utilisés.

Un nom de fichier n'est qu'un lien vers un inode. Réel, avec `ln` (lien dur) et `ln -s`
(lien symbolique) :

```
942908 -rw-r--r-- 2 root root 6 a.txt
942908 -rw-r--r-- 2 root root 6 dur.txt
942909 lrwxrwxrwx 1 root root 5 sym.txt -> a.txt
```

`a.txt` et `dur.txt` sont **le même fichier** (même inode, 2 liens) ; `sym.txt` est un
autre fichier qui contient un chemin.

## Un conteneur, c'est un processus

Un conteneur n'est pas une machine virtuelle : c'est un processus Linux ordinaire, avec
deux mécanismes du noyau.

- Les **namespaces** limitent ce qu'il **voit** : ses propres processus (pid), son réseau
  (net), ses points de montage (mnt), son nom d'hôte (uts), ses utilisateurs (user)… La
  liste réelle dans `/proc/self/ns` : `cgroup ipc mnt net pid pid_for_children time
  time_for_children user uts`.
- Les **cgroups** limitent ce qu'il **consomme** : mémoire, CPU, nombre de processus,
  débit disque. Un processus qui dépasse sa limite mémoire est tué par le noyau : code de
  sortie 137, l'*OOMKilled* de Kubernetes.

Conséquences : `ps` sur l'hôte montre les processus des conteneurs ; un conteneur partage
le noyau de l'hôte (une faille noyau les touche tous) ; et `top` **dans** un conteneur peut
afficher la mémoire de toute la machine, pas sa limite.

## À retenir

- USE : utilisation, saturation, erreurs, pour chaque ressource.
- Charge moyenne à comparer au nombre de cœurs ; `vmstat` (r, wa, si/so) ; `available`, pas `free`.
- `strace` pour les appels système, `lsof` pour les fichiers ouverts.
- `df` ≠ `du` : un fichier supprimé mais ouvert ; « disque plein » avec de la place : `df -i`.
- Conteneur = processus + namespaces (voir) + cgroups (consommer).
