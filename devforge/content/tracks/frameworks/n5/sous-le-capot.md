---
id: frameworks.n5.sous-le-capot
title: Lire le code source d'un framework et écrire le sien
skills: [lire-le-code-source, mini-framework]
duration_min: 22
---

# Lire le code source d'un framework et écrire le sien

## Lire le code source d'un framework

La documentation dit ce qu'un framework **promet** ; le code dit ce qu'il **fait**. Quand un
comportement surprend, lire le code est souvent plus rapide que chercher sur Internet. Trois
portes d'entrée.

**1. Une pile d'appels.** Une exception, ou un point d'arrêt dans votre méthode, donne le chemin
complet depuis le démarrage du fil. Celle de notre contrôleur Spring, réelle (52 cadres, ici
sans les cadres de réflexion), à lire **de bas en haut** :

```
52  java.lang.Thread.run
49  org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker      ← le pool de fils de Tomcat
47  org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun     ← la socket
44  org.apache.coyote.http11.Http11Processor.service                 ← l'analyse HTTP
37-42  …Valve.invoke (Engine, Host, ErrorReport, Authenticator, Context, Wrapper)
33  org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal
29  org.springframework.web.filter.FormContentFilter.doFilterInternal
25  org.springframework.web.filter.RequestContextFilter.doFilterInternal
22  demo.FiltreJournal.doFilter                                      ← notre filtre
19  org.apache.tomcat.websocket.server.WsFilter.doFilter
10  org.springframework.web.servlet.DispatcherServlet.doDispatch      ← le cœur de Spring MVC
 7  …RequestMappingHandlerAdapter.invokeHandlerMethod
 4  org.springframework.web.method.support.InvocableHandlerMethod.doInvoke
 1  demo.ProduitControleur.lire                                      ← notre code
```

Tout le niveau 1 est là : le pool, la socket, HTTP, la chaîne de filtres (dont trois ajoutés par
Spring Boot, et le nôtre au milieu), puis `DispatcherServlet.doDispatch`, **la** méthode à lire
pour comprendre Spring MVC : elle cherche le gestionnaire, appelle les intercepteurs, invoque
l'adaptateur, gère les exceptions.

**2. Le point d'entrée d'une annotation.** Pour `@Transactional`, le proxy appelle
`TransactionInterceptor.invoke`, qui délègue à `TransactionAspectSupport.invokeWithinTransaction`.
En cas d'exception, celle-ci demande à l'attribut de transaction s'il faut annuler. Le bytecode
réel de `DefaultTransactionAttribute.rollbackOn` (spring-tx 6.1.14, obtenu avec `javap -c`) :

```
0: aload_1
1: instanceof    java/lang/RuntimeException
4: ifne          14
7: aload_1
8: instanceof    java/lang/Error
11: ifeq          18
14: iconst_1          (vrai : annuler)
18: iconst_0          (faux : valider)
```

Soit `return ex instanceof RuntimeException || ex instanceof Error;` : l'explication exacte du
piège du niveau 3, en dix lignes.

**3. La liste des mécanismes.** `META-INF/spring/…AutoConfiguration.imports` (152 lignes dans
Spring Boot 3.3.5) liste toutes les auto-configurations ; ouvrir `DataSourceAutoConfiguration`
montre ses conditions, et donc pourquoi elle s'active.

La méthode :

