---
id: android.n2.compose
title: Composables, état, recomposition et listes
skills: [composables, etat, recomposition, listes]
duration_min: 21
---

# Composables, état, recomposition et listes

**Jetpack Compose** décrit l'interface comme une **fonction de l'état** : `UI = f(état)`. On
n'écrit plus « change le texte de ce bouton », mais « voici ce qu'il faut afficher pour cet
état » ; quand l'état change, Compose rappelle les fonctions concernées. Toutes les sorties de
cette leçon viennent de tests Compose exécutés avec Robolectric.

## Les composables

Un **composable** est une fonction annotée `@Composable` qui émet des éléments d'interface :

```kotlin
@Composable
fun CarteTache(titre: String, faite: Boolean, onCocher: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = faite, onCheckedChange = { onCocher() })
        Text(titre, style = MaterialTheme.typography.bodyLarge)
    }
}
```

Les règles :

- un composable ne **renvoie** rien : il décrit ; il peut être appelé **souvent**, dans
  n'importe quel ordre, et doit donc être **rapide** et **sans effet de bord** (pas d'appel
  réseau, pas d'écriture en base dans le corps) ;
- les données descendent en **paramètres**, les événements remontent en **fonctions de rappel**
  (`onCocher`) : le flux unidirectionnel ;
- l'apparence se règle avec des **modificateurs** chaînés (`Modifier.padding(8.dp).fillMaxWidth()`),
  dont l'**ordre compte** (un `padding` avant ou après un `background` ne donne pas le même
  résultat) ;
- `@Preview` affiche un composable dans l'éditeur sans lancer l'application.

## L'état

Compose ne redessine que si l'état qu'il **observe** change. Un état observable se crée avec
`mutableStateOf` et se **garde** entre deux exécutions de la fonction avec `remember` :

```kotlin
var n by remember { mutableIntStateOf(0) }
Button(onClick = { n++ }) { Text("Ajouter") }
Text("Total : $n")
```

Sans `remember`, réel : après trois clics, le texte affiche toujours **« Total : 0 »**. Chaque
clic modifie l'état, ce qui relance la fonction… qui recrée un nouvel état à 0. (Android Studio
signale d'ailleurs cette erreur.)

Les niveaux de conservation :

| Outil | Survit à la recomposition | à la rotation | à la mort du processus |
|---|---|---|---|
| `remember` | oui | non | non |
| `rememberSaveable` | oui | oui | oui (via le `Bundle`) |
| un `ViewModel` (niveau 3) | oui | oui | non (sauf `SavedStateHandle`) |

**Remonter l'état** (*state hoisting*) : un composable réutilisable ne garde pas son état ; il le
reçoit en paramètre et signale les changements, et c'est l'appelant qui le possède. `CarteTache`
ci-dessus est **sans état** : facile à prévisualiser, à tester et à réutiliser.

## La recomposition

Quand un état change, Compose ne relance que les composables qui le **lisent** : c'est la
**recomposition**. Mesure réelle, en comptant les exécutions de chaque fonction pendant trois
clics :

```kotlin
@Composable fun Ecran() {
    var n by remember { mutableIntStateOf(0) }
    Column {
        Button(onClick = { n++ }) { Text("Ajouter") }
        Compteur(n)
        EnTete()
    }
}
```

```
RECOMPOSITION Ecran=4 Compteur=4 EnTete=1
```

`Ecran` lit `n` (pour le passer à `Compteur`) : elle est relancée à chaque clic (1 + 3 = 4).
`Compteur` reçoit une nouvelle valeur : relancé aussi. `EnTete`, sans paramètre qui change, est
**sautée** : Compose compare les paramètres et ne relance pas une fonction dont les entrées sont
identiques et **stables**.

Différer la lecture change tout. En passant une **lambda** `{ n }` au lieu de la valeur, seul le
composable qui appelle la lambda lit l'état :

```
DIFFERE Ecran=1 Compteur=4
```

`Ecran` n'est plus relancée. Pour un état qui change très souvent (position de défilement,
animation), lire l'état le plus **bas** possible, voire seulement dans la phase de dessin
(`Modifier.offset { … }`, `drawBehind`), évite des recompositions coûteuses.

La **stabilité** : Compose ne saute une fonction que si ses paramètres sont **stables** (types
immuables, `String`, `Int`, classes `data` dont les champs sont des `val` de types stables, types
annotés `@Immutable`). Une `List<T>` venant d'un module sans compilateur Compose, ou une classe
avec des `var`, peut rendre une fonction impossible à sauter. Les rapports du compilateur Compose
et le *Layout Inspector* (compteurs de recompositions) servent à le vérifier.

## Les listes

`Column` compose **tous** ses enfants ; `LazyColumn` ne compose que ceux qui sont **visibles**.
Réel, avec 1000 tâches :

```
COLUMN composés=1000
LAZY composés au départ=12, après défilement jusqu'à 500=24
```

`LazyColumn` a composé les 12 lignes visibles, puis, après un saut jusqu'à la tâche 500, les 12
lignes visibles à cet endroit : 24 en tout, au lieu de 1000. Les bonnes pratiques :

```kotlin
LazyColumn {
    items(taches, key = { it.id }, contentType = { it.type }) { tache ->
        CarteTache(tache.titre, tache.faite, onCocher = { vm.cocher(tache.id) })
    }
}
```

- une **clé** stable (`key = { it.id }`) : si l'on insère une tâche en tête, Compose sait quelles
  lignes ont bougé et garde leur état, au lieu de tout recomposer ou de mélanger les états ;
- `contentType` aide à réutiliser les lignes de même forme ;
- jamais de `LazyColumn` dans un conteneur qui défile verticalement (`verticalScroll`) : la liste
  ne connaît plus sa hauteur et Compose refuse ;
- pas de calcul lourd dans `items` : trier, filtrer et formater dans le ViewModel.

## À retenir

- Un composable décrit l'interface à partir de son état, sans effet de bord ; données vers le bas, événements vers le haut.
- remember garde l'état (sans lui, « Total : 0 » après trois clics) ; rememberSaveable survit à la rotation ; remonter l'état.
- Recomposition mesurée : Ecran=4, Compteur=4, EnTete=1 (sautée) ; lecture différée : Ecran=1 ; LazyColumn compose 24 lignes sur 1000.
