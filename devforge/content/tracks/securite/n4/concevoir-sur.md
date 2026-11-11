---
id: securite.n4.concevoir-sur
title: STRIDE, défense en profondeur, tests de sécurité et détection
skills: [modelisation-menaces, defense-profondeur, tests-securite, journalisation-securite]
duration_min: 20
---

# STRIDE, défense en profondeur, tests de sécurité et détection

## Modéliser les menaces

Corriger une faille de conception en production coûte cher ; la repérer sur un schéma coûte
une réunion. La **modélisation des menaces** répond à quatre questions (Adam Shostack) :

1. Que construisons-nous ? (un schéma des flux de données, avec les **frontières de
   confiance** : Internet, réseau interne, base, prestataire)
2. Qu'est-ce qui peut mal tourner ?
3. Que faisons-nous contre ?
4. Avons-nous bien travaillé ?

**STRIDE** (Microsoft) aide à répondre à la deuxième question, pour chaque élément du schéma :

| Menace | Propriété violée | Exemple | Contre-mesure type |
|---|---|---|---|
| **S**poofing (usurpation) | authentification | se faire passer pour un autre utilisateur ou service | MFA, TLS mutuel, signatures |
| **T**ampering (altération) | intégrité | modifier un montant en transit ou en base | signatures, chiffrement authentifié, contrôle d'accès |
| **R**epudiation (répudiation) | traçabilité | « ce n'est pas moi qui ai validé ce virement » | journaux d'audit protégés |
| **I**nformation disclosure (fuite) | confidentialité | messages d'erreur trop bavards, fuite de données | chiffrement, moindre privilège |
| **D**enial of service (déni de service) | disponibilité | saturer un endpoint coûteux | limitation de débit, quotas, élasticité |
| **E**levation of privilege (élévation) | autorisation | un utilisateur devient administrateur | autorisation côté serveur, isolation |

Le résultat est une liste de menaces priorisées, chacune avec une décision : corriger,
atténuer, accepter (en le disant) ou transférer (assurance, prestataire). On la refait à chaque
changement important d'architecture, pas une fois pour toutes.

## La défense en profondeur

Aucune protection n'est parfaite : on **empile des couches indépendantes**, pour qu'une faille
dans l'une soit arrêtée par une autre. Pour une injection SQL : requêtes paramétrées (code),
revue et analyse statique (processus), compte de base sans droits d'administration (moindre
privilège), données sensibles chiffrées (stockage), pare-feu applicatif (réseau), détection
des requêtes anormales (supervision).

Le **zero trust** pousse l'idée : le réseau interne n'est **pas** une zone de confiance. Chaque
requête est authentifiée et autorisée, quel que soit son point d'origine, en fonction de
l'identité (de l'utilisateur, du service, de l'appareil) et du contexte :

- authentification forte des utilisateurs (MFA, passkeys) et des **appareils** ;
- TLS mutuel et identités de charge de travail entre services (SPIFFE, service mesh) ;
- micro-segmentation : un service ne peut joindre que ceux dont il a besoin ;
- accès aux outils internes par un proxy d'identité plutôt qu'un VPN qui ouvre tout le réseau.

L'idée vient de l'expérience : la plupart des grandes intrusions commencent par **un** poste
ou **un** compte compromis, puis se déplacent latéralement dans un réseau interne trop permissif.

## Les tests de sécurité

| Type | Analyse | Trouve bien | Limites |
|---|---|---|---|
| **SAST** (statique) | le code source, sans l'exécuter | injections, fonctions dangereuses, secrets en dur | faux positifs ; ne voit pas les règles métier |
| **DAST** (dynamique) | l'application en marche, de l'extérieur (OWASP ZAP, Burp) | XSS, en-têtes manquants, configurations | couverture limitée aux pages explorées |
| **SCA** (composants) | les dépendances | vulnérabilités connues (niveau 3) | ne dit pas si le code vulnérable est atteint |
| **Fuzzing** | des entrées générées en masse | plantages, cas limites, fuites mémoire | demande un harnais par point d'entrée |
| **Test d'intrusion** | des humains qui attaquent | logique métier, enchaînements de failles | ponctuel, coûteux |

Exemple réel de SAST : `bandit` sur le code vulnérable du niveau 2.

```
[B608] Possible SQL injection vector through string-based query construction.   Severity: Medium  ligne 6
[B602] subprocess call with shell=True identified, security issue.              Severity: High    ligne 15
[B404] Consider possible security implications associated with the subprocess module.   Severity: Low
[B607] Starting a process with a partial executable path                        Severity: Low
```

Les deux vraies failles sont trouvées, avec deux alertes de faible valeur. Régler l'outil
(gravités, exceptions justifiées) est indispensable : une CI qui crie en permanence finit
ignorée.

Exemple réel de fuzzing : une fonction qui lit « 3 x clavier », bombardée de 100 000 entrées
aléatoires. Les `ValueError` (entrée refusée proprement) sont attendues ; le fuzzer signale un
plantage **inattendu** : `ZeroDivisionError` avec l'entrée `0`, trouvé dès le premier essai.
Sur de vrais parseurs (images, formats de fichiers, protocoles), le fuzzing guidé par la
couverture (libFuzzer, AFL++, Atheris, Jazzer, OSS-Fuzz) a trouvé des dizaines de milliers de
failles.

On place ces outils **dans la CI** (SAST, SCA, secrets à chaque pull request ; DAST sur
l'environnement de préproduction), et on garde les tests d'intrusion pour les jalons
importants.

## Journaliser et détecter

On ne peut pas tout empêcher ; il faut **voir** ce qui se passe. Les événements de sécurité à
journaliser :

- connexions réussies et **échouées**, changements de mot de passe et de MFA ;
- refus d'autorisation (un utilisateur qui essaie des identifiants de factures qui ne sont
  pas les siennes) ;
- actions sensibles : changement de rôle, export de données, suppression, modification d'un
  IBAN ;
- erreurs de validation en rafale (signe d'un scan automatique).

Avec **qui**, **quoi**, **quand**, **d'où** (IP, appareil) et un identifiant de requête. Mais
**jamais** de mot de passe, de jeton, de numéro de carte ou de donnée de santé dans les
journaux : ils deviennent eux-mêmes une fuite.

Les journaux partent vers un système central (un SIEM : Splunk, Elastic, Sentinel), où
l'attaquant ne peut pas les effacer, avec des **alertes** sur des scénarios précis : 50 échecs
de connexion sur des comptes différents depuis la même IP (*password spraying*), connexion
depuis un pays inhabituel suivie d'un export massif, création d'un compte administrateur hors
procédure. Le délai moyen de détection d'une intrusion se compte encore souvent en **mois** :
chaque jour gagné réduit les dégâts.

## À retenir

- Modéliser les menaces sur un schéma de flux avec ses frontières de confiance ; STRIDE pour chaque élément ; décider pour chaque menace.
- Défense en profondeur : des couches indépendantes ; zero trust : aucune confiance accordée au réseau, tout est authentifié et autorisé.
- SAST, DAST, SCA, fuzzing, tests d'intrusion : complémentaires ; dans la CI, réglés pour éviter le bruit (bandit : 2 vraies failles, 2 alertes faibles).
- Journaliser les événements de sécurité sans données sensibles, centraliser, alerter sur des scénarios précis.
