---
id: android.n3.approfondir
title: ViewModel, navigation, coroutines et persistance
skills: [viewmodel, navigation, coroutines-android, persistance]
duration_min: 22
---

# ViewModel, navigation, coroutines et persistance

## Le ViewModel

Le **ViewModel** porte l'état d'un écran et sa logique de présentation. Il survit aux changements
de configuration (niveau 1 : le même objet avant et après la rotation) et il est détruit quand
l'utilisateur quitte l'écran (`onCleared`). Le schéma standard, tiré de DevForge :

```kotlin
class SessionViewModel(private val container: AppContainer, private val request: SessionRequest) : ViewModel() {
    private val _state = MutableStateFlow<SessionState?>(null)
    val state: StateFlow<SessionState?> = _state.asStateFlow()     // lecture seule pour l'écran

    fun submit(answer: Answer, hintsUsed: Int) { … _state.value = nouvelEtat … }
}

@Composable
fun SessionScreen(viewModel: SessionViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    …
}
```

- l'état est exposé en `StateFlow` **en lecture seule** ; seul le ViewModel le modifie ;
- l'écran **observe** (`collectAsStateWithLifecycle` arrête la collecte quand l'écran n'est plus
  visible) et **envoie des intentions** (`submit`) : c'est le flux unidirectionnel (MVVM et MVI,
  parcours Architecture) ;
- un état d'écran est idéalement **une seule** classe immuable (`sealed interface` : chargement,
  contenu, erreur) plutôt que dix variables indépendantes qui peuvent se contredire ;
- le ViewModel ne connaît **ni** l'activité, **ni** les vues, **ni** un `Context` d'activité
  (fuite mémoire après rotation) : il ne parle qu'à la couche données.

## La navigation

Navigation Compose gère une **pile de retour** (*back stack*) d'écrans, appelés destinations. Dans
DevForge :

```kotlin
NavHost(navController = nav, startDestination = Routes.HOME) {
    composable(Routes.HOME) { HomeScreen(onOpenTrack = { nav.navigate(Routes.track(it)) }, …) }
    composable(Routes.TRACK, arguments = listOf(navArgument("trackId") { type = NavType.StringType })) { entry -> … }
}
```

Les principes :

- chaque destination a sa **route** et ses **arguments** ; depuis Navigation 2.8, des routes
  **typées** (`@Serializable data class Parcours(val id: String)`) évitent les chaînes à la main ;
- on passe des **identifiants**, pas des objets : l'écran d'arrivée recharge ses données depuis
  la couche données (sinon, après la mort du processus, l'objet a disparu) ;
- chaque destination de la pile a son propre `ViewModelStore` : un `viewModel()` appelé dans une
  destination vit tant que la destination est dans la pile ;
- `navigate(…) { popUpTo(…) }` pour vider la pile (après une connexion, Retour ne doit pas revenir
  à l'écran de connexion), `launchSingleTop` pour éviter les doublons ;
- les **liens profonds** (*deep links*) ouvrent directement une destination depuis une URL ou une
  notification.

## Les coroutines sur Android

Le **fil principal** dessine l'interface : 16 ms par image à 60 Hz. Tout travail long (réseau,
disque, calcul) doit en sortir, sinon l'interface saccade, et au bout de 5 secondes sans réponse
le système affiche « L'application ne répond pas » (**ANR**). Les coroutines (parcours
Concurrence) sont l'outil standard, avec des **portées liées au cycle de vie** :

- `viewModelScope` : annulée quand le ViewModel est détruit ;
- `lifecycleScope` + `repeatOnLifecycle(STARTED)` : pour collecter un flux seulement quand
  l'écran est visible.

Pourquoi c'est vital, mesure réelle : deux ViewModels lancent une boucle qui compte toutes les
100 ms, l'un dans `viewModelScope`, l'autre dans `GlobalScope`. On ferme l'écran
(`ViewModelStore.clear()`) puis on attend 1 seconde :

```
SCOPE avant l'écran fermé : viewModelScope=10, GlobalScope=10
SCOPE 1 s après : viewModelScope=10 (annulée=true), GlobalScope=20 (annulée=false)
```

La coroutine de `GlobalScope` continue après la fermeture de l'écran : elle consomme batterie et
réseau, et garde le ViewModel en mémoire (une fuite). Avec `viewModelScope`, l'annulation est
automatique.

Le choix du répartiteur : `Dispatchers.Main` pour l'interface, `Dispatchers.IO` pour les entrées-
sorties bloquantes, `Dispatchers.Default` pour le calcul. Une fonction `suspend` bien écrite est
**sûre pour le fil principal** : c'est elle qui fait `withContext(Dispatchers.IO)` à l'intérieur,
l'appelant n'a pas à s'en soucier.

## La persistance : Room et DataStore

**Room** est une couche au-dessus de SQLite : on déclare des entités et des requêtes, et le
compilateur (KSP) **vérifie le SQL** et génère le code.

```kotlin
@Entity(tableName = "taches")
data class Tache(@PrimaryKey(autoGenerate = true) val id: Long = 0, val titre: String, val faite: Boolean = false)

@Dao interface TacheDao {
    @Insert suspend fun ajouter(t: Tache): Long
    @Query("select * from taches order by id") fun observer(): Flow<List<Tache>>
    @Query("select * from taches order by id") fun toutesBloquant(): List<Tache>
    @Query("update taches set faite = 1 where id = :id") suspend fun terminer(id: Long)
}
```

Sorties réelles, base en mémoire sous Robolectric :

```
ROOM fil principal : Cannot access database on the main thread since it may potentially lock the UI for a long period of time.
ROOM émission 1 : (vide)
ROOM émission 2 : Réviser Room
ROOM émission 3 : Réviser Room ✓
```

- Room **refuse** les requêtes bloquantes sur le fil principal (protection contre les ANR) ;
  les méthodes `suspend` et les `Flow` s'exécutent ailleurs automatiquement ;
- une requête qui renvoie un `Flow` **réémet** à chaque modification de la table : l'interface
  se met à jour seule après l'ajout, puis après `terminer`. C'est la base d'une application
  « hors ligne d'abord » : l'écran observe la base, et la synchronisation avec le serveur écrit
  dans la base ;
- chaque changement de schéma exige une **migration** (`Migration(1, 2) { db.execSQL("alter
  table …") }`), testée (`MigrationTestHelper`) : une migration oubliée fait planter l'application
  chez tous les utilisateurs au lancement de la mise à jour.

**DataStore** remplace `SharedPreferences` pour les petits réglages (thème, série de jours) :
asynchrone (coroutines et `Flow`), transactionnel, sans lecture bloquante. Réel :

```kotlin
ctx.reglages.edit { it[SERIE] = (it[SERIE] ?: 0) + 1 }      // deux fois
ctx.reglages.data.map { it[SERIE] }.first()                 // → DATASTORE série = 2
```

Et DevForge ? Sa progression est un simple fichier JSON, écrit par un seul collecteur dans l'ordre
(`ProgressRepository`) : pour un document unique, c'est suffisant. Room s'impose dès qu'il faut
**interroger** des données nombreuses.

## À retenir

- ViewModel : état exposé en StateFlow en lecture seule, observé avec collectAsStateWithLifecycle, intentions envoyées par l'écran.
- Navigation : pile de retour, routes typées, passer des identifiants, popUpTo ; un ViewModelStore par destination.
- Coroutines : viewModelScope s'annule avec l'écran (10 restent 10), GlobalScope fuit (10 deviennent 20) ; Room refuse le fil principal et réémet un Flow à chaque changement ; DataStore pour les réglages.
