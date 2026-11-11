---
id: ordinateur.n2.le-processeur-et-le-programme
title: Composants, assembleur et du code source à l'exécutable
skills: [composants, assembleur, compilation-edition-liens]
duration_min: 18
---

# Composants, assembleur et du code source à l'exécutable

## Processeur, mémoire, stockage et bus

| Composant | Rôle | Ordre de grandeur |
|---|---|---|
| **Processeur** (CPU) | exécute les instructions | quelques milliards de cycles par seconde, par cœur |
| **Registres** | les « variables » du processeur, dans le processeur même | quelques dizaines, accès en 1 cycle |
| **Caches** L1, L2, L3 | copies rapides de la mémoire récemment utilisée | 1 à 20 ns |
| **Mémoire vive** (RAM) | données et programmes en cours ; perdue à l'extinction | environ 100 ns |
| **Stockage** (SSD, disque) | données persistantes | dizaines de µs (SSD) à ms (disque) |

Le processeur de la machine de test, réel (`lscpu`) : un Xeon à 2,30 GHz, **4 cœurs**, 48 Kio de
cache L1 de données et 2 Mio de cache L2 **par cœur**, petit-boutiste, avec les instructions
vectorielles AVX2. Les composants communiquent par des **bus** et des liens (PCIe pour les SSD
et les cartes graphiques) ; la carte mère et le chipset les relient.

L'architecture de **von Neumann** (1945) est toujours là : programmes et données dans la même
mémoire, un processeur qui va chercher les instructions une à une. Toute la complexité moderne
(caches, pipeline, prédiction, plusieurs cœurs) sert à masquer la lenteur relative de la mémoire.

## Instructions, registres et assembleur

Le processeur ne comprend qu'un **jeu d'instructions** (x86-64 sur la plupart des PC et
serveurs, ARM64 sur les téléphones, les Mac récents et de plus en plus de serveurs, RISC-V qui
monte) : des opérations très simples sur des registres et la mémoire. Le **cycle** de base :
**chercher** l'instruction à l'adresse du compteur de programme, la **décoder**, l'**exécuter**,
passer à la suivante.

Code réellement généré par gcc (`-O1`) pour deux fonctions C :

```c
int somme(int a, int b) { return a + b; }
int carre(int x) { return x * x; }
```

```asm
somme:
	endbr64                    ; protection contre certains détournements de flux
	leal	(%rdi,%rsi), %eax     ; eax = rdi + rsi   (a et b arrivent dans rdi et rsi)
	ret                        ; le résultat repart dans eax
carre:
	endbr64
	imull	%edi, %edi           ; edi = edi * edi
	movl	%edi, %eax           ; eax = edi
	ret
```

Les **conventions d'appel** fixent où passent les arguments (ici les registres `rdi`, `rsi`) et
le résultat (`eax`). `leal` est une instruction de calcul d'adresse détournée pour faire une
addition en une seule instruction : le compilateur connaît ces astuces mieux que nous.

On n'écrit presque jamais d'assembleur, mais savoir le **lire** sert à comprendre une
optimisation (ou son absence), un plantage dans du code natif, ou la sortie d'un profileur.
L'outil **Compiler Explorer** (godbolt.org) montre l'assembleur produit par de nombreux
compilateurs, ligne à ligne.

## Du code source à l'exécutable

Pour un langage compilé comme C, quatre étapes :

1. **Préprocesseur** : inclut les en-têtes, remplace les macros.
2. **Compilation** : transforme le C en assembleur, puis en **code objet** (`.o`).
3. **Édition de liens** : assemble les fichiers objets et les bibliothèques en un exécutable,
   en résolvant les **symboles** (qui appelle quelle fonction, où est-elle ?).
4. **Chargement** : au lancement, le système charge l'exécutable en mémoire, avec les
   **bibliothèques partagées** dont il dépend.

Sorties réelles avec deux fichiers, `somme.c` (qui définit `somme`) et `principal.c` (dont le
`main` appelle `somme` et `printf`) :

```
$ gcc -c somme.c ; gcc -c principal.c
$ file somme.o
somme.o: ELF 64-bit LSB relocatable, x86-64
$ nm somme.o
0000000000000018 T carre          ← T : défini ici (dans le code)
0000000000000000 T somme
$ nm principal.o
0000000000000000 T main
                 U printf          ← U : utilisé ici, défini ailleurs
                 U somme
$ gcc somme.o principal.o -o programme && ./programme
42
$ ldd programme
	linux-vdso.so.1
	libc.so.6 => /lib/x86_64-linux-gnu/libc.so.6
	/lib64/ld-linux-x86-64.so.2
$ gcc principal.o -o incomplet
undefined reference to `somme'
```

L'éditeur de liens a relié l'appel `somme` à sa définition ; `printf` vient de la bibliothèque C
partagée (`libc.so.6`), chargée au démarrage par `ld-linux`. Sans `somme.o`, l'erreur
« undefined reference » vient de l'**édition de liens**, pas de la compilation : chaque fichier
compilait très bien seul.

Les autres modèles :

- **La JVM** compile le Kotlin ou le Java en **bytecode** portable (`.class`), interprété puis
  compilé à la volée par le JIT (parcours JVM) ; les « liens » se font au chargement des classes.
- **Python** compile en bytecode (`.pyc`) interprété par une machine virtuelle.
- **JavaScript** est compilé à la volée par le moteur du navigateur (V8, SpiderMonkey).
- **Go et Rust** produisent souvent un exécutable **statique**, sans bibliothèque partagée à
  installer, idéal dans un conteneur `scratch` (parcours DevOps, niveau 2).

## À retenir

- Registres, caches, mémoire, stockage : de la nanoseconde à la milliseconde ; toute l'architecture moderne masque la lenteur de la mémoire.
- Le processeur exécute un jeu d'instructions simples (x86-64, ARM64) ; savoir lire l'assembleur (`somme` = un `leal` et un `ret`).
- Préprocesseur, compilation (objets), édition de liens (symboles T/U, « undefined reference »), chargement (bibliothèques partagées, `ldd`) ; bytecode et JIT ailleurs.
