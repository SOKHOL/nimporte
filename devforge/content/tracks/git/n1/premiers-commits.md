---
id: git.n1.premiers-commits
title: Les trois zones et les premiers commits
skills: [trois-zones, commits, historique, gitignore]
duration_min: 12
---

# Les trois zones et les premiers commits

## Les trois zones

Git manipule trois endroits, et la plupart des confusions viennent de là :

| Zone | Contient | On y va avec |
|---|---|---|
| **Répertoire de travail** | tes fichiers tels que tu les modifies | ton éditeur |
| **Index** (*staging area*) | ce qui partira dans le prochain commit | `git add` |
| **Dépôt** | l'historique des commits | `git commit` |

```bash
git init                      # crée un dépôt dans le dossier courant
git add notes.txt             # place la version actuelle du fichier dans l'index
git commit -m "Ajoute les notes"
```

Démonstration réelle : on ajoute « ligne 2 » à `notes.txt`, on fait `git add`, puis on
ajoute « ligne 3 » **sans** `git add`.

```
$ git status --short
MM notes.txt
```

Deux `M` : la première colonne dit que l'index contient une modification (ligne 2), la
seconde que le répertoire de travail en contient une autre, pas encore indexée (ligne 3).
`git diff --staged` montre la première, `git diff` la seconde. Le prochain commit ne
contiendra **que** la ligne 2.

## L'historique

```bash
git log --oneline             # un commit par ligne
git log -p notes.txt          # l'historique d'un fichier, avec les changements
git show <commit>             # le détail d'un commit
git diff                      # répertoire de travail ↔ index
git diff --staged             # index ↔ dernier commit
```

## Un bon commit

- **Petit et cohérent** : un seul changement logique. Facile à relire, à annuler, à comprendre.
- **Un message qui dit pourquoi** : le code montre *quoi* ; le message explique *pourquoi*.
  Une première ligne courte à l'impératif (« Corrige le calcul de la TVA »), puis si
  besoin un paragraphe.
- **Qui compile et passe les tests** : chaque commit de la branche principale devrait
  être un point de retour possible.

## Ignorer des fichiers

Le fichier `.gitignore` liste ce qui ne doit jamais être versionné :

```
build/
.gradle/
*.log
local.properties
.env
```

Les fichiers générés, les dépendances téléchargées et surtout les **secrets** (`.env`,
clés d'API) n'ont rien à faire dans Git. Attention : un fichier déjà versionné reste suivi
même si on l'ajoute au `.gitignore` (il faut `git rm --cached`), et un secret poussé une
fois reste dans l'historique : il faut le **révoquer**, pas seulement le supprimer.

## À retenir

- Trois zones : travail, index (`git add`), dépôt (`git commit`).
- `MM` dans `git status --short` : une modification indexée, une autre non.
- Des commits petits, cohérents, dont le message explique pourquoi.
- `.gitignore` pour les fichiers générés et les secrets ; un secret poussé se révoque.
