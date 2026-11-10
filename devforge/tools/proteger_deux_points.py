#!/usr/bin/env python3
"""Met entre guillemets les valeurs YAML qui contiennent « : » (typographie française).

En YAML, `feedback: Le tas : partagé` est une erreur : le second « : » est lu comme une
nouvelle clé. Ce script repère ces valeurs sur une ligne et les protège avec des guillemets.
Les blocs `|` et `>` ne sont pas touchés.

Usage :
    python devforge/tools/proteger_deux_points.py            # tous les exercices
    python devforge/tools/proteger_deux_points.py fichier.yaml
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

import yaml

CONTENT = Path(__file__).resolve().parent.parent / "content"
VALEUR_A_RISQUE = re.compile(r'^(\s*(?:- )?[a-z_]+: )(?!["\'|>\[{])(.*: .*)$')
DEBUT_DE_BLOC = re.compile(r'^\s*(?:- )?[a-z_]+: [|>][-+]?\s*$')


def proteger(texte: str) -> tuple[str, int]:
    lignes, sortie = texte.split("\n"), []
    dans_bloc, indentation_bloc, modifications = False, 0, 0
    for ligne in lignes:
        indentation = len(ligne) - len(ligne.lstrip())
        if dans_bloc:
            if not ligne.strip() or indentation > indentation_bloc:
                sortie.append(ligne)
                continue
            dans_bloc = False
        trouve = VALEUR_A_RISQUE.match(ligne)
        if trouve:
            valeur = trouve.group(2).replace("\\", "\\\\").replace('"', '\\"')
            ligne = f'{trouve.group(1)}"{valeur}"'
            modifications += 1
        if DEBUT_DE_BLOC.match(ligne):
            dans_bloc, indentation_bloc = True, indentation
        sortie.append(ligne)
    return "\n".join(sortie), modifications


def main(arguments: list[str]) -> int:
    fichiers = [Path(a) for a in arguments] or sorted(CONTENT.glob("tracks/*/n*/*.yaml"))
    for fichier in fichiers:
        texte, modifications = proteger(fichier.read_text(encoding="utf-8"))
        if modifications:
            yaml.safe_load(texte)  # le résultat doit être du YAML valide
            fichier.write_text(texte, encoding="utf-8")
            print(f"{fichier} : {modifications} valeur(s) protégée(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
