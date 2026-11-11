---
id: devops.n4.infrastructure-et-orchestration
title: Terraform, Ansible, Kubernetes et Helm
skills: [terraform, ansible, kubernetes, kubernetes-debug, helm]
duration_min: 24
---

# Terraform, Ansible, Kubernetes et Helm

## L'infrastructure as code avec Terraform

Créer des serveurs, réseaux et bases à la main dans une console web, c'est l'assurance de ne
jamais savoir exactement ce qui existe ni de pouvoir le recréer. L'**infrastructure as code**
décrit l'infrastructure dans des fichiers versionnés, relus en pull request et appliqués par
un outil.

Terraform (ou son équivalent libre **OpenTofu**) est **déclaratif** : on décrit l'état
voulu, il calcule les actions pour y arriver. Sorties réelles de Terraform 1.9.8 sur une
configuration minimale :

```hcl
variable "replicas" {
  default = 2
}

resource "terraform_data" "serveur_api" {
  input = {
    taille   = "moyenne"
    replicas = var.replicas
  }
}
```

```
$ terraform plan
  # terraform_data.serveur_api will be created
  + resource "terraform_data" "serveur_api" {
      + input  = {
          + replicas = 2
          + taille   = "moyenne"
Plan: 1 to add, 0 to change, 0 to destroy.

$ terraform apply -auto-approve
Apply complete! Resources: 1 added, 0 changed, 0 destroyed.

$ terraform plan
No changes. Your infrastructure matches the configuration.

$ terraform plan -var replicas=4
  # terraform_data.serveur_api will be updated in-place
  ~ resource "terraform_data" "serveur_api" {
      ~ input  = {
          ~ replicas = 2 -> "4"
Plan: 0 to add, 1 to change, 0 to destroy.
```

Les symboles du plan : `+` créer, `~` modifier sur place, `-` détruire, `-/+` détruire puis
recréer (attention : une base de données recréée est une base **vide**). On lit toujours le
plan avant d'appliquer, et en pull request.

**L'état** (`terraform.tfstate`) fait le lien entre le code et les ressources réelles. Ici :
`terraform state list` affiche `terraform_data.serveur_api`, et le fichier en est à sa
version (`serial`) 2 après deux opérations. Règles :

- l'état est stocké **à distance** (S3, GCS, Terraform Cloud), jamais seulement sur un
  poste ;
- avec un **verrou** : deux `apply` simultanés sur le même état le corrompent ;
- il contient souvent des **secrets** en clair : accès restreint et chiffrement ;
- toute modification à la main dans la console crée une **dérive** (*drift*), que le
  prochain `plan` révèle et propose d'écraser.

On découpe en **modules** réutilisables (un module « service web » = réseau + équilibreur +
instances) et en états séparés par environnement et par domaine, pour limiter les dégâts
d'une erreur.

## La gestion de configuration avec Ansible

Terraform crée l'infrastructure ; **Ansible** configure les machines (paquets, fichiers,
services), sans agent, en se connectant en SSH.

```yaml
- hosts: serveurs_web
  become: true
  tasks:
    - name: Installer nginx
      ansible.builtin.apt:
        name: nginx
        state: present
    - name: Déployer la configuration
      ansible.builtin.template:
        src: nginx.conf.j2
        dest: /etc/nginx/nginx.conf
      notify: Recharger nginx
  handlers:
    - name: Recharger nginx
      ansible.builtin.service:
        name: nginx
        state: reloaded
```

La notion centrale est l'**idempotence** : on décrit un **état** (`state: present`), pas une
action. Lancer le playbook dix fois donne le même résultat, et la deuxième exécution
n'affiche que des `ok` (rien à changer). Un module `shell` avec une commande brute casse
cette propriété : à utiliser en dernier recours, avec `creates:` ou `changed_when:`.

Avec les conteneurs, on configure de moins en moins les machines une par une : on construit
des images immuables, et on remplace les machines au lieu de les modifier.

## Kubernetes

Kubernetes fait tourner des conteneurs sur un ensemble de machines. On lui décrit l'état
voulu en YAML, et des **contrôleurs** ajustent en permanence la réalité (une boucle de
réconciliation).

