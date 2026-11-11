---
id: debogage.n2.les-outils
title: Le débogueur, des journaux utiles et lire un code inconnu
skills: [debogueur, journaux-diagnostic, lire-le-code]
duration_min: 17
---

# Le débogueur, des journaux utiles et lire un code inconnu

## Le débogueur

Un **débogueur** arrête le programme où l'on veut et montre son état : variables, pile
d'appels, threads. Il remplace des dizaines de `println` et d'allers-retours de compilation.

Session réelle avec **gdb**, sur un programme C qui calcule la moyenne des notes d'une classe et
plante :

```
$ gdb -batch -ex run -ex bt -ex "print *e" -ex "print i" ./moyenne
Program received signal SIGSEGV, Segmentation fault.
0x000055555555519a in moyenne (e=0x7fffffffc298) at moyenne.c:6
6	    for (int i = 0; i < e->nb_notes; i++) total += e->notes[i];
#0  moyenne (e=0x7fffffffc298) at moyenne.c:6
#1  main () at moyenne.c:12
$1 = {nom = 0x555555556008 "Linus", notes = 0x0, nb_notes = 2}
$2 = 0
```

En quatre commandes : le programme plante **ligne 6**, appelé depuis `main` ligne 12 (`bt`,
*backtrace*), pour l'élève **Linus**, dont le tableau de notes est **NULL** alors que
`nb_notes` vaut 2, dès la première note (`i = 0`). Le bug est dans les données de Linus, pas
dans le calcul.

Les fonctions à maîtriser, dans n'importe quel débogueur (IntelliJ, Android Studio, VS Code,
gdb, lldb, pdb, les outils du navigateur) :

| Fonction | Usage |
|---|---|
| **Point d'arrêt** | s'arrêter à une ligne |
| **Point d'arrêt conditionnel** | s'arrêter seulement si `client.id == 7731` (indispensable dans une boucle de 10 000 tours) |
| **Point d'arrêt sur exception** | s'arrêter au moment où l'exception est **levée**, avec tout le contexte, avant qu'elle soit attrapée |
| **Pas à pas** | *step over* (ligne suivante), *step into* (entrer dans l'appel), *step out* (finir la fonction) |
| **Pile d'appels** | remonter les appelants et voir leurs variables |
| **Évaluer une expression** | calculer `panier.lignes.sumOf { it.prix }` en pleine pause |
| **Point d'observation** (*watchpoint*) | s'arrêter quand une **variable change** : trouver qui modifie une valeur |
| **Point de journalisation** | afficher une valeur sans s'arrêter ni modifier le code |

Le débogueur s'attache aussi à un programme **déjà lancé** (un serveur local, un émulateur, un
conteneur avec le port de débogage ouvert) ; on ne le fait jamais en production : une pause
fige toutes les requêtes.

## Des journaux pour diagnostiquer

Le débogueur ne sert pas en production, ni pour un bug intermittent qui survient une fois par
jour : là, ce sont les **journaux** et les métriques qui parlent (parcours SRE, niveau 2).

Un piège réel à connaître. Le même programme C, lancé deux fois :

```
$ ./moyenne                    # vers le terminal
Ada : 15.0
Segmentation fault

$ ./moyenne > sortie.txt       # vers un fichier
Segmentation fault (code de sortie 139)
$ cat sortie.txt               # vide : la ligne « Ada : 15.0 » est perdue
```

Vers un terminal, la sortie standard est vidée à chaque ligne ; vers un fichier ou un tube,
elle est **mise en tampon** par blocs. Quand le programme plante, le tampon n'est jamais écrit.
Conséquence : l'absence d'un message de débogage ne prouve **pas** que le code n'a pas été
atteint. On écrit les diagnostics sur la sortie d'**erreur** (non tamponnée), ou on vide
explicitement le tampon, ou on utilise une bibliothèque de journalisation.

Ce qui fait un journal utile pour diagnostiquer : **le contexte** (identifiants de requête, de
client, de commande), **les décisions** (quelle branche a été prise et pourquoi), **les entrées
et sorties des appels externes** (avec leur durée et leur code de retour), et **les erreurs avec
leur pile complète**. On retire les `println` de débogage avant de fusionner ; un journal
`DEBUG` activable par configuration (pour un module ou un client précis) les remplace
avantageusement.

## Lire un code qu'on ne connaît pas

On passe beaucoup plus de temps à lire du code qu'à en écrire, et souvent un code qu'on n'a pas
écrit. Une méthode :

1. **Partir d'un point d'entrée concret** : la route HTTP, l'écran, la tâche planifiée, le
   message traité. Chercher le texte de l'erreur ou de l'écran dans le code.
2. **Suivre un seul chemin** à la fois avec la navigation de l'IDE (aller à la définition,
   trouver les usages, hiérarchie des appels), plutôt que de lire les fichiers de haut en bas.
3. **Lire les tests** : ils montrent comment le code est censé être utilisé, avec des exemples.
4. **Exécuter** : un point d'arrêt sur le chemin et un pas-à-pas valent mieux que des heures de
   lecture.
5. **Utiliser l'historique** : `git log -p fichier`, `git blame` (qui a écrit cette ligne, dans
   quel commit, avec quel message, pour quel ticket ?). Une ligne étrange a souvent une raison
   documentée dans son commit.
6. **Prendre des notes** et dessiner : un schéma des classes et des appels se fait en dix
   minutes et évite de tout relire.

Et ne pas juger trop vite : un code qui paraît absurde contient souvent la correction d'un cas
particulier que tu ne connais pas encore.

## À retenir

- Débogueur : points d'arrêt (conditionnels, sur exception), pas à pas, pile, évaluation, points d'observation ; gdb trouve en 4 commandes un tableau NULL.
- La sortie tamponnée est perdue au plantage (démontré : rien dans le fichier) ; diagnostics sur la sortie d'erreur ou par un journal ; contexte, décisions, appels externes, piles complètes.
- Lire un code inconnu : un point d'entrée, un chemin, les tests, l'exécution, l'historique Git, des notes.
