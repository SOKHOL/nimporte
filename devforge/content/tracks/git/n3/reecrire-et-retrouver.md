---
id: git.n3.reecrire-et-retrouver
title: Réécrire, annuler et retrouver
skills: [rebase, annuler, reflog, bisect, stash-cherry-pick]
duration_min: 15
---

# Réécrire, annuler et retrouver

## Rebase : rejouer ses commits ailleurs

`git rebase main` prend les commits de ta branche et les **rejoue** par-dessus le dernier
`main`. L'historique devient linéaire, comme si tu avais commencé ton travail aujourd'hui.

```bash
git switch profil
git rebase main          # rejoue les commits de profil après ceux de main
```

`git rebase -i HEAD~3` (interactif) permet de réordonner, fusionner (`squash`) ou reformuler
les trois derniers commits avant de les partager.

**Règle d'or : ne jamais réécrire un historique déjà partagé.** Rebase crée de **nouveaux**
commits (nouveaux identifiants) ; si d'autres personnes ont déjà la branche, leurs copies
divergent. Sur ta branche personnelle, avant la revue : oui. Sur `main` : jamais.

## Annuler : reset, revert, restore

| Commande | Effet | Réécrit l'historique ? |
|---|---|---|
| `git restore fichier` | annule les modifications **non commitées** d'un fichier | non |
| `git reset --soft HEAD~1` | défait le dernier commit, garde les changements indexés | oui |
| `git reset --hard HEAD~1` | défait le dernier commit **et** jette les changements | oui |
| `git revert <commit>` | crée un nouveau commit qui fait l'inverse | **non** |

Sur une branche partagée, on annule avec `revert`, jamais avec `reset`. Sortie réelle :

```
$ git revert --no-edit HEAD
[main c6f63d7] Revert "Titre principal"
```

## Le reflog : rien n'est vraiment perdu

Git note **chaque** déplacement de `HEAD` dans le *reflog*. Démonstration réelle : trois
commits, puis un `git reset --hard HEAD~2` qui semble les effacer.

```
$ git log --oneline
5561056 Ajoute la page d'accueil

$ git reflog
5561056 HEAD@{0}: reset: moving to HEAD~2
c71d1c6 HEAD@{1}: commit: Ajoute les badges
d3f7439 HEAD@{2}: commit: Ajoute le profil
5561056 HEAD@{3}: commit (initial): Ajoute la page d'accueil

$ git reset --hard HEAD@{1}
$ git log --oneline
c71d1c6 Ajoute les badges
d3f7439 Ajoute le profil
5561056 Ajoute la page d'accueil
```

Les commits « perdus » sont revenus. Le reflog est local et conservé environ 90 jours :
un commit n'est vraiment perdu que s'il n'a jamais été commité.

## Bisect : trouver le commit fautif

Un bug est apparu entre une version qui marchait et aujourd'hui, 200 commits plus tard.
`git bisect` fait une recherche dichotomique dans l'historique : 8 étapes au lieu de 200.
Avec un script de test, c'est automatique :

```bash
git bisect start HEAD HEAD~7     # mauvais : HEAD ; bon : 7 commits avant
git bisect run ./teste.sh        # le script renvoie 0 si c'est bon, autre chose sinon
```

Sortie réelle, sur 8 versions dont la 6ᵉ introduit un bug de calcul :

```
4efbe05966ffc862a219c72ec8f7b8f13cc62ac3 is the first bad commit
    Version 6
```

C'est l'algorithme de recherche dichotomique (parcours *Algorithmes*) appliqué à
l'historique ; et une bonne raison d'avoir des commits petits et qui compilent.

## stash et cherry-pick

- `git stash` met de côté les modifications en cours (pour changer de branche en urgence) ;
  `git stash pop` les ramène.
- `git cherry-pick <commit>` applique **un seul** commit d'une autre branche (par exemple un
  correctif urgent à reporter sur une branche de version).

## À retenir

- `rebase` rejoue et réécrit : jamais sur un historique partagé.
- Partagé : `revert`. Local : `reset`. Fichier non commité : `restore`.
- `reflog` retrouve presque tout ce qu'on croit perdu.
- `bisect` trouve le commit fautif par dichotomie, automatiquement avec `bisect run`.