| Objet | Rôle |
|---|---|
| **Pod** | un ou plusieurs conteneurs qui partagent le réseau ; l'unité de base, jetable |
| **Deployment** | maintient N copies d'un pod et gère les mises à jour progressives |
| **Service** | une adresse stable et un équilibrage vers les pods (qui, eux, changent d'IP) |
| **Ingress** / **Gateway** | l'entrée HTTP depuis l'extérieur, par nom de domaine et chemin |
| **ConfigMap** / **Secret** | la configuration et les secrets, injectés en variables ou en fichiers |
| **Namespace** | un espace isolé (par équipe, par environnement) |

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api
spec:
  replicas: 3
  selector:
    matchLabels: { app: api }
  template:
    metadata:
      labels: { app: api }
    spec:
      containers:
        - name: api
          image: registre.exemple.com/api:3f9a2c1
          ports: [{ containerPort: 8080 }]
          resources:
            requests: { cpu: 250m, memory: 512Mi }   # réservé : sert au placement
            limits: { memory: 768Mi }                # au-delà : OOMKilled
          readinessProbe:                            # prêt à recevoir du trafic ?
            httpGet: { path: /pret, port: 8080 }
          livenessProbe:                             # bloqué ? alors redémarrer
            httpGet: { path: /vivant, port: 8080 }
            initialDelaySeconds: 30
```

Points d'expert :

- **requests** sert au placement (l'ordonnanceur ne met un pod que là où ses requests
  tiennent) ; **limits** est un plafond. Dépasser la limite mémoire, c'est la mort
  (`OOMKilled`, code 137) ; dépasser la limite CPU, c'est le bridage (voir le parcours
  Linux, niveau 5).
- **readiness** retire un pod du Service tant qu'il n'est pas prêt ; **liveness** le
  redémarre s'il est bloqué. Une liveness qui vérifie la base redémarre tous les pods quand
  la base a un hoquet : la liveness ne teste que le processus lui-même.
- Arrêt : Kubernetes envoie SIGTERM, attend `terminationGracePeriodSeconds` (30 s par
  défaut), puis SIGKILL. L'application doit finir ses requêtes et s'arrêter.

## Diagnostiquer dans Kubernetes

L'ordre des commandes, du plus général au plus précis :

```bash
kubectl get pods                          # l'état de chaque pod
kubectl describe pod api-7d9f8c6b5-x2k4p  # la section Events, en bas, explique presque tout
kubectl logs api-7d9f8c6b5-x2k4p --previous   # les journaux du conteneur qui vient de planter
kubectl get events --sort-by=.lastTimestamp
kubectl rollout history deployment/api ; kubectl rollout undo deployment/api
```

| Statut | Signification | Piste |
|---|---|---|
| `Pending` | aucun nœud ne peut accueillir le pod | requests trop grandes, quota, volume, affinités (`describe` → Events) |
| `ImagePullBackOff` / `ErrImagePull` | l'image ne peut pas être téléchargée | nom ou tag faux, registre privé sans identifiants |
| `CrashLoopBackOff` | le conteneur démarre puis meurt, en boucle | `logs --previous` : configuration manquante, exception au démarrage |
| `OOMKilled` (dans `describe`, code 137) | limite mémoire dépassée | limite trop basse ou fuite ; pour la JVM, `MaxRAMPercentage` |
| `Running` mais `0/1 READY` | la readiness échoue | dépendance absente, mauvais chemin ou port de sonde |

Après un mauvais déploiement, le premier geste est souvent `kubectl rollout undo` :
**rétablir d'abord, comprendre ensuite**.

## Helm

**Helm** est le gestionnaire de paquets de Kubernetes. Un **chart** est un ensemble de
gabarits YAML paramétrés par un fichier `values.yaml` :

```yaml
# values-production.yaml
replicas: 6
image:
  tag: 3f9a2c1
resources:
  limits: { memory: 768Mi }
```

```bash
helm upgrade --install api ./chart -f values-production.yaml   # installer ou mettre à jour
helm history api                                                # les révisions de la release
helm rollback api 41                                            # revenir à la révision 41
```

Chaque `upgrade` crée une **révision** de la release, ce qui rend le retour arrière
immédiat. On installe aussi des logiciels tiers par leurs charts officiels (PostgreSQL,
Prometheus, Argo CD). Alternative sans gabarits : **Kustomize**, qui applique des
surcouches (*overlays*) par environnement à des YAML ordinaires.

## À retenir

- Terraform : déclaratif ; lire le plan (`+`, `~`, `-`, `-/+`) ; état distant, verrouillé, protégé ; la dérive se voit au `plan`.
- Ansible : un état idempotent, pas des commandes ; de plus en plus remplacé par des images immuables.
- Kubernetes : Deployment, Service, probes (readiness ≠ liveness), requests ≠ limits, SIGTERM puis SIGKILL.
- Diagnostic : `get`, `describe` (Events), `logs --previous` ; rétablir d'abord (`rollout undo`).
- Helm : charts et values par environnement, révisions et `rollback`.
