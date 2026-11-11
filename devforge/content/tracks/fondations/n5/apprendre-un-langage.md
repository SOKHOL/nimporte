---
id: fondations.n5.apprendre-un-langage
title: Paradigmes et nouveaux langages
skills: [paradigmes, comparer-langages, apprendre-langage]
duration_min: 12
---

# Paradigmes et nouveaux langages

Un expert apprend un nouveau langage en quelques jours, parce qu'il ne réapprend pas à
programmer : il cherche comment ce langage répond à des questions qu'il connaît déjà.

## Les paradigmes

| Paradigme | Idée | Langages typiques |
|---|---|---|
| Impératif | une suite d'instructions qui modifient l'état | C, Go |
| Orienté objet | des objets qui protègent leur état et collaborent | Java, Kotlin, C# |
| Fonctionnel | des fonctions pures et des données immuables | Haskell, Elixir, F# |
| Déclaratif | décrire le résultat voulu, pas comment l'obtenir | SQL, HTML, Compose |
| Logique | des faits et des règles, la machine cherche les solutions | Prolog |

Les langages modernes sont **multi-paradigmes** : Kotlin mélange objet, fonctionnel et
déclaratif (Compose).

## Cinq langages, cinq philosophies

| | Kotlin | Python | Go | Rust | TypeScript |
|---|---|---|---|---|---|
| Typage | statique, inféré | dynamique (annotations facultatives) | statique, simple | statique, très expressif | statique, par-dessus JavaScript |
| Mémoire | ramasse-miettes (JVM) | comptage de références + détecteur de cycles | ramasse-miettes | possession, sans GC | ramasse-miettes |
| Erreurs | exceptions, `Result` | exceptions | valeurs d'erreur retournées (`val, err :=`) | type `Result<T, E>` | exceptions |
| Concurrence | coroutines | asyncio, GIL (limité au calcul) | goroutines et canaux | threads, async, sûreté vérifiée | boucle d'événements, async/await |
| Forces | Android, serveur, multiplateforme | données, IA, scripts | services réseau, outils | systèmes, performance, sûreté | web, front et back |

Exemple de différence qui surprend : les entiers.

```kotlin
println(Int.MAX_VALUE + 1)    // -2147483648 : débordement silencieux
```

```python
print(2**63)     # 9223372036854775808
print(2**100)    # 1267650600228229401496703205376 : entiers sans limite
print(-7 // 2)   # -4 : division entière arrondie vers le bas (Kotlin donne -3)
```

Sorties réelles. Python agrandit ses entiers automatiquement ; Kotlin, comme Java et
Go, utilise des entiers de taille fixe, rapides mais limités.

## Apprendre un langage en une semaine

Les questions à se poser, dans l'ordre :

1. **Les bases** : types, variables, conditions, boucles, fonctions. Une soirée.
2. **Les données** : collections, chaînes, structures. Comment représente-t-on « rien »
   (`null`, `None`, `Option`) ?
3. **Les erreurs** : exceptions, valeurs de retour, `Result` ?
4. **La mémoire** : ramasse-miettes, comptage, possession ? Valeurs ou références ?
5. **La concurrence** : threads, coroutines, boucle d'événements, canaux ?
6. **L'écosystème** : gestionnaire de paquets, outil de build, tests, formatage, linter.
7. **Les idiomes** : lis du code **réel** de la bibliothèque standard et de projets
   reconnus ; fais les exercices d'Exercism dans ce langage.

Puis écris un petit projet complet (un outil en ligne de commande, une API) : c'est là
qu'on découvre les vraies difficultés.

## À retenir

- Impératif, objet, fonctionnel, déclaratif, logique : des façons de penser, souvent combinées.
- Comparer les langages sur les mêmes axes : typage, mémoire, erreurs, concurrence, écosystème.
- Un nouveau langage s'apprend en répondant à ces questions, puis en lisant du vrai code.
