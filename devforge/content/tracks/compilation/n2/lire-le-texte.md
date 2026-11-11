---
id: compilation.n2.lire-le-texte
title: Analyse lexicale, analyse syntaxique et arbre syntaxique
skills: [analyse-lexicale, analyse-syntaxique, ast]
duration_min: 19
---

# Analyse lexicale, analyse syntaxique et arbre syntaxique

## L'analyse lexicale

Le compilateur commence par découper le texte en **jetons** (*tokens*) : les « mots » du
langage. Il ignore les espaces et les commentaires, et classe chaque jeton (nombre, nom,
mot-clé, symbole). Le cœur de l'analyseur lexical de Mini tient en une expression régulière :

```python
MOTS_CLES = {"soit", "si", "alors", "sinon", "fn", "vrai", "faux"}
MOTIF = re.compile(r"\s*(?:(\d+)|([A-Za-z_][A-Za-z0-9_]*)|(=>|==|[-+*/<>=();,]))")
```

Sortie réelle pour `soit prix = 2 + 3 * 4;` :

```
[MOT_CLE:soit, IDENT:prix, SYMBOLE:=, NOMBRE:2, SYMBOLE:+, NOMBRE:3, SYMBOLE:*, NOMBRE:4, SYMBOLE:;, FIN:]
```

Points de détail qui comptent :

- l'**ordre des alternatives** : `=>` et `==` sont testés **avant** `=`, sinon `==` serait lu
  comme deux `=` (la règle du « plus long jeton possible ») ;
- les **mots-clés** sont d'abord reconnus comme des noms, puis reclassés : `soit` est un
  mot-clé, `soitx` un nom ordinaire ;
- chaque jeton garde sa **position**, pour des messages d'erreur utiles. Réel :
  `soit y = 4 $ 2;` → `caractère inattendu '$' à la position 11`.

Dans les vrais compilateurs, l'analyseur lexical gère aussi les chaînes avec leurs échappements,
les nombres à virgule et en hexadécimal, les commentaires imbriqués, l'Unicode dans les noms,
et parfois l'**indentation** (Python produit des jetons `INDENT` et `DEDENT`).

## L'analyse syntaxique par descente récursive

L'analyseur syntaxique (*parser*) vérifie que les jetons respectent la grammaire et construit
l'**arbre**. La technique la plus simple et la plus répandue dans les compilateurs écrits à la
main (GCC, Clang, le compilateur de Go, celui de Kotlin K2) : la **descente récursive**. On écrit
**une fonction par règle** de la grammaire, et chaque fonction appelle celles des règles dont
elle dépend :

```python
def somme(self):                        # somme = produit { ("+" | "-") produit }
    g = self.produit()
    while self.courant().valeur in ("+", "-"):
        op = self.courant().valeur; self.i += 1
        g = Binaire(op, g, self.produit())
    return g

def produit(self):                      # produit = unaire { ("*" | "/") unaire }
    g = self.unaire()
    while self.courant().valeur in ("*", "/"):
        op = self.courant().valeur; self.i += 1
        g = Binaire(op, g, self.unaire())
    return g
```

Le code **ressemble** à la grammaire : c'est ce qui le rend facile à écrire et à corriger.
Comme `somme` appelle `produit`, la multiplication est traitée « plus bas » et lie plus fort.
Les combinateurs d'analyseurs du parcours Programmation fonctionnelle (niveau 4) en sont une
version fonctionnelle.

Les **erreurs** se signalent avec le jeton attendu et la position. Réel :

```
'soit x = (1 + 2;'   → attendu ')', trouvé ';' à la position 15
'soit = 3;'          → nom de variable attendu, trouvé '=' à la position 5
```

(La première version de Mini disait « attendu '=' » pour la seconde erreur : un message vrai,
mais qui n'aide pas. Les bons messages d'erreur demandent un vrai travail, et font la réputation
d'un langage : Elm et Rust sont réputés pour cela.)

Pour les grammaires plus complexes, on utilise des **générateurs d'analyseurs** (ANTLR, Bison,
tree-sitter) à partir d'une grammaire, ou la méthode de **Pratt** (*precedence climbing*), très
élégante pour les nombreux niveaux de priorité des opérateurs.

## L'arbre syntaxique abstrait

L'analyseur produit un **AST** (*Abstract Syntax Tree*) : la structure du programme, sans les
détails de surface (parenthèses, points-virgules, espaces). Arbre réel de `soit prix = 2 + 3 * 4;` :

```
Soit prix
  Binaire +
    Nombre 2
    Binaire *
      Nombre 3
      Nombre 4
```

La priorité est **dans la forme de l'arbre** : la multiplication est un enfant de l'addition,
donc elle sera calculée d'abord. Les parenthèses ont disparu : `(2 + 3) * 4` donnerait un arbre
où l'addition est l'enfant de la multiplication.

En Python, chaque nœud est une petite classe de données (`@dataclass class Binaire: op; gauche;
droite`) ; en Kotlin, on utiliserait une `sealed interface` et un `when` exhaustif (parcours
Programmation fonctionnelle, niveau 2). Toutes les étapes suivantes (interpréter, vérifier les
types, générer du code, optimiser) sont des **parcours de cet arbre**.

L'AST sert aussi bien au-delà des compilateurs : formateurs de code (Prettier, ktfmt), linters,
refactorings de l'IDE, outils de migration automatique, coloration syntaxique précise
(tree-sitter), et l'analyse statique de sécurité (parcours Sécurité, niveau 4).

## À retenir

- Analyse lexicale : des caractères aux jetons (le plus long jeton d'abord, mots-clés reclassés, positions conservées).
- Descente récursive : une fonction par règle, le code ressemble à la grammaire ; de bons messages d'erreur sont un vrai travail.
- AST : la structure sans la surface ; la priorité est dans la forme de l'arbre ; tout le reste du compilateur parcourt cet arbre.
