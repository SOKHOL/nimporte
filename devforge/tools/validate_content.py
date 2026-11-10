#!/usr/bin/env python3
"""Valide le contenu DevForge.

Deux niveaux de contrôle :
1. le format, avec les JSON Schema de content/schema/ ;
2. la cohérence : identifiants uniques, compétences existantes, prérequis sans
   cycle, numéros de lignes valides, scénarios sans impasse, etc.

Usage :
    python devforge/tools/validate_content.py [--root CHEMIN_VERS_CONTENT]

Code de sortie 0 si tout est valide, 1 sinon (utilisé par la CI).
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Callable

import yaml
from jsonschema import Draft202012Validator

DEFAULT_ROOT = Path(__file__).resolve().parent.parent / "content"

# XP par défaut selon la difficulté, quand l'exercice ne fixe pas "xp".
XP_BY_DIFFICULTY = {1: 10, 2: 20, 3: 35, 4: 50, 5: 80}

LEVEL_DIR = re.compile(r"^n([1-5])$")
PLACEHOLDER = re.compile(r"\{\{(\d+)\}\}")
FRONT_MATTER = re.compile(r"\A---\n(.*?)\n---\n", re.S)

COMMON_FIELDS = {
    "id", "type", "title", "skills", "difficulty", "xp", "language",
    "prompt", "code", "hints", "explanation", "tags",
}
FIELDS_BY_TYPE = {
    "qcm": {"choices", "multiple"},
    "predict-output": {"choices", "expected_output"},
    "find-bug": {"bug_lines"},
    "parsons": {"lines", "distractors"},
    "fill-blanks": {"blanks"},
    "match": {"pairs"},
    "code-review": {"issues", "min_found"},
    "scenario": {"start", "nodes"},
    "design-challenge": {"requirements", "constraints", "rubric", "reference_solution"},
    "code": {"starter_code", "tests", "solution", "timeout_ms"},
}


# ---------------------------------------------------------------------------
# Règles par type d'exercice (une fonction par type : pattern Strategy).
# Chaque règle reçoit l'exercice (déjà conforme au schéma) et renvoie la liste
# des problèmes trouvés.
# ---------------------------------------------------------------------------

def code_line_count(ex: dict) -> int:
    return len(ex.get("code", "").rstrip("\n").split("\n"))


def check_choices(ex: dict, single_answer: bool) -> list[str]:
    choices = ex.get("choices", [])
    if not choices:
        return []
    problems = []
    ids = [c["id"] for c in choices]
    duplicates = sorted(i for i, n in Counter(ids).items() if n > 1)
    if duplicates:
        problems.append(f"identifiants de choix en double : {duplicates}")
    correct = sum(1 for c in choices if c.get("correct"))
    if correct == 0:
        problems.append("aucun choix n'est marqué correct")
    elif single_answer and correct > 1:
        problems.append(f"{correct} choix corrects alors qu'une seule réponse est attendue")
    return problems


def check_qcm(ex: dict) -> list[str]:
    return check_choices(ex, single_answer=not ex.get("multiple", False))


def check_predict_output(ex: dict) -> list[str]:
    return check_choices(ex, single_answer=True)


def check_find_bug(ex: dict) -> list[str]:
    total = code_line_count(ex)
    return [
        f"bug_lines : la ligne {n} n'existe pas (le code a {total} lignes)"
        for n in ex["bug_lines"] if n > total
    ]


def check_parsons(ex: dict) -> list[str]:
    solution = set(ex["lines"])
    return [
        f"le leurre {d!r} est identique à une ligne de la solution"
        for d in ex.get("distractors", []) if d in solution
    ]


def check_fill_blanks(ex: dict) -> list[str]:
    problems = []
    found = PLACEHOLDER.findall(ex["code"])
    for key, n in Counter(found).items():
        if n > 1:
            problems.append(f"le trou {{{{{key}}}}} apparaît {n} fois dans le code")
    in_code, declared = set(found), set(ex["blanks"])
    if in_code - declared:
        problems.append(f"trous présents dans le code mais non décrits : {sorted(in_code - declared)}")
    if declared - in_code:
        problems.append(f"trous décrits mais absents du code : {sorted(declared - in_code)}")
    for key, blank in ex["blanks"].items():
        options = blank.get("options")
        if options and not set(blank["answers"]) & set(options):
            problems.append(f"trou {key} : aucune bonne réponse dans la banque de mots")
    return problems


def check_match(ex: dict) -> list[str]:
    problems = []
    for side in ("left", "right"):
        values = [p[side] for p in ex["pairs"]]
        duplicates = sorted(v for v, n in Counter(values).items() if n > 1)
        if duplicates:
            problems.append(f"valeurs en double côté {side} : {duplicates}")
    return problems


def check_code_review(ex: dict) -> list[str]:
    problems = []
    total = code_line_count(ex)
    for i, issue in enumerate(ex["issues"], start=1):
        for n in issue["lines"]:
            if n > total:
                problems.append(f"problème n°{i} : la ligne {n} n'existe pas (le code a {total} lignes)")
    min_found = ex.get("min_found", 1)
    if min_found > len(ex["issues"]):
        problems.append(f"min_found ({min_found}) dépasse le nombre de problèmes ({len(ex['issues'])})")
    return problems


def scenario_max_points(ex: dict) -> int:
    """Meilleur score possible (le graphe doit être sans cycle)."""
    nodes = ex["nodes"]
    memo: dict[str, int] = {}

    def best(node_id: str) -> int:
        if node_id not in memo:
            node = nodes[node_id]
            memo[node_id] = 0 if "ending" in node else max(
                o.get("points", 0) + best(o["next"]) for o in node["options"]
            )
        return memo[node_id]

    return best(ex["start"])


def check_scenario(ex: dict) -> list[str]:
    nodes, start = ex["nodes"], ex["start"]
    if start not in nodes:
        return [f"le nœud de départ {start!r} n'existe pas"]

    problems = []
    for node_id, node in nodes.items():
        for option in node.get("options", []):
            if option["next"] not in nodes:
                problems.append(f"nœud {node_id!r} : la suite {option['next']!r} n'existe pas")
    if problems:
        return problems

    # Parcours en profondeur : détecte les cycles et les nœuds atteignables.
    state: dict[str, str] = {}  # "en cours" ou "fini"

    def visit(node_id: str) -> bool:
        state[node_id] = "en cours"
        for option in nodes[node_id].get("options", []):
            nxt = option["next"]
            if state.get(nxt) == "en cours":
                problems.append(f"cycle détecté : {node_id!r} → {nxt!r}")
                return False
            if nxt not in state and not visit(nxt):
                return False
        state[node_id] = "fini"
        return True

    if not visit(start):
        return problems

    unreachable = sorted(set(nodes) - set(state))
    if unreachable:
        problems.append(f"nœuds jamais atteignables : {unreachable}")
    if not any(nodes[n].get("ending") == "success" for n in state):
        problems.append("aucune fin « success » n'est atteignable")
    return problems


CHECKS: dict[str, Callable[[dict], list[str]]] = {
    "qcm": check_qcm,
    "predict-output": check_predict_output,
    "find-bug": check_find_bug,
    "parsons": check_parsons,
    "fill-blanks": check_fill_blanks,
    "match": check_match,
    "code-review": check_code_review,
    "scenario": check_scenario,
}


def check_exercise(ex: dict) -> list[str]:
    """Contrôles propres au type, plus les champs qui n'ont rien à faire là."""
    allowed = COMMON_FIELDS | FIELDS_BY_TYPE[ex["type"]]
    problems = [
        f"champ {name!r} inutile pour un exercice de type {ex['type']!r}"
        for name in sorted(set(ex) - allowed)
    ]
    check = CHECKS.get(ex["type"])
    if check:
        problems += check(ex)
    return problems


