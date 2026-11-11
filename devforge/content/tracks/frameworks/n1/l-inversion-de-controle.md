---
id: frameworks.n1.l-inversion-de-controle
title: Inversion de contrôle et cycle de vie d'une requête
skills: [inversion-de-controle, bibliotheque-ou-framework, cycle-requete]
duration_min: 17
---

# Inversion de contrôle et cycle de vie d'une requête

Dans ce parcours, on ouvre le capot de vraies applications : Spring Boot 3.3 avec Hibernate et
PostgreSQL 16, Ktor 2.3, et un mini-framework écrit à la main. Toutes les sorties sont réelles.

## L'inversion de contrôle

Avec une **bibliothèque**, c'est votre code qui appelle : `Json.parse(texte)`, `Math.max(a, b)`.
Avec un **framework**, c'est le framework qui appelle votre code, au moment qu'il choisit :
« ne nous appelez pas, nous vous appellerons » (le principe d'Hollywood). C'est l'**inversion de
contrôle**.

```java
@RestController
class ProduitControleur {
    @GetMapping("/produits/{id}")
    Produit lire(@PathVariable long id) { … }
}
```

Personne, dans votre code, n'appelle `lire`. Vous déclarez **quand** il doit être appelé
(une requête `GET` sur `/produits/{id}`) et **ce qu'il lui faut** (un `long`), et le framework
fait le reste. Mesure réelle dans le contrôleur : la pile d'appels compte **52 cadres** au-dessus
de `lire`, tous écrits par Tomcat et Spring.

Les formes de l'inversion de contrôle, de la plus simple à la plus complète :

| Forme | Exemple |
|---|---|
| fonction de rappel (*callback*) | `bouton.setOnClickListener { … }` |
| méthode patron (*template method*) | `onCreate()` d'une activité Android, que le système appelle |
| événements | un `@EventListener`, un abonné Kafka |
| **injection de dépendances** | le framework construit vos objets et leur fournit ce qu'ils demandent (niveau 2) |

## Bibliothèque ou framework

| | Bibliothèque | Framework |
|---|---|---|
| qui appelle | votre code | le framework |
| remplacement | facile : une dépendance parmi d'autres | difficile : il structure toute l'application |
| apprentissage | une API | une API **et** des conventions, un cycle de vie, de la « magie » |
| exemples | Jackson, OkHttp, Guava, kotlinx.coroutines | Spring, Ktor, Android, Django, Rails, Angular |

Un framework apporte beaucoup (structure commune, sécurité, transactions, observabilité) mais
**couple** fortement votre code à ses choix. La parade, vue au parcours Architecture : garder le
cœur métier indépendant du framework (architecture hexagonale) et cantonner le framework aux
bords (contrôleurs, configuration, persistance).

La « magie » d'un framework est toujours un mécanisme : réflexion, annotations lues au
démarrage, proxies générés, conventions de nommage (parcours Métaprogrammation). Ce parcours
consiste à rendre ces mécanismes visibles.

## Le cycle de vie d'une requête web

Ce qui se passe réellement entre la socket et votre méthode. Une application Spring Boot
instrumentée avec un **filtre** servlet, un **intercepteur** Spring MVC, un contrôleur et un
gestionnaire d'exceptions (`@RestControllerAdvice`), sortie réelle pour `GET /produits/7` :

```
1. filtre servlet (avant)        [http-nio-8089-exec-2]
2. intercepteur preHandle        → demo.ProduitControleur#lire(long)
3. contrôleur, id = 7 (converti depuis le texte de l'URL)
4. le contrôleur renvoie un objet Java ; Jackson l'écrit en JSON
5. intercepteur afterCompletion
6. filtre servlet (après), statut 200

HTTP/1.1 200
Content-Type: application/json
{"id":7,"nom":"Clavier","prix":49.9}
```

Les étapes, de l'extérieur vers l'intérieur :

1. le **serveur** (Tomcat embarqué) accepte la connexion, lit les octets, analyse HTTP, et
   confie la requête à un **fil** de son pool (`http-nio-8089-exec-2`) ;
2. la chaîne de **filtres** servlet (sécurité, journalisation, CORS, compression) : chacun peut
   agir avant **et** après, ou court-circuiter la suite ;
3. le `DispatcherServlet` cherche le **gestionnaire** qui correspond à la méthode et au chemin
   (le **routage**), puis les **intercepteurs** s'exécutent ;
4. la **liaison des paramètres** : le texte `"7"` de l'URL devient un `long`, le corps JSON
   devient un objet ;
5. votre méthode, puis la **conversion** du résultat (Jackson : objet → JSON, négociation du
   type de contenu) ;
6. en cas d'exception, le **gestionnaire d'exceptions** produit la réponse d'erreur.

Deux surprises réelles de cette expérience :

- `GET /produits/abc` a été traité par le gestionnaire prévu pour `IllegalArgumentException`,
  avec le message interne `For input string: "abc"`. La conversion a échoué **avant** le
  contrôleur, et Spring associe un gestionnaire à l'exception **ou à sa cause**
  (`NumberFormatException` est une `IllegalArgumentException`). Un gestionnaire trop large
  expose des messages internes au client ;
- la requête de test sur `/` a d'abord produit un 404, puis un **second passage** dans
  l'intercepteur, vers `BasicErrorController` : Spring Boot **redirige en interne** les erreurs
  vers `/error`.

Le même cycle existe partout, avec d'autres noms : *middlewares* dans Express ou Django,
*plugins* et *pipeline* dans Ktor (niveau 4), *interceptors* d'OkHttp côté client.

## À retenir

- Inversion de contrôle : le framework appelle votre code (52 cadres sous le contrôleur) ; callbacks, méthode patron, événements, injection.
- Bibliothèque (vous appelez, remplaçable) contre framework (il vous appelle, il structure) ; la magie est toujours un mécanisme.
- Requête : serveur et pool de fils, filtres, routage, intercepteurs, liaison des paramètres, contrôleur, conversion, gestion des exceptions.
