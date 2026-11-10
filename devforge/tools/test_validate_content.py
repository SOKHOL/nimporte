"""Tests du validateur de contenu.

Lancer : python -m unittest discover -s devforge/tools -v
"""
import shutil
import tempfile
import unittest
from pathlib import Path

import yaml

import validate_content as vc


def scenario(nodes: dict, start: str = "a") -> dict:
    return {"type": "scenario", "start": start, "nodes": nodes}


class ExerciseChecksTest(unittest.TestCase):
    def test_qcm_needs_exactly_one_correct_answer_unless_multiple(self):
        ex = {"type": "qcm", "choices": [{"id": "a", "text": "x", "correct": True},
                                         {"id": "b", "text": "y", "correct": True}]}
        self.assertTrue(vc.check_qcm(ex))
        self.assertEqual(vc.check_qcm({**ex, "multiple": True}), [])

    def test_qcm_without_correct_answer_is_rejected(self):
        ex = {"type": "qcm", "choices": [{"id": "a", "text": "x"}, {"id": "b", "text": "y"}]}
        self.assertIn("aucun choix n'est marqué correct", vc.check_qcm(ex))

    def test_find_bug_line_must_exist(self):
        ex = {"type": "find-bug", "code": "a\nb\n", "bug_lines": [3]}
        self.assertTrue(vc.check_find_bug(ex))
        self.assertEqual(vc.check_find_bug({**ex, "bug_lines": [2]}), [])

    def test_parsons_distractor_cannot_be_a_solution_line(self):
        ex = {"type": "parsons", "lines": ["a", "b", "c"], "distractors": ["b"]}
        self.assertTrue(vc.check_parsons(ex))

    def test_fill_blanks_placeholders_must_match_declared_blanks(self):
        ex = {"type": "fill-blanks", "code": "x = {{1}} + {{2}}",
              "blanks": {"1": {"answers": ["a"]}, "3": {"answers": ["c"]}}}
        problems = vc.check_fill_blanks(ex)
        self.assertEqual(len(problems), 2)  # {{2}} non décrit, {{3}} absent du code

    def test_fill_blanks_word_bank_must_contain_an_answer(self):
        ex = {"type": "fill-blanks", "code": "{{1}}",
              "blanks": {"1": {"answers": ["val"], "options": ["var", "fun"]}}}
        self.assertTrue(vc.check_fill_blanks(ex))

    def test_code_review_min_found_cannot_exceed_issues(self):
        ex = {"type": "code-review", "code": "a\nb", "min_found": 2,
              "issues": [{"lines": [1], "category": "bug", "description": "d"}]}
        self.assertTrue(vc.check_code_review(ex))

    def test_scenario_valid_and_max_points(self):
        ex = scenario({
            "a": {"text": "t", "options": [{"text": "1", "next": "b", "points": 1},
                                           {"text": "2", "next": "c", "points": 3}]},
            "b": {"text": "t", "ending": "partial"},
            "c": {"text": "t", "options": [{"text": "3", "next": "b", "points": 2}]},
        })
        self.assertTrue(vc.check_scenario(ex))  # aucune fin "success"
        ex["nodes"]["b"]["ending"] = "success"
        self.assertEqual(vc.check_scenario(ex), [])
        self.assertEqual(vc.scenario_max_points(ex), 5)

    def test_scenario_detects_cycles_and_dangling_links(self):
        cyclic = scenario({
            "a": {"text": "t", "options": [{"text": "1", "next": "b"}]},
            "b": {"text": "t", "options": [{"text": "2", "next": "a"}]},
        })
        self.assertTrue(any("cycle" in p for p in vc.check_scenario(cyclic)))
        dangling = scenario({"a": {"text": "t", "options": [{"text": "1", "next": "zzz"}]},
                             "b": {"text": "t", "ending": "success"}})
        self.assertTrue(any("zzz" in p for p in vc.check_scenario(dangling)))

    def test_fields_of_another_type_are_rejected(self):
        ex = {"type": "qcm", "choices": [{"id": "a", "text": "x", "correct": True},
                                         {"id": "b", "text": "y"}], "bug_lines": [1]}
        self.assertTrue(any("bug_lines" in p for p in vc.check_exercise(ex)))

    def test_find_cycle(self):
        self.assertIsNone(vc.find_cycle({"a": ["b"], "b": [], "c": ["a"]}))
        self.assertEqual(vc.find_cycle({"a": ["b"], "b": ["a"]}), ["a", "b", "a"])


class ContentTreeTest(unittest.TestCase):
    """Valide le vrai contenu, puis des copies volontairement cassées."""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.content = self.tmp / "content"
        shutil.copytree(vc.DEFAULT_ROOT, self.content)

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def edit_yaml(self, relative: str, change) -> None:
        path = self.content / relative
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
        change(data)
        path.write_text(yaml.safe_dump(data, allow_unicode=True, sort_keys=False), encoding="utf-8")

    def errors(self) -> list[str]:
        validator = vc.Validator(self.content)
        validator.run()
        return validator.errors

    def test_real_content_is_valid(self):
        self.assertEqual(self.errors(), [])

    def test_unknown_skill_is_reported(self):
        self.edit_yaml("tracks/poo/n1/exercises.yaml",
                       lambda d: d["exercises"][0]["skills"].append("inexistante"))
        self.assertTrue(any("inexistante" in e for e in self.errors()))

    def test_duplicate_exercise_id_is_reported(self):
        def duplicate(d):
            d["exercises"].append(dict(d["exercises"][0]))
        self.edit_yaml("tracks/poo/n1/exercises.yaml", duplicate)
        self.assertTrue(any("déjà utilisé" in e for e in self.errors()))

    def test_boss_with_missing_exercise_is_reported(self):
        self.edit_yaml("tracks/poo/track.yaml",
                       lambda d: d["levels"][0]["boss"]["exercises"].append("poo.n1.fantome"))
        self.assertTrue(any("poo.n1.fantome" in e for e in self.errors()))

    def test_prerequisite_cycle_is_reported(self):
        def cycle(d):
            fondations = next(t for t in d["tracks"] if t["id"] == "fondations")
            fondations["requires"] = ["poo@1"]
        self.edit_yaml("curriculum.yaml", cycle)
        self.assertTrue(any("cycle" in e for e in self.errors()))

    def test_planned_track_cannot_hold_exercises(self):
        level_dir = self.content / "tracks" / "maths" / "n1"
        level_dir.mkdir(parents=True)
        shutil.copy(self.content / "tracks" / "poo" / "n1" / "exercises.yaml", level_dir / "exercises.yaml")
        self.assertTrue(any("planned" in e and "maths" in e for e in self.errors()))

    def test_skill_from_a_higher_level_is_reported(self):
        self.edit_yaml("tracks/poo/n1/exercises.yaml",
                       lambda d: d["exercises"][0]["skills"].append("solid"))
        self.assertTrue(any("niveau 3" in e for e in self.errors()))


if __name__ == "__main__":
    unittest.main()
