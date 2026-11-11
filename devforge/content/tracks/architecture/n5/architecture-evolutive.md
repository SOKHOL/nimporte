---
id: architecture.n5.architecture-evolutive
title: Fitness functions, strangler fig et loi de Conway
skills: [fitness-functions, strangler-fig, conway]
duration_min: 18
---

# Fitness functions, strangler fig et loi de Conway

Une architecture n'est jamais « finie ». Ce niveau traite de la façon de la faire évoluer
sans la dégrader, de remplacer un vieux système sans tout arrêter, et du lien entre
l'architecture et l'organisation des équipes.

## Les fitness functions : tester l'architecture

Les règles d'architecture écrites dans un wiki s'érodent : sous la pression d'une
échéance, quelqu'un importe une classe interdite, puis dix autres suivent. Une **fitness
function** (*Building Evolutionary Architectures*, Ford, Parsons et Kua) est un **test
automatisé** qui vérifie une propriété de l'architecture, lancé à chaque build.

Exemple réel, écrit pour DevForge : vérifier que les modules `core` n'importent ni Android
ni une couche extérieure.

```kotlin
val interdits = mapOf(
    "core/model" to listOf("android.", "androidx.", "dev.devforge.core.domain", "dev.devforge.core.data"),
    "core/domain" to listOf("android.", "androidx.", "dev.devforge.core.data"),
    "core/data" to listOf("android.", "androidx."),
)
// pour chaque fichier .kt de chaque module : signaler les imports interdits
```

Sur le dépôt : `14 fichiers analysés, 0 violation(s)`. Sur une copie où une ligne
`import dev.devforge.core.data.FileProgressStore` a été ajoutée dans le domaine :

```
14 fichiers analysés, 1 violation(s)
  core/domain/src/main/kotlin/dev/devforge/core/domain/Ports.kt:3 → import dev.devforge.core.data.FileProgressStore
```

Le build échoue, la règle ne s'érode plus. Des bibliothèques font ce travail : **ArchUnit**
(Java), **Konsist** (Kotlin), *dependency-cruiser* (JavaScript), *import-linter* (Python).

```kotlin
@ArchTest
val domaineIndependant = noClasses().that().resideInAPackage("..domain..")
    .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "org.springframework..")
```

Une fitness function peut vérifier bien d'autres propriétés : absence de cycles entre
modules, temps de réponse au p99 sous 200 ms dans un test de charge, taille de l'APK sous
15 Mo, aucune dépendance avec une faille connue, couverture des modules métier. Ce qui
n'est pas mesuré automatiquement finit par se dégrader.

## Remplacer un vieux système : le strangler fig

Réécrire un système entier d'un coup (*big bang*) est l'un des projets les plus risqués qui
soient : des années sans livrer, un ancien système qui continue d'évoluer pendant ce temps,
et des comportements implicites oubliés.

Le **strangler fig** (Martin Fowler, d'après le figuier étrangleur qui pousse autour d'un
arbre jusqu'à le remplacer) procède par petits morceaux :

1. Placer une **façade** (un proxy, une passerelle d'API) devant l'ancien système. Au
   début, elle transmet tout.
2. Réécrire **une** fonctionnalité dans le nouveau système.
3. Router cette fonctionnalité vers le nouveau système (par chemin d'URL, par client, par
   pourcentage du trafic).
4. Recommencer, jusqu'à ce que l'ancien système ne reçoive plus rien ; puis l'éteindre.

Techniques associées :

- **Couche anticorruption** : le nouveau code ne voit jamais le modèle de l'ancien.
- **Exécution en parallèle** (*parallel run*) : envoyer la requête aux deux systèmes,
  servir la réponse de l'ancien, et comparer les résultats pour trouver les écarts avant de
  basculer. GitHub l'a fait avec sa bibliothèque *Scientist*.
- **Branch by abstraction** : à l'intérieur d'un même code, introduire une interface devant
  l'ancien composant, écrire la nouvelle implémentation derrière, basculer avec un flag.
- **Capture des modifications** (CDC) : synchroniser les données de l'ancienne base vers la
  nouvelle pendant la transition (Debezium).

Chaque étape livre de la valeur et peut être annulée. L'échec classique est de ne jamais
finir : deux systèmes à maintenir pour toujours. Il faut une date d'extinction et la suivre.

## La loi de Conway

> « Les organisations qui conçoivent des systèmes produisent des conceptions qui copient la
> structure de communication de ces organisations. » (Melvin Conway, 1968)

Trois équipes qui écrivent un compilateur produiront un compilateur en trois passes. Une
équipe base de données, une équipe back-end et une équipe front-end produiront trois couches
techniques, et chaque fonctionnalité demandera trois équipes. L'architecture et
l'organisation ne se pensent pas séparément.

La **manœuvre de Conway inverse** : organiser les équipes selon l'architecture **voulue**.
Pour des services alignés sur les contextes métier, on crée des équipes alignées sur ces
contextes, qui possèdent leur code de l'interface à la base.

## Team Topologies

*Team Topologies* (Skelton et Pais, 2019) propose quatre types d'équipes :

| Type | Rôle |
|---|---|
| **Alignée sur un flux** (*stream-aligned*) | livre de bout en bout un flux de valeur (un produit, un parcours client) ; la majorité des équipes |
| **Plateforme** | fournit des services internes en libre-service (déploiement, bases, observabilité) qui réduisent la charge des autres |
| **Facilitatrice** (*enabling*) | aide temporairement d'autres équipes à acquérir une compétence (tests, sécurité) |
| **Sous-système complexe** | possède un composant qui demande une expertise rare (moteur de calcul, vidéo, cryptographie) |

Et trois modes d'interaction : **collaboration** (temporaire, pour découvrir), **X-as-a-Service**
(une équipe consomme le service d'une autre sans se coordonner), **facilitation**.

L'idée centrale est la **charge cognitive** : une équipe ne peut maîtriser qu'une quantité
limitée de domaines et de technologies. Si une équipe doit connaître Kubernetes, Terraform,
trois bases de données et cinq domaines métier, elle ralentit et se trompe. Les frontières
d'équipes (et donc d'architecture) se dessinent pour que chaque équipe tienne dans sa tête
ce qu'elle possède.

## À retenir

- Une fitness function est un test automatisé d'une propriété d'architecture ; sans elle, les règles s'érodent.
- Strangler fig : une façade, puis migrer fonctionnalité par fonctionnalité ; exécution en parallèle pour comparer ; et une date d'extinction.
- Loi de Conway : l'architecture copie l'organisation ; la manœuvre inverse organise les équipes selon l'architecture voulue.
- Team Topologies : équipes alignées sur un flux, plateforme, facilitatrices, sous-système complexe ; limiter la charge cognitive.
