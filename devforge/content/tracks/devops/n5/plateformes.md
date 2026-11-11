---
id: devops.n5.plateformes
title: GitOps, platform engineering et chaîne logicielle
skills: [gitops, platform-engineering, supply-chain]
duration_min: 20
---

# GitOps, platform engineering et chaîne logicielle

## GitOps

Avec un pipeline classique (« push »), la CI exécute `kubectl apply` ou `helm upgrade` vers
le cluster. Elle détient donc des identifiants puissants de production, et rien ne garantit
que le cluster reste conforme à ce qui a été déployé : un `kubectl edit` à la main, et la
réalité diverge silencieusement.

**GitOps** inverse le sens :

1. L'état voulu de chaque environnement est décrit dans un **dépôt Git** (manifestes YAML,
   charts Helm, overlays Kustomize).
2. Un **agent dans le cluster** (Argo CD, Flux) compare en permanence le dépôt et le
   cluster, et **réconcilie** : il applique ce qui manque, corrige ce qui a dérivé (*self-heal*)
   et supprime ce qui n'est plus décrit (*prune*).
3. Déployer = fusionner une pull request qui change un tag d'image dans le dépôt.

Ce que ça apporte :

- **Git devient le journal d'audit** : qui a déployé quoi, quand, approuvé par qui.
- **Retour arrière** = `git revert`.
- **Pas d'identifiants de production dans la CI** : l'agent tire les changements depuis
  l'intérieur du cluster (modèle « pull »).
- **Pas de dérive** : un changement manuel est annulé en quelques minutes, ou signalé
  (`OutOfSync`).
- **Reconstruire un cluster** entier = pointer un nouvel agent vers le dépôt.

Organisation courante : un dépôt pour le code de l'application (la CI construit et pousse
l'image), un dépôt de **configuration** par environnement (le tag d'image y est mis à jour
par une pull request automatique ou un outil comme Argo CD Image Updater). Les secrets ne
vont jamais en clair dans Git : chiffrés (Sealed Secrets, SOPS) ou référencés depuis un
gestionnaire (External Secrets avec Vault ou le gestionnaire du cloud).

## Platform engineering

Quand chaque équipe doit maîtriser Kubernetes, Terraform, la CI, la supervision, les
certificats et les politiques de sécurité, la **charge cognitive** explose (voir Team
Topologies dans le parcours Architecture). Le *platform engineering* regroupe cette
complexité dans une **plateforme interne** (*Internal Developer Platform*), gérée **comme un
produit** par une équipe plateforme dont les clients sont les développeurs.

Ce qu'offre une bonne plateforme :

- des **chemins balisés** (*golden paths*) : « créer un nouveau service » génère en une
  minute un dépôt, un pipeline, les manifestes, la supervision et les tableaux de bord,
  avec les bonnes pratiques déjà en place ;
- du **libre-service** : une base de données, un environnement éphémère, un secret, sans
  ticket ni attente ;
- un **portail** (Backstage, créé par Spotify) : le catalogue des services, leur
  propriétaire, leur documentation, leur état.

Pièges : construire une plateforme que personne n'a demandée (il faut écouter ses
utilisateurs et mesurer l'adoption), imposer au lieu de rendre le bon chemin le plus facile,
et ajouter une couche d'abstraction qui cache tout, au point que plus personne ne sait
diagnostiquer quand elle casse.

## La sécurité de la chaîne logicielle

Les attaques visent de plus en plus la **chaîne** qui produit le logiciel plutôt que le
logiciel lui-même :

- **SolarWinds** (2020) : du code malveillant injecté dans le **système de build**, puis
  distribué, signé, à 18 000 clients ;
- **Log4Shell** (2021) : une faille dans une dépendance présente presque partout ; la
  première question de chaque entreprise était « où utilise-t-on Log4j ? » ;
- **xz-utils** (2024) : un contributeur a gagné la confiance du mainteneur pendant deux ans,
  puis a caché une porte dérobée dans l'archive publiée ; découverte par hasard, à cause de
  500 ms de latence en trop sur SSH ;
- la **confusion de dépendances** : publier sur le registre public un paquet du même nom
  qu'un paquet interne, avec une version plus haute.

Les défenses :

**1. Savoir ce qu'on livre : le SBOM.** Un *Software Bill of Materials* (formats SPDX ou
CycloneDX, généré par Syft, Trivy ou le build) liste chaque composant et sa version. Même
l'image `alpine:3.20` contient réellement 14 paquets, dont `busybox-1.36.1-r31`,
`libcrypto3-3.3.7-r0`, `libssl3-3.3.7-r0`, `musl-1.2.5-r3` et `zlib-1.3.2-r0`. Quand une
faille est annoncée dans une bibliothèque, le SBOM répond en une requête à « sommes-nous
concernés, et où ? ».

**2. Figer exactement ce qu'on utilise.** Un tag peut être déplacé ; une empreinte non :

```dockerfile
FROM alpine:3.20@sha256:d9e853e87e55526f6b2917df91a2115c36dd7c696a35be12163d44e6e2a4b6bc
```

Même chose pour les dépendances (fichiers de verrouillage avec empreintes) et les actions
de CI (`uses: actions/checkout@<SHA du commit>` plutôt qu'un tag modifiable). Des outils
comme Renovate ou Dependabot proposent ensuite les mises à jour en pull request.

**3. Signer et vérifier.** **Sigstore / cosign** signe les images (sans gérer de clé à long
terme, avec une identité de CI), et le cluster **refuse** les images non signées ou signées
par une autre identité (politiques Kyverno, Sigstore policy-controller).

**4. Prouver comment l'artefact a été construit : la provenance.** Le cadre **SLSA**
(*Supply-chain Levels for Software Artifacts*) définit des niveaux : un build scripté, puis
un build sur un service dédié qui produit une **attestation de provenance** signée (quel
dépôt, quel commit, quel pipeline), puis un build isolé et non falsifiable.

**5. Réduire la surface** : moins de dépendances, des images minimales, des registres
internes qui servent de cache filtré, des droits minimaux pour la CI (le jeton de DevForge
n'a que `contents: write`, pour publier la version).

## À retenir

- GitOps : Git décrit l'état voulu, un agent dans le cluster réconcilie ; audit, retour arrière par revert, plus de dérive ni d'identifiants de prod dans la CI.
- Platform engineering : une plateforme gérée comme un produit, des chemins balisés et du libre-service pour réduire la charge cognitive.
- Chaîne logicielle : SBOM, versions figées par empreinte, signature (cosign), provenance (SLSA), droits minimaux.
