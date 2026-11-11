---
id: frameworks.n2.le-conteneur
title: Conteneur d'injection, portées, cycle de vie et auto-configuration
skills: [conteneur-injection, portees, cycle-de-vie, auto-configuration]
duration_min: 20
---

# Conteneur d'injection, portées, cycle de vie et auto-configuration

## Le conteneur d'injection et les beans

Le cœur de Spring est un **conteneur** (l'`ApplicationContext`) qui crée vos objets, appelés
**beans**, et leur fournit leurs dépendances. Au démarrage, il :

1. **trouve** les définitions : classes annotées (`@Component`, `@Service`, `@RestController`,
   `@Repository`) dans les paquets analysés, et méthodes `@Bean` des classes `@Configuration` ;
2. construit un **graphe de dépendances** à partir des constructeurs ;
3. crée les beans **dans l'ordre** (les dépendances d'abord), en passant chacune au
   constructeur de ceux qui en ont besoin ;
4. les **décore** si nécessaire (proxies pour `@Transactional`, `@Async`, `@Cacheable` : parcours
   Métaprogrammation).

```java
@Service
class ServiceCommande {
    private final Commandes commandes;      // un dépôt Spring Data
    private final Paiement paiement;        // une interface ; Spring trouve l'implémentation
    ServiceCommande(Commandes commandes, Paiement paiement) { … }   // injection par constructeur
}
```

Préférez l'injection **par constructeur** : les dépendances sont visibles, les champs peuvent
être `final`, et la classe se teste sans Spring (`new ServiceCommande(fauxDepot, fauxPaiement)`).
Erreurs classiques détectées **au démarrage** : aucune implémentation trouvée, plusieurs
candidates (il faut `@Primary` ou `@Qualifier`), dépendance circulaire (refusée par défaut depuis
Spring Boot 2.6).

Mesure réelle : l'application de démonstration (un contrôleur, un service, deux dépôts JPA)
contient **236 beans, dont 10 écrits par nous**. Les 226 autres viennent de l'auto-configuration.

## Les portées

La **portée** d'un bean dit combien d'instances existent et combien de temps elles vivent :

| Portée | Instances | Usage |
|---|---|---|
| `singleton` (par défaut) | une seule pour tout le conteneur | services, dépôts : **sans état** ou à état partagé thread-safe |
| `prototype` | une nouvelle à chaque demande | objets avec état propre |
| `request`, `session` | une par requête HTTP, par session | données de la requête courante |

Un singleton est partagé par **tous les fils** de Tomcat en même temps : un champ modifiable
dans un service singleton est une **course de données** (parcours Concurrence).

Le piège réel du prototype injecté dans un singleton :

```
[portée] singleton ? true
[portée] ServiceNaif, deux appels : panier 1 puis 1
[portée] ServiceCorrect, deux appels : panier 2 puis 3
```

`ServiceNaif` reçoit un `Panier` (prototype) dans son constructeur. Mais le constructeur d'un
singleton n'est appelé **qu'une fois** : il garde le même panier pour toujours. `ServiceCorrect`
reçoit un `ObjectProvider<Panier>` et demande un nouveau panier à chaque fois. Les beans de portée
`request` évitent ce piège en injectant un **proxy** qui retrouve le bon objet à chaque appel.

## Le cycle de vie des beans

Sortie réelle, de la création à la fermeture du contexte :

```
[cycle] Horloge : constructeur
[cycle] Horloge : @PostConstruct (dépendances injectées)
… l'application tourne …
[cycle] Horloge : @PreDestroy (fermeture du contexte)
```

L'ordre complet pour un singleton : instanciation (constructeur), injection des champs et
méthodes `@Autowired` éventuels, méthodes `*Aware`, `BeanPostProcessor` « avant », `@PostConstruct`
(ou `afterPropertiesSet`), `BeanPostProcessor` « après » (c'est **là** que sont créés les proxies),
puis, à la fermeture, `@PreDestroy`. Conséquences :

- dans un `@PostConstruct`, les dépendances sont prêtes, mais le **proxy** n'existe pas encore :
  un appel à une méthode `@Transactional` de l'objet lui-même n'y ouvre aucune transaction ;
- `@PreDestroy` n'est appelé que si le contexte est **fermé proprement** (arrêt normal, SIGTERM),
  jamais après un `kill -9`, et jamais pour les prototypes (Spring ne les suit pas après création).

## L'auto-configuration de Spring Boot

Comment 226 beans apparaissent-ils sans une ligne de configuration ? Le jar
`spring-boot-autoconfigure` 3.3.5 liste **152 classes d'auto-configuration** (dans
`META-INF/spring/…AutoConfiguration.imports`). Chacune est une `@Configuration` **conditionnelle**,
évaluée au démarrage. Le rapport réel, obtenu avec `--debug` :

```
Positive matches: 60      Negative matches: 137

DataSourceAutoConfiguration matched:
   - @ConditionalOnClass found required classes 'javax.sql.DataSource', … (OnClassCondition)
   - @ConditionalOnMissingBean (types: io.r2dbc.spi.ConnectionFactory) did not find any beans

JacksonAutoConfiguration matched:
   - @ConditionalOnClass found required class 'com.fasterxml.jackson.databind.ObjectMapper'

MongoAutoConfiguration:
   Did not match:
   - @ConditionalOnClass did not find required class 'com.mongodb.client.MongoClient'
```

Les conditions principales :

- `@ConditionalOnClass` : la bibliothèque est-elle **présente** dans les dépendances ? Ajouter le
  pilote PostgreSQL suffit à déclencher la création d'une `DataSource` (réel :
  `com.zaxxer.hikari.HikariDataSource`, le pool de connexions par défaut) ;
- `@ConditionalOnMissingBean` : vous n'avez **pas** défini ce bean vous-même ? Définir votre
  propre `ObjectMapper` remplace celui de Spring Boot. C'est la règle d'or : **vos beans
  gagnent** ;
- `@ConditionalOnProperty` : une propriété est-elle définie (`spring.jpa.open-in-view=false`) ?

Quand « ça ne marche pas tout seul », le réflexe est ce rapport (`--debug`, ou l'endpoint
`/actuator/conditions`) : il dit **pourquoi** chaque auto-configuration a été retenue ou écartée.
Et chaque starter (`spring-boot-starter-data-jpa`) n'est qu'un jar vide qui tire les bonnes
dépendances, donc les bonnes conditions.

## À retenir

- Le conteneur trouve les définitions, construit le graphe et crée les beans dépendances d'abord ; injection par constructeur ; 236 beans dont 10 à nous.
- Portées : singleton partagé par tous les fils ; un prototype injecté dans un singleton n'est créé qu'une fois (panier 1 puis 1) : utiliser ObjectProvider.
- Cycle de vie : constructeur, injection, @PostConstruct, proxies, @PreDestroy ; l'auto-configuration, ce sont 152 configurations conditionnelles (60 retenues, 137 écartées), et vos beans gagnent.
