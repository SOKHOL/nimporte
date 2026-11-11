---
id: clean-code.n5.une-culture-de-qualite
title: Tests de mutation, architecture testable, stratégie de tests et revue de code
skills: [mutation, architecture-testable, strategie-tests, revue-de-code]
duration_min: 20
---

# Tests de mutation, architecture testable, stratégie de tests et revue de code

## Le test de mutation

La couverture dit quelles lignes sont exécutées ; le **test de mutation** dit si les tests
**remarqueraient** un bug. L'outil crée des **mutants** (des copies du code avec un petit
changement : `>=` devient `>`, `+` devient `-`, une condition devient `true`, un `return`
renvoie une constante) et relance les tests pour chacun :

- si un test échoue, le mutant est **tué** : les tests protègent ce comportement ;
- si tout passe, le mutant **survit** : un bug de ce genre passerait inaperçu.

Démonstration réelle sur la fonction `remise` (5 % dès 100 €), avec 7 mutants :

| Tests | Mutants survivants | Score de mutation |
|---|---|---|
| `remise(200) == 190` et `remise(50) == 50` (couverture 100 %) | `> 100` et `>= 101` | **5/7 = 71 %** |
| + `remise(100) == 95` et `remise(99.99) == 99.99` | aucun | **7/7 = 100 %** |

Les survivants montrent exactement le test manquant : la **limite**. (Anecdote réelle : la
première exécution de cet outil maison donnait un score faux, à cause du cache de bytecode
de Python qui réutilisait l'ancienne version d'un fichier modifié dans la même seconde. Les
vrais outils gèrent ce genre de piège.)

Les outils : **PIT** (Java, Kotlin), **Stryker** (JavaScript, C#, Scala), **mutmut** et
**cosmic-ray** (Python), **cargo-mutants** (Rust). Le coût : relancer les tests pour chaque
mutant, donc on le réserve au **code critique** (calculs financiers, règles métier, sécurité),
on le lance seulement sur le code modifié, ou la nuit. Certains mutants sont **équivalents**
(le changement ne modifie pas le comportement) : ils survivent sans que ce soit un défaut des
tests.

## Une architecture testable

Le code difficile à tester est presque toujours du code **mal découpé** : la logique est
mélangée aux entrées-sorties (base, réseau, fichiers, horloge, aléa).

Le principe du **cœur fonctionnel, coquille impérative** (*functional core, imperative shell*,
Gary Bernhardt) :

- le **cœur** contient toutes les décisions, sous forme de fonctions **pures** : mêmes
  entrées, mêmes sorties, aucun effet de bord. Il se teste avec des milliers de cas, en
  millisecondes, sans aucune doublure ;
- la **coquille**, aussi mince que possible, lit les données, appelle le cœur, et applique
  ses décisions (écrire en base, envoyer l'e-mail). Elle se teste avec quelques tests
  d'intégration.

```kotlin
// Cœur : pur, testable sans rien simuler
fun decisionsDeRelance(factures: List<Facture>, aujourdhui: LocalDate): List<Relance> =
    factures.filter { it.estImpayee && it.echeance.plusDays(30) <= aujourdhui }
            .map { Relance(it.client, it.numero, niveau = niveauPour(it, aujourdhui)) }

// Coquille : les entrées-sorties
fun relancerLesImpayes() {
    val relances = decisionsDeRelance(factures.toutes(), LocalDate.now(horloge))
    relances.forEach { emails.envoyer(it) }
}
```

C'est la même idée que l'architecture hexagonale (parcours Architecture) et que le réducteur
MVI : la logique au centre, sans dépendance ; la technique autour. Autres leviers : injecter
les dépendances par le constructeur, injecter le **temps** et l'**aléa**, éviter les
singletons et l'état global.

## La stratégie de tests d'une équipe

Une stratégie répond à : **quels risques** veut-on couvrir, **à quel niveau**, et **quand** ?

- **Où mettre l'effort** : la logique métier critique (unitaires, propriétés, mutation), les
  intégrations (base, files, API externes : tests d'intégration et de contrat), les parcours
  qui rapportent de l'argent (quelques tests de bout en bout).
- **Quand** : à chaque modification, des tests rapides (moins de 10 minutes de CI) ; avant le
  déploiement, les tests plus lents ; après le déploiement, des **tests en production** :
  sondes synthétiques qui rejouent un parcours toutes les minutes, déploiement canary, et
  supervision des erreurs (parcours SRE).
- **Qui** : les développeurs écrivent les tests de leur code ; les testeurs, quand l'équipe en
  a, explorent, conçoivent les cas difficiles et outillent, plutôt que de rejouer à la main
  des scénarios automatisables.
- **Mesurer** : la durée de la CI, le taux de tests instables, les bugs trouvés en production
  qu'un test aurait dû attraper (chacun devient un test de non-régression).

Ce qu'on évite : viser un pourcentage de couverture, laisser une suite de tests lente que
personne ne lance, et « on testera à la fin » (la fin n'arrive jamais).

## La revue de code

La **revue de code** (*code review*) a plusieurs objectifs, par ordre d'importance : partager
la connaissance (au moins deux personnes connaissent chaque partie), trouver les problèmes de
conception et de compréhension, puis les bugs. Le style (indentation, imports, nommage
mécanique) est l'affaire des **outils automatiques** (formateurs, linters), pas des humains.

Pour l'auteur :

- des **petites** pull requests (quelques centaines de lignes au plus) : au-delà, l'attention
  s'effondre et la revue devient « LGTM » ;
- une **description** qui dit le pourquoi, ce qui a été testé, et où regarder en priorité ;
- séparer refactoring et changement de comportement (niveau 2).

Pour le relecteur :

- relire **vite** (dans la journée) : une revue qui attend trois jours ralentit toute l'équipe ;
- chercher d'abord : est-ce la bonne approche ? est-ce compréhensible ? les cas limites et les
  erreurs sont-ils gérés ? y a-t-il des tests ? la sécurité (entrées, droits, secrets) ?
- commenter le **code**, pas la personne ; poser des questions (« que se passe-t-il si la
  liste est vide ? ») plutôt qu'affirmer ; distinguer le bloquant du facultatif (« détail : »,
  « suggestion : ») ;
- savoir **approuver** : la perfection n'est pas le critère, l'amélioration de l'existant l'est.

## À retenir

- Test de mutation : des mutants tués ou survivants ; démontré : 71 % puis 100 % en ajoutant les tests de limite ; à réserver au code critique.
- Architecture testable : cœur fonctionnel pur, coquille d'entrées-sorties mince ; injecter dépendances, temps et aléa.
- Stratégie : couvrir les risques au bon niveau, CI rapide, tests en production, chaque bug devient un test.
- Revue de code : petites PR, relecture rapide, conception et compréhension avant le style (automatisé), bienveillance et précision.
