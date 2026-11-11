---
id: devops.n3.integration-et-deploiement
title: CI, CD, stratégies de déploiement et feature flags
skills: [ci, cd, strategies-deploiement, feature-flags]
duration_min: 19
---

# CI, CD, stratégies de déploiement et feature flags

## L'intégration continue

L'**intégration continue** (CI) consiste à fusionner son travail dans la branche principale
**au moins une fois par jour**, chaque changement étant vérifié automatiquement : compilation,
tests, analyse. Les conflits et les régressions sont trouvés quand ils sont encore petits.

Un bon pipeline de CI :

- est **rapide** : moins de 10 minutes, sinon on ne l'attend plus et on empile les
  changements ;
- échoue **tôt** : les vérifications rapides (compilation, tests unitaires, lint) avant les
  lentes (tests d'intégration, de bout en bout) ;
- est **fiable** : un test instable (*flaky*), qui échoue une fois sur vingt sans raison,
  apprend à tout le monde à ignorer le rouge ; on le corrige ou on le met en quarantaine
  avec un ticket, tout de suite ;
- se lance sur **chaque** pull request, et la branche principale reste toujours verte.

Le pipeline réel de DevForge (GitHub Actions, extrait) :

```yaml
on:
  push:
    paths: ["devforge/**"]
concurrency:
  group: devforge-android-${{ github.ref }}
  cancel-in-progress: true             # un nouveau push annule l'exécution en cours

jobs:
  build:                               # tests puis APK : 3,1 min mesurées
    steps:
      - uses: actions/checkout@v4
      - uses: gradle/actions/setup-gradle@v4      # cache Gradle entre les exécutions
      - run: ./gradlew :core:model:test :core:markdown:test :core:domain:test :core:data:test
      - run: ./gradlew :app:assembleDebug
      - uses: actions/upload-artifact@v4          # l'APK devient un artefact
  smoke-test:                          # l'app sur un émulateur Android : 3,9 min mesurées
  release:
    needs: [build, smoke-test]         # publie seulement si les deux sont verts
    if: github.event_name == 'push'
```

Pratiques associées : le **développement sur le tronc** (*trunk-based development*) avec des
branches courtes (moins d'un jour ou deux), plutôt que des branches de fonctionnalité qui
vivent des semaines et finissent en fusions douloureuses.

## La livraison et le déploiement continus

- **Livraison continue** (*continuous delivery*) : chaque changement qui passe le pipeline
  **peut** partir en production d'un clic.
- **Déploiement continu** (*continuous deployment*) : il y part **automatiquement**.

Les règles de base :

- **Construire une fois** : l'artefact (image Docker, APK, JAR) est produit une seule fois,
  versionné, stocké dans un registre, puis promu d'environnement en environnement. Dans
  DevForge, la publication télécharge l'artefact produit par le job `build`, au lieu de
  reconstruire.
- **Des versions immuables et traçables** : on retrouve pour chaque version le commit, le
  pipeline et les tests qui l'ont produite. Jamais de tag `latest` en production.
- **Le déploiement est un non-événement** : automatisé, identique pour chaque environnement,
  et le **retour arrière** aussi (redéployer la version précédente).

## Les stratégies de déploiement

| Stratégie | Principe | Avantage | Coût |
|---|---|---|---|
| **Recreate** | on arrête tout, on démarre la nouvelle version | simple | coupure de service |
| **Rolling** (progressif) | on remplace les instances une par une (le défaut de Kubernetes) | pas de coupure, pas de capacité en plus | deux versions servent en même temps pendant le déploiement |
| **Blue/green** | la nouvelle version (*green*) est déployée à côté de l'ancienne (*blue*), puis on bascule le trafic d'un coup | bascule et retour arrière instantanés | double de capacité pendant la bascule |
| **Canary** | une petite part du trafic (1 %, puis 5 %, 25 %…) va sur la nouvelle version, en surveillant les métriques | un défaut ne touche que peu d'utilisateurs ; décision sur des données réelles | outillage (routage fin, métriques comparées) |

Un **canary** se pilote avec des critères explicites : si le taux d'erreurs ou le p99 de la
nouvelle version dépasse celui de l'ancienne, retour arrière automatique (Argo Rollouts,
Flagger, Spinnaker).

### Le piège des bases de données

Avec un rolling ou un canary, **deux versions du code** tournent en même temps sur **la
même base**. Renommer une colonne d'un coup casse l'ancienne version. La méthode
**expand / contract** découpe le changement :

1. **Expand** : ajouter la nouvelle colonne, sans toucher à l'ancienne.
2. Déployer un code qui écrit **dans les deux**, et lit l'ancienne.
3. Recopier les données existantes.
4. Déployer un code qui lit la **nouvelle**.
5. **Contract** : une fois l'ancienne version disparue, supprimer l'ancienne colonne.

Chaque étape est compatible avec la précédente et peut être annulée.

## Les feature flags

Un **feature flag** sépare le **déploiement** (le code est en production) de la
**livraison** (la fonctionnalité est visible) :

```kotlin
if (flags.estActif("nouveau-paiement", utilisateur)) nouveauPaiement(panier)
else ancienPaiement(panier)
```

Usages :

- **livrer du code inachevé** sur le tronc, masqué, au lieu d'une branche qui vit des mois ;
- **ouvrir progressivement** : aux employés, puis à 5 % des clients, puis à tous ;
- **coupe-circuit** (*kill switch*) : désactiver en une seconde une fonctionnalité qui pose
  problème, sans redéployer ;
- **tests A/B**.

Le coût est la **dette de flags** : chaque flag double les chemins possibles du code. Un
flag de livraison a une date de suppression ; une fois la fonctionnalité ouverte à 100 %,
on retire le flag et l'ancien code. Knight Capital a perdu 440 millions de dollars en 45
minutes en 2012, en partie parce qu'un vieux flag réutilisé a réactivé du code mort sur un
serveur oublié par le déploiement.

## À retenir

- CI : intégrer tous les jours, pipeline rapide et fiable, branche principale toujours verte, branches courtes.
- Livraison continue = prêt à partir ; déploiement continu = part automatiquement. Construire une fois, promouvoir, versions immuables.
- Rolling, blue/green, canary ; canary piloté par des métriques ; expand/contract pour la base.
- Feature flags : déployer n'est pas livrer ; coupe-circuit ; supprimer les flags après usage.
