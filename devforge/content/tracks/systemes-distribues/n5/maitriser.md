---
id: systemes-distribues.n5.maitriser
title: CRDT, split-brain, vérification et articles fondateurs
skills: [crdt, split-brain, verification, articles-fondateurs]
duration_min: 25
---

# CRDT, split-brain, vérification et articles fondateurs

## Les CRDT

Un **CRDT** (*Conflict-free Replicated Data Type*) est une structure de données que chaque
réplique peut modifier **sans coordination**, même hors ligne, et dont les copies **convergent
toujours** vers le même état une fois qu'elles ont échangé leurs modifications. Le secret : une
opération de **fusion** commutative (l'ordre n'importe pas), associative (le regroupement non
plus) et idempotente (recevoir deux fois la même chose ne change rien). Le réseau peut alors
dupliquer, retarder et réordonner les messages sans dommage.

**Le compteur qui ne fait que croître** (G-Counter) : chaque réplique garde **un compteur par
réplique**, n'incrémente que le sien, et la fusion prend le maximum de chaque composante.
Simulation réelle, trois répliques qui comptent des « j'aime » :

```
compteur : 11 7 11                  (b n'a pas encore reçu les incréments de a)
après un échange de plus : 11 11 11
compteur naïf (max) : 5 au lieu de 11
```

Le compteur naïf (une seule valeur, garder la plus grande) **perd** des incréments, le CRDT non.
Pour décrémenter, on combine deux G-Counters (PN-Counter : positif moins négatif). Les lois de
la fusion, vérifiées sur 2000 cas aléatoires (tests de propriétés, parcours Clean code) :

```
lois : {'commutative': 2000, 'associative': 2000, 'idempotente': 2000}
```

**L'ensemble où l'ajout gagne** (OR-Set, *observed-remove set*) : chaque ajout reçoit une
étiquette unique, et un retrait ne supprime que les étiquettes **qu'il a vues**. Réel : Alice
et Bob partagent une liste de courses ; hors ligne, Alice retire le lait pendant que Bob le
rajoute :

```
panier : ['lait', 'pain'] ['lait', 'pain']
```

Le rajout de Bob, qu'Alice n'avait pas vu, survit : les deux copies convergent, avec une règle
**prévisible** plutôt qu'une écriture perdue au hasard de l'horloge.

On trouve des CRDT dans Riak, Redis Enterprise (géo-réplication active-active), AntidoteDB,
l'édition collaborative (Automerge, Yjs : un texte est une séquence CRDT) et les applications
*local-first*. La limite : ils ne savent pas garantir un **invariant global** (« le stock ne
devient jamais négatif », « un seul utilisateur par pseudo ») ; pour cela, il faut un consensus.

## Split-brain et jetons de fencing

Le **split-brain** : deux nœuds se croient chacun leader (ou détenteur d'un verrou) et écrivent
en même temps. Le niveau 4 a montré un ancien leader qui se croit encore leader pendant une
partition. Avec un verrou distribué à **bail** (*lease*, un verrou qui expire), le scénario
classique est :

1. le client 1 obtient le bail pour 10 secondes ;
2. il subit une **pause** de 15 secondes (GC, swap, machine virtuelle suspendue) ;
3. le bail expire, le client 2 l'obtient et écrit ;
4. le client 1 se réveille, **croit** toujours détenir le bail, et écrit par-dessus.

Le client 1 ne peut pas se protéger lui-même : la pause peut survenir **entre** sa vérification
du bail et son écriture. La parade, décrite par Martin Kleppmann (2016) : le **jeton de
fencing**. Le service de verrou renvoie un numéro qui **croît** à chaque attribution, le client
le joint à chaque écriture, et c'est le **stockage** qui refuse tout jeton inférieur au plus
grand déjà vu.

Exploration exhaustive réelle de **tous** les entrelacements possibles (deux clients, un bail
qui peut expirer à tout moment, trois attributions au plus) :

```
sans jeton de fencing : 72 états explorés, écriture périmée en 5 étapes :
    C1 acquiert
    le bail expire
    C2 acquiert
    C2 écrit avec le jeton 2
    C1 écrit avec le jeton 1
avec jeton de fencing : 112 états explorés, aucune écriture périmée possible
```

Leçon générale : la sécurité d'un système ne doit pas dépendre du **temps** (ni des horloges, ni
de la durée des pauses), seulement de choses qu'on peut vérifier, comme un numéro qui croît. Les
mandats de Raft jouent le même rôle : un message d'un mandat dépassé est ignoré. En pratique :
etcd et ZooKeeper fournissent ces numéros (révision, zxid) ; un verrou dans Redis (Redlock) n'en
fournit pas, d'où le débat de 2016.

