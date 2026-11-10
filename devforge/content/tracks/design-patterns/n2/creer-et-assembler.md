---
id: design-patterns.n2.creer-et-assembler
title: Créer et assembler des objets
skills: [abstract-factory, builder, prototype, adapter, decorator, facade, proxy]
duration_min: 16
---

# Créer et assembler des objets

Sept patterns du Gang of Four, en version Kotlin. Certains sont presque inutiles grâce
au langage ; d'autres sont partout dans les bibliothèques que tu utilises.

## Les patterns de création

### Builder

**Problème :** construire un objet qui a beaucoup de paramètres, dont la plupart sont
optionnels.

En Java, on écrit un `Builder` avec une méthode par paramètre. En Kotlin, les
**paramètres nommés et par défaut** rendent ce pattern presque toujours inutile :

```kotlin
data class Requete(
    val url: String,
    val methode: String = "GET",
    val delaiMs: Long = 5_000,
    val entetes: Map<String, String> = emptyMap(),
)

val r = Requete("https://devforge.dev", delaiMs = 2_000)
```

Le Builder reste utile pour l'interopérabilité avec Java, pour une construction en
plusieurs étapes, ou sous forme de DSL (`buildList { }`, `buildString { }`).

### Abstract Factory

**Problème :** créer des **familles** d'objets qui vont ensemble, sans que le code client
connaisse leurs classes concrètes.

```kotlin
interface FabriqueUi {
    fun bouton(texte: String): Bouton
    fun champ(): Champ
}

class FabriqueMaterial : FabriqueUi { /* boutons et champs Material */ }
class FabriqueIos : FabriqueUi { /* boutons et champs iOS */ }
```

Le code reçoit une `FabriqueUi` et obtient toujours des composants cohérents entre eux.

### Prototype

**Problème :** créer un objet en copiant un modèle existant. En Kotlin : `copy()` des
`data class`. Mais attention, la copie est **superficielle** :

```kotlin
data class Equipe(val nom: String, val membres: MutableList<String>)

val a = Equipe("A", mutableListOf("Ada"))
val b = a.copy(nom = "B")
b.membres += "Linus"
println(a.membres)    // [Ada, Linus] : les deux équipes partagent la même liste !
```

Sortie réelle. Avec des propriétés immuables (`List`, `val`), ce piège disparaît.

## Les patterns de structure

### Adapter

**Problème :** utiliser une classe dont l'interface ne correspond pas à celle qu'attend
ton code (une vieille bibliothèque, une API externe).

```kotlin
interface Paiement { fun payer(centimes: Long) }

class AdaptateurAncienneBanque(private val banque: AncienneBanque) : Paiement {
    override fun payer(centimes: Long) = banque.effectuerVirement(centimes / 100.0, "EUR")
}
```

Ton code ne connaît que `Paiement` ; l'adaptateur traduit.

### Decorator

**Problème :** ajouter des comportements à un objet, en les combinant, sans créer une
sous-classe par combinaison. Chaque décorateur enveloppe un objet de **même interface**.

```kotlin
fun interface Texte { fun rendu(): String }

class Brut(private val contenu: String) : Texte { override fun rendu() = contenu }
class Prefixe(private val texte: Texte) : Texte { override fun rendu() = "> " + texte.rendu() }
class Tronque(private val texte: Texte) : Texte { override fun rendu() = texte.rendu().take(5) }

println(Tronque(Prefixe(Brut("DevForge"))).rendu())   // > Dev
println(Prefixe(Tronque(Brut("DevForge"))).rendu())   // > DevFo
```

Sorties réelles : **l'ordre des décorateurs change le résultat**. Les flux d'entrée-sortie
de Java (`BufferedReader(InputStreamReader(…))`) et les intercepteurs d'OkHttp sont des
décorateurs.

### Facade

**Problème :** un sous-système complexe (dix classes, un ordre d'appel précis) que les
clients utilisent toujours de la même façon. La façade offre **une** porte d'entrée simple :

```kotlin
class Commander(
    private val stock: Stock,
    private val paiement: Paiement,
    private val livraison: Livraison,
) {
    fun commander(panier: Panier) {
        stock.reserver(panier)
        paiement.payer(panier.total)
        livraison.planifier(panier)
    }
}
```

### Proxy

**Problème :** contrôler l'accès à un objet : chargement paresseux, cache, vérification des
droits, appel distant. Le proxy a la **même interface** que l'objet réel. En Kotlin,
`by lazy` est un proxy paresseux ; Retrofit, Spring et Mockito génèrent des proxies (voir le
parcours *Métaprogrammation*).

## Adapter, Decorator, Proxy, Facade : ne pas confondre

| Pattern | Interface exposée | Intention |
|---|---|---|
| Adapter | **différente** de l'objet enveloppé | faire correspondre deux interfaces |
| Decorator | **identique** | ajouter un comportement, combinable |
| Proxy | **identique** | contrôler l'accès |
| Facade | **nouvelle, plus simple** | cacher un sous-système entier |

## À retenir

- Builder : souvent remplacé par les paramètres nommés en Kotlin.
- Prototype : `copy()` est superficielle ; préfère des propriétés immuables.
- Adapter traduit, Decorator ajoute, Proxy contrôle, Facade simplifie.
- L'ordre des décorateurs compte.
