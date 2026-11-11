---
id: securite.n3.proteger-l-application
title: Autorisation, secrets, cryptographie appliquée et dépendances
skills: [autorisation, secrets, crypto-appliquee, dependances]
duration_min: 21
---

# Autorisation, secrets, cryptographie appliquée et dépendances

## L'autorisation

L'**authentification** répond à « qui es-tu ? » ; l'**autorisation** à « as-tu le droit de
faire ça ? ». Trois modèles courants :

| Modèle | Principe | Exemple |
|---|---|---|
| **RBAC** (rôles) | des permissions attachées à des rôles, des rôles attachés aux utilisateurs | « les comptables peuvent valider les factures » |
| **ABAC** (attributs) | une règle sur les attributs de l'utilisateur, de la ressource et du contexte | « un médecin lit le dossier de ses patients, pendant son service » |
| **ReBAC** (relations) | les droits découlent de relations dans un graphe | « peut modifier un document s'il est éditeur du dossier parent » (Google Zanzibar) |

Règles qui valent pour tous :

- **refus par défaut** : ce qui n'est pas explicitement permis est interdit ;
- vérifier **côté serveur**, **à chaque requête**, au plus près de la donnée (l'IDOR du niveau
  2 est un oubli de cette règle) ;
- **centraliser** la décision (une fonction, un service, un moteur de règles comme OPA ou
  Cedar) plutôt que des `if` dispersés ;
- **moindre privilège** : un compte de service, un jeton, un rôle n'a que ce dont il a besoin,
  et seulement le temps nécessaire.

## Les secrets

Un **secret** (mot de passe de base, clé d'API, clé privée, jeton) ne doit jamais être dans
le code ni dans Git. Démonstration réelle : un fichier `config.env` contenant
`API_KEY="sk_live_51Hd8x"` est commité, puis supprimé dans un second commit. Le dépôt ne
contient plus que `.gitignore`, mais :

```
$ git log -p --all -S "sk_live"
commit 4213b50…   -API_KEY="sk_live_51Hd8x"
commit 97f7842…   +API_KEY="sk_live_51Hd8x"
```

Le secret est **dans l'historique pour toujours**, chez tous ceux qui ont cloné le dépôt. Des
robots parcourent GitHub en continu à la recherche de clés publiées, en quelques minutes. Un
secret poussé est un secret **compromis** : la seule bonne réaction est de le **révoquer et le
remplacer** immédiatement ; réécrire l'historique ne vient qu'en complément.

Les bonnes pratiques :

- un **gestionnaire de secrets** (HashiCorp Vault, AWS Secrets Manager, GCP Secret Manager,
  Azure Key Vault) ; l'application les lit au démarrage ou les reçoit injectés ;
- des **secrets dynamiques et courts** : Vault crée un utilisateur de base de données valable
  une heure ; une fuite expire d'elle-même ;
- **plus de secret du tout** quand c'est possible : identités de charge de travail (un pod
  Kubernetes ou une fonction cloud reçoit une identité vérifiable, sans clé stockée), OIDC
  entre la CI et le cloud ;
- des **détecteurs** dans le poste et la CI (gitleaks, trufflehog, la détection de secrets de
  GitHub) qui bloquent le commit ;
- une **rotation** prévue et outillée : si changer une clé demande trois jours et une réunion,
  on ne le fera jamais en urgence.

## La cryptographie appliquée

Règle n° 1 : **ne jamais inventer sa cryptographie**, ni ses algorithmes, ni ses protocoles,
ni même ses combinaisons. On utilise des bibliothèques de haut niveau et éprouvées (libsodium,
Tink, la bibliothèque `cryptography` de Python) avec leurs réglages par défaut.

**Chiffrer avec authentification.** Le chiffrement seul cache le contenu, mais n'empêche pas
de le **modifier**. Un mode **authentifié** (AES-GCM, ChaCha20-Poly1305) ajoute une étiquette
qui détecte la moindre modification. Démonstration réelle avec AES-256-GCM :

