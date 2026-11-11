---
id: bdd.n2.relier-et-modeliser
title: Jointures, agrégation, modélisation et normalisation
skills: [jointures, agregation, modelisation, normalisation]
duration_min: 18
---

# Jointures, agrégation, modélisation et normalisation

## Les jointures

Une jointure assemble les lignes de deux tables selon une condition.

| Jointure | Garde… |
|---|---|
| `inner join` (ou `join`) | seulement les lignes qui ont une correspondance des deux côtés |
| `left join` | toutes les lignes de gauche ; NULL à droite quand il n'y a pas de correspondance |
| `right join` | l'inverse (rare : on préfère réécrire en `left join`) |
| `full join` | tout, des deux côtés |
| `cross join` | toutes les combinaisons (rarement voulu) |

Exemple réel sur une base de 100 001 acheteurs et 1 000 000 de commandes : la jointure
interne donne 1 000 000 de lignes, et le `left join` permet de trouver l'acheteur qui n'a
jamais commandé :

```sql
select count(*) from acheteurs a
left join commandes c on c.acheteur_id = a.id
where c.id is null;          -- 1
```

Le piège du **comptage après un `left join`** (résultat réel) :

```sql
select s.nom, count(*), count(e.nom)
from (values ('R&D'), ('Ventes'), ('Support')) as s(nom)
left join employes e on e.service = s.nom
group by s.nom;
```

| nom | count(*) | count(e.nom) |
|---|---|---|
| R&D | 2 | 2 |
| Support | **1** | **0** |
| Ventes | 1 | 1 |

Le service Support n'a personne, mais le `left join` produit quand même une ligne (avec
NULL côté employés) : `count(*)` la compte, `count(e.nom)` non.

Autre piège : joindre deux relations « un à plusieurs » en même temps (les commandes **et**
les adresses d'un client) multiplie les lignes : 10 commandes × 3 adresses = 30 lignes, et
les sommes sont fausses. On agrège chaque relation séparément avant de joindre.

## Agréger : GROUP BY et HAVING

```sql
select a.pays, count(*) as nb, sum(c.montant) as total
from commandes c join acheteurs a on a.id = c.acheteur_id
group by a.pays
having count(*) > 1000          -- filtre APRÈS le regroupement (where filtre avant)
order by total desc;
```

Résultat réel : 4 pays, 250 000 commandes chacun, entre 125 998 250 et 126 250 420 de total.
Règle : chaque colonne du `select` est soit dans le `group by`, soit dans un agrégat
(`count`, `sum`, `avg`, `min`, `max`).

## Les fonctions de fenêtre

Une **fonction de fenêtre** calcule sur un groupe de lignes **sans les fusionner** : chaque
ligne garde son identité.

```sql
-- classement des commandes de chaque acheteur, par montant (réel, acheteur 42)
select id, montant, rank() over (partition by acheteur_id order by montant desc) as rang
from commandes where acheteur_id = 42 order by rang limit 3;
--   724839 | 979 | 1
--    24839 | 789 | 2
--   324839 | 729 | 3

-- cumul des salaires (réel)
select nom, salaire, sum(salaire) over (order by salaire desc) as cumul from employes;
--   Ada 5200 → 5200 ; Linus 4800 → 10000 ; Grace 4100 → 14100 ; Alan 3900 → 18000
```

Usages courants : top N par catégorie (`row_number()` ou `rank()`), cumul, moyenne mobile,
comparaison avec la ligne précédente (`lag()`), part d'un total. Sans fenêtres, ces calculs
demandent des sous-requêtes corrélées lentes ou du code applicatif.

## Modéliser : clés étrangères et cardinalités

Une **clé étrangère** garantit qu'une référence pointe vers une ligne qui existe :

```sql
create table commandes (
    id          bigint primary key,
    acheteur_id int not null references acheteurs(id),   -- impossible de pointer vers un acheteur inexistant
    ...
);
```

Les trois cardinalités :

- **un à un** : une colonne unique qui référence l'autre table (rare, souvent une seule
  table suffit) ;
- **un à plusieurs** : la clé étrangère va du côté « plusieurs » (chaque commande porte
  l'identifiant de son acheteur) ;
- **plusieurs à plusieurs** : une **table d'association** (`etudiants_cours(etudiant_id,
  cours_id)`), qui peut porter ses propres données (date d'inscription, note).

`on delete` décide de ce qui arrive aux lignes qui référencent une ligne supprimée :
`restrict` (refuser, le défaut sûr), `cascade` (supprimer aussi, à manier avec
précaution) ou `set null`. Et une clé étrangère doit presque toujours avoir un **index** :
PostgreSQL n'en crée pas automatiquement, et sans lui chaque suppression côté parent
parcourt toute la table enfant.

## Normaliser, puis dénormaliser si besoin

La **normalisation** élimine les redondances, pour qu'une information ne soit écrite qu'à
un endroit :

| Forme | Règle | Contre-exemple |
|---|---|---|
| **1NF** | des valeurs atomiques, pas de listes dans une colonne | `telephones = '06…, 07…'` |
| **2NF** | chaque colonne dépend de **toute** la clé | dans `lignes(commande_id, produit_id, nom_produit)`, `nom_produit` ne dépend que du produit |
| **3NF** | aucune colonne ne dépend d'une autre colonne non clé | `ville` et `code_postal` dans la table client : la ville dépend du code postal |

Sans normalisation : des **anomalies** de mise à jour (le nom d'un produit changé à un
endroit et pas à l'autre), d'insertion et de suppression.

La **dénormalisation** est un choix conscient pour la performance de lecture : stocker un
total de commande précalculé, un compteur de commentaires, une copie du nom dans un index de
recherche. Elle se paie en logique de synchronisation. Et certaines « redondances » n'en sont
pas : le **prix au moment de la commande** doit être copié dans la ligne de commande, car le
prix du catalogue changera.

## À retenir

- `left join` + `where droite.id is null` : trouver ce qui manque ; `count(*)` compte aussi les lignes vides du `left join`.
- Joindre deux relations « un à plusieurs » multiplie les lignes : agréger d'abord.
- `where` filtre avant le regroupement, `having` après ; fonctions de fenêtre pour classer et cumuler sans fusionner.
- Clés étrangères (avec un index), table d'association pour le plusieurs à plusieurs, `on delete` choisi.
- Normaliser jusqu'à la 3NF ; dénormaliser par choix, pour la lecture.
