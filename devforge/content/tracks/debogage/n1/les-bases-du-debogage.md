---
id: debogage.n1.les-bases-du-debogage
title: Lire une erreur, reproduire, et la méthode scientifique
skills: [lire-erreurs, reproduire, methode-scientifique]
duration_min: 15
---

# Lire une erreur, reproduire, et la méthode scientifique

Le débutant change des choses au hasard jusqu'à ce que « ça marche » ; l'expert **lit**,
**reproduit**, **formule une hypothèse** et la **vérifie**. La différence de vitesse est
énorme, et l'expert sait **pourquoi** c'est corrigé.

## Lire un message d'erreur et une pile d'appels

Un message d'erreur est une information précieuse, pas un bruit à faire disparaître. Pile
d'appels réelle, produite par un petit programme Kotlin :

```
Exception in thread "main" java.lang.IllegalStateException: commande 7731 introuvable
	at DepotCommandes.charger(Commande.kt:5)
	at ServiceFacturation.facturer(Commande.kt:10)
	at CommandeKt.main(Commande.kt:12)
	at CommandeKt.main(Commande.kt)
Caused by: java.io.FileNotFoundException: commandes/7731.json (No such file or directory)
	at java.base/java.io.FileInputStream.open0(Native Method)
	at java.base/java.io.FileInputStream.open(FileInputStream.java:213)
	at java.base/java.io.FileInputStream.<init>(FileInputStream.java:152)
	at kotlin.io.FilesKt__FileReadWriteKt.readText(FileReadWrite.kt:131)
	at DepotCommandes.lireFichier(Commande.kt:7)
	at DepotCommandes.charger(Commande.kt:3)
	... 3 more
```

Comment la lire :

1. **La première ligne** : le type d'exception et le message. Ici, la commande est introuvable.
2. **De haut en bas**, chaque ligne est un appel : le haut est **là où l'erreur a été levée**,
   le bas est le point de départ (`main`). On cherche la **première ligne de notre propre
   code** (`DepotCommandes.charger`, ligne 5) en ignorant les lignes des bibliothèques.
3. **Les `Caused by`** : l'exception d'origine, enveloppée. La **dernière** cause est souvent la
   vraie : un fichier `commandes/7731.json` absent, cherché dans un chemin **relatif** au
   dossier courant. Les `... 3 more` signifient « les mêmes lignes que l'exception
   englobante ».

Le même principe vaut partout : en Python, la pile se lit **à l'envers** (la dernière ligne est
l'erreur, `The above exception was the direct cause of…` relie les causes) ; en JavaScript, la
console du navigateur ; en C, le débogueur affiche la pile avec `bt` (niveau 2).

Réflexes : copier le message **exact** dans un moteur de recherche (ou dans les issues de la
bibliothèque) ; ne jamais « avaler » une exception (`catch (e: Exception) {}`) ni l'envelopper
sans garder la cause ; et, quand on écrit un message d'erreur, y mettre ce qui aidera
(l'identifiant, la valeur fautive, ce qui était attendu).

## Reproduire un bug

Un bug qu'on ne sait pas reproduire ne se corrige pas : on ne peut ni comprendre la cause, ni
vérifier la correction. Avant de toucher au code :

- **Recueillir les faits** : la version exacte, l'environnement, les données, les étapes, le
  moment, ce qui était attendu et ce qui s'est passé, les journaux et l'identifiant de la
  requête.
- **Reproduire** au plus près : mêmes données (anonymisées si besoin), même configuration, même
  version.
- **Réduire** jusqu'au cas minimal qui reproduit encore (niveau 3).
- **Écrire un test automatisé** qui reproduit le bug et échoue. Il prouvera la correction, et
  empêchera la régression.

Si le bug ne se reproduit pas, ce sont les **différences** entre l'environnement où il se
produit et le tien qui t'intéressent : données, configuration, version, charge, fuseau
horaire, langue, cache, droits.

## La méthode scientifique

Le débogage efficace est une démarche expérimentale :

1. **Observer** : que se passe-t-il exactement ? Qu'est-ce qui marche, qu'est-ce qui ne marche
   pas ?
2. **Formuler une hypothèse** précise et falsifiable : « le total est faux parce que la remise
   est appliquée deux fois quand le panier contient un code promo ».
3. **Prédire** : si l'hypothèse est vraie, que devrait-on observer ? (sans code promo, le
   total est juste ; avec, il manque exactement 5 %).
4. **Tester** par une expérience qui peut **réfuter** l'hypothèse, en ne changeant **qu'une
   chose à la fois**.
5. **Conclure**, puis recommencer avec une nouvelle hypothèse si elle est réfutée.

Tenir un **journal** (les hypothèses, les expériences, les résultats) évite de tourner en rond,
surtout sur un bug de plusieurs jours. Et expliquer le problème à voix haute, à un collègue ou
à un canard en plastique (*rubber duck debugging*), oblige à formuler ses hypothèses : souvent,
la réponse apparaît en cours d'explication.

Les pièges : **supposer** au lieu de vérifier (« ce code ne peut pas être appelé deux fois »),
changer plusieurs choses à la fois, s'arrêter dès que le symptôme disparaît sans comprendre
pourquoi (le bug reviendra), et chercher sous le lampadaire (là où c'est facile à regarder
plutôt que là où est le bug).

## À retenir

- Pile d'appels : la première ligne de son propre code, et la dernière cause (`Caused by`) ; ne jamais avaler une exception.
- Reproduire avant de corriger ; chercher les différences entre les environnements ; un test qui reproduit le bug.
- Méthode scientifique : observer, hypothèse falsifiable, prédiction, une seule variable à la fois, journal ; expliquer à voix haute.
