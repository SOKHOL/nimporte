---
id: metaprog.n3.aop
title: La programmation orientée aspect
skills: [aop-concepts, tissage, spring-aop, transactional]
duration_min: 12
---

# La programmation orientée aspect (AOP)

L'AOP généralise ce qu'on a fait à la main avec les proxies : décrire **une fois** un
comportement transverse et **où** l'appliquer, sans toucher au code métier.

## Le vocabulaire

| Terme | Anglais | Sens |
|---|---|---|
| **Aspect** | aspect | le module qui regroupe une préoccupation transverse (journalisation, transactions…) |
| **Point de jonction** | join point | un point de l'exécution où l'on peut intervenir, typiquement l'exécution d'une méthode |
| **Coupe** | pointcut | l'expression qui sélectionne des points de jonction |
| **Greffon** | advice | le code exécuté : avant (`@Before`), après (`@After`, `@AfterReturning`, `@AfterThrowing`) ou autour (`@Around`) |
| **Tissage** | weaving | l'opération qui insère les greffons dans le programme |

## Un aspect complet

```kotlin
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Chronometre

@Aspect
class ChronometreAspect {
    @Around("@annotation(com.devforge.Chronometre)")       // la coupe
    fun mesurer(pjp: ProceedingJoinPoint): Any? {            // le greffon
        val debut = System.nanoTime()
        try {
            return pjp.proceed()                             // exécute la vraie méthode
        } finally {
            val ms = (System.nanoTime() - debut) / 1_000_000
            println("${pjp.signature.name} : $ms ms")
        }
    }
}
```

Toute méthode annotée `@Chronometre` est désormais chronométrée. Quelques coupes
courantes :

- `execution(* com.devforge.service..*(..))` : toutes les méthodes du package `service` et de ses sous-packages ;
- `@annotation(com.devforge.Chronometre)` : les méthodes portant cette annotation ;
- `within(com.devforge.api..*)` : tout ce qui est défini dans le package `api`.

## Quand le tissage a-t-il lieu ?

| Moment | Comment | Exemple |
|---|---|---|
| Compilation | le compilateur AspectJ (`ajc`) modifie le bytecode produit | AspectJ complet |
| Chargement | un agent Java modifie les classes quand la JVM les charge | AspectJ *load-time weaving* |
| Exécution | un **proxy** enveloppe l'objet | **Spring AOP** |

Le tissage par bytecode intercepte tout, même les appels internes. Le tissage par
proxy, celui de Spring, est plus simple… et a des limites qu'un expert connaît.

## Le piège n°1 : l'appel interne

```kotlin
class ServiceImpl : Service {
    override fun traiter() {
        println("traiter")
        enregistrer()            // this.enregistrer() : ne passe PAS par le proxy
    }
    override fun enregistrer() { println("enregistrer") }
}
```

Avec un proxy qui affiche `[aspect]` à chaque appel intercepté, `proxy.traiter()` affiche
réellement :

```
[aspect] début traiter
traiter
enregistrer
```

L'appel interne à `enregistrer()` passe directement par `this`, la vraie cible :
l'aspect ne le voit jamais. Avec Spring, c'est pourquoi un `@Transactional` sur une
méthode appelée depuis la même classe **n'a aucun effet**.

Solutions : déplacer la méthode dans un autre bean, ou mettre l'annotation sur la
méthode appelée de l'extérieur.

## Les autres limites de Spring AOP

- **Méthodes `private`** : jamais interceptées, l'annotation est ignorée en silence.
- **Classes et méthodes `final`** (le défaut en Kotlin) : pas de proxy par sous-classe
  possible, d'où le plugin `kotlin-spring`.
- **Seules les exécutions de méthodes** des beans Spring sont des points de jonction.

## @Transactional sous le capot

1. Spring crée un proxy autour du bean.
2. À l'appel, l'intercepteur de transaction **ouvre** une transaction (ou rejoint celle en cours).
3. Il appelle la vraie méthode.
4. Si elle se termine normalement : **commit**. Si elle lève une exception non vérifiée
   (`RuntimeException` ou `Error`) : **rollback**.

Piège d'expert : par défaut, une exception **vérifiée** (`IOException`, `SQLException`…)
provoque un **commit**. En Kotlin, où rien ne t'oblige à déclarer ces exceptions, on
l'oublie facilement. Précise-le : `@Transactional(rollbackFor = [Exception::class])`.

## Quand utiliser l'AOP

- ✅ Préoccupations réellement transverses : transactions, sécurité, métriques, traces, cache.
- ❌ La logique métier : un comportement important qui n'apparaît pas dans le code est
  un piège pour le prochain lecteur. Ce qui est invisible doit rester ennuyeux.

## À retenir

- Aspect = coupe (où) + greffon (quoi).
- Spring AOP tisse par **proxy** : appels internes, méthodes privées et classes finales échappent aux aspects.
- `@Transactional` = proxy + intercepteur ; rollback par défaut seulement sur les exceptions non vérifiées.
