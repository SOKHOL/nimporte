---
id: memoire.n4.fuites-memoire
title: Les fuites de mémoire, malgré le GC
skills: [fuites-gc, references-faibles, heap-dump]
duration_min: 10
---

# Les fuites de mémoire, malgré le GC

« J'ai un ramasse-miettes, donc pas de fuite » : c'est faux. Avec un GC, une fuite
est un objet **encore accessible** dont on ne se servira plus jamais. Le GC ne peut pas
deviner tes intentions : s'il existe un chemin depuis une racine, l'objet reste.

## Les coupables habituels

| Cause | Pourquoi ça fuit |
|---|---|
| Collection statique qui grossit (cache maison, registre) | un champ statique est une racine : tout ce qu'il contient vit pour toujours |
| Écouteur jamais désabonné | le bus d'événements garde une référence vers l'écouteur, donc vers son objet englobant |
| Lambda ou classe interne qui capture `this` | une `Activity` Android capturée survit à sa destruction, avec toute sa vue |
| `ThreadLocal` jamais nettoyé dans un pool | les threads du pool vivent indéfiniment, leurs `ThreadLocal` aussi |
| Class loaders après un redéploiement à chaud | les anciennes classes restent chargées : fuite de *metaspace* |

```kotlin
object EventBus {
    private val abonnes = mutableListOf<(String) -> Unit>()
    fun abonner(abonne: (String) -> Unit) { abonnes += abonne }
}

class EcranProfil {
    private val image = ByteArray(5_000_000)

    fun ouvrir() {
        // La lambda capture `this` (elle appelle rafraichir) : chaque écran ouvert
        // reste accessible depuis EventBus, avec ses 5 Mo, pour toujours.
        EventBus.abonner { evenement -> rafraichir(evenement) }
    }

    private fun rafraichir(evenement: String) { /* … */ }
}
```

## Références faibles, douces et fantômes

Le JDK permet de référencer un objet **sans le garder en vie** :

| Type | Le GC peut libérer l'objet… | Usage |
|---|---|---|
| `WeakReference` | dès qu'il n'est plus accessible que par des références faibles | caches associés à un objet (`WeakHashMap`), écouteurs |
| `SoftReference` | seulement en cas de manque de mémoire | anciens caches ; aujourd'hui, préfère un cache borné |
| `PhantomReference` / `Cleaner` | après sa finalisation, pour libérer une ressource externe | remplacer `finalize()` (obsolète) |

Les références faibles ne remplacent pas un désabonnement explicite : la vraie
correction reste souvent de **libérer au bon moment** (`fermer()`, cycle de vie).

## Diagnostiquer une fuite

1. **Le symptôme** : la mémoire occupée **après chaque GC** augmente au fil des heures,
   jusqu'à l'`OutOfMemoryError`. Une courbe en dents de scie qui monte.
2. **Capturer le tas** : `jcmd <pid> GC.heap_dump /tmp/tas.hprof`, ou automatiquement
   avec `-XX:+HeapDumpOnOutOfMemoryError`.
3. **Analyser** avec Eclipse MAT ou IntelliJ :
   - l'**arbre des dominateurs** : quels objets retiennent le plus de mémoire ;
   - la taille **retenue** (*retained*) : ce qui serait libéré si cet objet disparaissait,
     à ne pas confondre avec la taille **propre** (*shallow*) de l'objet seul ;
   - le **chemin vers les racines** : la chaîne de références qui empêche la libération.
4. **Comparer deux captures** prises à quelques minutes d'intervalle : ce qui grossit est suspect.

Sur Android, **LeakCanary** détecte automatiquement les activités et fragments qui fuient.

## À retenir

- Avec un GC, une fuite est un objet **accessible mais inutile**.
- Méfie-toi de tout ce qui est statique, global ou à longue durée de vie et qui
  accumule des références.
- On ne devine pas une fuite : on **capture le tas** et on suit le chemin vers les racines.
