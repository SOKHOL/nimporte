---
id: compilation.n4.typer-et-generer
title: Systèmes de types, inférence et bytecode
skills: [systemes-types, inference, bytecode]
duration_min: 22
---

# Systèmes de types, inférence et bytecode

## Les systèmes de types

Un **vérificateur de types** parcourt l'AST **avant** l'exécution et calcule le type de chaque
expression, sans rien exécuter. Celui de Mini, réel :

```
soit a = 1 + 2; soit b = a < 10;            → {'a': 'Int', 'b': 'Bool'}
soit a = 1; soit b = vrai; afficher(a + b); → '+' attend deux Int, reçu Int et Bool
soit c = si 1 < 2 alors 3 sinon faux;       → les branches de si ont des types différents : Int et Bool
soit d = si 5 alors 1 sinon 2;              → la condition de si doit être un Bool
```

Chaque règle est un cas du parcours : `+` exige deux `Int` et donne un `Int` ; `<` exige deux
`Int` et donne un `Bool` ; `si` exige une condition `Bool` et deux branches du même type. Un
système de types est une **preuve automatique** qu'une classe d'erreurs ne peut pas arriver,
pour **toutes** les exécutions, là où un test n'en vérifie qu'une.

Le vocabulaire, souvent confondu :

| Axe | D'un côté | De l'autre |
|---|---|---|
| **Quand** vérifier | statique : à la compilation (Kotlin, Rust, Java, TypeScript) | dynamique : à l'exécution (Python, JavaScript, Ruby) |
| **Conversions implicites** | fort : `1 + "a"` est une erreur (Python, Kotlin) | faible : `1 + "a"` donne `"1a"` (JavaScript, C pour les nombres) |
| **Compatibilité** | nominale : même nom déclaré (Java, Kotlin) | structurelle : même forme (TypeScript, Go pour les interfaces) |

Python est donc dynamique **et** fort ; C est statique **et** faible. Un système de types
statique est toujours **conservateur** : il refuse des programmes qui auraient marché
(`si vrai alors 1 sinon faux`), parce qu'il raisonne sans exécuter. Les systèmes riches
(génériques, types nullables, types somme, *smart casts* de Kotlin) réduisent ces refus tout en
gardant la garantie.

## L'inférence de types

Écrire tous les types serait pénible ; l'**inférence** les déduit. Principe (algorithme de
Hindley-Milner, utilisé par OCaml, Haskell, F#, et en version locale par Rust et Kotlin) :

1. donner à chaque inconnue une **variable de type** (`'a`, `'b`…) ;
2. parcourir l'arbre en accumulant des **contraintes** (« le paramètre de `+` est un `Int` ») ;
3. les résoudre par **unification** : rendre deux types égaux en liant les variables.

L'inférence ajoutée à Mini (une centaine de lignes), sorties réelles :

```
soit ttc = fn(ht) => ht + ht * 20 / 100;           → (Int) -> Int
soit fact = fn(n) => si n < 2 alors 1 sinon …;     → (Int) -> Int
soit identite = fn(x) => x;                         → ('a) -> 'a
soit deux_fois = fn(f, x) => f(f(x));               → (('a) -> 'a, 'a) -> 'a
soit compose = fn(f, g) => fn(x) => f(g(x));        → (('a) -> 'b, ('c) -> 'a) -> ('c) -> 'b
```

Aucune annotation, et pourtant tout est typé. `identite` est **générique** (`'a` peut être
n'importe quel type) : après `soit a = identite(3); soit b = identite(vrai);`, l'inférence donne
`a : Int` et `b : Bool`, car chaque utilisation **instancie** de nouvelles variables. Le type de
`compose` est le **plus général** possible : l'algorithme le trouve seul.

Les erreurs, réelles :

```
soit inc = fn(n) => n + 1; soit r = inc(vrai);   → Int attendu, Bool trouvé
soit f = fn(x) => si x alors x + 1 sinon 0;      → Bool attendu, Int trouvé
soit f = fn(x) => x(x);                          → type infini
```

La deuxième montre la faiblesse de l'inférence globale : x est d'abord déduit `Bool` (la
condition), puis le conflit éclate plus loin ; le message désigne l'endroit où la contradiction
apparaît, pas forcément l'erreur. C'est pourquoi Kotlin et Rust **exigent** les types des
paramètres de fonction publique : l'inférence reste locale, les messages restent clairs, et la
signature sert de documentation.

## Bytecode et machine à pile

Pour aller plus vite qu'un interpréteur d'arbre, on **compile** l'arbre une fois en une liste
d'instructions simples, exécutées par une boucle très serrée. Le compilateur de Mini vers sa
machine à pile, réel :

```
soit x = 6; afficher(si x > 5 alors x * 10 sinon 0);

  0  EMPILER       6
  1  RANGER        x
  2  CHARGER       x
  3  EMPILER       5
  4  SUP
  5  SAUT_SI_FAUX  10
  6  CHARGER       x
  7  EMPILER       10
  8  MUL
  9  SAUT          11
 10  EMPILER       0
 11  AFFICHER                            → 60
```

La génération est un parcours de l'arbre en **ordre postfixe** : les enfants d'abord (empiler
les opérandes), puis l'opération. Le `si` devient deux **sauts** : le compilateur émet
`SAUT_SI_FAUX` sans connaître sa destination, compile la branche `alors`, puis revient
**corriger** l'adresse (*backpatching*). La machine elle-même est une boucle `while` et une
instruction par cas, sans récursion : la pile de l'hôte n'est plus une limite.

Deux grandes familles de machines virtuelles, comparées sur la même fonction Kotlin
(`remise`, voir niveau 1), compilée réellement pour les deux :

```
JVM (pile), 10 instructions            Android (registres, d8 + dexdump), 6 instructions
dload_0                                const-wide/high16 v0, 100.0
ldc2_w 100.0                           cmpl-double v0, v2, v0
dcmpl                                  if-ltz v0, 000c
iflt 16                                const-wide v0, 0.95
dload_0                                mul-double/2addr v2, v0
ldc2_w 0.95                            return-wide v2
dmul
goto 17
dload_0
dreturn
```

| Machine à **pile** | Machine à **registres** |
|---|---|
| JVM, CPython, WebAssembly, .NET, Mini | Dalvik/ART (Android), Lua 5, la plupart des processeurs |
| instructions courtes, sans opérandes de registre | moins d'instructions, chacune nomme ses registres |
| facile à générer | moins de répartitions d'instructions, donc plus rapide à interpréter |

Android a choisi les registres (et `d8` réécrit le bytecode JVM en bytecode Dalvik) pour
interpréter plus vite sur les téléphones de 2008, avant de compiler à l'avance et à la volée
avec ART.

## À retenir

- Un vérificateur de types prouve l'absence d'une classe d'erreurs pour toutes les exécutions ; statique/dynamique, fort/faible, nominal/structurel sont trois axes distincts.
- Inférence : variables de type + contraintes + unification ; elle trouve le type le plus général ('a) mais ses messages pointent l'endroit du conflit, d'où les signatures explicites.
- Compiler l'arbre en bytecode (postfixe, sauts corrigés après coup) ; pile (JVM, 10 instructions) contre registres (Android, 6 instructions).
