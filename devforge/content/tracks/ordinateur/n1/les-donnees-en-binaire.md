---
id: ordinateur.n1.les-donnees-en-binaire
title: Binaire, texte et nombres à virgule flottante
skills: [binaire, texte-encodage, flottants]
duration_min: 17
---

# Binaire, texte et nombres à virgule flottante

Tout ce qu'un ordinateur manipule (nombres, texte, images, programmes) est stocké sous forme
de **bits** : 0 ou 1, un courant qui passe ou pas. Huit bits forment un **octet**. Les
sorties citées viennent de Kotlin (JVM) et de Python.

## Binaire, hexadécimal et entiers

En base 2, chaque position vaut une puissance de 2 : `101010` = 32 + 8 + 2 = **42**.
L'**hexadécimal** (base 16, chiffres 0 à 9 puis a à f) est une notation compacte : un chiffre
hexadécimal = 4 bits, deux chiffres = un octet. Réel :

```kotlin
42.toString(2)     // 101010
42.toString(16)    // 2a
0xFF               // 255
0b1010             // 10
```

Les entiers ont une **taille fixe** : un `Int` occupe 32 bits, un `Long` 64. Les entiers
**signés** utilisent le **complément à deux** : le bit de poids fort compte négativement, et
`-1` s'écrit avec tous les bits à 1. Conséquence, le **débordement** silencieux :

```kotlin
Int.MAX_VALUE + 1                // -2147483648   (2 147 483 647 + 1 « fait le tour »)
Integer.toBinaryString(-1)       // 11111111111111111111111111111111
(200).toByte()                   // -56           (un octet signé va de -128 à 127)
```

Aucune erreur, aucun avertissement : la valeur repart du minimum. Des bugs réels en ont découlé
(la fusée Ariane 5 en 1996, une conversion de 64 bits vers 16 bits ; des compteurs de jours
qui débordent). Remèdes : `Math.addExact` (qui lève une exception), des `Long`, ou des entiers
de taille arbitraire (`BigInteger` ; Python n'a que des entiers illimités).

L'ordre des octets en mémoire (*endianness*) : les processeurs x86 et ARM courants sont
**petit-boutistes** (*little endian*, l'octet de poids faible en premier ; réel sur la machine
de test : `Byte Order: Little Endian`), alors que les protocoles réseau sont gros-boutistes.
Ça compte dès qu'on lit ou écrit un format binaire.

## Le texte : ASCII, Unicode et UTF-8

Un texte est une suite de nombres, chacun représentant un caractère selon un **encodage** :

- **ASCII** (1963) : 128 caractères sur 7 bits, l'anglais sans accent ;
- **Unicode** : un numéro (*point de code*) pour **chaque** caractère de toutes les écritures,
  plus les symboles et les emojis (plus de 150 000) ;
- **UTF-8** : la façon dominante d'encoder Unicode en octets, de longueur **variable**.

Octets réels en UTF-8 :

| Caractère | Octets |
|---|---|
| `A` | `41` (1 octet, identique à l'ASCII) |
| `é` | `c3 a9` (2 octets) |
| `€` | `e2 82 ac` (3 octets) |
| `😀` | `f0 9f 98 80` (4 octets) |

Conséquences pratiques :

- **la longueur dépend de ce qu'on compte** : en Kotlin, `"😀".length` vaut **2** (des unités
  UTF-16 de la JVM), mais il n'y a qu'**un** point de code (`codePointCount` = 1) ; `"é"`
  occupe 2 octets en UTF-8. Tronquer une chaîne « à 10 caractères » peut couper un emoji en
  deux ;
- le **mojibake** (`Ã©` au lieu de `é`) : des octets UTF-8 lus comme si c'était un autre
  encodage (Latin-1). On fixe l'encodage explicitement partout : fichiers, base de données,
  en-têtes HTTP (`charset=utf-8`) ;
- comparer des textes demande parfois une **normalisation** : `é` peut être un seul point de
  code, ou `e` suivi d'un accent combinant.

## Les nombres à virgule flottante

Les nombres réels sont stockés au format **IEEE 754** : un signe, un exposant et une mantisse,
comme une notation scientifique en base 2. Un `Double` a 64 bits (environ 15 à 17 chiffres
significatifs). Le problème : la plupart des fractions **décimales** n'ont pas de représentation
exacte en base 2, comme 1/3 n'en a pas en base 10. Réel :

```
0.1 en mémoire (IEEE 754, 64 bits) : 3fb999999999999a  →  0.10000000000000000555…
0.1 + 0.2                          →  0.30000000000000004
0.1f + 0.2f (Float, 32 bits)       →  0.3        (les arrondis tombent juste par chance)
1e16 + 1 == 1e16                   →  true       (le 1 est perdu : plus assez de chiffres)
```

Les règles qui en découlent :

- **jamais de flottants pour l'argent** : des centimes en entiers (`Long`), ou un type décimal
  exact (`BigDecimal`, `numeric` en SQL, parcours Bases de données) ;
- **ne pas comparer des flottants avec `==`** : comparer avec une tolérance
  (`abs(a - b) < 1e-9`) ;
- additionner des nombres de tailles très différentes **perd** les petits (l'ordre des
  additions change le résultat) ;
- les valeurs spéciales : `NaN` (« pas un nombre », et `NaN != NaN`), `Infinity`, `-0.0`.

## À retenir

- Binaire, hexadécimal (4 bits par chiffre) ; entiers de taille fixe en complément à deux ; le débordement est silencieux (`Int.MAX_VALUE + 1` = -2147483648).
- Unicode numérote les caractères, UTF-8 les encode sur 1 à 4 octets ; la « longueur » d'une chaîne dépend de ce qu'on compte ; fixer l'encodage partout.
- IEEE 754 : 0.1 n'existe pas exactement (`0.1 + 0.2` = 0.30000000000000004) ; pas de flottants pour l'argent, pas de `==` entre flottants.
