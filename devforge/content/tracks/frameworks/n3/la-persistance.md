---
id: frameworks.n3.la-persistance
title: Contexte de persistance, dirty checking, chargement paresseux et transactions
skills: [contexte-de-persistance, dirty-checking, chargement-paresseux, transactions]
duration_min: 23
---

# Contexte de persistance, dirty checking, chargement paresseux et transactions

JPA (l'interface standard) et Hibernate (son implémentation la plus répandue) font
correspondre des objets et des tables (**ORM**). Ils font gagner beaucoup de code, et causent
une bonne part des problèmes de performance des applications Java, parce qu'ils agissent dans
votre dos. Toutes les sorties viennent d'une application Spring Boot 3.3 (Hibernate 6) sur
PostgreSQL 16, avec le journal SQL activé et des compteurs de requêtes.

```java
@Entity class Client   { @Id @GeneratedValue Long id; String nom; }
@Entity class Commande { @Id @GeneratedValue Long id; double montant;
                         @ManyToOne(fetch = FetchType.LAZY) Client client; }
```

## Le contexte de persistance

Dans une transaction, Hibernate tient un **contexte de persistance** (la `Session`, ou
`EntityManager`) : une carte « (type, id) → objet » de toutes les entités chargées. Réel :

```
[contexte] deux find(1) : même objet ? true
[contexte] requêtes SQL : 1
```

Le second `find` ne va pas en base : l'objet est déjà dans le contexte (le **cache de premier
niveau**). Et c'est **le même objet** Java : dans une transaction, une ligne correspond à une
seule instance (**identity map**). Le contexte vit le temps de la transaction ; il n'est pas
partagé entre requêtes.

Autre détail réel du journal : 10 clients insérés, mais seulement 2 `select nextval('client_seq')`.
Hibernate réserve les identifiants **par blocs** (50 par défaut) pour éviter un aller-retour par
insertion.

## Le dirty checking

```java
@Transactional void renommer() {
    Client c = em.find(Client.class, 1L);
    c.nom = "Ada";              // aucun save()
}
```

Journal SQL réel :

```
select c1_0.id,c1_0.nom from client c1_0 where c1_0.id=?
[dirty] nom modifié en mémoire, pas de save()
update client set nom=? where id=?
```

Au chargement, Hibernate garde une **copie** de l'état de chaque entité. À la fin de la
transaction (au **flush**, juste avant le `commit`), il compare chaque entité à sa copie et
génère un `UPDATE` pour celles qui ont changé. Conséquences :

- `save()` est **inutile** pour une entité déjà gérée (il ne sert qu'aux nouvelles) ;
- une modification « juste pour l'affichage » d'une entité dans une transaction est **écrite en
  base**. Préférez des objets dédiés (DTO) pour transformer des données ;
- avec beaucoup d'entités chargées, la comparaison coûte du temps et de la mémoire, d'où
  `@Transactional(readOnly = true)` pour les lectures : Hibernate saute alors le dirty checking.

## Le chargement paresseux et le N+1

Avec `fetch = LAZY`, charger une commande ne charge **pas** son client : Hibernate met à la place
un **proxy**, une sous-classe générée qui ira chercher les données au premier accès. Réel :

```
[proxy] classe : Client$HibernateProxy$2ViBRDkd
[proxy] champ lu directement : null ; via le getter : client1
```

Piège réel rencontré en écrivant cette démonstration : lire le **champ** `c.client.nom` renvoie
`null`, car le proxy est une coquille vide dont seules les **méthodes** déclenchent le
chargement. Toujours passer par des accesseurs sur des entités.

Le problème le plus coûteux de l'ORM, le **N+1** : charger N commandes (1 requête), puis accéder
au client de chacune (N requêtes). Réel, avec 10 commandes :

```
[n+1] findAll puis c.client.nom : 11 requêtes SQL
[n+1] join fetch : 1 requête(s) SQL
```

```sql
-- la version join fetch, une seule requête réelle :
select c1_0.id,c2_0.id,c2_0.nom,c1_0.montant from commande c1_0 join client c2_0 on c2_0.id=c1_0.client_id
```

Avec 10 000 commandes, c'est 10 001 allers-retours, invisibles dans le code. Les parades :
`join fetch` en JPQL, `@EntityGraph` sur la méthode du dépôt, le chargement par lots
(`@BatchSize`, qui regroupe les N requêtes en N/taille), ou une **projection** qui ne charge que
les colonnes utiles. Et surtout : compter les requêtes dans les tests (statistiques Hibernate,
comme ici).

Hors transaction, le contexte est fermé, et le proxy ne peut plus se charger. Réel :

```
[paresseux] hors transaction : LazyInitializationException
```

La « solution » d'*Open Session in View* (activée par défaut dans Spring Boot, `spring.jpa.open-in-view`)
garde le contexte ouvert jusqu'à la fin de la requête HTTP : l'exception disparaît, mais les
requêtes N+1 partent depuis la vue, une connexion reste occupée pendant tout l'affichage, et
Spring Boot affiche un avertissement au démarrage. Mieux vaut la désactiver et charger
explicitement ce dont on a besoin.

## Les transactions

`@Transactional` est un **proxy** qui ouvre une transaction avant la méthode et la valide ou
l'annule après (parcours Métaprogrammation, niveau 3 : l'appel interne `this.methode()` ne passe
pas par le proxy). Le piège réel le plus surprenant :

```java
@Transactional void annulerAvecErreurMetier() throws ErreurMetier {     // ErreurMetier extends Exception
    Client c = em.find(Client.class, 2L); c.nom = "MODIFIÉ";
    throw new ErreurMetier("stock insuffisant");
}
```

```
[tx] client 2 après exception vérifiée : MODIFIÉ
[tx] client 3 avec rollbackFor : client3
```

Par défaut, Spring n'annule la transaction que pour les exceptions **non vérifiées**
(`RuntimeException`, `Error`). Une exception **vérifiée** (qui hérite d'`Exception`) laisse la
transaction **se valider** : le dirty checking écrit la modification malgré l'erreur. Il faut
`@Transactional(rollbackFor = Exception.class)`, ou des exceptions métier non vérifiées (le choix
habituel en Kotlin, qui n'a pas d'exceptions vérifiées).

Les autres réglages à connaître : la **propagation** (`REQUIRED` par défaut : rejoindre la
transaction existante ; `REQUIRES_NEW` : en ouvrir une indépendante, par exemple pour un journal
d'audit qui doit survivre à l'annulation), l'**isolation** (parcours Bases de données, niveau 3),
et le délai (`timeout`). Une transaction doit être **courte** : jamais d'appel HTTP vers un autre
service à l'intérieur, car elle garde une connexion et des verrous pendant ce temps.

## À retenir

- Contexte de persistance : identity map et cache de premier niveau, le temps d'une transaction (deux find, une requête, le même objet).
- Dirty checking : toute modification d'une entité gérée est écrite au flush, sans save() ; readOnly = true pour les lectures.
- LAZY = proxy (champ lu directement : null) ; N+1 mesuré 11 requêtes contre 1 avec join fetch ; LazyInitializationException hors transaction.
- @Transactional n'annule pas sur une exception vérifiée (MODIFIÉ écrit malgré l'erreur) : rollbackFor, ou exceptions non vérifiées.