## Vérifier un protocole

Les bugs distribués se cachent dans des **entrelacements rares** : des tests qui passent mille
fois ne prouvent rien. La **vérification de modèles** (*model checking*) explore **tous** les
états atteignables d'un modèle réduit du protocole et vérifie un **invariant** dans chacun. Le
résultat ci-dessus vient d'un explorateur en largeur d'une vingtaine de lignes de Python :

```python
def explorer(initial, transitions, invariant):
    vus, file = {initial: None}, deque([initial])
    while file:
        etat = file.popleft()
        if not invariant(etat):
            return trace_jusqua(etat)          # en remontant vus[etat] jusqu'au départ
        for nom, suivant in transitions(etat):
            if suivant not in vus: vus[suivant] = etat; file.append(suivant)
```

L'exploration en largeur trouve le **contre-exemple le plus court**, ce qui le rend lisible.
Sur un modèle encore plus simple (deux processus qui font `compteur = compteur + 1` en deux
étapes), elle trouve en 13 états la trace où les deux lisent 0 avant d'écrire : le compteur
finit à 1.

L'outil de référence est **TLA+** (Lamport) avec son vérificateur **TLC**. On décrit l'état
initial, les transitions et les invariants dans une notation mathématique :

```
Ecrire(p) == /\ etape[p] = "ecrire"
             /\ compteur' = lu[p] + 1
             /\ etape' = [etape EXCEPT ![p] = "fini"]
             /\ UNCHANGED lu

Correct == (\A p \in Procs : etape[p] = "fini") => compteur = 2
```

Amazon l'utilise depuis 2011 sur S3, DynamoDB et EBS : des bugs nécessitant **35 étapes** ont été
trouvés, impossibles à atteindre en test (article *How Amazon Web Services Uses Formal Methods*,
2015). Microsoft (Cosmos DB), MongoDB et Elastic font de même. Les approches voisines : les tests
de **simulation déterministe** (FoundationDB : tout le cluster dans un seul processus, horloge et
réseau simulés, un très grand nombre d'exécutions rejouables à l'identique), et **Jepsen** (Kyle
Kingsbury), qui injecte partitions et pannes dans de vraies bases et a trouvé des pertes de
données dans presque toutes celles qu'il a testées.

## Les articles fondateurs

| Année | Article | Ce qu'il apporte |
|---|---|---|
| 1978 | Lamport, *Time, Clocks, and the Ordering of Events* | arrivé-avant, horloges logiques (niveau 2) |
| 1985 | Fischer, Lynch, Paterson, *Impossibility of Distributed Consensus with One Faulty Process* | FLP (niveau 4) |
| 1998 | Lamport, *The Part-Time Parliament* (Paxos) | le premier consensus pratique |
| 2007 | DeCandia et al., *Dynamo* (Amazon) | sans leader, quorums relâchés, versions vectorielles, hachage cohérent |
| 2011 | Shapiro et al., *Conflict-free Replicated Data Types* | les CRDT |
| 2012 | Corbett et al., *Spanner* (Google) | TrueTime, transactions mondiales linéarisables |
| 2013 | Dean et Barroso, *The Tail at Scale* | latence de queue (niveau 1) |
| 2014 | Ongaro et Ousterhout, *In Search of an Understandable Consensus Algorithm* | Raft |

Pour un livre : *Designing Data-Intensive Applications* de Martin Kleppmann, la meilleure synthèse
de tout ce parcours. Méthode de lecture d'un article : le résumé et l'introduction (quel
problème ?), les figures, la conclusion, et seulement ensuite le détail ; puis chercher ce que
d'autres en ont dit depuis (les limites découvertes, les corrections).

## À retenir

- CRDT : fusion commutative, associative, idempotente (vérifiées sur 2000 cas) ; convergence sans coordination, mais pas d'invariant global.
- Split-brain : la sécurité ne doit pas dépendre du temps ; le jeton de fencing, vérifié par le stockage, élimine l'écriture périmée (trouvée en 5 étapes sans lui, impossible avec).
- Vérifier par exploration exhaustive (TLA+, TLC, simulation déterministe, Jepsen) ; lire les articles fondateurs, de Lamport 1978 à Raft 2014.
