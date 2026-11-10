---
id: memoire.n2.comptage-et-possession
title: Comptage de références et possession
skills: [comptage-references, ownership]
duration_min: 12
---

# Comptage de références et possession

Entre « tout à la main » (C) et « un ramasse-miettes s'occupe de tout » (JVM), il existe
deux autres stratégies, utilisées par Python, Swift, Objective-C, C++ et Rust.

## Le comptage de références

Chaque objet porte un compteur : le nombre de références qui pointent vers lui. Une
référence de plus : +1. Une de moins : −1. À zéro, l'objet est libéré **immédiatement**.

- ✅ Libération prévisible, dès que l'objet n'est plus utilisé (pas de pause de GC).
- ❌ Chaque copie de référence coûte une mise à jour du compteur (atomique entre threads).
- ❌ **Les cycles ne sont jamais libérés** : deux objets qui se référencent gardent
  chacun un compteur à 1.

Python utilise le comptage de références, plus un **détecteur de cycles** qui passe de
temps en temps. Démonstration :

```python
import gc
import weakref

gc.disable()  # pour montrer le comptage de références seul

class Noeud:
    pass

a = Noeud()
b = Noeud()
a.voisin = b
b.voisin = a
sonde = weakref.ref(a)

del a, b
print(sonde() is None)   # False : le cycle a survécu au comptage
gc.collect()
print(sonde() is None)   # True : le détecteur de cycles l'a libéré
```

Swift, lui, n'a pas de détecteur de cycles : on doit casser les cycles soi-même avec des
références `weak`. Une source classique de fuites dans les apps iOS.

## La possession (ownership) en Rust

Rust choisit une troisième voie : ni ramasse-miettes, ni comptage par défaut, mais des
**règles vérifiées à la compilation** :

1. Chaque valeur a **un seul propriétaire**.
2. Quand le propriétaire sort de sa portée, la valeur est libérée.
3. On peut **prêter** une valeur : soit **plusieurs prêts en lecture** (`&T`), soit
   **un seul prêt en écriture** (`&mut T`), jamais les deux en même temps.

```rust
fn main() {
    let a = String::from("DevForge");
    let b = a;                  // la propriété passe à b : a n'est plus utilisable
    println!("{} {}", a, b);
}
```

```
error[E0382]: borrow of moved value: `a`
```

Le compilateur refuse : après `let b = a`, la chaîne appartient à `b`. Sinon, `a` et `b`
la libéreraient tous les deux, ce qui donnerait une double libération. Prêter est
autorisé :

```rust
fn longueur(s: &String) -> usize { s.len() }

fn main() {
    let a = String::from("DevForge");
    let n = longueur(&a);       // prêt en lecture : a reste propriétaire
    println!("{} {}", a, n);    // DevForge 8
}
```

Et voici pourquoi la règle « lecture ou écriture, pas les deux » est si précieuse :

```rust
let mut v = vec![1, 2, 3];
let premier = &v[0];    // prêt en lecture d'un élément
v.push(4);              // ⛔ refusé : push peut déplacer le tableau en mémoire
println!("{}", premier);
```

```
error[E0502]: cannot borrow `v` as mutable because it is also borrowed as immutable
```

`push` peut réallouer le vecteur ailleurs : `premier` pointerait alors vers une zone
libérée. En C++, ce code compile… et fait un use-after-free. Rust l'interdit **avant
même l'exécution**.

## Le bilan des quatre stratégies

| Stratégie | Langages | Sûreté | Coût |
|---|---|---|---|
| Manuelle | C | aucune garantie | aucun surcoût, beaucoup de bugs |
| Comptage de références | Python, Swift, `shared_ptr` | fuites sur les cycles | compteurs à maintenir |
| Ramasse-miettes traçant | Java, Kotlin, Go, C# | sûre | pauses, mémoire en plus |
| Possession vérifiée | Rust | sûre | courbe d'apprentissage |

## À retenir

- Comptage de références : libération immédiate, mais les cycles fuient.
- Rust : un seul propriétaire, des prêts en lecture **ou** en écriture, vérifiés à la compilation.
- Chaque stratégie est un compromis entre sûreté, performance et simplicité.
