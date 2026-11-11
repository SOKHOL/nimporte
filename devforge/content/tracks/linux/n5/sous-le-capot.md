---
id: linux.n5.sous-le-capot
title: Appels système, ordonnanceur, mémoire virtuelle, durcissement et eBPF
skills: [appels-systeme, ordonnanceur, durcissement, ebpf]
duration_min: 22
---

# Sous le capot

## Les appels système : la frontière avec le noyau

Un programme ne touche jamais directement le disque, le réseau ou l'écran. Il demande au
noyau, par un **appel système** (*syscall*) : `openat`, `read`, `write`, `mmap`, `clone`,
`execve`… Le processeur passe alors du mode utilisateur au mode noyau, et ce passage a un
coût.

Mesure réelle : écrire 100 000 octets un par un, sans tampon, puis avec un tampon.

| Version | Appels `write` (strace -c) | Durée |
|---|---|---|
| sans tampon (`buffering=0`) | 100 006 | environ 50 ms |
| avec un tampon de 64 Kio | 8 | environ 11 ms |

Même résultat, quatre à cinq fois plus lent, à cause de 100 000 allers-retours vers le
noyau. C'est pour ça qu'existent `BufferedWriter`, `BufferedOutputStream` ou `stdio` : ils
regroupent les petites écritures en gros appels système.

**Créer un processus** se fait en deux temps : `fork` (ou `clone`) duplique le processus
courant, puis `execve` remplace son programme. Réel, quand un shell lance `ls` :

```
execve("/usr/bin/sh", ["sh", "-c", "ls >/dev/null"], …) = 0
strace: Process 2494 attached
[pid  2494] execve("/usr/bin/ls", ["ls"], …) = 0
[pid  2493] wait4(-1,  <unfinished ...>
[pid  2494] +++ exited with 0 +++
--- SIGCHLD {si_signo=SIGCHLD, si_code=CLD_EXITED, si_pid=2494, …} ---
```

Le parent attend avec `wait4` et reçoit `SIGCHLD` quand l'enfant se termine. Un parent qui
n'appelle jamais `wait` laisse des **zombies** (état `Z` dans `ps`) : des entrées de la
table des processus qui ne seront jamais libérées.

Les **descripteurs de fichiers** (0, 1, 2, puis 3, 4…) sont des numéros dans une table
propre à chaque processus. Leur nombre est limité (`ulimit -n` : 20000 sur la machine de
test, souvent 1024 par défaut). Un serveur qui oublie de fermer ses connexions finit par
afficher « Too many open files ».

## L'ordonnanceur

Avec 4 cœurs et des centaines de tâches, le noyau décide en permanence qui tourne.
L'ordonnanceur de Linux (CFS jusqu'à Linux 6.6, **EEVDF** depuis) partage le temps CPU
équitablement, pondéré par la priorité *nice* (-20 = prioritaire, 19 = très gentil). Réel :
`nice -n 10 sh -c 'ps -o pid,ni,comm -p $$'` affiche `NI 10`.

Deux sortes de **changements de contexte** (dans `/proc/PID/status`) :

- *voluntary* : la tâche a rendu la main (attente de disque, de réseau, d'un verrou, d'un
  `sleep`). Réel : un script qui dort 10 ms affiche `voluntary_ctxt_switches: 2`.
- *nonvoluntary* : l'ordonnanceur l'a interrompue parce que son temps était écoulé. Beaucoup
  de changements involontaires : trop de threads pour trop peu de cœurs.

**Le piège des limites CPU dans les conteneurs.** Une limite de « 1 CPU » dans Kubernetes
se traduit par un quota cgroup : 100 ms de CPU par période de 100 ms. Un service Java avec
8 threads occupés consomme ce quota en 12,5 ms de temps réel, puis **tous ses threads sont
gelés** pendant les 87,5 ms restantes. Le CPU moyen semble raisonnable (40 %), mais le p99
de latence explose. Le symptôme se lit dans `nr_throttled` du fichier `cpu.stat` du cgroup
(ou la métrique `container_cpu_cfs_throttled_periods_total`). Remèdes : relever ou supprimer
la limite CPU (en gardant une *request*), et dimensionner les pools de threads sur le
quota, pas sur le nombre de cœurs de la machine.

## La mémoire virtuelle

Chaque processus croit disposer d'un immense espace d'adresses à lui seul. Le noyau et le
processeur (la MMU) traduisent ces adresses virtuelles en adresses physiques, par **pages**
de 4 Kio (`getconf PAGE_SIZE` : `4096`).

