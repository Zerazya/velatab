# VelaTab

Pour Paper 26.2 : **tab**, **tag au-dessus de la tête** et **chat** colorés selon le grade.

- Tab : en-tête et pied de page (joueurs en ligne, ping, TPS, monde, heure...), noms colorés selon le grade, joueurs triés par grade.
- Tag : même couleur et même préfixe que le grade (équipes du scoreboard principal).
- Chat : `[Grade] Pseudo » message`, pseudo de la couleur du grade. Désactivez `chat.enabled` si vous avez déjà ChatFormat.
- LuckPerms (préfixe, suffixe, groupe principal, poids) et PlaceholderAPI sont facultatifs. Sans LuckPerms : permissions `velatab.group.<grade>`.

Commandes : `/velatab reload|refresh|info [joueur]` (permission `velatab.admin`).
Permission `velatab.chat.color` : couleurs `&a` / `&#ff8800` dans le chat.

Note : le tag utilise le scoreboard principal ; un plugin qui donne un scoreboard personnel (sidebar) aux joueurs peut masquer les couleurs du tag.

Build : `./gradlew build` (Java 25) ou GitHub Actions.
