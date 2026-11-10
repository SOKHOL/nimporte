---
id: concurrence.n3.modele-memoire
title: Le modèle mémoire Java
skills: [jmm]
duration_min: 12
---

# Le modèle mémoire Java

Sans synchronisation, la question « que voit un thread quand un autre écrit ? » n'a pas
de réponse intuitive. Le **modèle mémoire Java** (*JMM*) donne la réponse officielle ;
Kotlin sur la JVM suit exactement les mêmes règles.

## Ce que le matériel et le compilateur se permettent

Pour aller vite, le processeur garde des valeurs dans ses caches et ses registres, et le
compilateur JIT **réordonne** les instructions, tant que le résultat reste le même
**pour un seul thread**.

```kotlin
// Variables partagées, sans aucune synchronisation
var x = 0
var y = 0

// Thread A :  x = 1;  r1 = y
// Thread B :  y = 1;  r2 = x
```

Intuitivement, au moins un des deux threads voit l'écriture de l'autre. Pourtant,
`r1 == 0` et `r2 == 0` **en même temps** est un résultat autorisé, et observé sur de
vraies machines : chaque thread peut voir sa lecture passer avant son écriture. L'outil
**jcstress** de l'OpenJDK sert justement à mettre en évidence ces comportements.

## La relation « arrive-avant »

Le JMM ne garantit la visibilité que le long d'une relation **arrive-avant**
(*happens-before*) : si A arrive-avant B, tout ce que A a écrit est visible par B.

| Règle | Garantie |
|---|---|
| Ordre du programme | dans un thread, chaque instruction arrive-avant la suivante |
| Verrou | relâcher un verrou arrive-avant toute prise ultérieure du **même** verrou |
| Volatile | une écriture volatile arrive-avant toute lecture ultérieure de la même variable |
| Démarrage | `thread.start()` arrive-avant tout ce que fait le nouveau thread |
| Fin | tout ce que fait un thread arrive-avant le retour de `join()` sur ce thread |
| Champs `val` | une fois le constructeur terminé, ses `val` sont visibles sans synchronisation (si `this` ne s'est pas échappé pendant la construction) |

La relation est **transitive**, et c'est ce qui permet de publier des données : écris
des données ordinaires, puis un drapeau `@Volatile` ; le thread qui voit le drapeau à
`true` voit aussi les données écrites avant lui.

Une **course de données** (*data race*), c'est deux accès à la même variable, dont au
moins une écriture, sans relation arrive-avant entre eux. Un programme qui en contient
peut se comporter de façon très surprenante.

## L'exemple célèbre : le double-checked locking

```kotlin
class Service private constructor() {
    val config: Map<String, String> = chargerConfig()

    companion object {
        private var instance: Service? = null      // ⚠️ il manque @Volatile

        fun get(): Service {
            if (instance == null) {                 // lecture sans verrou
                synchronized(this) {
                    if (instance == null) instance = Service()
                }
            }
            return instance!!
        }
    }
}
```

La première lecture, hors du verrou, n'a aucune relation arrive-avant avec l'écriture :
un thread peut voir une référence **non nulle vers un objet pas encore entièrement
construit**, par exemple avec `config` encore à `null`. Corrections, de la meilleure à la
moins bonne :

1. `object Service` : la JVM s'occupe de tout (initialisation de classe) ;
2. `val service by lazy { Service() }` : synchronisé par défaut ;
3. `@Volatile private var instance` avec le double test.

## La publication sûre

Un objet est partagé sans risque s'il passe par : un champ `@Volatile` ou atomique, un
champ protégé par un verrou, une collection concurrente, l'initialisation d'un `object`,
ou s'il est **immuable** (que des `val` vers des objets eux-mêmes immuables). C'est une des
raisons pour lesquelles les experts aiment tant l'immutabilité.

## À retenir

- Sans relation arrive-avant : aucune garantie, ni de visibilité ni d'ordre.
- Verrous, volatile, `start`/`join`, `val` : les sources de relations arrive-avant.
- Double-checked locking : `@Volatile` obligatoire, ou mieux, `object` et `by lazy`.
