---
id: metaprog.n4.generer-et-transformer
title: Générer et transformer du code
skills: [manipulation-bytecode, agents-java, generation-code, plugins-compilateur]
duration_min: 14
---

# Générer et transformer du code

La réflexion et les proxies agissent **à l'exécution**. On peut aussi agir **avant** :
pendant la compilation, ou au moment où la JVM charge une classe. C'est ce que font les
outils qui semblent « magiques » : Lombok, Hibernate, Mockito, les APM (Datadog, New
Relic), Compose, kotlinx.serialization, Room, Hilt…

## Quatre moments pour intervenir

| Moment | Technique | Exemples |
|---|---|---|
| Compilation, nouveau code | traitement d'annotations, **KSP** | Room, Hilt, Moshi |
| Compilation, code modifié | **plugin du compilateur** | Compose, kotlinx.serialization, `all-open` |
| Chargement des classes | **agent Java** + manipulation de bytecode | APM, couverture de tests (JaCoCo), certains mocks |
| Exécution | génération de classes (ByteBuddy, CGLIB) | proxies Spring, Mockito |

## Les plugins du compilateur : ce que génère kotlinx.serialization

```kotlin
@Serializable
data class Utilisateur(val nom: String, val niveau: Int = 1)
```

Le plugin ajoute à la compilation une classe complète, sans aucune réflexion. Sortie
réelle de `javap` :

```
public final class Utilisateur$$serializer
    implements kotlinx.serialization.internal.GeneratedSerializer<Utilisateur> {
  public static final Utilisateur$$serializer INSTANCE;
  public final void serialize(kotlinx.serialization.encoding.Encoder, Utilisateur);
  public final Utilisateur deserialize(kotlinx.serialization.encoding.Decoder);
  public final kotlinx.serialization.descriptors.SerialDescriptor getDescriptor();
  …
}
```

Et `Json.encodeToString(Utilisateur("Ada"))` donne réellement `{"nom":"Ada"}` : par
défaut, les valeurs égales à leur valeur par défaut ne sont **pas** écrites
(`encodeDefaults = false`). Un détail qui surprend quand un autre service attend le champ.

Compose fonctionne de la même façon : le plugin réécrit chaque fonction `@Composable`
pour y ajouter des paramètres cachés et la logique de recomposition.

## KSP : générer de nouveaux fichiers

**KSP** (*Kotlin Symbol Processing*) lit les déclarations annotées de ton code et génère
de **nouveaux fichiers** source, compilés avec le reste. Room lit tes `@Dao` et écrit leur
implémentation ; Hilt lit tes `@Inject` et écrit le graphe d'injection. Le code généré est
lisible dans `build/generated/ksp/` : va le lire, c'est la meilleure façon de comprendre
ces bibliothèques. KSP remplace `kapt`, plus lent.

## Les agents Java : modifier les classes au chargement

Un agent est un JAR chargé avant le `main` (`-javaagent:agent.jar`). Sa méthode `premain`
reçoit un objet `Instrumentation` qui lui permet de transformer le bytecode de chaque
classe au moment où elle est chargée.

```java
public class AgentComptage {
    public static void premain(String args, Instrumentation inst) {
        AtomicInteger total = new AtomicInteger();
        AtomicInteger miennes = new AtomicInteger();
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String nom, Class<?> classe,
                                    ProtectionDomain domaine, byte[] octets) {
                total.incrementAndGet();
                if (nom != null && nom.startsWith("dev/devforge")) miennes.incrementAndGet();
                return null;   // null = classe inchangée
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
            System.out.println("[agent] classes chargées : " + total + " dont " + miennes + " de l'application")));
    }
}
```

Lancé devant un petit programme Kotlin, sortie réelle :
`[agent] classes chargées : 26 dont 2 de l'application`. Un vrai agent renverrait le
bytecode **modifié** (par exemple pour mesurer chaque appel de méthode) au lieu de `null` :
c'est exactement ainsi que fonctionnent les outils de supervision et de couverture de tests.

## ASM et ByteBuddy

Écrire du bytecode à la main est possible, avec **ASM** : rapide, bas niveau, on manipule
instruction par instruction (la machine à pile du parcours JVM). **ByteBuddy** offre une
API de haut niveau par-dessus (« intercepte toutes les méthodes annotées `@Chronometre` »).
Mockito, Hibernate et de nombreux agents l'utilisent.

## À retenir

- Plugins du compilateur (Compose, serialization) et KSP (Room, Hilt) : du code généré à
  la compilation, rapide, vérifié, compatible avec R8.
- Les agents Java transforment le bytecode au chargement : supervision, couverture, mocks.
- ASM (bas niveau) et ByteBuddy (haut niveau) servent à écrire et modifier du bytecode.
- Le code généré se lit : `build/generated/` est une mine d'or pour comprendre la « magie ».
