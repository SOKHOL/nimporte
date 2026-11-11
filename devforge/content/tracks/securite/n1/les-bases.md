---
id: securite.n1.les-bases
title: CIA, hachage, chiffrement, mots de passe et MFA
skills: [triade-cia, hachage-chiffrement, mots-de-passe, authentification-forte]
duration_min: 18
---

# CIA, hachage, chiffrement, mots de passe et MFA

## La triade CIA

La sécurité protège trois propriétés :

| Propriété | Question | Atteinte typique |
|---|---|---|
| **Confidentialité** | seules les bonnes personnes lisent-elles les données ? | fuite d'une base de clients |
| **Intégrité** | les données sont-elles exactes et non modifiées ? | montant d'un virement altéré, journal effacé |
| **Disponibilité** | le service est-il accessible quand il le faut ? | déni de service, rançongiciel qui chiffre tout |

Toute mesure de sécurité protège au moins l'une des trois, et en gêne parfois une autre
(chiffrer protège la confidentialité, mais perdre la clé détruit la disponibilité). On
y ajoute souvent l'**authenticité** (qui a fait quoi) et la **non-répudiation**.

La sécurité se raisonne en **risques** : la probabilité qu'une menace exploite une faiblesse,
multipliée par l'impact. On ne sécurise pas tout au même niveau ; on protège d'abord ce qui
compte le plus.

## Encodage, hachage et chiffrement

Trois opérations souvent confondues :

| | Réversible ? | Avec une clé ? | Sert à |
|---|---|---|---|
| **Encodage** (Base64, URL, hex) | oui, par tout le monde | non | transporter des données ; **aucune** sécurité |
| **Hachage** (SHA-256) | **non** | non | empreinte, intégrité, mots de passe (avec un algorithme lent) |
| **Chiffrement** (AES, RSA) | oui, avec la clé | oui | confidentialité |

Une **fonction de hachage** cryptographique transforme n'importe quelle entrée en une
empreinte de taille fixe ; la même entrée donne toujours la même empreinte (réel :
`sha256('soleil123')` commence toujours par `939b08112dfd60f1…`), et on ne peut pas retrouver
l'entrée à partir de l'empreinte, ni trouver deux entrées de même empreinte. MD5 et SHA-1 sont
**cassés** pour la sécurité ; on utilise SHA-256 ou plus.

Le **chiffrement symétrique** (AES) utilise la même clé pour chiffrer et déchiffrer : rapide,
mais il faut partager la clé. Le **chiffrement asymétrique** (RSA, courbes elliptiques) utilise
une clé publique et une clé privée : la publique chiffre ou vérifie une signature, seule la
privée déchiffre ou signe. TLS combine les deux (niveau 3 de Réseau et web).

## Stocker des mots de passe

On ne stocke **jamais** un mot de passe, ni chiffré (la clé finit par fuiter avec la base), ni
haché avec un hachage **rapide**. Mesure réelle, en Python sur un seul cœur :

| Algorithme | Essais par seconde |
|---|---|
| SHA-256 | **3 millions** |
| PBKDF2-SHA256, 600 000 itérations | **12,6** (79 ms par essai) |
| scrypt (N = 2¹⁵, r = 8) | environ 14 (72 ms par essai) |

Un attaquant qui vole la base essaie des milliards de mots de passe probables ; avec une carte
graphique, SHA-256 se calcule à des milliards par seconde. Un algorithme **volontairement
lent et gourmand en mémoire** (Argon2id, recommandé ; scrypt ; bcrypt ; PBKDF2 avec beaucoup
d'itérations) rend l'attaque des centaines de milliers de fois plus coûteuse, pour un coût
négligeable à la connexion.

Le **sel** : une valeur aléatoire propre à chaque utilisateur, ajoutée avant le hachage. Réel :
le même mot de passe avec deux sels donne `b5b693952aaa4803…` et `0b496ef6cd77cb54…`. Sans
sel, deux utilisateurs avec le même mot de passe ont la même empreinte, et des tables
précalculées (*rainbow tables*) cassent tous les mots de passe courants d'un coup. Les
bibliothèques (Argon2, bcrypt) gèrent le sel automatiquement et le stockent dans le résultat.

Côté règles : une longueur minimale (12 caractères et plus), pas de règles de composition
absurdes qui produisent `Soleil2026!`, vérifier que le mot de passe n'apparaît pas dans des
fuites connues, et pas de changement forcé périodique sans raison (recommandations du NIST et
de l'ANSSI).

## MFA et passkeys

Un mot de passe seul fuit : réutilisé sur un autre site piraté, hameçonné, deviné. Le
**second facteur** (MFA) exige deux types de preuves parmi : ce que l'on **sait** (mot de
passe), ce que l'on **possède** (téléphone, clé de sécurité), ce que l'on **est** (biométrie).

| Second facteur | Résistance |
|---|---|
| SMS | faible : détournement de carte SIM, interception |
| Code TOTP (application d'authentification) | bonne, mais un faux site peut demander le code et l'utiliser aussitôt |
| Notification « Approuver ? » | moyenne : la « fatigue MFA » (l'attaquant envoie des dizaines de demandes jusqu'à ce que la victime accepte) |
| **Clé de sécurité / passkey** (FIDO2, WebAuthn) | **résiste à l'hameçonnage** |

Les **passkeys** remplacent le mot de passe par une paire de clés : la clé privée reste dans
l'appareil (protégée par l'empreinte ou le code de l'appareil), le site ne garde que la clé
publique. À la connexion, l'appareil signe un défi, **lié au nom de domaine** : sur un faux
site `banque-exemple.co`, la passkey de `banque.exemple.com` ne fonctionne tout simplement pas.
Plus rien à voler côté serveur, rien à hameçonner côté utilisateur.

## À retenir

- Confidentialité, intégrité, disponibilité ; raisonner en risques.
- Encodage ≠ hachage ≠ chiffrement ; MD5 et SHA-1 sont cassés.
- Mots de passe : Argon2id (ou scrypt, bcrypt, PBKDF2) avec sel ; jamais un hachage rapide (3 millions d'essais/s contre 12,6 mesurés).
- MFA ; les passkeys (FIDO2) résistent à l'hameçonnage, les SMS non.
