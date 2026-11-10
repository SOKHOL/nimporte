---
id: jvm.n2.stack-traces
title: Lire une stack trace
skills: [exceptions-jvm]
duration_min: 6
---

# Lire une stack trace

Une stack trace est la photo de la pile d'appels au moment où une exception a été
créée. La lire vite et bien fait gagner des heures.

```kotlin
class ConfigException(message: String, cause: Throwable) : RuntimeException(message, cause)

fun lirePort(texte: String): Int = texte.toInt()

fun chargerConfig(): Int = try {
    lirePort("80a")
} catch (e: NumberFormatException) {
    throw ConfigException("Configuration invalide", e)
}

fun main() {
    chargerConfig()
}
```

La trace réelle (légèrement raccourcie) :

```
Exception in thread "main" ConfigException: Configuration invalide
	at StacktraceKt.chargerConfig(stacktrace.kt:8)
	at StacktraceKt.main(stacktrace.kt:12)
Caused by: java.lang.NumberFormatException: For input string: "80a"
	at java.base/java.lang.Integer.parseInt(Integer.java:662)
	at StacktraceKt.lirePort(stacktrace.kt:3)
	at StacktraceKt.chargerConfig(stacktrace.kt:6)
	... 2 more
```

## La méthode

1. La première ligne donne le type et le message de l'exception **visible**.
2. **Descends jusqu'au dernier `Caused by:`** : c'est la cause racine. Ici, `"80a"` n'est
   pas un nombre.
3. Dans ce bloc, cherche la première ligne qui vient de **ton** code (`lirePort`,
   ligne 3), en sautant celles du JDK et des frameworks.
4. `... 2 more` : les lignes suivantes sont identiques à celles de l'exception
   englobante, elles sont omises.

## Les bonnes pratiques

- **Chaîne toujours la cause** (`throw MonException("…", e)`) : sinon la cause racine est
  perdue pour toujours.
- N'avale jamais une exception (`catch (e: Exception) {}`) sans au moins la journaliser.
- Les exceptions coûtent cher : leur création capture toute la pile d'appels. Ne t'en
  sers pas pour le déroulement normal du programme (« fin de liste », « pas trouvé »).
- Un `NoClassDefFoundError` cache souvent un `ExceptionInInitializerError` plus ancien :
  remonte les logs jusqu'à la toute première erreur.
