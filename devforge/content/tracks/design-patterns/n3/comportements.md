---
id: design-patterns.n3.comportements
title: Les patterns de comportement
skills: [command, state, template-method, chain-of-responsibility, iterator, mediator]
duration_min: 16
---

# Les patterns de comportement

Ces patterns organisent **qui fait quoi** et **comment les objets communiquent**.

## Command : une action devenue objet

Transformer une action en objet permet de la stocker, l'annuler, la rejouer, la mettre
en file d'attente.

```kotlin
interface Commande {
    fun executer()
    fun annuler()
}

class Ecrire(private val editeur: Editeur, private val mot: String) : Commande {
    override fun executer() { editeur.texte.append(mot) }
    override fun annuler() { editeur.texte.setLength(editeur.texte.length - mot.length) }
}
```

Avec un historique (`ArrayDeque<Commande>`) : écrire « Dev », « Forge », « !! », annuler
deux fois, écrire « Ops » donne réellement `DevOps`. C'est le principe de tout « Annuler »
(et du Parsons de cette app : `ParsonsBoard`).

## State : le comportement change avec l'état

Au lieu d'un `when (etat)` dans chaque méthode, chaque état devient un objet qui sait
quoi faire et vers quel état aller. En Kotlin, une `sealed interface` s'y prête parfaitement :

```kotlin
sealed interface EtatArticle {
    fun suivant(): EtatArticle
}
data object Brouillon : EtatArticle { override fun suivant() = EnRevue }
data object EnRevue : EtatArticle { override fun suivant() = Publie }
data object Publie : EtatArticle { override fun suivant() = Publie }
```

Le compilateur vérifie que chaque `when` traite tous les états : en ajouter un ne peut
pas être oublié. Les écrans Compose (`Loading`, `Ready`, `Error`) et la session de cette
app (`Answering`, `Feedback`, `Finished`) sont des machines à états.

## Template Method : l'algorithme fixe, les étapes variables

```kotlin
abstract class Rapport {
    fun generer(): String = entete() + corps() + pied()   // l'algorithme, figé
    protected open fun entete() = "[début]"
    protected abstract fun corps(): String
    protected open fun pied() = "[fin]"
}

class RapportVentes : Rapport() {
    override fun corps() = " ventes: 42 "
    override fun pied() = "[confidentiel]"
}
```

`RapportVentes().generer()` donne réellement `[début] ventes: 42 [confidentiel]`. En
Kotlin moderne, on remplace souvent l'héritage par des fonctions passées en paramètre
(Strategy), plus souple.

## Chain of Responsibility : une requête traverse des maillons

Chaque maillon traite la requête, la modifie, la refuse ou la passe au suivant :

```kotlin
fun interface Maillon {
    fun traiter(requete: String, suivant: (String) -> String): String
}

val journal = Maillon { requete, suivant ->
    println("→ $requete")
    val reponse = suivant(requete)
    println("← $reponse")
    reponse
}
val droits = Maillon { requete, suivant ->
    if (requete.startsWith("/admin")) "403" else suivant(requete)
}
```

Avec la chaîne `journal → droits → 200`, appeler `/accueil` puis `/admin` affiche
réellement `→ /accueil`, `← 200`, `→ /admin`, `← 403`. Les intercepteurs d'OkHttp, les
filtres de servlets et les middlewares web fonctionnent ainsi.

## Iterator : parcourir sans connaître la structure

Kotlin l'intègre au langage : toute classe qui fournit `iterator()` marche dans un `for`.
Les **séquences** vont plus loin, elles calculent à la demande :

```kotlin
val nombres = sequence {
    println("calcul du premier")
    yield(1)
    println("calcul du second")
    yield(2)
}
println("avant")
println(nombres.first())
```

Sortie réelle : `avant`, `calcul du premier`, `1`. Le second élément n'est **jamais
calculé** : `first()` s'arrête dès qu'il a sa réponse.

## Mediator : un chef d'orchestre

Quand dix objets se parlent tous entre eux, on obtient des dépendances en toile
d'araignée. Le médiateur centralise la communication : chaque objet ne parle qu'à lui.
Exemple : un écran où cocher une case active un champ et désactive un bouton ; le
ViewModel joue le rôle de médiateur entre les composants.

## À retenir

- Command : une action en objet, pour annuler, rejouer, mettre en file.
- State : un objet par état ; en Kotlin, une `sealed interface`.
- Template Method : l'algorithme fixe, des étapes redéfinissables.
- Chain of Responsibility : des maillons qui traitent ou transmettent.
- Iterator et séquences : parcourir à la demande.
- Mediator : centraliser la communication entre objets.
