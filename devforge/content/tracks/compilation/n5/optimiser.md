---
id: compilation.n5.optimiser
title: Optimisations, SSA et LLVM, créer son propre langage
skills: [optimisations, ssa-llvm, son-langage]
duration_min: 22
---

# Optimisations, SSA et LLVM, créer son propre langage

## Repliement de constantes et code mort

Un optimiseur **réécrit l'arbre** (ou la représentation intermédiaire) en un programme
équivalent mais moins coûteux. Trois passes ajoutées à Mini, mesurées sur ce programme :

```
soit debug = faux;
soit x = 2 * 3 + 4;
soit y = si debug alors x * 100 sinon x * 1 + 0;
afficher(y);
```

| Version | Instructions de bytecode | Sortie |
|---|---|---|
| sans optimisation | 22 | 10 |
| **repliement** des constantes (`2 * 3 + 4` → `10`) et identités (`x * 1` → `x`, `x + 0` → `x`) | 14 | 10 |
| + **propagation** des constantes (remplacer `debug` et `x` par leur valeur) et **élimination des branches mortes** (`si faux alors … sinon …`) | **2** | 10 |

Résultat final, réel : `EMPILER 10`, `AFFICHER`. Les passes **se nourrissent** les unes les
autres : la propagation crée des constantes que le repliement calcule, ce qui rend une condition
constante, donc une branche morte. Les vrais compilateurs enchaînent des dizaines de passes, en
boucle (LLVM en a plus de cent).

La règle absolue : une optimisation doit **préserver le sens** du programme. Vérifié avec clang
18 en `-O2` :

```
int    entier_fois_zero(int x)  { return x * 0; }    → ret i32 0           (remplacé)
double fois_zero(double x)      { return x * 0.0; }  → fmul double %0, 0.0 (gardé !)
```

Pour les entiers, `x * 0` vaut toujours 0. Pour les flottants, non : `NaN * 0.0` vaut `NaN`, et
`-5.0 * 0.0` vaut `-0.0`. Le compilateur garde donc la multiplication. Avec `-ffast-math`, on
lui permet d'ignorer ces cas, et il renvoie `0.0` : plus rapide, mais un résultat différent pour
NaN. De même, la propagation de Mini n'est correcte que parce que ses variables ne sont **jamais
réaffectées** ; avec des affectations, il faut savoir quelle définition atteint chaque usage.

Les autres grandes optimisations : l'**inlining** (remplacer un appel par le corps de la fonction,
ce qui ouvre la porte à toutes les autres), la sortie des **invariants de boucle**, la
**vectorisation** (parcours Fonctionnement d'un ordinateur, niveau 5), l'**élimination des
sous-expressions communes**, et l'**analyse d'échappement** de la JVM (parcours JVM).

## SSA et LLVM

Pour savoir « quelle définition atteint quel usage », les compilateurs modernes utilisent la
forme **SSA** (*Static Single Assignment*) : chaque variable est affectée **une seule fois**.
Une variable modifiée dans une boucle devient plusieurs versions, réunies par un nœud **φ (phi)**
qui choisit la valeur selon le bloc d'où l'on vient.

Cette fonction C, compilée réellement en représentation intermédiaire de **LLVM** :

```c
int somme_jusqua(int n) {
    int total = 0;
    for (int i = 1; i <= n; i++) total += i;
    return total;
}
```

En `-O0`, chaque variable est une case mémoire (`alloca`) lue et écrite sans cesse (`load`,
`store`). Après la passe `mem2reg`, réel :

```
for.cond:
  %total.0 = phi i32 [ 0, %entry ], [ %add, %for.inc ]
  %i.0     = phi i32 [ 1, %entry ], [ %inc, %for.inc ]
  %cmp = icmp sle i32 %i.0, %n
  br i1 %cmp, label %for.body, label %for.end
for.body:
  %add = add nsw i32 %total.0, %i.0
  ...
for.inc:
  %inc = add nsw i32 %i.0, 1
```

`%total.0` vaut 0 si l'on vient de l'entrée, `%add` si l'on vient du tour précédent. Chaque
nom n'a qu'une définition : propager, éliminer, déplacer devient simple et sûr. Puis en `-O2`,
LLVM reconnaît la boucle (une **somme d'entiers**) et la **remplace par une formule fermée**
(en n(n+1)/2, calculée avec des entiers de 33 bits pour éviter le débordement) : plus de boucle
du tout. Et `int constante(void) { return somme_jusqua(10) * 2; }` devient réellement
`ret i32 110` : inlining, puis calcul complet à la compilation.

L'IR de LLVM est le « langage commun » du dorsal : écrire un nouveau langage, c'est produire
cette IR, et l'on hérite des optimisations et de toutes les cibles (x86, ARM, WebAssembly).
Rust, Swift, Zig, Julia et Kotlin/Native le font. Les JIT (HotSpot C2, V8 TurboFan) utilisent
aussi des formes SSA.

## Créer son propre langage

Mini tient en **environ 340 lignes de Python** : analyseur lexical, analyseur syntaxique, AST,
interpréteur avec fermetures, vérificateur et inférence de types, compilateur vers une machine à
pile, optimiseur. Un vrai langage demande surtout ce qui l'entoure.

**Quand en créer un ?** Rarement un langage généraliste. Souvent un **DSL** (*Domain-Specific
Language*) : règles de tarification, filtres de recherche, formules de tableur, requêtes
d'administration, règles d'alerte. Le choix, du plus léger au plus lourd :

1. **une configuration** (YAML, JSON) : souvent suffisant ; au-delà de quelques conditions,
   c'est un langage mal conçu qui ne dit pas son nom ;
2. un **DSL interne** : une API qui se lit comme un langage, dans le langage hôte (les
   *type-safe builders* de Kotlin : Gradle Kotlin DSL, Ktor, Compose) ; on garde l'IDE, les
   types et le débogueur gratuitement ;
3. un **DSL externe** : une vraie syntaxe, avec analyseur (écrit à la main ou ANTLR,
   tree-sitter) et interpréteur d'arbre, comme Mini ;
4. un **compilateur** vers une cible existante : JVM, WebAssembly, LLVM, ou du code source
   d'un autre langage (*transpileur*, comme TypeScript vers JavaScript).

**Ce qui fait vraiment le coût** : les messages d'erreur (niveau 2), la documentation, un
formateur, la coloration et l'autocomplétion via un serveur **LSP** (*Language Server Protocol* :
un seul serveur pour tous les éditeurs), le débogage, la compatibilité entre versions, et des
utilisateurs à former. Un DSL mal outillé coûte plus cher qu'un peu de code ordinaire.

Pour aller plus loin : *Crafting Interpreters* de Robert Nystrom (gratuit en ligne, un
interpréteur d'arbre puis une machine à bytecode en C), *Engineering a Compiler* (Cooper et
Torczon), le tutoriel *Kaleidoscope* de LLVM.

## À retenir

- Optimiser = réécrire en préservant le sens ; les passes se nourrissent (22 → 14 → 2 instructions mesurées) ; x * 0.0 n'est pas 0 à cause de NaN.
- SSA : une affectation par nom, des nœuds φ aux jonctions ; LLVM remplace la boucle par une formule et constante() par 110.
- Son langage : configuration < DSL interne < DSL externe < compilateur ; l'outillage (erreurs, LSP, docs) coûte plus que le cœur.
