---
id: metaprog.n1.annotations-reflexion
title: Annotations et réflexion
skills: [annotations, reflexion, reflexion-kotlin]
duration_min: 10
---

# Annotations et réflexion

## Les annotations : des étiquettes sur le code

Une annotation n'exécute rien. C'est une **métadonnée** que d'autres outils lisent : le
compilateur (`@Deprecated`, `@Suppress`), un générateur de code (`@Serializable`,
`@Entity` pour Room), ou le programme lui-même à l'exécution (`@Transactional`, `@GET`).

```kotlin
@Target(AnnotationTarget.FUNCTION)          // où peut-on la poser ?
@Retention(AnnotationRetention.RUNTIME)     // jusqu'où survit-elle ?
annotation class Chronometre
```

| Rétention | Visible… | Usage |
|---|---|---|
| `SOURCE` | seulement dans le code source | indications au compilateur |
| `BINARY` | dans le `.class`, mais pas par réflexion | outils qui lisent le bytecode |
| `RUNTIME` (défaut en Kotlin) | à l'exécution, par réflexion | frameworks (Spring, Retrofit…) |

## La réflexion : le programme s'examine lui-même

La réflexion permet de découvrir à l'exécution les classes, champs, méthodes et
annotations d'un objet, et même de les lire, de les modifier ou de les appeler. Voici un
petit validateur :

```kotlin
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class NonVide

data class Inscription(@NonVide val email: String, @field:NonVide val pseudo: String)

fun champsObligatoiresVides(objet: Any): List<String> = objet.javaClass.declaredFields
    .filter { it.isAnnotationPresent(NonVide::class.java) }
    .filter { it.isAccessible = true; (it.get(objet) as String).isBlank() }
    .map { it.name }

fun main() {
    println(champsObligatoiresVides(Inscription(email = "", pseudo = "")))
}
```

Sortie réelle : `[pseudo]`. Les deux champs sont vides… où est passé `email` ?

## Le piège des cibles d'annotation en Kotlin

Une propriété déclarée dans un constructeur Kotlin est **plusieurs choses à la fois**
pour la JVM : un paramètre du constructeur, un champ privé, un getter. Quand une
annotation peut s'appliquer à plusieurs de ces cibles, Kotlin en choisit une : avec
Kotlin 2.0, le **paramètre du constructeur** en priorité. Ici, `@NonVide` sur `email` est
allée sur le paramètre, pas sur le champ : le validateur, qui lit les champs, ne la voit pas.

C'est exactement ce qui arrive avec Bean Validation (`@NotBlank`) ou Jackson : la règle
est **ignorée en silence**. La solution : préciser la cible.

| Préfixe | Cible |
|---|---|
| `@field:` | le champ Java |
| `@get:` | le getter |
| `@param:` | le paramètre du constructeur |
| `@property:` | la propriété Kotlin (invisible pour Java) |

Les versions récentes de Kotlin font évoluer cette règle par défaut : raison de plus pour
toujours écrire la cible explicitement.

## Réflexion Java ou réflexion Kotlin ?

- La réflexion Java (`objet.javaClass`, `declaredFields`, `getMethod`) fonctionne partout.
- La réflexion Kotlin complète (`KClass.memberProperties`, nullabilité, paramètres
  nommés) demande la bibliothèque `kotlin-reflect`, qui pèse plusieurs mégaoctets :
  à éviter sur Android.

## Le prix de la réflexion

- **Lente** comparée à un appel direct (vérifications, le JIT optimise moins).
- **Fragile** : rien n'est vérifié à la compilation ; renommer un champ casse le code à
  l'exécution.
- **Incompatible avec l'obfuscation** : sur Android, R8 renomme et supprime classes et
  champs ; la réflexion par nom échoue sans règles `-keep`.

C'est pourquoi les bibliothèques modernes **génèrent du code à la compilation**
(kotlinx.serialization, Room, Hilt avec KSP) plutôt que de tout faire par réflexion
(voir le niveau 4).

## À retenir

- Une annotation est une métadonnée : `@Target` (où), `@Retention` (jusqu'à quand).
- La réflexion lit et appelle le code à l'exécution, au prix de la performance et de la sûreté.
- En Kotlin, précise toujours la cible : `@field:`, `@get:`, `@param:`.
