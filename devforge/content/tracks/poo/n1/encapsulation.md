---
id: poo.n1.encapsulation
title: L'encapsulation
skills: [encapsulation]
duration_min: 5
---

# L'encapsulation

**Idée clé :** un objet protège son état. Le code extérieur ne modifie pas les
données directement : il passe par des méthodes qui garantissent que l'objet
reste **valide**.

## Sans encapsulation

```kotlin
class CompteBancaire {
    var solde: Long = 0
}

fun main() {
    val compte = CompteBancaire()
    compte.solde = -1_000_000 // rien ne l'empêche 😱
}
```

## Avec encapsulation

```kotlin
class CompteBancaire {
    var solde: Long = 0      // en centimes
        private set          // lecture publique, écriture réservée à la classe

    fun deposer(montant: Long) {
        require(montant > 0) { "Le montant doit être positif" }
        solde += montant
    }

    fun retirer(montant: Long) {
        require(montant > 0) { "Le montant doit être positif" }
        check(montant <= solde) { "Solde insuffisant" }
        solde -= montant
    }
}
```

La règle « le solde n'est jamais négatif » est un **invariant**. Seule la classe
peut le garantir, parce qu'elle seule peut modifier `solde`.

## Les visibilités en Kotlin

| Modificateur | Visible depuis |
|---|---|
| `public` (par défaut) | partout |
| `internal` | le même module |
| `protected` | la classe et ses sous-classes |
| `private` | la classe (ou le fichier, pour une déclaration de haut niveau) |

## Piège classique : exposer une collection modifiable

```kotlin
class Panier {
    private val _articles = mutableListOf<String>()
    val articles: List<String> get() = _articles.toList() // copie en lecture seule
}
```

Si tu exposes directement la `MutableList`, n'importe qui peut la modifier sans
passer par tes règles.

## À retenir

- Expose des **comportements** (`deposer`), pas des **données** modifiables.
- Commence en `private`, n'ouvre que si c'est nécessaire.
- Pour l'argent, préfère des centimes en `Long` (ou `BigDecimal`) à `Double` :
  les nombres flottants arrondissent.
