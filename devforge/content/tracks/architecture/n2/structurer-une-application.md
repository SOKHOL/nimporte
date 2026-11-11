---
id: architecture.n2.structurer-une-application
title: MVVM, MVI, injection de dépendances et modules
skills: [mvvm-mvi, di-architecture, modularisation, principes-packages]
duration_min: 17
---

# MVVM, MVI, injection de dépendances et modules

## MVVM : un état observable

Sur Android (et dans la plupart des interfaces modernes), **MVVM** remplace MVC :

- la **Vue** (un écran Compose) affiche un état et transmet les actions de l'utilisateur ;
- le **ViewModel** expose cet état (`StateFlow<EtatEcran>`) et reçoit les actions ; il
  survit aux rotations d'écran et ne connaît pas la vue ;
- le **Modèle** (cas d'usage, dépôts) porte les données et les règles.

La vue **observe** l'état et se redessine quand il change (le pattern Observer). Le
ViewModel ne dit jamais « mets ce texte en rouge » : il publie un état, la vue en déduit
l'affichage.

## MVI : un seul état, des actions, un réducteur

**MVI** pousse l'idée plus loin avec un **flux unidirectionnel** :

```
action de l'utilisateur → réducteur(état, action) → nouvel état → la vue l'affiche
```

```kotlin
data class Etat(val compteur: Int = 0, val chargement: Boolean = false, val erreur: String? = null)

sealed interface Action {
    data object Incrementer : Action
    data object DebutChargement : Action
    data class Echec(val message: String) : Action
}

fun reduire(etat: Etat, action: Action): Etat = when (action) {
    Action.Incrementer -> etat.copy(compteur = etat.compteur + 1)
    Action.DebutChargement -> etat.copy(chargement = true, erreur = null)
    is Action.Echec -> etat.copy(chargement = false, erreur = action.message)
}
```

Le réducteur est une **fonction pure** : même état et même action donnent toujours le même
résultat. On le teste sans Android, et on peut rejouer une suite d'actions pour reproduire un
bug. Sortie réelle en appliquant Incrementer, DebutChargement, Echec("hors ligne") puis
Incrementer : `Etat(compteur=2, chargement=false, erreur=hors ligne)`.

C'est le même modèle que Redux (JavaScript) et Elm. Un état **unique et immuable** élimine
une classe entière de bugs : l'écran qui affiche à la fois « chargement » et « erreur »
parce que deux variables n'ont pas été mises à jour ensemble.

## L'injection de dépendances à l'échelle d'une application

Au niveau d'une classe, l'injection consiste à recevoir ses dépendances en paramètre au lieu
de les créer. À l'échelle d'une application, il faut un endroit qui **assemble** tout : la
**racine de composition** (*composition root*).

```kotlin
// La racine de composition de DevForge (extrait du vrai code)
class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val loader = YamlContentLoader(AssetContentFiles(context.assets))

    val progress = ProgressRepository(FileProgressStore(File(context.filesDir, "progress.json")), scope)
}
```

`YamlContentLoader` (module `core:data`) ne sait pas d'où vient le contenu : il reçoit un
`ContentFiles`. Dans l'application, c'est un adaptateur qui lit les *assets* de l'APK ; dans
les tests, un adaptateur qui lit le dossier `content` du dépôt.

DevForge assemble ses objets à la main dans un `AppContainer` : pour une petite application,
c'est simple et sans magie. Les frameworks (Hilt et Koin sur Android, Spring côté serveur)
automatisent cet assemblage et gèrent les **portées** : une instance pour toute
l'application (singleton), une par écran, une par requête HTTP.

Règles qui ne changent pas, avec ou sans framework :

- les dépendances passent par le **constructeur** (pas de champ injecté en cachette) ;
- seule la racine de composition connaît les classes concrètes ;
- le code métier ne connaît pas le framework d'injection.

## Modulariser

Un projet de 200 000 lignes dans un seul module compile lentement, et rien n'empêche
n'importe quel fichier d'utiliser n'importe quel autre. Découper en **modules** (Gradle,
Maven, packages Go, workspaces npm) apporte :

- des **frontières vérifiées par le compilateur** : un module n'utilise que ce qu'il
  déclare dépendre, et seulement ce que l'autre expose (`internal` en Kotlin) ;
- des builds plus rapides (compilation en parallèle, et seuls les modules modifiés sont
  recompilés) ;
- la possibilité de confier un module à une équipe.

Découpages courants : par **couche** (`core:model`, `core:domain`, `core:data`, `app`), ou
par **fonctionnalité** (`feature:paiement`, `feature:catalogue`), souvent les deux.

Les modules réels de DevForge et leurs dépendances directes :

```
:app          → :core:data, :core:markdown
:core:data    → :core:domain
:core:domain  → :core:model
:core:model   → (rien)
:core:markdown → (rien)
```

Aucun fichier des modules `core` n'importe `android.*` (vérifié) : tout le métier se teste
sur une JVM ordinaire.

## Principes de cohésion et de couplage des modules

Robert C. Martin a formulé six principes pour les modules (*packages*). Les trois du
couplage sont les plus utiles :

| Principe | Énoncé |
|---|---|
| **ADP** (dépendances acycliques) | le graphe des dépendances entre modules ne contient aucun cycle |
| **SDP** (dépendances stables) | on dépend dans le sens de la stabilité : un module instable dépend de modules stables |
| **SAP** (abstractions stables) | un module très stable doit être abstrait (interfaces), pour pouvoir être étendu sans être modifié |

L'**instabilité** d'un module se calcule : `I = Ce / (Ca + Ce)`, où `Ce` compte les modules
dont il dépend et `Ca` ceux qui dépendent de lui. 0 = très stable (beaucoup en dépendent,
il ne dépend de rien), 1 = très instable.

Sur DevForge : `:core:model` a `Ca = 1`, `Ce = 0`, donc `I = 0` ; `:app` a `Ca = 0`,
`Ce = 2`, donc `I = 1`. Les dépendances vont bien de l'instable (l'application, qui change
tout le temps) vers le stable (le modèle, qui change rarement).

Les trois principes de **cohésion** disent quoi mettre ensemble : on livre ensemble ce qu'on
réutilise ensemble (REP), on regroupe ce qui change pour les mêmes raisons (CCP), et on ne
force pas un utilisateur à dépendre de ce qu'il n'utilise pas (CRP).

Un **cycle** entre modules (A dépend de B qui dépend de A) les soude : impossible de les
compiler, tester ou livrer séparément. On le casse en extrayant la partie commune, ou en
inversant une dépendance avec une interface.

## À retenir

- MVVM : la vue observe un état exposé par le ViewModel. MVI : un état unique, des actions, un réducteur pur.
- Une racine de composition assemble l'application ; le métier ne connaît pas le framework d'injection.
- Les modules donnent des frontières vérifiées et des builds plus rapides.
- Pas de cycle ; dépendre vers le stable ; `I = Ce / (Ca + Ce)`.
