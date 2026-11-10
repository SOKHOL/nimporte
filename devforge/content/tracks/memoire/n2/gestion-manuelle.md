---
id: memoire.n2.gestion-manuelle
title: Gérer la mémoire à la main
skills: [allocation-manuelle, bugs-memoire, raii]
duration_min: 12
---

# Gérer la mémoire à la main

En C, pas de ramasse-miettes : c'est toi qui demandes la mémoire et toi qui la rends.
Comprendre ce monde-là, c'est comprendre ce que les langages modernes font pour toi,
et pourquoi la majorité des failles de sécurité graves viennent encore de la mémoire.

## malloc et free

```c
char *nom = malloc(16);     // demande 16 octets sur le tas
strcpy(nom, "DevForge");    // les utilise
free(nom);                  // les rend : à faire exactement une fois
```

`malloc` renvoie l'adresse d'une zone sur le tas (ou `NULL` si la mémoire manque).
Rien ne t'empêche ensuite de mal t'en servir : le compilateur ne vérifie rien.

## Les cinq bugs mémoire classiques

| Bug | Ce qui se passe | Conséquence |
|---|---|---|
| **Fuite** | la mémoire n'est jamais libérée | le programme grossit jusqu'à l'épuisement |
| **Use-after-free** | on utilise une zone déjà libérée | données corrompues, faille de sécurité |
| **Double libération** | `free` deux fois sur le même pointeur | corruption de l'allocateur, plantage |
| **Dépassement de tampon** | on écrit au-delà de la taille allouée | écrasement de données voisines, faille |
| **Pointeur nul** | on suit un pointeur qui ne pointe sur rien | plantage immédiat |

Les trois du milieu sont des **comportements indéfinis** : le programme peut sembler
marcher, afficher n'importe quoi, ou planter, et ça peut changer d'une exécution à l'autre.

## Les outils qui voient l'invisible

Un exemple vrai, et ce que dit **AddressSanitizer** (`gcc -fsanitize=address`) :

```c
char *dupliquer(const char *source) {
    char *copie = malloc(strlen(source));   // oubli du +1 pour le '\0' final
    strcpy(copie, source);
    return copie;
}
```

```
ERROR: AddressSanitizer: heap-buffer-overflow
WRITE of size 9 …
    #1 in dupliquer dup.c:7
```

`strlen("DevForge")` vaut 8, mais la chaîne occupe 9 octets avec son `'\0'` final :
`strcpy` écrit un octet de trop. Sans outil, ce bug passe souvent inaperçu… jusqu'au jour
où il écrase une donnée voisine.

Et **valgrind** sur une fonction qui oublie `free`, appelée 1 000 fois :

```
definitely lost: 16,000 bytes in 1,000 blocks
```

Réflexe d'expert : en C ou C++, les tests tournent **toujours** avec les sanitizers activés.

## RAII : lier la libération à la portée

Le C++ et Rust ont trouvé la parade : attacher la libération d'une ressource à la **fin
de vie d'une variable**. Quand la variable sort de sa portée, son destructeur s'exécute
automatiquement : c'est le **RAII** (*Resource Acquisition Is Initialization*).

```rust
struct Fichier { nom: String }

impl Drop for Fichier {
    fn drop(&mut self) {
        println!("fermeture de {}", self.nom);
    }
}

fn main() {
    let _a = Fichier { nom: String::from("a.txt") };
    {
        let _b = Fichier { nom: String::from("b.txt") };
        println!("fin du bloc");
    }
    println!("fin de main");
}
```

Sortie réelle : `fin du bloc`, `fermeture de b.txt`, `fin de main`, `fermeture de a.txt`.
Chaque ressource est libérée au bon moment, sans oubli possible.

Le même principe existe en Kotlin pour les ressources (fichiers, connexions), qui ne
sont **pas** gérées par le ramasse-miettes : `use { }` ferme la ressource à la sortie du
bloc, même en cas d'exception.

```kotlin
File("journal.txt").bufferedWriter().use { it.write("bonjour") }   // fermé ici
```

## À retenir

- `malloc`/`free` : la libération est ta responsabilité, exactement une fois.
- Fuites, use-after-free, double libération, dépassements : les bugs les plus dangereux.
- AddressSanitizer et valgrind les rendent visibles : à utiliser systématiquement.
- RAII (et `use` en Kotlin) : la libération suit la portée, impossible de l'oublier.
