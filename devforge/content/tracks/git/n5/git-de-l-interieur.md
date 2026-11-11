---
id: git.n5.git-de-l-interieur
title: Git de l'intérieur
skills: [objets-git, references, gros-depots]
duration_min: 12
---

# Git de l'intérieur

Git est une base de données très simple : un dictionnaire où chaque contenu est rangé
sous l'**empreinte** (hash SHA-1) de ce contenu. Tout le reste en découle.

## Les quatre types d'objets

| Objet | Contient |
|---|---|
| **blob** | le contenu d'un fichier (sans son nom) |
| **tree** | un dossier : des noms de fichiers, chacun pointant vers un blob ou un autre tree |
| **commit** | un tree (l'état complet du projet), le ou les parents, l'auteur, le message |
| **tag** annoté | une étiquette sur un objet, avec un message |

Le hash dépend **uniquement** du contenu. Sortie réelle :

```
$ echo "bonjour" | git hash-object --stdin
1cd909e05d33f0f6bc4ea1caf19b5749b434ceb3
```

Sur n'importe quel ordinateur, dans n'importe quel dépôt, « bonjour » suivi d'un retour à
la ligne donne ce même hash. Deux fichiers identiques ne sont stockés qu'une fois.

On peut lire les objets d'un vrai dépôt :

```
$ git cat-file -t HEAD
commit

$ git cat-file -p HEAD
tree 15873a4c11f57b97e30ff3a6c5c19332bcc21c2d
parent 8d5244d0e09ab732e5b50ffa63faa19885f22aca
author Ada <ada@exemple.dev> 1794387777 +0000
committer Ada <ada@exemple.dev> 1794387777 +0000

$ git cat-file -p HEAD^{tree}
100644 blob f70f10e4db19068f79bc43844b49f3eece45c4e8	a.txt
100644 blob 4fc856ad2b66a3f55cbf4d7a85270a40a41de0ca	notes.txt
```

Un commit contient le hash de son tree **et** celui de son parent. Modifier un ancien
commit change son hash, donc celui de tous ses descendants : l'historique est une chaîne
infalsifiable (le même principe qu'une blockchain, bien avant elle).

## Les références

Une **branche** n'est qu'un petit fichier qui contient le hash d'un commit :

```
$ cat .git/HEAD
ref: refs/heads/main

$ cat .git/refs/heads/main
c6f63d7670d416f7903f2050a0a134acab8d72b5
```

`HEAD` indique la branche courante. Créer une branche, c'est écrire 41 octets : c'est pour ça
que c'est instantané. Faire un commit, c'est créer les objets puis avancer le fichier de la
branche. « Détacher HEAD », c'est le faire pointer directement vers un commit, sans branche :
les commits faits ensuite ne sont retenus par aucune étiquette (mais le reflog s'en souvient).

## Les très gros dépôts

Quand un dépôt contient des millions de fichiers ou des fichiers binaires énormes :

- **Git LFS** : les gros binaires (images, vidéos, modèles) sont stockés à part ; le dépôt ne
  garde qu'un pointeur.
- **Clone partiel** (`--filter=blob:none`) : on ne télécharge le contenu des fichiers qu'au
  moment où on en a besoin.
- **Sparse checkout** : on ne déploie qu'une partie des dossiers dans le répertoire de travail.
- **Clone superficiel** (`--depth 1`) : seulement le dernier commit, idéal en CI.

## À retenir

- Blob (contenu), tree (dossier), commit (tree + parents + message), tag.
- Le hash dépend uniquement du contenu ; l'historique est une chaîne de hashes.
- Une branche est un fichier de 41 octets ; HEAD dit où l'on est.
- Gros dépôts : LFS, clone partiel, sparse checkout, clone superficiel.
