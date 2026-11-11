---
id: android.n4.concevoir
title: Architecture en modules, WorkManager, tests et injection
skills: [architecture-modules, workmanager, tests, injection]
duration_min: 22
---

# Architecture en modules, WorkManager, tests et injection

## Architecture en couches et en modules

L'architecture recommandée par Google tient en trois couches, avec des dépendances qui ne vont
que dans un sens :

```
interface (écrans Compose, ViewModels)
   ↓
domaine (règles métier, cas d'usage)      ← facultative, mais précieuse
   ↓
données (dépôts, sources : Room, réseau, fichiers)
```

Les **modules Gradle** rendent ces frontières **vérifiables par le compilateur** : un module qui ne
déclare pas une dépendance ne peut pas l'utiliser. DevForge en est un exemple réel :

```
app            (Android, Compose)       → core:data, core:markdown
core:data      (Kotlin pur, JVM)        → core:domain
core:domain    (Kotlin pur, JVM)        → core:model
core:model     (Kotlin pur, JVM)
core:markdown  (Kotlin pur, JVM)
```

Quatre modules sur cinq sont du **Kotlin pur** : ils ne connaissent pas Android. Le domaine (la
machine d'état des séances, la répétition espacée, les boss) se teste donc sur la JVM, en une
fraction de seconde. Mesures réelles des tests de DevForge :

```
core/domain    25 tests   0,11 s
core/markdown   8 tests   0,15 s
core/data       6 tests   0,95 s   (dont la lecture du vrai contenu du dépôt)
```

Les autres bénéfices : compilation **incrémentale** et parallèle (on ne recompile que les modules
touchés), propriété claire par équipe, et réutilisation (le même domaine pourrait servir une
version web ou bureau avec Kotlin Multiplatform). Les grandes applications découpent aussi **par
fonctionnalité** (`feature:profil`, `feature:session`), chaque module de fonctionnalité dépendant
des modules `core`, jamais des autres fonctionnalités.

## WorkManager

Pour un travail qui doit **aboutir même si l'utilisateur quitte l'application** ou si le téléphone
redémarre (synchroniser, envoyer des photos, nettoyer un cache), Android fournit **WorkManager** :
on décrit le travail et ses **contraintes**, et le système l'exécute au bon moment, en
respectant la batterie.

```kotlin
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result =
        if (runAttemptCount == 0) Result.retry() else Result.success()   // échoue au premier essai
}

val req = OneTimeWorkRequestBuilder<SyncWorker>()
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .build()
WorkManager.getInstance(ctx).enqueue(req)
```

Sortie réelle avec le pilote de test de WorkManager :

```
WORK après enqueue : ENQUEUED
WORK réseau disponible :   SyncWorker essai 1 → ENQUEUED, essais=1, prochain essai dans 30 s
```

Rien ne s'exécute tant que la contrainte « réseau disponible » n'est pas remplie. Au premier
essai, le travail demande à être réessayé : il repasse en attente, avec un délai **exponentiel**
(30 s, puis 60 s, 120 s…). Le travail est enregistré dans une base interne : il survit à la mort
du processus et au redémarrage du téléphone. Les autres possibilités : travaux **périodiques**
(au moins 15 minutes d'intervalle), **chaînes** de travaux, travaux **uniques** (`enqueueUniqueWork`
avec `KEEP` ou `REPLACE`, pour ne pas empiler dix synchronisations), et travaux **accélérés** pour
ce qui doit commencer tout de suite.

Ce qui n'est **pas** pour WorkManager : ce qui doit s'arrêter avec l'écran (une coroutine dans
`viewModelScope` suffit), et ce qui doit tourner en continu sous les yeux de l'utilisateur
(lecture de musique, navigation GPS : un service de premier plan avec une notification).

## Les tests

La pyramide des tests (parcours Clean code) s'applique, avec trois étages propres à Android :

| Type | Où | Vitesse réelle | Pour quoi |
|---|---|---|---|
| tests unitaires JVM | `src/test`, sans Android | 25 tests en 0,11 s (domaine de DevForge) | règles métier, ViewModels avec un faux dépôt |
| tests Robolectric | `src/test`, Android simulé sur la JVM | 11 tests en 7,9 s (démonstrations de ce parcours) | cycle de vie, Room, WorkManager, Compose |
| tests instrumentés | `src/androidTest`, sur un appareil ou un émulateur | des minutes | parcours complets, intégration réelle |

Les tests Compose se ressemblent quel que soit l'étage : on cherche des nœuds dans l'**arbre
sémantique**, on agit, on vérifie.

```kotlin
@get:Rule val regle = createComposeRule()

@Test fun ajouterIncremente() {
    regle.setContent { Ecran() }
    repeat(3) { regle.onNodeWithText("Ajouter").performClick() }
    regle.onNodeWithText("Total : 3").assertExists()
}
```

Les conseils : tester les ViewModels et le domaine sur la JVM avec des **faux** (un faux dépôt en
mémoire plutôt qu'un objet simulé fragile), utiliser `kotlinx-coroutines-test` (`runTest`, un
répartiteur de test pour `Dispatchers.Main`), garder peu de tests instrumentés mais sur les
parcours critiques, et ajouter des **tests de captures d'écran** (Roborazzi, Paparazzi) pour
détecter les régressions visuelles.

## L'injection de dépendances

Un ViewModel a besoin d'un dépôt, qui a besoin d'une base et d'un client réseau : quelqu'un doit
construire ce graphe. Deux approches :

**Un conteneur manuel**, comme DevForge (`AppContainer`, 85 lignes) : une classe créée dans
`Application.onCreate`, qui construit les objets et les distribue. Simple, explicite, sans
bibliothèque, parfait pour une petite application.

**Hilt** (au-dessus de Dagger) : on annote, et le graphe est généré **à la compilation** (parcours
Frameworks, niveau 5 : pas de réflexion au démarrage, erreurs détectées à la compilation).

```kotlin
@HiltAndroidApp class MonApplication : Application()

@Module @InstallIn(SingletonComponent::class)
object ModuleDonnees {
    @Provides @Singleton fun base(@ApplicationContext ctx: Context) = Room.databaseBuilder(ctx, BaseTaches::class.java, "taches").build()
    @Provides fun dao(base: BaseTaches) = base.taches()
}

@HiltViewModel
class TachesViewModel @Inject constructor(private val dao: TacheDao) : ViewModel()

@AndroidEntryPoint class MainActivity : ComponentActivity()
```

Les **composants** de Hilt correspondent aux cycles de vie Android : `SingletonComponent` (toute
l'application), `ViewModelComponent`, `ActivityComponent`. Une dépendance a la portée de son
composant. Quel que soit l'outil, l'essentiel est le même : les classes **reçoivent** leurs
dépendances par leur constructeur, ce qui permet de leur donner des faux dans les tests.

## À retenir

- Couches interface → domaine → données ; des modules Kotlin purs (4 sur 5 dans DevForge) se testent en millisecondes (25 tests en 0,11 s).
- WorkManager : contraintes, réessais exponentiels (réel : ENQUEUED, prochain essai dans 30 s), persistant ; pas pour le travail lié à l'écran.
- Tests JVM, Robolectric (11 tests en 7,9 s), instrumentés ; injection par constructeur, conteneur manuel ou Hilt généré à la compilation.
