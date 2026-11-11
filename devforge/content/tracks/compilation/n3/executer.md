---
id: compilation.n3.executer
title: Interpréteur d'arbre, portée et fermetures
skills: [interpreteur, portee, fermetures]
duration_min: 20
---

# Interpréteur d'arbre, portée et fermetures

## Un interpréteur d'arbre

La façon la plus directe d'exécuter un programme : **parcourir son AST** avec une fonction
`evaluer(nœud, environnement)` qui renvoie la valeur du nœud. Un cas par type de nœud :

```python
def evaluer(n, env):
    if isinstance(n, Nombre):   return n.valeur
    if isinstance(n, Variable): return env.lire(n.nom)
    if isinstance(n, Binaire):  return OPS[n.op](evaluer(n.gauche, env), evaluer(n.droite, env))
    if isinstance(n, Si):       return evaluer(n.alors if evaluer(n.condition, env) else n.sinon, env)
    if isinstance(n, Soit):     env.definir(n.nom, evaluer(n.valeur, env))
    ...
```

C'est récursif comme l'arbre lui-même. Notez le `Si` : seule la branche choisie est évaluée,
sinon `fact` ne s'arrêterait jamais. Avec une vingtaine de lignes de plus pour les fonctions,
Mini exécute réellement :

```
soit fact = fn(n) => si n < 2 alors 1 sinon n * fact(n - 1);
afficher(fact(10));                                        → 3628800
```

Deux limites, mesurées :

- **la lenteur** : `fib(22)` prend 105,8 ms dans Mini contre 1,49 ms en Python pur, soit
  **71 fois** plus lent (et Python lui-même est un interpréteur). Chaque addition passe par des
  `isinstance`, des appels de fonction et des recherches dans des dictionnaires. Au niveau 4, on
  compile vers une machine à pile pour aller plus vite ;
- **la pile de l'hôte** : la récursion de Mini utilise celle de Python. Une fonction récursive
  de Mini plante au-delà de **330** appels imbriqués (`RecursionError`), car chaque appel Mini
  consomme plusieurs cadres Python et la limite par défaut est 1000.

Les erreurs de l'interpréteur doivent parler le langage de l'utilisateur, pas celui de l'hôte.
La première version de Mini répondait à `soit a = 3; afficher(a(2));` par
`AttributeError: 'int' object has no attribute 'env'` : une erreur de **l'implémentation**.
Corrigée, réel :

```
3 n'est pas une fonction
2 argument(s) attendu(s), 1 reçu(s)
```

## Portée et environnements

Un **environnement** associe des noms à des valeurs, avec un lien vers son **parent** :

```python
class Environnement:
    def __init__(self, parent=None): self.vars, self.parent = {}, parent
    def lire(self, nom):
        if nom in self.vars: return self.vars[nom]
        if self.parent: return self.parent.lire(nom)
        raise NameError(f"variable inconnue : {nom}")
```

Chercher un nom, c'est remonter la chaîne jusqu'à le trouver. Chaque appel de fonction crée un
nouvel environnement pour ses paramètres, ce qui donne le **masquage** (*shadowing*). Réel :

```
soit x = 1;
soit f = fn(x) => x * 100;
afficher(f(7));      → 700   (le x du paramètre masque le x global)
afficher(x);         → 1     (le x global n'a pas changé)
```

Question décisive : quand une fonction lit une variable qui n'est pas à elle, **quel parent**
consulte-t-elle ? Celui de l'endroit où elle a été **écrite**, ou celui de l'endroit où elle
est **appelée** ?

```
soit x = 1;
soit lire_x = fn() => x;
soit g = fn(x) => lire_x();
afficher(g(99));
```

| Portée | Parent de l'appel | Résultat réel |
|---|---|---|
| **lexicale** (statique) | l'environnement où la fonction a été créée | `1` |
| **dynamique** | l'environnement de l'appelant | `99` |

La différence tient en **une ligne** de l'interpréteur (`Environnement(f.env)` contre
`Environnement(env)`), et pourtant presque tous les langages modernes ont choisi la portée
lexicale : on comprend une fonction **en lisant son code**, sans connaître tous ses appelants.
La portée dynamique survit dans Emacs Lisp (par défaut), les variables d'environnement d'un
shell, et sous forme contrôlée dans les `ThreadLocal` et les `CoroutineContext`.

## Les fermetures

Pour la portée lexicale, une fonction doit **emporter** l'environnement où elle est née. Cette
paire (code + environnement de création) s'appelle une **fermeture** (*closure*) :

```python
if isinstance(n, Fonction): return Fermeture(n.parametres, n.corps, env)   # capture env
if isinstance(n, Appel):
    f = evaluer(n.fonction, env)
    local = Environnement(f.env)            # parent = environnement de CRÉATION
    for p, a in zip(f.parametres, args): local.definir(p, a)
    return evaluer(f.corps, local)
```

Ce qui permet des fonctions qui fabriquent des fonctions. Réel :

```
soit ajouteur = fn(n) => fn(x) => x + n;
soit ajoute10 = ajouteur(10);
afficher(ajoute10(5));       → 15
```

L'appel `ajouteur(10)` est **terminé** quand `ajoute10` est appelée, et pourtant `n` vaut
toujours 10 : l'environnement survit parce que la fermeture le référence. Conséquence : les
environnements ne peuvent pas vivre sur la pile, ils sont alloués **sur le tas** et ramassés par
le GC. C'est exactement ce que font les lambdas Kotlin et Java (les variables capturées sont
copiées dans un objet), et pourquoi une lambda qui capture un gros objet peut provoquer une
fuite mémoire (parcours Mémoire).

La **récursion** fonctionne aussi grâce aux environnements : `fact` est définie dans
l'environnement global, et le corps de la fermeture, évalué plus tard, l'y retrouve.

## À retenir

- Un interpréteur d'arbre parcourt l'AST : simple, mais lent (71 fois plus que Python mesuré) et limité par la pile de l'hôte.
- Environnements chaînés : masquage, et choix décisif entre portée lexicale (1) et dynamique (99), en une ligne.
- Fermeture = code + environnement de création ; les environnements capturés vivent sur le tas.
