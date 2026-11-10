---
id: memoire.n1.pile-et-tas
title: La pile et le tas
skills: [pile-tas, valeurs-references, duree-de-vie]
duration_min: 8
---

# La pile et le tas

Un programme range ses données dans deux zones très différentes. Les confondre est
la source de beaucoup d'incompréhensions (« pourquoi mon objet a changé ? »,
« pourquoi cette récursion plante ? »).

## La pile (stack)

- **Une pile par thread.**
- À chaque appel de fonction, on empile une **frame** : les paramètres, les variables
  locales, l'adresse de retour.
- Au retour de la fonction, la frame est dépilée : sa mémoire est libérée
  **instantanément, sans ramasse-miettes**.
- Très rapide, mais **petite** (de l'ordre du mégaoctet par thread).

## Le tas (heap)

- **Partagé** par tous les threads du programme.
- Contient les **objets** et les tableaux.
- Un objet y vit tant que quelqu'un le référence. Il est libéré plus tard, par le
  ramasse-miettes (Kotlin, Java, Go…) ou à la main (`free` en C).
- Grand (des centaines de mégaoctets à plusieurs gigaoctets), mais l'allocation et la
  libération ont un coût.

```kotlin
fun main() {
    val n = 42             // n vit dans la frame de main (pile)
    val p = Point(1)       // l'objet Point vit sur le tas ;
                           // p, dans la pile, ne contient qu'une référence vers lui
}
```

```
   PILE (thread main)              TAS
  ┌───────────────────┐         ┌──────────────┐
  │ frame main        │         │ Point        │
  │   n = 42          │         │   x = 1      │
  │   p = ●───────────┼────────▶│              │
  └───────────────────┘         └──────────────┘
```

## Valeurs et références

En Kotlin comme en Java, **les arguments sont toujours passés par valeur**. Mais pour un
objet, cette valeur est une **référence** : la fonction reçoit une copie de la flèche,
pas une copie de l'objet.

```kotlin
class Point(var x: Int)

fun deplacer(p: Point) { p.x += 10 }      // modifie l'objet pointé

fun incrementer(n: Int) {
    var m = n
    m += 10                               // modifie une copie locale
}

fun remplacer(p: Point) {
    var q = p
    q = Point(99)                         // change la flèche locale, pas l'objet
}

fun main() {
    val p = Point(1)
    val n = 1
    deplacer(p)
    incrementer(n)
    remplacer(p)
    println("${p.x} $n")                  // affiche : 11 1
}
```

## Quand la pile déborde

Chaque appel ajoute une frame. Une récursion trop profonde (ou infinie) remplit la
pile et provoque un `StackOverflowError`, pas un manque de mémoire sur le tas.

```kotlin
fun profondeur(n: Int): Int = profondeur(n + 1) + 1 // StackOverflowError
```

La JVM n'optimise pas les appels récursifs terminaux. En Kotlin, le mot-clé `tailrec`
demande au compilateur de transformer une récursion terminale en boucle.

## Ce que coûte un objet

Sur une JVM 64 bits typique :

| Donnée | Taille |
|---|---|
| `Int` | 4 octets |
| `Long`, `Double` | 8 octets |
| une référence | 4 octets (8 sans *compressed oops*) |
| un objet vide | ~12 octets d'en-tête, arrondi au multiple de 8 |
| un `Integer` (Int « boxé ») | ~16 octets |

Conséquence : une `List<Int>` d'un million de grands nombres pèse environ **20 Mo**
(un objet `Integer` + une référence par élément), contre **4 Mo** pour un `IntArray`.

## À retenir

- Pile : par thread, frames, libération automatique au retour, petite.
- Tas : partagé, objets, libération par le GC, grande.
- Une variable d'objet contient une **référence** ; on passe toujours des copies de
  valeurs, et parfois ces valeurs sont des références.
- Un `StackOverflowError` vient de la pile, un `OutOfMemoryError` le plus souvent du tas.
