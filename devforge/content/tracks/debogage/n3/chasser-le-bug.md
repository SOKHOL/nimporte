---
id: debogage.n3.chasser-le-bug
title: git bisect, réduire un cas de test et les bugs intermittents
skills: [git-bisect, reduire, bugs-intermittents]
duration_min: 18
---

# git bisect, réduire un cas de test et les bugs intermittents

## Trouver le commit fautif avec git bisect

« Ça marchait il y a deux semaines. » Entre les deux, 40 commits. Plutôt que de lire les 40
diffs, **`git bisect`** fait une **recherche par dichotomie** dans l'historique : il propose un
commit au milieu, on dit s'il est bon ou mauvais, et il coupe l'intervalle en deux. Avec un
script de test, tout est automatique.

Démonstration réelle : un dépôt de 40 commits, dont le 27e a remplacé un `*` par un `+` dans le
calcul d'une remise. Un script renvoie 0 si `remise(200) == 190`, 1 sinon.

```
$ git bisect start HEAD <premier-commit>
$ git bisect run python3 -B test_remise.py
teste : Modification 29
teste : Modification 24
teste : Modification 26
teste : Modification 28
teste : Modification 27
07bdf84… is the first bad commit
    Modification 27
```

Cinq exécutions du test pour quarante commits (log₂ 40 ≈ 5,3). Pour mille commits, une
dizaine.

Points pratiques :

- le script renvoie **0** pour « bon », **1 à 127** pour « mauvais », et **125** pour « ce commit
  ne peut pas être testé » (il ne compile pas, par exemple) : bisect le saute ;
- un historique fait de **petits commits** qui compilent tous rend bisect redoutable ; un commit
  géant de 3 000 lignes désigné comme fautif n'aide pas beaucoup ;
- le test doit être **fiable** : un test instable fait accuser le mauvais commit.

Anecdote réelle de cette démonstration : la première exécution désignait le **dernier** commit,
à tort. Python réutilisait la version compilée (`.pyc`) d'un fichier modifié dans la même
seconde, donc chaque version testée était en fait la même. Avec `python3 -B` (sans cache), le
bon commit est trouvé. Quand un outil donne un résultat surprenant, on vérifie aussi l'outil.

## Réduire un cas de test

Un fichier de 401 lignes fait planter un import. Pour comprendre, il faut le cas **minimal** qui
plante encore. On peut le réduire à la main (supprimer la moitié, voir si ça plante toujours,
recommencer), ou automatiquement : c'est le **delta debugging** (Andreas Zeller).

Démonstration réelle, avec un petit algorithme de réduction par moitiés :

```
entrée de départ : 401 lignes
cas minimal : 2 ligne(s) → ['c199,p199,3\n', 'c200,p200,trois\n']
erreur : ValueError invalid literal for int() with base 10: 'trois'
```

Deux lignes : une ligne qui sert d'en-tête (la première est ignorée par l'import) et la ligne
fautive, dont la quantité est « trois » en toutes lettres. Le cas minimal rend la cause
évidente, et devient un test de non-régression parfait.

La même idée existe à grande échelle : **C-Reduce** et **creduce** pour les bugs de
compilateurs, la réduction (*shrinking*) automatique des tests par propriétés (Hypothesis a
réduit un contre-exemple à `'0'` dans le parcours Clean code), et à la main pour signaler un bug
à une bibliothèque : un exemple de dix lignes est corrigé ; un projet entier est ignoré.

## Les bugs intermittents

Un bug qui apparaît « parfois » a toujours une cause déterministe, qui dépend de quelque chose
qu'on ne contrôle pas : l'ordre d'exécution des threads, le moment, les données, l'état laissé
par une exécution précédente, la mémoire.

**Les courses entre threads.** Démonstration réelle sur la JVM : 4 threads incrémentent chacun
un million de fois un compteur partagé.

```
essai 1 : Int ordinaire = 3155186, AtomicInteger = 4000000 (attendu 4000000)
essai 2 : Int ordinaire = 3253096, AtomicInteger = 4000000 (attendu 4000000)
essai 3 : Int ordinaire = 3095247, AtomicInteger = 4000000 (attendu 4000000)
```

`compteur++` lit, ajoute et écrit en trois étapes ; deux threads lisent la même valeur et l'un
des deux incréments est perdu : environ 20 % ici, un nombre différent à chaque essai. Avec un
compteur atomique, le résultat est toujours juste (parcours Concurrence).

Les causes fréquentes d'intermittence et comment les débusquer :

| Cause | Indice | Piste |
|---|---|---|
| course entre threads | résultat différent à chaque exécution, surtout sous charge | rendre l'opération atomique ou synchronisée ; outils de détection (ThreadSanitizer, `-race` en Go) |
| dépendance au temps | échoue à minuit, en fin de mois, au changement d'heure | injecter l'horloge, tester les dates limites |
| ordre non garanti | ordre de résultats qui varie | trier, ou ne pas dépendre de l'ordre |
| état partagé entre tests | passe seul, échoue dans la suite | isoler chaque test |
| ressource épuisée | échoue après des heures | pools, descripteurs, mémoire, disque |
| réseau | délais, coupures | délais explicites, réessais idempotents |

Pour reproduire : **augmenter la probabilité** (boucler 1 000 fois, ajouter de la charge, ralentir
artificiellement une étape avec un `sleep` pour élargir la fenêtre de la course), et journaliser
assez de contexte pour comprendre l'occurrence suivante. Un *heisenbug* disparaît quand on
l'observe (le débogueur ou un `println` change le minutage) : on préfère alors les journaux
légers et les compteurs aux pauses.

## À retenir

- `git bisect run` : la dichotomie dans l'historique, 5 tests pour 40 commits mesurés ; petits commits et test fiable ; et vérifier l'outil quand le résultat surprend.
- Réduire jusqu'au cas minimal (401 lignes → 2 mesurées) : la cause devient évidente et le test de non-régression est tout trouvé.
- Un bug intermittent a une cause déterministe : courses (20 % d'incréments perdus mesurés), temps, ordre, état partagé, ressources ; augmenter la probabilité pour reproduire.