```
chiffré (début) : e39d49fa53b22f5dc14334c6a95fbfe3
déchiffré       : b'virement de 100 EUR vers FR76...'
un bit modifié  : InvalidTag (refusé)
```

**Le mode ECB est à proscrire** : il chiffre chaque bloc de 16 octets indépendamment, donc des
blocs identiques donnent des chiffrés identiques et la structure des données transparaît.
Réel, avec deux fois le bloc `ATTAQUE A L'AUBE` :

```
ECB : 42621ea3c2dea0a0 42621ea3c2dea0a0 → identiques : True
GCM : d08f492575830669 17ebfa529deb53d0 → identiques : False
```

Autres règles :

- **Le nonce** (valeur unique) d'AES-GCM ne doit **jamais** être réutilisé avec la même clé :
  sinon la confidentialité et l'authentification s'effondrent. Un nonce aléatoire de 96 bits
  ou un compteur.
- **L'aléa** : pour les jetons, mots de passe temporaires et clés, un générateur
  cryptographique (`secrets` en Python, `SecureRandom` en Java, `crypto.getRandomValues` en
  JavaScript), jamais `random` ou `Math.random`, prévisibles.
- **Les comparaisons** de jetons et signatures en temps constant (`hmac.compare_digest`),
  sinon le temps de réponse révèle combien de caractères sont corrects.
- **Signer** plutôt que chiffrer quand il s'agit de prouver une origine (Ed25519, ECDSA).
- **La gestion des clés** est le vrai problème : où sont-elles, qui y accède, comment les
  changer ? Le **chiffrement d'enveloppe** chiffre les données avec une clé de données, elle-même
  chiffrée par une clé maîtresse dans un KMS ou un HSM qui ne sort jamais.

## Les dépendances vulnérables

Une application moderne, c'est 10 % de code écrit par l'équipe et 90 % de dépendances. Audit
réel avec `pip-audit` d'un fichier qui ne déclare que deux bibliothèques anciennes,
`requests==2.19.0` et `jinja2==2.10` :

| Paquet | Vulnérabilités connues |
|---|---|
| requests 2.19.0 | 5 |
| jinja2 2.10 | 6 |
| urllib3 1.23 (dépendance de requests) | 12 |
| idna 2.7 (dépendance de requests) | 2 |
| **Total** | **25**, sur 7 paquets installés |

La moitié des failles sont dans des dépendances **transitives**, que personne n'a choisies
explicitement. D'où :

- un **outil de SCA** dans la CI (pip-audit, npm audit, OWASP Dependency-Check, Trivy, Snyk,
  Dependabot) qui signale les vulnérabilités connues (CVE) ;
- des **mises à jour fréquentes et petites** (Renovate, Dependabot) plutôt qu'une montée de
  version tous les trois ans, toujours douloureuse ;
- des versions **figées** par un fichier de verrouillage, pour savoir exactement ce qui tourne ;
- un **SBOM** pour répondre en minutes à « sommes-nous touchés par la faille du jour ? »
  (Log4Shell, 2021) ;
- et prioriser : une vulnérabilité **atteignable** dans un service exposé passe avant une
  vulnérabilité théorique dans un outil de build.

## À retenir

- Autorisation : RBAC, ABAC, ReBAC ; refus par défaut, vérification côté serveur à chaque requête, décision centralisée, moindre privilège.
- Un secret poussé dans Git y reste (démontré) : le révoquer ; gestionnaire de secrets, secrets courts ou absents, détecteurs, rotation outillée.
- Cryptographie : bibliothèques éprouvées, chiffrement authentifié (un bit modifié → InvalidTag), jamais ECB, nonce unique, aléa cryptographique, comparaisons en temps constant, gestion des clés.
- Dépendances : 25 failles connues derrière 2 dépendances anciennes ; SCA en CI, mises à jour fréquentes, verrouillage, SBOM.
