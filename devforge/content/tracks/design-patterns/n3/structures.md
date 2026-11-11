---
id: design-patterns.n3.structures
title: Composite, Bridge et Flyweight
skills: [composite, bridge, flyweight]
duration_min: 9
---

# Composite, Bridge et Flyweight

## Composite : traiter pareil un élément et un groupe

Un dossier contient des fichiers **et** des dossiers. Si fichiers et dossiers partagent
la même interface, le code client n'a jamais à les distinguer :

```kotlin
sealed interface Element {
    fun taille(): Long
}

class Fichier(val nom: String, private val octets: Long) : Element {
    override fun taille() = octets
}

class Dossier(val nom: String, private val enfants: List<Element>) : Element {
    override fun taille() = enfants.sumOf { it.taille() }
}
```

Un dossier contenant un README de 2 000 octets, un sous-dossier `src` (5 000 + 3 000) et
un dossier vide pèse réellement `10000`. La récursion est naturelle : un dossier demande
leur taille à ses enfants, qui font de même. Les arbres de vues (Compose, HTML), les
menus et les expressions mathématiques sont des composites.

## Bridge : deux dimensions qui varient séparément

Des formes (cercle, carré) à dessiner de plusieurs façons (écran, PDF, SVG). Avec
l'héritage seul : `CercleEcran`, `CerclePdf`, `CarreEcran`… soit 2 × 3 = 6 classes, et
chaque ajout multiplie. Le Bridge sépare les deux hiérarchies et les relie par
**composition** :

```kotlin
interface Rendu {
    fun cercle(rayon: Double)
    fun carre(cote: Double)
}

abstract class Forme(protected val rendu: Rendu) {
    abstract fun dessiner()
}

class Cercle(rendu: Rendu, private val rayon: Double) : Forme(rendu) {
    override fun dessiner() = rendu.cercle(rayon)
}
```

2 formes + 3 rendus = 5 classes, et chaque ajout n'en coûte qu'une.

## Flyweight : partager ce qui se répète

Quand un programme manipule des millions de petits objets identiques, on partage les
instances au lieu de les dupliquer. Tu l'as déjà rencontré : le **cache des `Integer`**
de -128 à 127 (parcours JVM, niveau 1) est un Flyweight. Autres exemples : les glyphes
d'un éditeur de texte (un seul objet « lettre a » en police Arial 12), les tuiles d'une
carte, les `String` internées.

Condition : les objets partagés doivent être **immuables**, sinon une modification
toucherait tous ceux qui les partagent.

## À retenir

- Composite : même interface pour l'élément et le groupe, récursion naturelle.
- Bridge : deux dimensions de variation reliées par composition, au lieu de les multiplier.
- Flyweight : partager des objets immuables très répétés (cache des `Integer`).
