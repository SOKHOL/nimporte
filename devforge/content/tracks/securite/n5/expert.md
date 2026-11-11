---
id: securite.n5.expert
title: Réponse à incident, test d'intrusion, RGPD et facteur humain
skills: [reponse-incident, pentest, vie-privee, facteur-humain]
duration_min: 20
---

# Réponse à incident, test d'intrusion, RGPD et facteur humain

## La réponse à incident de sécurité

Le jour où une intrusion est découverte, l'improvisation coûte cher. Les étapes classiques
(NIST, SANS) :

1. **Préparer** (avant l'incident) : qui décide, qui prévenir (direction, juridique,
   délégué à la protection des données, assureur), des journaux centralisés et conservés,
   des sauvegardes **hors ligne et testées**, des contacts d'urgence, et des exercices.
2. **Détecter et qualifier** : est-ce un incident ? quelle gravité ? quelles données et quels
   systèmes ?
3. **Contenir** : isoler les machines touchées (sans les éteindre : la mémoire contient des
   preuves), révoquer les identifiants et jetons compromis, bloquer les accès de l'attaquant.
4. **Éradiquer** : supprimer les accès laissés (comptes créés, portes dérobées, clés SSH
   ajoutées), corriger la faille d'entrée.
5. **Rétablir** : reconstruire depuis des sources sûres plutôt que « nettoyer » une machine
   compromise, restaurer, surveiller de près.
6. **Tirer les leçons** : un post-mortem sans reproche, et des actions suivies.

Pendant tout l'incident : un **journal de bord** horodaté (qui a fait quoi, quand), la
**préservation des preuves** (copie des disques et de la mémoire, journaux), et une
communication maîtrisée. Les erreurs classiques : effacer les traces en voulant réparer vite,
prévenir l'attaquant (qui lit peut-être les e-mails internes), et redémarrer en production
avec la même faille.

Face à un **rançongiciel**, la protection décisive se prépare avant : des sauvegardes que
l'attaquant ne peut ni chiffrer ni effacer (hors ligne, immuables), et des restaurations
testées. Payer la rançon ne garantit ni la récupération des données ni qu'elles ne soient pas
publiées.

## Le test d'intrusion éthique

Un **test d'intrusion** (*pentest*) attaque un système **avec l'accord écrit** de son
propriétaire, pour trouver les failles avant les attaquants. Sans autorisation, les mêmes
gestes sont un délit (en France, articles 323-1 et suivants du Code pénal).

Le cadre :

- une **convention** signée : périmètre précis (domaines, IP, applications), dates, techniques
  permises ou interdites (déni de service, ingénierie sociale), contacts d'urgence ;
- les étapes : **reconnaissance** (ce qui est exposé), **analyse** des vulnérabilités,
  **exploitation** contrôlée pour prouver l'impact, **post-exploitation** limitée (jusqu'où
  peut-on aller depuis ce point ?), puis le **rapport** : chaque faille avec sa gravité, sa
  preuve, et une recommandation de correction ;
- le **red team** simule un attaquant réel sur toute l'organisation (technique, physique,
  humaine), face à une équipe de défense (*blue team*) qui ne sait pas toujours quand.

Pour apprendre légalement : des plateformes d'entraînement (Root Me, HackTheBox, TryHackMe,
PortSwigger Web Security Academy), des applications volontairement vulnérables (OWASP Juice
Shop, DVWA), et les programmes de **bug bounty** (YesWeHack, HackerOne), qui définissent un
périmètre autorisé et récompensent les découvertes.

Pour une entreprise : publier une **politique de divulgation** (un fichier `security.txt` et
une adresse dédiée) pour que les chercheurs qui trouvent une faille sachent à qui la signaler.

## Le RGPD et la protection de la vie privée

Le **RGPD** (Règlement général sur la protection des données, 2018) s'applique à toute donnée
**personnelle** (qui permet d'identifier directement ou indirectement une personne) de
résidents européens.

Ses principes, qui deviennent des décisions techniques :

| Principe | En pratique |
|---|---|
| **Licéité** et transparence | une base légale (consentement, contrat, obligation légale, intérêt légitime…) et une information claire |
| **Limitation des finalités** | des données collectées pour un usage précis ne servent pas à autre chose sans base légale |
| **Minimisation** | ne collecter que le nécessaire (faut-il vraiment la date de naissance ?) |
| **Exactitude** | permettre la correction |
| **Limitation de la conservation** | une durée définie, puis suppression ou anonymisation **automatiques** |
| **Sécurité** | chiffrement, contrôle d'accès, pseudonymisation |

Les droits des personnes demandent un outillage : **accès** (exporter toutes ses données),
**rectification**, **effacement** (y compris dans les copies, caches et index de recherche),
**portabilité**, **opposition**. Une **violation** de données présentant un risque doit être
notifiée à l'autorité (la CNIL en France) **dans les 72 heures** après en avoir pris
connaissance, et aux personnes concernées si le risque est élevé. Les sanctions vont jusqu'à
4 % du chiffre d'affaires mondial.

La **protection des données dès la conception** (*privacy by design*) en découle :
pseudonymiser les identifiants dans les entrepôts analytiques, ne pas envoyer de données
personnelles dans les journaux ou aux outils tiers sans nécessité, chiffrer les champs
sensibles, prévoir les suppressions dès le modèle de données. Attention : **pseudonymiser**
(remplacer le nom par un identifiant) n'est pas **anonymiser** ; des données pseudonymisées
restent personnelles, et le recoupement de quelques attributs (code postal, date de naissance,
sexe) suffit souvent à réidentifier quelqu'un.

## L'ingénierie sociale et le facteur humain

Une grande partie des intrusions commence par **une personne** trompée, pas par une faille
technique :

- **hameçonnage** (*phishing*) : un faux e-mail, un faux site de connexion ;
- **fraude au président** (*business email compromise*) : un faux dirigeant demande un
  virement urgent et confidentiel ;
- **faux support** : « je suis du service informatique, donnez-moi le code reçu par SMS » ;
- **fatigue MFA** : des dizaines de demandes d'approbation jusqu'à ce que la victime accepte ;
- **vishing** et désormais **voix clonées** par IA, au téléphone.

Les défenses sont d'abord **techniques**, pour ne pas reposer sur la vigilance de chacun à
chaque instant : passkeys résistantes à l'hameçonnage, MFA avec correspondance de nombre,
filtrage et marquage des e-mails externes (SPF, DKIM, DMARC), double validation **par un autre
canal** pour tout changement d'IBAN ou virement inhabituel.

Ensuite, la **culture** : former avec des exemples réels, rendre le signalement facile et
**jamais** puni (quelqu'un qui a cliqué et le dit tout de suite est un allié précieux), et des
procédures qui autorisent à dire non à « l'urgence » d'un dirigeant.

## À retenir

- Incident : préparer, détecter, contenir, éradiquer, rétablir, apprendre ; journal de bord, preuves préservées, sauvegardes hors ligne testées.
- Test d'intrusion : toujours avec une autorisation écrite et un périmètre ; s'entraîner sur des plateformes prévues pour ; publier un `security.txt`.
- RGPD : base légale, finalité, minimisation, durée de conservation, droits outillés, notification en 72 h ; pseudonymiser n'est pas anonymiser.
- Facteur humain : défenses techniques d'abord (passkeys, DMARC, double validation), puis une culture où signaler est encouragé.
