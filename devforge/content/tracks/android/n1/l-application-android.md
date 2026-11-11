---
id: android.n1.l-application-android
title: Composants, cycle de vie, manifeste et ressources
skills: [composants, cycle-de-vie, manifeste-ressources]
duration_min: 19
---

# Composants, cycle de vie, manifeste et ressources

Dans ce parcours, les mesures viennent de deux sources réelles : l'application **DevForge**
elle-même (celle que vous utilisez, en Kotlin et Compose), et des tests exécutés avec
**Robolectric**, qui fait tourner le vrai code d'Android 14 sur la JVM.

## Les composants d'une application

Une application Android n'a pas de `main` : c'est le **système** qui crée ses composants quand il
en a besoin (inversion de contrôle, parcours Frameworks). Quatre types de composants :

| Composant | Rôle | Exemple |
|---|---|---|
| **Activité** | un écran avec lequel l'utilisateur interagit | l'écran principal de DevForge |
| **Service** | un travail sans interface (lecture de musique, appel en cours) | un lecteur audio |
| **Récepteur de diffusion** | réagir à un événement du système | « le téléphone a redémarré » |
| **Fournisseur de contenu** | partager des données avec d'autres applications | les contacts, la galerie |

Les applications modernes ont souvent **une seule activité** : la navigation entre écrans se fait
à l'intérieur, avec Compose (niveau 3). DevForge n'a qu'une activité à lui, `MainActivity`. Et
pourtant, le manifeste **fusionné** de son APK, lu avec `aapt2`, en contient d'autres :

```
activity  dev.devforge.MainActivity
provider  androidx.startup.InitializationProvider
activity  androidx.activity.ComponentActivity
activity  androidx.compose.ui.tooling.PreviewActivity
receiver  androidx.profileinstaller.ProfileInstallReceiver
uses-permission: dev.devforge.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
```

Chaque bibliothèque apporte son propre manifeste, **fusionné** à la compilation : un fournisseur
qui initialise des bibliothèques au démarrage (App Startup), un récepteur qui installe les profils
d'optimisation (niveau 5), deux activités d'outillage pour les aperçus et les tests (présentes
dans la version de débogage), et une permission interne ajoutée par `androidx.core`. Ce que
votre application déclare n'est donc qu'une partie de ce qu'elle **contient** : à vérifier avant
une publication (Android Studio : onglet *Merged Manifest*).

## Le cycle de vie d'une activité

Le système crée, met en pause et détruit les activités selon ce que fait l'utilisateur, et
appelle des méthodes à chaque transition. Une activité instrumentée, pilotée par Robolectric,
sortie réelle :

```
--- lancement
onCreate (bundle absent) instance @241
  ViewModel créé
onStart
onResume
--- touche Accueil
onPause
onStop
onSaveInstanceState
--- retour dans l'app
onStart
onResume
--- rotation de l'écran
onPause
onStop
onSaveInstanceState
onDestroy (isChangingConfigurations=true)
onCreate (bundle présent) instance @365
onStart
onResume
après rotation : champ=0, bundle=5, viewModel=5, même objet activité=false, même ViewModel=true
--- touche Retour (l'activité se termine)
onPause
onStop
  ViewModel détruit (onCleared)
onDestroy (isChangingConfigurations=false)
```

Ce qu'il faut en retenir :

- **visible** entre `onStart` et `onStop`, **au premier plan** entre `onResume` et `onPause` ;
- la touche Accueil **ne détruit pas** l'activité : elle s'arrête (`onStop`) et sauvegarde son
  état (`onSaveInstanceState`) au cas où le système tuerait le processus pour libérer de la
  mémoire ;
- la **rotation** (et tout **changement de configuration** : langue, thème sombre, taille de
  fenêtre) **détruit et recrée** l'activité : c'est une **nouvelle instance** (@241 puis @365).
  Mesure réelle avec un compteur à 5 avant la rotation :

| Où était rangé le compteur | Après la rotation |
|---|---|
| un champ de l'activité | **0** (perdu avec l'ancienne instance) |
| le `Bundle` de `onSaveInstanceState` | 5 |
| un `ViewModel` | 5 (le **même** objet, qui survit aux changements de configuration) |

- quand l'utilisateur **quitte** (Retour), le `ViewModel` est détruit (`onCleared`) avant
  `onDestroy`, et `isChangingConfigurations` vaut `false`.

Le ViewModel survit à la rotation mais **pas à la mort du processus** (le système tue les
applications en arrière-plan quand il manque de mémoire) ; le `Bundle`, lui, y survit. D'où la
règle : l'état d'écran important (texte saisi, onglet choisi, position) va dans un état
**sauvegardable** (`rememberSaveable`, `SavedStateHandle`), les données dans un ViewModel et une
base. Testez-le avec l'option développeur « Ne pas conserver les activités ».

## Le manifeste et les ressources

Le **manifeste** (`AndroidManifest.xml`) déclare l'identité et les capacités de l'application :
identifiant (`dev.devforge`), composants, **permissions** demandées, versions d'Android visées.
Réel pour DevForge :

```
package: name='dev.devforge' versionCode='1' versionName='0.1.0' compileSdkVersion='35'
targetSdkVersion:'35'
launchable-activity: name='dev.devforge.MainActivity'
```

- `minSdk` (26 pour DevForge, Android 8) : en dessous, l'application ne s'installe pas ;
- `targetSdk` (35) : la version dont l'application **accepte les comportements**. Augmenter le
  `targetSdk` active de nouvelles règles (permissions, exécution en arrière-plan, affichage bord
  à bord) : c'est une migration à tester. Google Play impose un `targetSdk` récent ;
- `android:exported="true"` : le composant peut être lancé par d'autres applications. Obligatoire
  pour l'activité de lancement, à éviter partout ailleurs (parcours Sécurité).

Les **ressources** (`res/`) sont tout ce qui n'est pas du code : textes (`strings.xml`),
couleurs, images, polices. Leur force est la **sélection automatique selon la configuration** :
`values/strings.xml` par défaut, `values-en/strings.xml` en anglais ; `drawable-night/` en thème
sombre ; des dimensions différentes pour les tablettes. Le système choisit la meilleure
variante au moment de l'exécution, et c'est justement pour la recharger qu'il recrée l'activité
lors d'un changement de configuration. Chaque ressource reçoit un identifiant (`R.string.titre`),
et les ressources compilées sont rangées dans `resources.arsc` dans l'APK. DevForge, lui, embarque
son contenu dans les **assets** (300 fichiers sous `assets/` dans l'APK de débogage), qui sont
copiés tels quels, sans sélection par configuration.

## À retenir

- Quatre composants créés par le système ; une seule activité dans les applications modernes ; le manifeste fusionné contient aussi les composants des bibliothèques (réel : 5 composants pour 1 déclaré).
- Cycle de vie réel : Accueil = onPause, onStop, onSaveInstanceState ; rotation = nouvelle instance ; champ perdu (0), Bundle et ViewModel conservés (5).
- Manifeste : identité, composants, permissions, minSdk et targetSdk ; ressources sélectionnées selon la configuration.
