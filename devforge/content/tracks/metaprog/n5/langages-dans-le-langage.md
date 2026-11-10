---
id: metaprog.n5.langages-dans-le-langage
title: Des langages dans le langage
skills: [dsl-kotlin, macros, cout-magie]
duration_min: 12
---

# Des langages dans le langage

## Les DSL Kotlin

Un **DSL** (*Domain-Specific Language*) est un petit langage taillé pour un domaine. Tu en
utilises déjà : les scripts `build.gradle.kts`, Jetpack Compose, Ktor, kotlinx.html. En
Kotlin, ils reposent sur une fonctionnalité simple : les **lambdas avec receveur**.

```kotlin
@DslMarker
annotation class HtmlDsl

@HtmlDsl
class Balise(private val nom: String) {
    private val enfants = mutableListOf<String>()

    fun texte(contenu: String) { enfants += contenu }

    fun balise(nom: String, contenu: Balise.() -> Unit) {
        enfants += Balise(nom).apply(contenu).rendu()
    }

    fun rendu(): String = "<$nom>${enfants.joinToString("")}</$nom>"
}

fun html(contenu: Balise.() -> Unit): String = Balise("html").apply(contenu).rendu()

fun main() {
    val page = html {
        balise("h1") { texte("DevForge") }
        balise("p") { texte("Forge tes compétences") }
    }
    println(page)
}
```

Sortie réelle : `<html><h1>DevForge</h1><p>Forge tes compétences</p></html>`.

`Balise.() -> Unit` est une lambda **avec receveur** : à l'intérieur, `this` est une
`Balise`, d'où les appels directs à `balise(…)` et `texte(…)`. `@DslMarker` empêche
d'appeler par erreur une méthode de la balise **extérieure** depuis un bloc intérieur.
Aucune magie : tout est vérifié par le compilateur, avec l'autocomplétion de l'IDE.

## Les macros : du code qui écrit du code à la compilation

Certains langages permettent de transformer le code source avant la compilation.

En **C**, le préprocesseur fait du remplacement de **texte** :

```c
#define CARRE(x) x * x
printf("%d\n", CARRE(2 + 3));   // affiche réellement 11
```

Le texte devient `2 + 3 * 2 + 3`, soit 11. Le piège est si connu qu'on écrit
`((x) * (x))`… ce qui ne règle pas le problème d'un argument évalué deux fois
(`CARRE(i++)`).

En **Rust**, les macros manipulent l'**arbre syntaxique**, pas le texte :

```rust
macro_rules! carre {
    ($x:expr) => {
        $x * $x
    };
}

fn main() {
    println!("{}", carre!(2 + 3));   // affiche réellement 25
}
```

`$x:expr` capture une **expression entière** : `(2 + 3) * (2 + 3)` = 25. Les macros Rust
sont aussi *hygiéniques* : leurs variables internes ne peuvent pas entrer en collision
avec celles du code appelant. Lisp, lui, a inventé l'idée : en Lisp, le code est une
structure de données que les macros transforment librement.

## Quand la magie coûte trop cher

Annotations, proxies, AOP, génération de code, DSL, macros : toute cette « magie » a un
prix, et un expert sait le chiffrer.

| Coût | Exemple |
|---|---|
| **Comportement invisible** | un `@Transactional` ignoré (appel interne), un champ JSON omis par défaut |
| **Débogage difficile** | des stack traces pleines de proxies et de classes générées |
| **Temps de build** | traitement d'annotations, plugins, macros lourdes |
| **Courbe d'apprentissage** | chaque nouvel arrivant doit apprendre la magie de l'équipe |
| **Couplage** | le code ne fonctionne plus hors du framework |

Les questions à se poser avant d'ajouter de la magie :

- Le problème est-il vraiment **répété** à de nombreux endroits ?
- Le code explicite serait-il vraiment plus long, ou seulement moins élégant ?
- Un nouvel arrivant comprendra-t-il ce qui se passe **sans** lire la documentation du framework ?
- Les erreurs apparaîtront-elles **à la compilation** ou en production ?

Une bonne magie est **ennuyeuse** : prévisible, documentée, visible dans l'IDE.

## À retenir

- Les DSL Kotlin reposent sur les lambdas avec receveur, et restent vérifiés par le compilateur.
- Macros C : remplacement de texte, dangereux (11). Macros Rust : arbre syntaxique, hygiéniques (25).
- Toute magie a un coût : comportement caché, débogage, build, apprentissage. On la choisit en connaissance de cause.
