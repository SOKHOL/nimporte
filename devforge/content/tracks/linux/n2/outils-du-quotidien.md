---
id: linux.n2.outils-du-quotidien
title: Texte, scripts, processus et environnement
skills: [texte, scripts-bash, processus-signaux, environnement]
duration_min: 15
---

# Texte, scripts, processus et environnement

## Les outils de texte

| Outil | Rôle | Exemple |
|---|---|---|
| `grep` | garder les lignes qui correspondent | `grep -i erreur *.log`, `grep -c ERROR app.log` |
| `sort`, `uniq -c` | trier, compter les doublons voisins | `sort \| uniq -c` |
| `cut`, `awk` | extraire des colonnes, calculer | `awk '{s += $2} END {print s}'` |
| `sed` | remplacer du texte | `sed 's/http:/https:/g' config` |
| `wc -l` | compter les lignes | `wc -l < app.log` |
| `find` | chercher des fichiers | `find . -name "*.log" -mtime +7` |
| `xargs` | passer une liste en arguments | `find . -name "*.tmp" \| xargs rm` |

Exemple réel : `printf "a 10\nb 32\nc 5\n" | awk '{s += $2} END {print s}'` affiche `47`.

## Des scripts bash fiables

Par défaut, bash **continue après une erreur**. Démonstration réelle :

```bash
cd /dossier/inexistant
echo "je continue quand même, dans $(pwd)"
```

Le `cd` échoue, mais la ligne suivante s'exécute… dans le mauvais dossier. Imagine un
`rm -rf *` à la place de l'`echo`. La première ligne de tout script devrait être :

```bash
#!/usr/bin/env bash
set -euo pipefail
```

| Option | Effet |
|---|---|
| `-e` | arrêter le script dès qu'une commande échoue |
| `-u` | une variable non définie est une erreur (et non une chaîne vide) |
| `-o pipefail` | un pipeline échoue si **n'importe quelle** commande échoue, pas seulement la dernière |

Avec ces options, le même script s'arrête au `cd` (code de sortie 1) et l'`echo` n'est
jamais exécuté.

**Toujours mettre les variables entre guillemets.** Vérifié avec un fichier nommé
`mon rapport.txt` :

```bash
f="mon rapport.txt"
ls $f      # ls: cannot access 'mon'… et 'rapport.txt'…
ls "$f"    # mon rapport.txt
```

Sans guillemets, le shell découpe la valeur sur les espaces : deux arguments au lieu d'un.

Chaque commande renvoie un **code de sortie** : `0` = succès, autre chose = échec, lisible
dans `$?`. `grep -q INFO app.log` renvoie réellement 0 (trouvé), `grep -q FATAL app.log`
renvoie 1 (pas trouvé). C'est ce qu'utilisent `if`, `&&` et `||`.

## Processus et signaux

```bash
ps aux                  # tous les processus
top / htop              # en direct
commande &              # en arrière-plan ;  jobs, fg, bg
kill 1234               # envoie SIGTERM : « arrête-toi proprement »
kill -9 1234            # envoie SIGKILL : arrêt immédiat, impossible à intercepter
```

`SIGTERM` peut être intercepté pour nettoyer (fermer les connexions, finir le travail en
cours). Démonstration réelle avec `trap "echo nettoyage avant de quitter; exit 0" TERM` :
après `kill -TERM`, le script affiche `nettoyage avant de quitter` et sort avec le code 0.
Avec `kill -9`, aucun nettoyage, et le code de sortie vaut **137** (128 + 9) : le même code
que celui d'un conteneur tué par manque de mémoire (*OOMKilled*).

Règle : toujours `SIGTERM` d'abord ; `SIGKILL` seulement si le processus ne répond pas.
Kubernetes fait exactement ça : SIGTERM, 30 secondes de grâce, puis SIGKILL.

## Les variables d'environnement

```bash
export DATABASE_URL="postgres://…"     # visible par les processus lancés ensuite
echo "$PATH"                            # où le shell cherche les commandes
env | grep JAVA                         # lister l'environnement
```

Un processus hérite d'une **copie** de l'environnement de son parent. C'est le moyen
standard de passer la configuration à une application (principe des *12 facteurs*), sans
la mettre dans le code.

## À retenir

- grep, sort, uniq, awk, sed, find, xargs : de petits outils à combiner.
- `set -euo pipefail` en tête de chaque script ; les variables entre guillemets ; `$?` pour le code de sortie.
- SIGTERM (poli, interceptable) avant SIGKILL (brutal, code 137).
- La configuration passe par l'environnement, hérité par copie.
