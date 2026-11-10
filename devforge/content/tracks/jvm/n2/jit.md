---
id: jvm.n2.jit
title: Le compilateur JIT
skills: [jit]
duration_min: 10
---

# Le compilateur JIT

La JVM commence par **interpréter** le bytecode, puis compile en code machine les
méthodes qui comptent : c'est la compilation *à la volée* (*Just-In-Time*).

## La compilation par paliers

| Palier | Qui | Quand |
|---|---|---|
| 0 | l'interpréteur | au début, pour tout le code |
| 1 à 3 | **C1**, compilateur rapide | la méthode devient « chaude » ; le palier 3 collecte aussi un profil |
| 4 | **C2**, compilateur optimisant | la méthode est très chaude : C2 l'optimise grâce au profil |

Voici la trace réelle (`java -XX:+PrintCompilation`) d'un programme qui appelle
50 millions de fois `fun carre(x: Int) = x * x` :

```
30   10       3       ChaudKt::carre (4 bytes)
30   11       4       ChaudKt::carre (4 bytes)
31   10       3       ChaudKt::carre (4 bytes)   made not entrant
31   12 %     3       ChaudKt::main @ 4 (36 bytes)
32   14 %     4       ChaudKt::main @ 4 (36 bytes)
```

À 30 ms, `carre` est compilée par C1 (palier 3), presque aussitôt par C2 (palier 4), et
la version C1 est abandonnée (*made not entrant*). Le `%` signale une compilation
**OSR** (*on-stack replacement*) : la boucle de `main`, qui tourne encore, est remplacée
en plein vol par sa version compilée.

## Les optimisations de C2

- **Inlining** : le corps des petites méthodes est recopié à l'endroit de l'appel.
  `carre` disparaît, il ne reste qu'une multiplication. C'est l'optimisation mère, qui
  rend possibles toutes les autres.
- **Escape analysis** : un objet qui ne sort pas de la méthode n'est pas alloué sur le
  tas ; ses champs deviennent de simples variables locales.
- **Élimination du code mort** : un calcul dont le résultat n'est jamais utilisé est supprimé.
- **Optimisations spéculatives** : si le profil montre qu'un appel d'interface n'a vu
  qu'une seule implémentation, C2 parie dessus. Si le pari devient faux (une nouvelle
  classe apparaît), le code est **désoptimisé** : retour à l'interpréteur, puis
  nouvelle compilation.

## Ce que ça change pour toi

- **Le temps de chauffe** (*warm-up*) : une application JVM est plus lente pendant ses
  premières secondes ou minutes. Juste après un déploiement, les premières requêtes
  sont lentes : prévois-le (préchauffage, sondes de disponibilité, déploiement progressif).
- **Les microbenchmarks faits main mentent** : sans préchauffage, on mesure
  l'interpréteur ; si le résultat n'est pas utilisé, le JIT supprime le calcul et on
  mesure… une boucle vide. Utilise **JMH**, l'outil de mesure de l'OpenJDK, qui évite
  ces pièges.
- **Écris du code simple** : le JIT optimise très bien les cas courants (petites
  méthodes, appels toujours vers la même classe). Les micro-optimisations faites à la
  main sont souvent inutiles, parfois contre-productives.

## À retenir

- Interpréteur → C1 → C2, guidés par le profil d'exécution.
- Inlining, escape analysis, code mort, spéculation et désoptimisation.
- Le temps de chauffe est réel ; on mesure avec JMH, pas avec `System.currentTimeMillis()`.