- partir d'une **question précise** (« pourquoi pas de rollback ? »), pas lire au hasard ;
- télécharger les sources (l'IDE le fait : « Download Sources ») et **naviguer** : aller à la
  définition, chercher les implémentations, la hiérarchie d'appels ;
- poser un **point d'arrêt** dans le code du framework et regarder les variables : rien n'est
  plus instructif ;
- lire les **tests** du framework : ils décrivent les comportements attendus, cas limites compris ;
- ignorer d'abord les détails (journalisation, cas spéciaux) pour suivre le chemin principal.

## Écrire son propre mini-framework

Rien ne démystifie mieux un framework que d'en écrire un. Le nôtre tient en **66 lignes de
Kotlin** : un conteneur d'injection et un routeur, fondés uniquement sur la réflexion de la JVM.

```kotlin
@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.RUNTIME) annotation class Composant
@Target(AnnotationTarget.FUNCTION) @Retention(AnnotationRetention.RUNTIME) annotation class Get(val chemin: String)

class Conteneur(private val classes: List<Class<*>>) {
    private val instances = mutableMapOf<Class<*>, Any>()
    private val enCours = ArrayDeque<Class<*>>()

    fun <T> obtenir(type: Class<T>): T {
        instances[type]?.let { return it as T }                              // singleton
        val impl = classes.firstOrNull { type.isAssignableFrom(it) && it.isAnnotationPresent(Composant::class.java) }
            ?: error("aucun composant pour ${type.simpleName}")
        if (impl in enCours) error("dépendance circulaire : " + (enCours + impl).joinToString(" → ") { it.simpleName })
        enCours.addLast(impl)
        val constructeur = impl.declaredConstructors.single()
        val arguments = constructeur.parameterTypes.map { obtenir(it) }       // résolution récursive
        val instance = constructeur.newInstance(*arguments.toTypedArray())
        enCours.removeLast()
        instances[type] = instance; instances[impl] = instance
        return instance as T
    }
}
```

Le routeur parcourt les méthodes annotées `@Get`, découpe les chemins (`/produits/{id}`),
convertit les variables selon le type du paramètre (`Long`), et appelle la méthode par
réflexion. L'application, qui ne connaît pas le framework :

```kotlin
interface Depot { fun nom(id: Long): String? }
@Composant class DepotMemoire : Depot { … }
@Composant class Prix { fun de(id: Long) = id * 10 + 9.9 }
@Composant class Catalogue(private val depot: Depot, private val prix: Prix) {
    @Get("/produits/{id}") fun produit(id: Long) = "${depot.nom(id) ?: "inconnu"} à ${prix.de(id)} €"
    @Get("/sante") fun sante() = "ok"
}
```

Sortie réelle :

```
démarrage :
  [conteneur] créé DepotMemoire()
  [conteneur] créé Prix()
  [conteneur] créé Catalogue(Depot, Prix)
GET /produits/2 → 200 Souris à 29.9 €
GET /produits/x → 400 paramètre invalide : For input string: "x"
GET /sante → 200 ok
GET /panier → 404 aucune route pour /panier
erreur : dépendance circulaire : A → B → A
```

Tout y est en miniature : les dépendances créées **d'abord** (graphe résolu récursivement), une
**interface** résolue vers son implémentation, des **singletons**, la **détection de cycle**, le
**routage**, la **liaison** des paramètres (le texte « 2 » devient un `Long`) et la traduction
des erreurs en statuts HTTP.

Ce qui manque pour en faire un vrai framework donne la mesure du travail de Spring : l'analyse
automatique des paquets (lire les fichiers `.class` du classpath), plusieurs constructeurs et
qualificatifs, les portées, le cycle de vie, les **proxies** pour les aspects, la configuration
externe, un vrai serveur HTTP, la sérialisation JSON, la sécurité, et des messages d'erreur
clairs. Et surtout la **performance du démarrage** : la réflexion coûte cher, d'où les approches
qui font ce travail **à la compilation** (Dagger et Hilt sur Android, Micronaut, Quarkus,
Spring AOT pour GraalVM native) : le graphe est vérifié et le code d'assemblage généré avant même
l'exécution.

## À retenir

- Lire un framework par une question précise : la pile d'appels (52 cadres, DispatcherServlet.doDispatch au centre), le point d'entrée d'une annotation (rollbackOn : RuntimeException ou Error), la liste des auto-configurations.
- Un conteneur d'injection et un routeur tiennent en 66 lignes de réflexion : graphe résolu récursivement, singletons, cycles détectés, liaison des paramètres.
- Les frameworks modernes déplacent ce travail à la compilation (Dagger, Micronaut, Spring AOT) pour démarrer plus vite et détecter les erreurs plus tôt.
