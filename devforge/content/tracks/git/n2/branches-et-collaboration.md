---
id: git.n2.branches-et-collaboration
title: Branches, fusions et conflits
skills: [branches, fusions, conflits, depots-distants, pull-requests]
duration_min: 14
---

# Branches, fusions et conflits

## Les branches

Une branche est une **ligne de développement** : tu travailles sur une fonctionnalité sans
toucher à la branche principale, puis tu fusionnes.

```bash
git switch -c profil        # crée la branche « profil » et s'y place
# … commits …
git switch main             # revient sur main
git merge profil            # intègre le travail de profil dans main
```

Créer une branche ne coûte rien : ce n'est qu'une étiquette qui pointe vers un commit
(niveau 5).

## Avance rapide ou commit de fusion ?

Si `main` n'a pas bougé depuis la création de la branche, Git déplace simplement
l'étiquette : c'est une **avance rapide** (*fast-forward*). Sortie réelle :

```
$ git merge fonction
Updating 7cd2be0..e8b69ac
Fast-forward
```

Si les deux branches ont avancé, Git crée un **commit de fusion** qui a deux parents.

## Les conflits

Quand les deux branches ont modifié **les mêmes lignes**, Git ne peut pas choisir. Sortie
réelle :

```
$ git merge branche-x
Auto-merging notes.txt
CONFLICT (content): Merge conflict in notes.txt
Automatic merge failed; fix conflicts and then commit the result.
```

Et le fichier contient :

```
<<<<<<< HEAD
Titre principal
=======
Titre X
>>>>>>> branche-x
```

En haut, ta version (`HEAD`) ; en bas, celle de la branche fusionnée. Pour résoudre :
éditer le fichier pour garder ce qui est juste (parfois un mélange des deux), supprimer les
marqueurs, puis `git add notes.txt` et `git commit`. En cas de doute : `git merge --abort`
revient à l'état d'avant.

Les conflits ne sont pas des erreurs : ce sont deux décisions incompatibles qu'un humain
doit arbitrer. Des branches courtes et des fusions fréquentes les rendent petits et rares.

## Les dépôts distants

```bash
git clone https://github.com/equipe/projet.git   # copie un dépôt distant
git fetch                     # récupère les nouveautés, sans toucher à tes branches
git pull                      # fetch + fusion dans ta branche courante
git push -u origin profil     # publie ta branche
```

`origin` est le nom par défaut du dépôt distant. `git fetch` est toujours sans danger ;
`git pull` modifie ta branche.

## Les pull requests

Sur GitHub ou GitLab, une **pull request** (ou *merge request*) propose de fusionner une
branche. C'est le lieu de la **revue de code** et des vérifications automatiques (tests,
lint). Une bonne pull request est petite (moins de 400 lignes), explique son but et la façon
de la tester, et ne mélange pas refactoring et nouvelle fonctionnalité.

## À retenir

- Une branche = une étiquette sur un commit ; `git switch -c` pour la créer.
- Avance rapide si la cible n'a pas bougé, sinon commit de fusion.
- Conflit : `<<<<<<<`, `=======`, `>>>>>>>` ; on choisit, on `add`, on `commit` ; `--abort` pour reculer.
- `fetch` récupère sans toucher, `pull` fusionne, `push` publie.
- Des pull requests petites, expliquées, vérifiées automatiquement.
