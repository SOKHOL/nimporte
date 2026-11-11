---
id: git.n4.travailler-en-equipe
title: Travailler en équipe avec Git
skills: [workflows, messages-commits, versions-tags, hooks]
duration_min: 12
---

# Travailler en équipe avec Git

## Les workflows

| Workflow | Principe | Pour qui |
|---|---|---|
| **Trunk-based** | tout le monde intègre sur `main` au moins une fois par jour, par de très petites branches ; les fonctionnalités inachevées sont cachées derrière des *feature flags* | équipes qui livrent en continu |
| **GitHub Flow** | une branche par fonctionnalité, une pull request, fusion dans `main`, déploiement | la plupart des équipes |
| **GitFlow** | branches `develop`, `release`, `hotfix` et `main` | logiciels livrés en versions espacées (applications installées, bibliothèques) |

Les études *Accelerate* (DORA) montrent que les équipes les plus performantes ont des
branches de **courte durée** (moins d'une journée) : moins de conflits, des revues plus
petites, une intégration continue au vrai sens du terme. GitFlow, pensé pour des sorties
rares, devient lourd quand on livre plusieurs fois par jour.

## Les messages de commit conventionnels

La convention *Conventional Commits* structure la première ligne :

```
feat(profil): ajoute le radar de compétences
fix(session): corrige le chronomètre du boss après une rotation
docs: explique l'installation de l'APK
refactor(domaine): extrait le calcul d'XP
feat(api)!: renomme le champ « niveau » en « rang »
```

`feat` (nouvelle fonctionnalité), `fix` (correction), `docs`, `refactor`, `test`, `chore`…
Le `!` signale un changement **incompatible**. Des outils en déduisent automatiquement la
prochaine version et génèrent le journal des modifications.

## Versions et tags

Un **tag** est une étiquette fixe sur un commit, typiquement une version :

```bash
git tag -a v1.4.0 -m "Version 1.4.0"
git push origin v1.4.0
```

Avec le versionnage sémantique (parcours *Fondations*, niveau 4) : un `fix` fait monter le
correctif (1.4.1), un `feat` la mineure (1.5.0), un `!` la majeure (2.0.0).

## Les hooks

Les *hooks* sont des scripts que Git exécute à certains moments : `pre-commit` (avant chaque
commit), `commit-msg` (pour vérifier le message), `pre-push`. Usages : formater le code,
lancer un linter rapide, refuser un message mal formé, bloquer un secret.

Les hooks locaux (`.git/hooks/`) ne sont pas partagés par le dépôt et peuvent être
contournés (`--no-verify`) : ils aident, mais la **vraie** barrière est la CI. Des outils
(pre-commit, Husky, lefthook) les partagent dans le dépôt.

## Monorepo ou plusieurs dépôts ?

| | Monorepo (un seul dépôt) | Plusieurs dépôts |
|---|---|---|
| Changement transversal | un seul commit, atomique | plusieurs pull requests coordonnées |
| Visibilité du code | tout est là | il faut savoir où chercher |
| Outillage | builds incrémentaux indispensables (Gradle, Bazel, Nx) | simple au début |
| Droits d'accès | plus difficiles à cloisonner | naturels |

Google et Meta utilisent des monorepos géants ; beaucoup d'équipes préfèrent un dépôt par
service. Cette app (`devforge/`) est un petit monorepo : contenu, outils et app ensemble.

## À retenir

- Branches courtes et intégration fréquente : moins de conflits, livraison plus rapide.
- Conventional Commits : `feat`, `fix`, `!` ; les versions et le journal en découlent.
- Tags pour les versions ; hooks pour aider, CI pour garantir.
- Monorepo ou multi-dépôts : un compromis entre atomicité et autonomie.
