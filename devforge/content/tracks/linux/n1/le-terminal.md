---
id: linux.n1.le-terminal
title: Fichiers, permissions et pipes
skills: [navigation, permissions, pipes-redirections]
duration_min: 13
---

# Fichiers, permissions et pipes

## Naviguer

```bash
pwd                     # où suis-je ?
ls -la                  # lister, y compris les fichiers cachés, en détail
cd /var/log             # chemin absolu (commence par /)
cd ../tmp               # chemin relatif (.. = dossier parent)
cd ~                    # dossier personnel
mkdir -p projets/demo   # créer, avec les dossiers intermédiaires
cp a.txt b.txt          # copier ;  mv : déplacer ou renommer ;  rm : supprimer
cat fichier ; less fichier ; head -n 20 ; tail -f journal.log
```

Sous Linux, **tout est fichier** : les disques (`/dev/sda`), les processus (`/proc/1234`), la
configuration (`/etc`). `rm` ne passe pas par une corbeille : `rm -rf` est définitif.

## Les permissions

```
$ ls -l script.sh
-rwxr-xr--  …  script.sh
```

Les neuf lettres après le type (`-` = fichier, `d` = dossier) se lisent par trois : le
**propriétaire** (`rwx`), le **groupe** (`r-x`), les **autres** (`r--`). `r` = lire, `w` =
écrire, `x` = exécuter (ou, pour un dossier, y entrer).

En chiffres, r = 4, w = 2, x = 1, qu'on additionne : `rwx` = 7, `r-x` = 5, `r--` = 4.
Sortie réelle après `chmod 754 script.sh` : `-rwxr-xr--`.

| Valeur | Usage courant |
|---|---|
| `755` | script ou dossier public : tout le monde lit et exécute, seul le propriétaire écrit |
| `644` | fichier ordinaire |
| `600` | secret (clé SSH privée, fichier `.env`) : le propriétaire seul |
| `777` | tout le monde peut tout faire : presque toujours une erreur |

## Pipes et redirections

Chaque programme a trois flux : l'entrée standard (0), la sortie standard (1) et la sortie
d'erreur (2).

| Syntaxe | Effet |
|---|---|
| `cmd > f` | sortie standard vers le fichier (écrase) |
| `cmd >> f` | sortie standard ajoutée à la fin du fichier |
| `cmd 2> f` | erreurs vers le fichier |
| `cmd > f 2>&1` | sortie **et** erreurs vers le fichier |
| `cmd1 \| cmd2` | la sortie de cmd1 devient l'entrée de cmd2 |

Piège d'ordre, vérifié : `ls absent > f 2>&1` met bien l'erreur dans le fichier, mais
`ls absent 2>&1 > f` affiche l'erreur à l'écran et laisse le fichier **vide**. Les
redirections s'appliquent de gauche à droite : `2>&1` copie la destination **actuelle**
de la sortie standard.

Le pipe est la force du shell : de petits outils qui font une chose, combinés. Sur un
journal de 7 lignes :

```
$ grep ERROR app.log | awk '{print $4}' | sort | uniq -c | sort -rn
      3 base
      1 paiement
```

« Quels composants produisent le plus d'erreurs ? », en une ligne.

## À retenir

- Chemins absolus (`/…`) et relatifs (`..`) ; `rm` est définitif.
- Permissions propriétaire/groupe/autres, `rwx` = 4 + 2 + 1 ; `600` pour les secrets, jamais `777`.
- `>`, `>>`, `2>`, `> f 2>&1` (dans cet ordre), et `|` pour enchaîner les outils.