Mesure réelle en Python, avec `mmap` :

| Étape | VmSize (virtuel) | VmRSS (vraiment en RAM) |
|---|---|---|
| Au départ | 35 588 kB | 11 288 kB |
| Après avoir réservé 8 Go | 8 424 196 kB | 11 460 kB |
| Après avoir écrit dans 200 Mo | 8 424 196 kB | 216 260 kB |

Réserver 8 Go ne coûte **rien** : une page n'existe physiquement qu'au premier accès (un
*défaut de page* la fournit). C'est pourquoi on regarde la **RSS** et non la taille
virtuelle, et pourquoi Linux pratique l'**overcommit** : il promet plus de mémoire qu'il
n'en a (`/proc/sys/vm/overcommit_memory` vaut `0`, le mode heuristique par défaut). Si tout
le monde utilise sa promesse en même temps, l'**OOM killer** choisit un processus (le plus
gros, selon `oom_score`) et le tue.

Le **swap** déplace des pages peu utilisées sur le disque (`swappiness` : `60` par défaut
ici). Un peu de swap est normal ; un swap actif en continu (`si`/`so` dans `vmstat`) rend
tout lent. Pour une JVM ou une base de données, on évite le swap : un GC qui parcourt un tas
à moitié sur disque est catastrophique.

## Durcir un serveur

| Mesure | Contre quoi |
|---|---|
| SSH : `PasswordAuthentication no`, `PermitRootLogin no` | essais de mots de passe automatisés, accès root direct |
| Pare-feu « tout refuser sauf » (nftables, ufw) | services exposés par erreur |
| Mises à jour de sécurité automatiques | failles connues, les plus exploitées |
| Un utilisateur par service, sans shell | une faille dans un service ne donne pas tout le reste |
| *Capabilities* au lieu de root (`CAP_NET_BIND_SERVICE` pour le port 80) | un service compromis qui aurait tous les droits |
| Bac à sable systemd (`NoNewPrivileges`, `ProtectSystem=strict`, `PrivateTmp`) | un service qui écrit ou s'élève là où il ne devrait pas |
| AppArmor ou SELinux | ce qu'un processus peut ouvrir, même compromis |
| Journaux envoyés hors de la machine | un attaquant qui efface ses traces localement |
| Rien d'inutile qui écoute (`ss -tlnp`) | surface d'attaque |

`systemd-analyze security api` note de 0 à 10 l'exposition d'un service et liste les
options de bac à sable manquantes.

## eBPF : observer le noyau sans le ralentir

`strace` utilise `ptrace` : il arrête le processus à **chaque** appel système. Mesure
réelle sur le programme sans tampon : **0,07 s** sans strace, **6 s** avec, environ 80 fois
plus lent. Inutilisable sur un serveur chargé.

**eBPF** permet de charger de petits programmes **dans le noyau**, attachés à des
événements (appels système, fonctions du noyau, paquets réseau, *tracepoints*). Un
**vérificateur** prouve avant le chargement qu'ils se terminent et n'accèdent qu'à une
mémoire autorisée. Ils agrègent les données dans le noyau (des *maps*) et ne remontent que
le résultat : le coût reste faible, même en production.

```bash
# Qui ouvre quels fichiers, sur toute la machine (bpftrace)
bpftrace -e 'tracepoint:syscalls:sys_enter_openat { printf("%s %s\n", comm, str(args->filename)); }'
# Outils prêts à l'emploi (BCC) : execsnoop, opensnoop, biolatency, tcplife, runqlat
```

eBPF est partout dans l'infrastructure moderne : réseau des conteneurs (Cilium), équilibrage
de charge à très haut débit (XDP), sécurité en temps réel (Falco, Tetragon), profilage
continu de toute une flotte (Parca, Pyroscope).

## À retenir

- Un appel système coûte : on regroupe les petites écritures (tampons). `fork` + `execve`, `wait`, sinon des zombies.
- L'ordonnanceur partage le CPU ; une limite CPU de conteneur gèle tous les threads une fois le quota épuisé (throttling).
- Mémoire virtuelle : les pages n'existent qu'au premier accès ; regarder la RSS ; overcommit et OOM killer.
- Durcir : pas de mot de passe SSH, pare-feu par défaut fermé, moindre privilège, bac à sable, mises à jour.
- eBPF observe le noyau en production à faible coût, là où strace ralentit tout.
