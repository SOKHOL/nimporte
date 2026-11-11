---
id: android.n5.maitriser
title: Performance, accessibilité, publication et suivi en production
skills: [performance, accessibilite, publication]
duration_min: 22
---

# Performance, accessibilité, publication et suivi en production

## La performance

Sur mobile, la performance se voit et se paie : un démarrage lent, une liste qui saccade ou un
APK trop lourd font désinstaller l'application. Trois axes.

**La taille et R8.** R8 est l'outil qui, à la compilation de la version publiée, **supprime** le
code et les ressources inutilisés, **raccourcit** les noms et **optimise** (inlining, suppression
de branches mortes). Mesure réelle sur la petite application de démonstration de ce parcours
(Compose, Room, DataStore, WorkManager) :

```
sans R8 : app-release-unsigned.apk  7116 Ko, 2 fichiers dex, 102552 méthodes référencées
avec R8 : app-minifie-unsigned.apk   804 Ko, 1 fichier dex,    8836 méthodes référencées
```

**Neuf fois plus petit**, et onze fois moins de méthodes : les bibliothèques embarquent beaucoup
de code que l'application n'appelle jamais. Les contreparties : le code accédé par **réflexion**
(sérialisation, injection par réflexion) doit être protégé par des règles `-keep`, sinon il est
supprimé et l'application plante seulement en version publiée ; et les piles d'appels des plantages
sont **obscurcies** : il faut conserver le fichier `mapping.txt` de chaque version pour les relire
(niveau 4 du parcours Débogage pour la lecture des piles). À tester donc sur la version minifiée,
pas seulement en débogage.

