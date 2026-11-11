---
id: linux.n3.administrer
title: Services, réseau, SSH et tâches planifiées
skills: [systemd, reseau-commandes, ssh, planification]
duration_min: 16
---

# Services, réseau, SSH et tâches planifiées

## systemd : faire tourner un service

Sur presque toutes les distributions, **systemd** est le premier processus (PID 1). Il
démarre les services, les relance s'ils plantent et collecte leurs journaux. Un service se
décrit dans un fichier *unit* :

```ini
# /etc/systemd/system/api.service
[Unit]
Description=API DevForge
After=network-online.target

[Service]
User=api
WorkingDirectory=/opt/api
EnvironmentFile=/etc/api/env
ExecStart=/usr/bin/java -jar /opt/api/api.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload          # relire les fichiers unit
sudo systemctl enable --now api       # démarrer maintenant ET à chaque démarrage
systemctl status api                  # état, PID, dernières lignes de journal
journalctl -u api -f                  # suivre le journal du service
journalctl -u api --since "10 min ago" -p err   # seulement les erreurs récentes
```

Pièges classiques :

- `start` démarre maintenant, `enable` au prochain démarrage : il faut souvent les deux
  (`enable --now`).
- Le service ne tourne **pas** dans ton shell : il n'a ni ton `PATH`, ni tes variables, ni
  ton dossier courant. D'où les chemins absolus, `WorkingDirectory` et `EnvironmentFile`.
- Ne jamais faire tourner un service en `root` s'il n'en a pas besoin : `User=api`.

## Le réseau en ligne de commande

| Question | Commande |
|---|---|
| Quelles adresses a ma machine ? | `ip addr` |
| Par où sortent les paquets ? | `ip route` |
| Quel processus écoute sur quel port ? | `ss -tlnp` (t = TCP, l = en écoute, n = numérique, p = processus) |
| Ce nom se résout-il, et vers quoi ? | `dig api.exemple.com` |
| Le service répond-il, et en combien de temps ? | `curl -w "%{http_code} %{time_connect} %{time_total}\n" -o /dev/null -s URL` |
| Par où passent les paquets jusqu'à la cible ? | `traceroute` ou `mtr` |
| Que circule-t-il vraiment sur le réseau ? | `tcpdump -i eth0 port 443` |

Mesure réelle de `curl -w` sur un serveur local : `code 200, connexion 0.000361s, total
0.005926s`. Sur un service distant, comparer `time_connect` (réseau) et `time_total`
(réseau + traitement) dit où passe le temps.

**Refusé ou délai dépassé ?** C'est un diagnostic d'expert :

- `Connection refused`, immédiat (réel : `Failed to connect to 127.0.0.1 port 9 after 0 ms`) :
  la machine est joignable et a répondu, mais **rien n'écoute** sur ce port. Le service est
  arrêté, ou écoute sur un autre port ou une autre interface (`127.0.0.1` au lieu de
  `0.0.0.0`).
- Délai dépassé (*timeout*) : personne ne répond. Un pare-feu jette les paquets, la machine
  est éteinte, ou la route n'existe pas.

## SSH et les clés

```bash
ssh-keygen -t ed25519 -C "ada@portable"     # crée ~/.ssh/id_ed25519 (privée) et .pub
ssh-copy-id ada@serveur                      # ajoute la clé publique dans ~/.ssh/authorized_keys du serveur
ssh -L 5433:localhost:5432 ada@serveur       # tunnel : localhost:5433 chez toi → la base du serveur
```

La clé **privée** ne quitte jamais ta machine (permissions `600`, protégée par une phrase
de passe) ; la clé **publique** se distribue. Le serveur envoie un défi que seule la clé
privée sait signer : aucun secret ne transite.

Un fichier `~/.ssh/config` évite de tout retaper :

```
Host prod
    HostName 203.0.113.10
    User ada
    IdentityFile ~/.ssh/id_ed25519
```

Ensuite, `ssh prod` suffit.

## Tâches planifiées

Le format **cron** : minute, heure, jour du mois, mois, jour de la semaine.

```
# m  h  jdm mois jds  commande
30   2  *   *    1    /opt/scripts/sauvegarde.sh     # chaque lundi à 2 h 30
*/5  *  *   *    *    /opt/scripts/sonde.sh          # toutes les 5 minutes
0    0  1   *    *    /opt/scripts/facturation.sh    # le 1er de chaque mois à minuit
```

Pièges : cron a un environnement minimal (chemins absolus), sa sortie se perd si on ne la
redirige pas, et rien ne prévient quand une tâche échoue. Les **timers systemd** règlent
une partie de ces problèmes : journal dans `journalctl`, `Persistent=true` pour rattraper
une exécution manquée pendant que la machine était éteinte. Et une tâche planifiée
critique doit être **surveillée** (une alerte si la sauvegarde n'a pas tourné depuis
26 heures).

## Les paquets

`apt install` / `dnf install` installent depuis des dépôts signés ; `apt list
--upgradable` liste les mises à jour. Sur un serveur, les mises à jour de sécurité doivent
être automatiques (`unattended-upgrades`) ou faire partie d'une routine, et l'image de
base d'un conteneur doit être reconstruite régulièrement pour les recevoir.

## À retenir

- systemd : un fichier unit, `enable --now`, `status`, `journalctl -u`. Le service n'a pas ton environnement.
- `ss -tlnp`, `dig`, `curl -w`, `ip` ; « refusé » = rien n'écoute, « délai dépassé » = rien ne répond.
- SSH par clé ed25519 : la privée reste chez toi, la publique va dans `authorized_keys`.
- cron (5 champs) ou timers systemd ; toujours surveiller les tâches planifiées.
