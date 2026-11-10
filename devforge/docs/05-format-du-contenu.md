# 05 — Format du contenu

Le contenu vit dans [`content/`](../content/). Il est validé par
[`tools/validate_content.py`](../tools/validate_content.py) à chaque push.

## Arborescence

```
content/
├── curriculum.yaml               # rangs, domaines, parcours et prérequis
├── schema/                       # JSON Schema (utilisables aussi dans VS Code / IntelliJ)
└── tracks/
    └── <parcours>/
        ├── track.yaml            # niveaux, compétences, boss
        └── n<niveau>/            # n1 à n5
            ├── <lecon>.md        # une leçon (Markdown + en-tête YAML)
            └── exercises.yaml    # les exercices du niveau (un ou plusieurs fichiers)
```

Un parcours `planned` peut déjà avoir son `track.yaml` (le programme de ses 5 niveaux,
affiché dans l'app), mais pas encore de dossier de niveau : pour ajouter des leçons ou
des exercices, passe-le en `pilot` dans `curriculum.yaml`.

## Identifiants

- Parcours et compétences : `minuscules-avec-tirets` (ex. `design-patterns`, `factory-method`).
- Leçons, exercices et boss : `<parcours>.n<niveau>.<nom>` (ex. `poo.n1.encapsulation.qcm-invariant`).
- Tous les identifiants sont **uniques** et **ne changent plus** une fois publiés :
  la progression des utilisateurs y fait référence.

## Prérequis

Dans `curriculum.yaml`, `requires: [poo@2, clean-code@1]` signifie : le parcours se
débloque quand le niveau 2 de POO **et** le niveau 1 de Clean code sont validés.

## Leçon

```markdown
---
id: poo.n1.encapsulation
title: L'encapsulation
skills: [encapsulation]
duration_min: 5
---

# L'encapsulation
…
```

## Champs communs à tous les exercices

| Champ | Obligatoire | Description |
|---|---|---|
| `id` | oui | identifiant unique |
| `type` | oui | un des types ci-dessous |
| `title` | oui | titre court |
| `skills` | oui | compétences travaillées (du niveau courant ou des niveaux inférieurs) |
| `difficulty` | oui | 1 à 5 |
| `prompt` | oui | consigne (Markdown) |
| `explanation` | oui | explication affichée après la réponse, juste ou fausse |
| `xp` | non | par défaut selon la difficulté : 10, 20, 35, 50, 80 |
| `language` | non | `kotlin`, `java`, `python`, `bash`, `dockerfile`, `yaml`, `sql`, `text` |
| `code` | selon le type | code affiché |
| `hints` | non | indices progressifs, du plus vague au plus précis |
| `tags` | non | mots-clés libres |

## Champs par type

Chaque type n'accepte que ses propres champs, en plus des champs communs.

| Type | Champs | Règles vérifiées |
|---|---|---|
| `qcm` | `choices`, `multiple` | au moins un choix correct ; exactement un si `multiple` est absent |
| `predict-output` | `code` + `choices` ou `expected_output` | exactement un choix correct |
| `find-bug` | `code`, `bug_lines` | les lignes existent (numérotées à partir de 1) ; dans l'app, la réponse est juste si toutes les lignes touchées sont dans `bug_lines` |
| `parsons` | `lines` (dans l'ordre correct), `distractors` | aucun leurre identique à une ligne de la solution |
| `fill-blanks` | `code` avec `{{1}}`, `{{2}}`…, `blanks` | chaque trou est décrit, apparaît une fois, et sa banque de mots contient une bonne réponse |
| `match` | `pairs` (`left`, `right`) | au moins 3 paires, pas de doublons |
| `code-review` | `code`, `issues` (`lines`, `category`, `description`), `min_found` | les lignes existent ; `min_found` ≤ nombre de problèmes |
| `scenario` | `start`, `nodes` | chaque suite existe, pas de cycle, tous les nœuds sont atteignables, une fin `success` est atteignable |
| `design-challenge` | `requirements`, `constraints`, `rubric`, `reference_solution` | au moins un critère |
| `code` | `language`, `starter_code`, `tests`, `solution`, `timeout_ms` | (phase 3) |

Catégories de `code-review` : `bug`, `securite`, `performance`, `lisibilite`, `conception`, `test`.

### Exemple de scénario

```yaml
start: alerte
nodes:
  alerte:
    text: "🚨 L'API ne répond plus. Que fais-tu ?"
    options:
      - text: Je lis les logs du dernier crash.
        next: logs
        points: 3
        feedback: Bon réflexe.
      - text: Je redémarre tout.
        next: echec
        points: 0
  logs:
    text: …
    options: […]
  echec:
    ending: failure       # success, partial ou failure
    text: Le problème revient 5 minutes plus tard.
```

Le score d'un scénario est la somme des `points` du chemin suivi, rapportée au
meilleur chemin possible (calculé par le validateur).

## Exemples complets

| Type | Fichier |
|---|---|
| `qcm`, `predict-output`, `find-bug`, `fill-blanks`, `parsons` | [`tracks/poo/n1/exercises.yaml`](../content/tracks/poo/n1/exercises.yaml) |
| `match`, `code-review`, `fill-blanks`, `predict-output` | [`tracks/design-patterns/n1/exercises.yaml`](../content/tracks/design-patterns/n1/exercises.yaml) |
| `code-review` (architecture) | [`tracks/architecture/n3/exercises.yaml`](../content/tracks/architecture/n3/exercises.yaml) |
| `scenario` | [`tracks/devops/n4/exercises.yaml`](../content/tracks/devops/n4/exercises.yaml) |
| `design-challenge` | [`tracks/system-design/n4/exercises.yaml`](../content/tracks/system-design/n4/exercises.yaml) |

## Pièges de rédaction

- **Les deux-points à la française.** En YAML, `texte : suite` sans guillemets est une
  erreur. Mets le texte entre guillemets, ou utilise un bloc `|`.
- **Commencer par un backtick.** Une valeur qui commence par `` ` `` doit être entre guillemets.
- **Vérifie le code.** Pour `predict-output`, exécute réellement le programme : une
  réponse attendue fausse ruine la confiance dans l'app.
- **Parsons sans ambiguïté.** Choisis un code dont l'ordre est imposé par la logique
  (sinon plusieurs ordres seraient corrects).
- **Chaque mauvais choix enseigne quelque chose.** Un `feedback` par mauvaise réponse
  vaut mieux qu'une explication générique.

## Valider

```bash
pip install -r devforge/tools/requirements.txt
python devforge/tools/validate_content.py              # le contenu
python -m unittest discover -s devforge/tools -v       # les tests du validateur
```
