---
id: devops.n1.culture-devops
title: Culture DevOps, environnements et 12 facteurs
skills: [culture, environnements, twelve-factor]
duration_min: 15
---

# Culture DevOps, environnements et 12 facteurs

## DevOps est d'abord une culture

Avant DevOps, les développeurs voulaient du changement et les opérations voulaient de la
stabilité. Le code passait « par-dessus le mur » une fois par trimestre, dans une mise en
production nocturne et stressante, et chacun accusait l'autre en cas de panne.

DevOps rapproche les deux : **une équipe est responsable de son logiciel jusqu'en
production** (« you build it, you run it », Werner Vogels, Amazon). Le modèle **CALMS**
résume les piliers :

| | |
|---|---|
| **C**ulture | responsabilité partagée, confiance, apprentissage plutôt que recherche de coupable |
| **A**utomatisation | tests, build, déploiement, infrastructure : tout ce qui se répète |
| **L**ean | petits lots, flux continu, éliminer l'attente |
| **M**esure | décider sur des données (métriques de livraison et de production) |
| **S**hare (partage) | connaissances, outils, astreintes, retours d'expérience |

Un outil n'est pas une culture : acheter Kubernetes ne rend pas une organisation DevOps si
les déploiements demandent encore trois validations et un comité.

## Mesurer : les quatre métriques DORA

Les études DORA (*DevOps Research and Assessment*, *Accelerate*, Forsgren, Humble et Kim)
ont identifié quatre métriques qui distinguent les équipes performantes :

| Métrique | Question | Équipes d'élite |
|---|---|---|
| **Fréquence de déploiement** | à quelle fréquence livre-t-on en production ? | plusieurs fois par jour |
| **Délai de livraison** (*lead time*) | combien de temps entre un commit et sa mise en production ? | moins d'une journée |
| **Taux d'échec des changements** | quelle part des déploiements provoque un incident ? | faible |
| **Temps de rétablissement** (MTTR) | combien de temps pour réparer après un incident ? | moins d'une heure |

Le résultat le plus important de ces études : **vitesse et stabilité ne s'opposent pas**.
Les équipes qui livrent le plus souvent sont aussi celles qui cassent le moins, parce que
de petits changements fréquents sont faciles à vérifier et à annuler.

Exemple réel, DevForge : chaque `git push` lance les tests, la compilation de l'APK, un test
sur émulateur, puis publie la nouvelle version. Durées mesurées sur la dernière exécution :
3,1 min pour les tests et l'APK, 3,9 min pour l'émulateur (en parallèle), 0,2 min pour la
publication. Le délai entre un commit et une version installable est d'environ 5 minutes.

Après un incident, on écrit un **post-mortem sans reproche** (*blameless*) : que s'est-il
passé, pourquoi le système a permis l'erreur, quelles actions pour qu'elle ne se reproduise
pas. Chercher un coupable apprend aux gens à cacher les erreurs ; chercher la cause dans le
système le rend plus robuste.

## Les environnements

Un logiciel passe par plusieurs environnements avant les utilisateurs :

| Environnement | Rôle |
|---|---|
| **Développement** (le poste, ou un environnement par développeur) | coder et tester vite |
| **Intégration / CI** | vérifier chaque changement automatiquement |
| **Préproduction** (*staging*) | une copie aussi fidèle que possible de la production, pour les tests de bout en bout |
| **Production** | les vrais utilisateurs |

Principes :

- **Le même artefact partout** : on construit l'image une seule fois, puis on la **promeut**
  d'environnement en environnement. Reconstruire pour la production, c'est livrer autre
  chose que ce qu'on a testé.
- **La parité** : mêmes versions de base de données, de système, de dépendances. « Ça marche
  en staging » ne vaut rien si staging utilise PostgreSQL 12 et la production PostgreSQL 16.
- **Seule la configuration change** d'un environnement à l'autre, par des variables
  d'environnement ou des secrets, jamais par du code.
- Les **environnements éphémères** (un par pull request, détruit après fusion) permettent
  de tester chaque changement isolément.

## Les 12 facteurs

La méthode des **12 facteurs** (Heroku, 2011) décrit une application facile à déployer et à
mettre à l'échelle sur n'importe quelle plateforme. Les plus importants :

| Facteur | Règle |
|---|---|
| 1. Base de code | un dépôt, plusieurs déploiements |
| 2. Dépendances | déclarées explicitement et isolées (pas de « il faut installer X sur le serveur ») |
| 3. Configuration | dans l'**environnement**, pas dans le code ; test : pourrait-on publier le code en open source sans exposer un secret ? |
| 4. Services externes | base, cache, file : des ressources attachées par une URL, interchangeables |
| 5. Build, release, run | trois étapes séparées ; une release = un build + une configuration, immuable |
| 6. Processus | sans état ; l'état vit dans les services externes |
| 7. Port | l'application expose elle-même son service HTTP sur un port |
| 8. Concurrence | on monte en charge en ajoutant des processus |
| 9. Jetable | démarrage rapide, arrêt propre sur SIGTERM |
| 10. Parité dev/prod | environnements aussi proches que possible |
| 11. Journaux | écrits sur la sortie standard, comme un flux ; la plateforme les collecte |
| 12. Tâches d'administration | migrations et scripts lancés comme des processus ponctuels, avec le même code |

Ces règles sont devenues la base des conteneurs et de Kubernetes : une image immuable,
configurée par l'environnement, sans état, qui écrit ses journaux sur la sortie standard et
s'arrête proprement sur SIGTERM.

## À retenir

- DevOps : une équipe responsable jusqu'en production ; CALMS ; un outil n'est pas une culture.
- DORA : fréquence, délai, taux d'échec, temps de rétablissement ; vitesse et stabilité vont ensemble.
- Post-mortems sans reproche : corriger le système, pas les personnes.
- Un artefact construit une fois, promu partout ; seule la configuration change ; parité des environnements.
- 12 facteurs : configuration dans l'environnement, processus sans état, journaux sur la sortie standard, arrêt propre.