**Le démarrage.** On distingue le démarrage **à froid** (le processus n'existe pas : le système le
crée, puis `Application.onCreate`, puis la première activité et sa première image), **tiède** et
**à chaud** (l'activité est déjà en mémoire). Les leviers :

- ne rien faire de lourd dans `Application.onCreate` ni dans le premier écran (charger en
  arrière-plan, initialiser les bibliothèques à la demande) ; DevForge charge son contenu dans une
  coroutine et affiche un état « chargement » ;
- les **profils de référence** (*Baseline Profiles*) : une liste des classes et méthodes utilisées
  au démarrage, compilée à l'avance en code machine à l'installation (ART compile sinon ce code
  par JIT, au fil des exécutions). C'est le rôle du `ProfileInstallReceiver` vu dans le manifeste
  de DevForge au niveau 1. Gain annoncé par Google : souvent 20 à 30 % sur le démarrage ;
- mesurer avec **Macrobenchmark** sur un vrai téléphone, en version publiée, jamais en débogage
  (le code de débogage n'est pas optimisé et Compose y est nettement plus lent).

**La fluidité.** À 60 Hz, une image doit être prête en **16 ms** (8 ms à 120 Hz) ; au-delà,
l'image est sautée (*jank*). Les causes classiques : travail sur le fil principal (lecture disque,
gros JSON), recompositions inutiles (niveau 2 : Ecran=4 contre Ecran=1 en différant la lecture),
listes sans `LazyColumn` ni clés, images trop grandes décodées en mémoire. Les outils :
`StrictMode` en débogage (signale les accès disque et réseau sur le fil principal), le profileur
d'Android Studio, Perfetto pour une trace système, et le *Layout Inspector* pour compter les
recompositions.

## L'accessibilité

Une personne sur six vit avec un handicap ; beaucoup utilisent un **lecteur d'écran** (TalkBack),
une police agrandie, ou un contrôle par contacteurs. Ce que voit TalkBack, c'est l'**arbre
sémantique** de Compose. Réel, pour deux boutons-icônes dont un seul a une description :

```
 |-Node #53 at (l=4.0, t=60.0, r=44.0, b=100.0)px
 | Role = 'Button'
 | ContentDescription = '[Supprimer la tâche]'
 | Text = '[🗑]'
 | Actions = [OnClick, RequestFocus, …]
 |-Node #55 at (l=4.0, t=108.0, r=44.0, b=148.0)px
   Role = 'Button'
   Text = '[✎]'
   Actions = [OnClick, RequestFocus, …]
```

Le premier est annoncé « Supprimer la tâche, bouton ». Le second ne dit rien d'utile : un symbole,
sans description. Les règles de base :

- toute icône **cliquable** a une `contentDescription` (et une icône purement décorative a
  `contentDescription = null`, pour être ignorée) ;
- des zones tactiles d'au moins **48 dp** de côté ;
- un **contraste** suffisant (4,5:1 pour le texte normal, selon les WCAG) ;
- des tailles de texte en `sp` qui suivent le réglage de l'utilisateur, et des écrans qui restent
  utilisables avec une police à 200 % ;
- regrouper ce qui se lit ensemble (`Modifier.semantics(mergeDescendants = true)`) : une carte de
  tâche annoncée en une phrase, pas en cinq morceaux ;
- ne jamais transmettre une information **seulement** par la couleur (une bonne réponse en vert
  doit aussi avoir une icône ou un texte).

Ces propriétés se **testent** : les tests Compose cherchent justement par texte et par description
(`onNodeWithContentDescription("Supprimer la tâche")`), et l'*Accessibility Scanner* de Google
analyse un écran réel. Une interface accessible est aussi plus facile à tester automatiquement.

## La publication et le suivi en production

**Publier sur Google Play** :

- on envoie un **Android App Bundle** (`.aab`), pas un APK : Play génère pour chaque appareil un
  APK ne contenant que ses ressources (langue, densité d'écran, processeur), plus petit ;
- la **signature** : avec *Play App Signing*, Google garde la clé de signature de l'application, et
  vous signez vos envois avec une clé d'**envoi** remplaçable. Perdre une clé de signature sans ce
  service, c'est ne plus jamais pouvoir mettre à jour l'application ;
- chaque version a un `versionCode` **strictement croissant** ;
- les **pistes** : test interne, test fermé, test ouvert, production ; et le **déploiement
  progressif** (1 %, 5 %, 20 %, 50 %, 100 %), interrompu au premier signal de problème.

DevForge, lui, est distribué comme un APK sur GitHub, signé avec une clé de **débogage**
volontairement versionnée dans le dépôt : ainsi chaque nouvelle version s'installe par-dessus
l'ancienne sans perdre la progression. C'est acceptable pour une application personnelle ;
c'est exactement ce qu'il ne faut **pas** faire pour une application publiée, car n'importe qui
pourrait signer une fausse mise à jour.

**Le suivi en production** : on ne voit pas les téléphones des utilisateurs, il faut donc des
**remontées** automatiques.

- les **plantages** et les **ANR** : Android vitals dans la Play Console, avec des seuils au-delà
  desquels Google réduit la visibilité de l'application (taux de plantages perçus par
  l'utilisateur au-delà de 1,09 %, d'ANR au-delà de 0,47 %) ; un outil comme Firebase Crashlytics
  ou Sentry pour les piles d'appels (désobscurcies avec `mapping.txt`) ;
- les **performances réelles** : temps de démarrage, images sautées, mesurés chez les utilisateurs ;
- les **indicateurs de fonctionnalités** (*feature flags*, parcours DevOps) et la configuration à
  distance, pour couper une fonctionnalité défaillante **sans attendre** une nouvelle version, car
  une mise à jour met des jours à atteindre la majorité des téléphones ;
- et les avis des utilisateurs, lus et classés comme des signalements de bugs.

## À retenir

- R8 : 7116 Ko → 804 Ko et 102 552 → 8 836 méthodes (réel), mais règles -keep pour la réflexion et mapping.txt pour les piles ; démarrage à froid, profils de référence ; 16 ms par image.
- Accessibilité : l'arbre sémantique est ce que lit TalkBack (réel : un bouton décrit, un muet) ; 48 dp, contraste, sp, pas d'information par la seule couleur.
- Publication : App Bundle, Play App Signing, versionCode croissant, déploiement progressif ; suivi des plantages et ANR, indicateurs de fonctionnalités pour couper sans mise à jour.
