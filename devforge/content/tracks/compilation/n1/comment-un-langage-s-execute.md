---
id: compilation.n1.comment-un-langage-s-execute
title: Interprété ou compilé, les étapes d'un compilateur et les grammaires
skills: [interprete-compile, etapes, grammaires]
duration_min: 16
---

# Interprété ou compilé, les étapes d'un compilateur et les grammaires

Dans ce parcours, on construit un vrai petit langage, **Mini**, en Python. Toutes les sorties
citées sont réelles.

```
soit taux = 20;
soit ttc = fn(ht) => ht + ht * taux / 100;
afficher(ttc(50));                                         → 60
soit fact = fn(n) => si n < 2 alors 1 sinon n * fact(n - 1);
afficher(fact(10));                                        → 3628800
```

## Interprété, compilé, bytecode et JIT

La frontière est floue : la plupart des langages combinent plusieurs techniques.

| Approche | Principe | Exemples |
|---|---|---|
| **Compilation anticipée** (AOT) | traduire tout le programme en code machine avant de l'exécuter | C, C++, Rust, Go, Swift, Kotlin/Native |
| **Bytecode + machine virtuelle** | compiler vers un code intermédiaire portable, exécuté par une VM | Java, Kotlin (JVM), C#, Python |
| **JIT** (*just in time*) | la VM compile en code machine, pendant l'exécution, ce qui est souvent exécuté | HotSpot (JVM), V8 (JavaScript), .NET, PyPy |
| **Interprétation directe** | parcourir le programme et l'exécuter pas à pas | les premiers BASIC, beaucoup de petits langages, Mini au niveau 3 |

La même fonction, réellement compilée par deux machines virtuelles :

```python
def remise(total):
    return total * 0.95 if total >= 100 else total
```

```
Python (dis)                              JVM (javap -c, version Kotlin)
LOAD_FAST        0 (total)                0: dload_0
LOAD_CONST       1 (100)                  1: ldc2_w  #7    // double 100.0d
COMPARE_OP     188 (bool(>=))             4: dcmpl
POP_JUMP_IF_FALSE  5 (to L1)              5: iflt    16
LOAD_FAST        0 (total)                8: dload_0
LOAD_CONST       2 (0.95)                 9: ldc2_w  #9    // double 0.95d
BINARY_OP        5 (*)                   12: dmul
RETURN_VALUE                             13: goto    17
L1: LOAD_FAST    0 (total)               16: dload_0
RETURN_VALUE                             17: dreturn
```

Deux **machines à pile** : on empile les valeurs (`LOAD_FAST`, `dload_0`), une opération dépile
ses arguments et empile son résultat (`BINARY_OP *`, `dmul`), un saut conditionnel choisit la
branche. Le bytecode de la JVM est **typé** (`d` pour *double*) ; celui de Python ne l'est pas,
il découvre les types à l'exécution. Au niveau 4, Mini aura sa propre machine à pile.

Les compromis : l'AOT démarre vite et prévisiblement ; le JIT démarre plus lentement mais peut
optimiser selon le comportement **réel** du programme (les types vraiment vus, les branches
vraiment prises) ; l'interprétation est simple à écrire et à porter, mais lente.

## Les étapes d'un compilateur

```
texte source
  → analyse lexicale    : des caractères aux jetons        (niveau 2)
  → analyse syntaxique  : des jetons à l'arbre (AST)        (niveau 2)
  → analyse sémantique  : noms, portées, types              (niveaux 3 et 4)
  → représentation intermédiaire et optimisations           (niveau 5)
  → génération de code  : bytecode ou code machine          (niveau 4)
```

On distingue le **frontal** (*front end* : comprendre le langage source et signaler les
erreurs) et le **dorsal** (*back end* : optimiser et produire du code pour une machine). C'est
l'idée de **LLVM** : des dizaines de langages (Clang pour C et C++, Rust, Swift, Kotlin/Native,
Julia, Zig) partagent le même dorsal, et chacun profite de ses optimisations et de toutes ses
cibles (x86, ARM, WebAssembly).

## Les grammaires

La **syntaxe** d'un langage se décrit par une **grammaire**, souvent en notation EBNF. Celle de
Mini (simplifiée) :

```
programme   = { instruction ";" } ;
instruction = "soit" IDENT "=" expression | expression ;
expression  = "si" expression "alors" expression "sinon" expression
            | "fn" "(" [ IDENT { "," IDENT } ] ")" "=>" expression
            | comparaison ;
comparaison = somme [ ( "<" | ">" | "==" ) somme ] ;
somme       = produit { ( "+" | "-" ) produit } ;
produit     = unaire { ( "*" | "/" ) unaire } ;
unaire      = "-" unaire | appel ;
appel       = primaire { "(" [ expression { "," expression } ] ")" } ;
primaire    = NOMBRE | "vrai" | "faux" | IDENT | "(" expression ")" ;
```

`{ … }` signifie « zéro fois ou plus », `[ … ]` « facultatif », `|` « ou ». Cette grammaire
encode la **priorité** des opérateurs par sa structure : une `somme` est faite de `produit`s,
donc `2 + 3 * 4` se lit `2 + (3 * 4)`. Et l'**associativité** : `somme` boucle de gauche à
droite, donc `10 - 4 - 3` vaut `(10 - 4) - 3 = 3`.

Une grammaire est **ambiguë** si un même texte peut se lire de deux façons ; le cas classique est
le « sinon pendant » : dans `si a alors si b alors x sinon y`, à quel `si` appartient le
`sinon` ? Les langages tranchent par une règle (le plus proche), ou par la syntaxe (des
accolades obligatoires).

## À retenir

- AOT, bytecode + VM, JIT, interprétation : la plupart des langages en combinent plusieurs ; Python et la JVM sont des machines à pile (bytecodes réels comparés).
- Frontal (lexical, syntaxique, sémantique) et dorsal (représentation intermédiaire, optimisations, génération) ; LLVM partage le dorsal entre langages.
- Grammaire EBNF : la structure encode la priorité et l'associativité ; attention aux ambiguïtés.
