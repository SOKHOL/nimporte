---
id: debogage.n5.les-cas-difficiles
title: Systèmes distribués, sanitizers et code des autres
skills: [debogage-distribue, sanitizers, code-des-autres]
duration_min: 18
---

# Systèmes distribués, sanitizers et code des autres

## Déboguer un système distribué

Dans un système de microservices, le bug n'est souvent dans **aucun** service pris isolément :
il naît de leurs **interactions** (un délai trop court ici, un réessai là, des données dans un
état inattendu ailleurs). Les outils changent :

- **L'identifiant de corrélation** (identifiant de trace) présent dans **chaque** journal de
  chaque service : on reconstitue l'histoire d'une requête précise en filtrant sur lui.
- **Les traces distribuées** (parcours SRE, niveau 3) : où le temps a été passé, quel appel a
  échoué, avec quels attributs.
- **La chronologie** : aligner les événements de plusieurs services. Attention aux horloges, qui
  ne sont jamais parfaitement synchronisées entre machines (parcours System design, niveau 5) :
  un écart de quelques millisecondes peut inverser l'ordre apparent de deux événements.
- **Les données**, autant que le code : un message en double, un événement arrivé avant celui
  dont il dépend, une réplique en retard (99 % de lectures périmées mesurées au parcours System
  design) expliquent beaucoup de « bugs impossibles ».

Questions à se poser systématiquement : cette opération peut-elle être exécutée **deux fois**
(réessai, message relivré) ? Dans **le désordre** ? **Partiellement** (une étape réussie, la
suivante non) ? Avec des données **périmées** ? Et ne pas oublier les **changements récents**
autour du service : un déploiement d'un autre service, une modification de configuration, un
certificat, une mise à jour de bibliothèque, une hausse de trafic.

## Les bugs mémoire et les sanitizers

En C et C++ (et dans le code natif appelé depuis Java, Python ou Android), un accès mémoire
invalide ne plante **pas forcément** : il peut lire ou écrire **silencieusement** n'importe
quoi, et le symptôme apparaît bien plus loin, ou jamais en test. Démonstration réelle : lire un
entier après l'avoir libéré.

```c
int *solde = malloc(sizeof(int));
*solde = 100;
free(solde);
printf("solde : %d\n", *solde);      // affiche réellement : solde : 1456454365
```

Pas de plantage, une valeur absurde. Les **sanitizers** et outils dynamiques détectent ces
erreurs **au moment où elles se produisent**.

**Valgrind** (sans recompiler, mais 20 à 50 fois plus lent) :

```
Invalid read of size 4
   at 0x1091BD: main (apres_free.c:7)
 Address 0x4a7b040 is 0 bytes inside a block of size 4 free'd
   at 0x484988F: free (…)
```

**AddressSanitizer** (`-fsanitize=address`, environ 2 fois plus lent seulement) sur une copie
de 13 octets dans un bloc de 8 :

```
ERROR: AddressSanitizer: heap-buffer-overflow on address 0x502000000018
WRITE of size 13 at 0x502000000018 thread T0
    #1 0x5650402b023d in main debordement.c:5
0x502000000018 is located 0 bytes after 8-byte region [0x502000000010,0x502000000018)
allocated by thread T0 here:
    #0 … in malloc
```

Le rapport donne la ligne fautive, la taille de l'accès, et **où le bloc a été alloué** (et
libéré, pour un use-after-free). Les autres sanitizers : **UBSan** (comportements indéfinis :
débordement d'entier signé, décalage invalide), **ThreadSanitizer** (courses entre threads),
**MemorySanitizer** (lecture de mémoire non initialisée). On les active dans la CI et pendant le
fuzzing (parcours Sécurité, niveau 4) : c'est ainsi qu'OSS-Fuzz trouve des milliers de failles
dans les projets libres.

Les langages à gestion automatique de la mémoire (Java, Kotlin, Go, Python) et Rust (par son
système de possession) éliminent la plupart de ces bugs par construction, ce qui explique
l'importance croissante de Rust pour le code système (parcours Mémoire).

## Déboguer le code des autres

Le bug est parfois dans une **bibliothèque**, un framework, le système d'exploitation ou un
service tiers. Avant de le conclure (c'est rarement le cas), et quand c'est le cas :

1. **Vérifier ses hypothèses** : la bonne version de la bibliothèque est-elle vraiment chargée ?
   (`./gradlew dependencies`, `npm ls`, `pip show`) ; deux versions en conflit dans le
   classpath ; une configuration par défaut différente de ce qu'on croit.
2. **Lire le code de la bibliothèque** : l'IDE télécharge les sources ; on peut y poser des
   points d'arrêt comme dans son propre code. Le comportement réel est dans le code, pas dans la
   documentation.
3. **Chercher dans les issues et le journal des modifications** (*changelog*) du projet : le bug
   est souvent connu, corrigé dans une version plus récente, ou documenté avec un contournement.
4. **Réduire** jusqu'à un exemple minimal, indépendant de ton projet (niveau 3).
5. **Signaler** (issue) avec l'exemple minimal, les versions exactes, le comportement attendu et
   observé ; idéalement **proposer la correction** (pull request). C'est aussi comme ça qu'on
   apprend le plus.
6. En attendant : un **contournement** isolé et commenté (avec le lien vers l'issue), et un test
   qui échouera le jour où la bibliothèque sera corrigée, pour penser à retirer le contournement.

## À retenir

- Distribué : identifiant de corrélation partout, traces, chronologie (attention aux horloges), et les questions « deux fois, dans le désordre, partiellement, avec des données périmées ? ».
- Bugs mémoire : silencieux sans outil (une valeur absurde, pas de plantage) ; valgrind, ASan (ligne, taille, où le bloc a été alloué), UBSan, TSan, MSan dans la CI et le fuzzing.
- Code des autres : vérifier les versions, lire les sources, chercher les issues, réduire, signaler avec un exemple minimal, contourner proprement.