def exercise_xp(ex: dict) -> int:
    return ex.get("xp", XP_BY_DIFFICULTY[ex["difficulty"]])


# ---------------------------------------------------------------------------
# Validation de l'arborescence complète
# ---------------------------------------------------------------------------

class Validator:
    def __init__(self, root: Path):
        self.root = root
        self.errors: list[str] = []
        self.schemas = {
            name: Draft202012Validator(
                json.loads((root / "schema" / f"{name}.schema.json").read_text(encoding="utf-8"))
            )
            for name in ("curriculum", "track", "lesson", "exercises")
        }
        self.ids: dict[str, str] = {}  # identifiant -> fichier qui le déclare
        self.exercises: dict[str, tuple[str, int, dict]] = {}  # id -> (parcours, niveau, exercice)
        self.lessons = 0

    # -- utilitaires ---------------------------------------------------------

    def rel(self, path: Path) -> str:
        return str(path.relative_to(self.root.parent))

    def error(self, where: str, message: str) -> None:
        self.errors.append(f"{where} : {message}")

    def load_yaml(self, path: Path):
        try:
            return yaml.safe_load(path.read_text(encoding="utf-8"))
        except yaml.YAMLError as e:
            self.error(self.rel(path), f"YAML invalide : {e}")
            return None

    def schema_ok(self, schema: str, data, where: str) -> bool:
        errors = sorted(self.schemas[schema].iter_errors(data), key=lambda e: list(map(str, e.absolute_path)))
        for e in errors:
            location = "/".join(map(str, e.absolute_path)) or "(racine)"
            self.error(where, f"{location} : {e.message}")
        return not errors

    def register_id(self, identifier: str, where: str) -> None:
        if identifier in self.ids:
            self.error(where, f"identifiant {identifier!r} déjà utilisé dans {self.ids[identifier]}")
        else:
            self.ids[identifier] = where

    # -- curriculum ----------------------------------------------------------

    def validate_curriculum(self) -> dict | None:
        path = self.root / "curriculum.yaml"
        where = self.rel(path)
        data = self.load_yaml(path)
        if data is None or not self.schema_ok("curriculum", data, where):
            return None

        if [r["level"] for r in data["ranks"]] != [1, 2, 3, 4, 5]:
            self.error(where, "les rangs doivent être numérotés 1 à 5 dans l'ordre")

        domains = {d["id"] for d in data["domains"]}
        tracks = {}
        for t in data["tracks"]:
            if t["id"] in tracks:
                self.error(where, f"parcours {t['id']!r} déclaré deux fois")
            tracks[t["id"]] = t
            if t["domain"] not in domains:
                self.error(where, f"parcours {t['id']!r} : domaine inconnu {t['domain']!r}")

        graph: dict[str, list[str]] = {}
        for t in tracks.values():
            graph[t["id"]] = []
            for req in t["requires"]:
                req_track = req.split("@")[0]
                if req_track == t["id"]:
                    self.error(where, f"parcours {t['id']!r} : se requiert lui-même")
                elif req_track not in tracks:
                    self.error(where, f"parcours {t['id']!r} : prérequis inconnu {req!r}")
                else:
                    graph[t["id"]].append(req_track)

        cycle = find_cycle(graph)
        if cycle:
            self.error(where, f"cycle dans les prérequis : {' → '.join(cycle)}")
        return tracks

    # -- parcours ------------------------------------------------------------

    def validate_tracks(self, curriculum: dict) -> None:
        tracks_dir = self.root / "tracks"
        dirs = {p.name: p for p in sorted(tracks_dir.iterdir()) if p.is_dir()} if tracks_dir.is_dir() else {}
        pilots = {tid for tid, t in curriculum.items() if t["status"] == "pilot"}

        for missing in sorted(pilots - set(dirs)):
            self.error("content/tracks", f"le parcours pilote {missing!r} n'a pas de dossier")
        for extra in sorted(set(dirs) - set(curriculum)):
            self.error(f"content/tracks/{extra}", "dossier qui ne correspond à aucun parcours de curriculum.yaml")
        for not_pilot in sorted((set(dirs) & set(curriculum)) - pilots):
            self.error(f"content/tracks/{not_pilot}", "le parcours a du contenu mais n'est pas marqué « pilot »")

        for track_id, track_dir in dirs.items():
            if track_id in curriculum:
                self.validate_track(track_id, track_dir)

    def validate_track(self, track_id: str, track_dir: Path) -> None:
        path = track_dir / "track.yaml"
        where = self.rel(path)
        if not path.exists():
            self.error(self.rel(track_dir), "track.yaml manquant")
            return
        track = self.load_yaml(path)
        if track is None or not self.schema_ok("track", track, where):
            return
        if track["id"] != track_id:
            self.error(where, f"id {track['id']!r} différent du nom du dossier {track_id!r}")
        if [lv["level"] for lv in track["levels"]] != [1, 2, 3, 4, 5]:
            self.error(where, "les niveaux doivent être numérotés 1 à 5 dans l'ordre")

        skill_level: dict[str, int] = {}
        for lv in track["levels"]:
            for skill in lv["skills"]:
                if skill["id"] in skill_level:
                    self.error(where, f"compétence {skill['id']!r} déclarée deux fois")
                skill_level.setdefault(skill["id"], lv["level"])

        for level_dir in sorted(p for p in track_dir.iterdir() if p.is_dir()):
            match = LEVEL_DIR.match(level_dir.name)
            if not match:
                self.error(self.rel(level_dir), "les dossiers de niveau s'appellent n1 à n5")
                continue
            level = int(match.group(1))
            for file in sorted(level_dir.iterdir()):
                if file.suffix in (".yaml", ".yml"):
                    self.validate_exercise_file(file, track_id, level, skill_level)
                elif file.suffix == ".md":
                    self.validate_lesson(file, track_id, level, skill_level)
                else:
                    self.error(self.rel(file), "fichier inattendu (attendu : .yaml ou .md)")

        for lv in track["levels"]:
            if "boss" in lv:
                self.validate_boss(lv["boss"], track_id, lv["level"], where)

    def check_skills(self, skills: list[str], level: int, skill_level: dict[str, int], where: str) -> None:
        for skill in skills:
            if skill not in skill_level:
                self.error(where, f"compétence inconnue dans ce parcours : {skill!r}")
            elif skill_level[skill] > level:
                self.error(where, f"la compétence {skill!r} appartient au niveau {skill_level[skill]}, pas au niveau {level}")

    def check_id_prefix(self, identifier: str, track_id: str, level: int, where: str) -> None:
        prefix = f"{track_id}.n{level}."
        if not identifier.startswith(prefix):
            self.error(where, f"l'identifiant doit commencer par {prefix!r}")

    def validate_exercise_file(self, path: Path, track_id: str, level: int, skill_level: dict[str, int]) -> None:
        file_where = self.rel(path)
        data = self.load_yaml(path)
        if data is None or not self.schema_ok("exercises", data, file_where):
            return
        for ex in data["exercises"]:
            where = f"{file_where} [{ex['id']}]"
            self.register_id(ex["id"], file_where)
            self.check_id_prefix(ex["id"], track_id, level, where)
            self.check_skills(ex["skills"], level, skill_level, where)
            for problem in check_exercise(ex):
                self.error(where, problem)
            self.exercises[ex["id"]] = (track_id, level, ex)

    def validate_lesson(self, path: Path, track_id: str, level: int, skill_level: dict[str, int]) -> None:
        where = self.rel(path)
        text = path.read_text(encoding="utf-8")
        match = FRONT_MATTER.match(text)
        if not match:
            self.error(where, "en-tête YAML (front matter) manquant entre deux lignes ---")
            return
        try:
            meta = yaml.safe_load(match.group(1))
        except yaml.YAMLError as e:
            self.error(where, f"en-tête YAML invalide : {e}")
            return
        if not self.schema_ok("lesson", meta, where):
            return
        if not text[match.end():].strip():
            self.error(where, "la leçon est vide")
        self.register_id(meta["id"], where)
        self.check_id_prefix(meta["id"], track_id, level, where)
        self.check_skills(meta["skills"], level, skill_level, where)
        self.lessons += 1

    def validate_boss(self, boss: dict, track_id: str, level: int, where: str) -> None:
        self.register_id(boss["id"], where)
        self.check_id_prefix(boss["id"], track_id, level, f"{where} [boss]")
        for ex_id in boss["exercises"]:
            found = self.exercises.get(ex_id)
            if found is None:
                self.error(where, f"boss {boss['id']!r} : exercice introuvable {ex_id!r}")
            elif found[0] != track_id or found[1] > level:
                self.error(where, f"boss {boss['id']!r} : {ex_id!r} n'appartient pas à ce parcours jusqu'au niveau {level}")

    # -- point d'entrée ------------------------------------------------------

    def run(self) -> bool:
        curriculum = self.validate_curriculum()
        if curriculum is not None:
            self.validate_tracks(curriculum)
        return not self.errors

    def summary(self) -> str:
        types = Counter(ex["type"] for _, _, ex in self.exercises.values())
        per_track = Counter(t for t, _, _ in self.exercises.values())
        xp = sum(exercise_xp(ex) for _, _, ex in self.exercises.values())
        lines = [
            f"  {len(self.exercises)} exercices, {self.lessons} leçons, {xp} XP au total",
            "  par parcours : " + ", ".join(f"{t} {n}" for t, n in sorted(per_track.items())),
            "  par type     : " + ", ".join(f"{t} {n}" for t, n in sorted(types.items())),
        ]
        return "\n".join(lines)


def find_cycle(graph: dict[str, list[str]]) -> list[str] | None:
    state: dict[str, str] = {}
    stack: list[str] = []

    def visit(node: str) -> list[str] | None:
        state[node] = "en cours"
        stack.append(node)
        for nxt in graph.get(node, []):
            if state.get(nxt) == "en cours":
                return stack[stack.index(nxt):] + [nxt]
            if nxt not in state:
                cycle = visit(nxt)
                if cycle:
                    return cycle
        stack.pop()
        state[node] = "fini"
        return None

    for node in graph:
        if node not in state:
            cycle = visit(node)
            if cycle:
                return cycle
    return None


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Valide le contenu DevForge.")
    parser.add_argument("--root", type=Path, default=DEFAULT_ROOT, help="dossier content/ à valider")
    args = parser.parse_args(argv)

    validator = Validator(args.root.resolve())
    ok = validator.run()
    print("DevForge — validation du contenu")
    print(validator.summary())
    if ok:
        print("✅ Aucun problème.")
        return 0
    print(f"❌ {len(validator.errors)} problème(s) :")
    for e in validator.errors:
        print(f"  - {e}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
