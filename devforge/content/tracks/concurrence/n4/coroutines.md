---
id: concurrence.n4.coroutines
title: Les coroutines Kotlin
skills: [coroutines, concurrence-structuree]
duration_min: 14
---

# Les coroutines Kotlin

Une coroutine est un calcul **suspendable** : quand elle attend (réseau, délai), elle
libère son thread au lieu de le bloquer, puis reprend plus tard, éventuellement sur un
autre thread. On peut en lancer des centaines de milliers là où des threads
s'effondreraient.

## Suspendre n'est pas bloquer

```kotlin
suspend fun chargerProfil(id: String): Profil {
    val utilisateur = api.utilisateur(id)   // suspend : le thread fait autre chose en attendant
    val stats = api.statistiques(id)
    return Profil(utilisateur, stats)
}
```

Le mot-clé `suspend` indique qu'une fonction peut se mettre en pause. Le compilateur la
transforme en machine à états : le code reste lisible, comme du code séquentiel.
`delay()` suspend ; `Thread.sleep()` **bloque** le thread et n'a rien à faire dans une
coroutine.

## Lancer des coroutines

```kotlin
fun main() = runBlocking {
    launch {
        delay(100)
        println("B")
    }
    launch { println("A") }
    println("C")
}
```

Sortie réelle : `C`, `A`, `B`. `launch` **planifie** une coroutine et rend la main tout
de suite : `main` affiche `C` ; la première coroutine démarre et se suspend sur `delay` ;
la seconde affiche `A` ; 100 ms plus tard, la première affiche `B`. Le tout sur **un
seul thread**.

| Outil | Rôle |
|---|---|
| `launch` | lancer un travail sans attendre de résultat (renvoie un `Job`) |
| `async` / `await` | lancer un calcul et récupérer son résultat plus tard |
| `coroutineScope { }` | attendre toutes les coroutines lancées à l'intérieur |
| `withContext(Dispatchers.IO) { }` | exécuter un bloc sur un autre pool, puis revenir |
| `runBlocking` | pont avec le monde bloquant (`main`, tests) ; jamais dans une app Android |

Deux `async` de 300 ms chacun, attendus ensemble, prennent environ 300 ms, pas 600 :
vérifié pour ce cours.

## Les dispatchers

| Dispatcher | Pour |
|---|---|
| `Dispatchers.Default` | le calcul (autant de threads que de cœurs) |
| `Dispatchers.IO` | les appels bloquants : fichiers, JDBC, anciennes bibliothèques (64 threads par défaut) |
| `Dispatchers.Main` | l'interface graphique (Android) |

## La concurrence structurée

Toute coroutine vit dans une **portée** (*scope*) : `viewModelScope`, `lifecycleScope`,
ou `coroutineScope { }`. Trois règles :

1. Un parent **attend** tous ses enfants.
2. Si un enfant **échoue**, le parent annule les autres enfants et propage l'erreur.
3. Annuler le parent **annule** tous ses enfants.

```kotlin
try {
    coroutineScope {
        launch {
            delay(100)
            error("panne réseau")
        }
        launch {
            try {
                delay(1_000)
                println("téléchargement fini")
            } finally {
                println("téléchargement annulé")
            }
        }
    }
} catch (e: IllegalStateException) {
    println("Erreur : ${e.message}")
}
```

Sortie réelle : `téléchargement annulé`, puis `Erreur : panne réseau`. Aucune coroutine
orpheline ne continue en arrière-plan. Sur Android, quand l'écran est fermé,
`viewModelScope` est annulé et tout ce qu'il a lancé s'arrête.

## L'annulation est coopérative

Annuler, c'est demander poliment : la coroutine s'arrête au prochain point de suspension
(`delay`, `withContext`, `yield`…), qui lève une `CancellationException`. Le piège classique :

```kotlin
suspend fun synchroniser() {
    while (true) {
        try {
            envoyerDonnees()
            delay(5_000)
        } catch (e: Exception) {      // ⚠️ attrape aussi CancellationException
            journaliser(e)
        }
    }
}
```

La `CancellationException` est avalée et la boucle continue ; chaque `delay` suivant
relève aussitôt l'exception, et la coroutine tourne à vide au lieu de s'arrêter. Dans la
démonstration préparée pour ce cours, elle a fait plus de 1 000 tours en quelques
millisecondes après l'annulation, et n'aurait jamais fini sans garde-fou. Une fois
corrigée (en relançant la `CancellationException`), elle s'arrête proprement après 3 tours.

Règle : n'avale jamais une `CancellationException`. Attrape des exceptions précises, ou
relance-la.

## À retenir

- Suspendre libère le thread : `delay`, jamais `Thread.sleep`.
- `launch` (sans résultat), `async` (avec résultat), `withContext` (changer de pool).
- Concurrence structurée : le parent attend, un échec annule les frères, l'annulation descend.
- L'annulation est coopérative : ne jamais avaler `CancellationException`.
