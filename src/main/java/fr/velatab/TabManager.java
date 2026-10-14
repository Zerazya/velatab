package fr.velatab;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Liste des joueurs (tab) : en-tête/pied de page, noms colorés selon le grade, tri par grade,
 * et tag au-dessus de la tête (équipes du scoreboard principal) de la même couleur.
 */
public final class TabManager {

    private final VelaTabPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private final Map<UUID, Rank> ranks = new ConcurrentHashMap<>();
    private final Map<String, Team> teams = new HashMap<>();
    private final Map<String, String> teamSig = new HashMap<>();
    private final Map<UUID, String> playerTeam = new HashMap<>();
    private final Map<UUID, Component> lastListName = new HashMap<>();
    private final Map<UUID, List<Component>> lastHeaderFooter = new HashMap<>();

    private boolean sortByRank = true;
    private boolean nametag = true;
    private boolean showPrefix = true;
    private boolean showSuffix = false;
    private boolean collision = true;
    private String playerFormat = "";
    private List<String> header = List.of();
    private List<String> footer = List.of();
    private ZoneId zone = ZoneId.of("Europe/Paris");
    private DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
    private DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private TextColor defaultColor = NamedTextColor.WHITE;

    public TabManager(VelaTabPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- réglages

    public void reloadSettings() {
        var c = plugin.getConfig();
        sortByRank = c.getBoolean("tab.sort-by-rank", true);
        playerFormat = c.getString("tab.player-format", "");
        header = c.getStringList("tab.header");
        footer = c.getStringList("tab.footer");
        nametag = c.getBoolean("nametag.enabled", true);
        showPrefix = c.getBoolean("nametag.show-prefix", true);
        showSuffix = c.getBoolean("nametag.show-suffix", false);
        collision = c.getBoolean("nametag.collision", true);
        TextColor def = RankColors.parse(c.getString("default-color", "white"));
        defaultColor = def == null ? NamedTextColor.WHITE : def;
        try {
            zone = ZoneId.of(c.getString("time.zone", "Europe/Paris"));
        } catch (Exception ex) {
            zone = ZoneId.of("Europe/Paris");
        }
        try {
            timeFmt = DateTimeFormatter.ofPattern(c.getString("time.format", "HH:mm"));
            dateFmt = DateTimeFormatter.ofPattern(c.getString("time.date-format", "dd/MM/yyyy"));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Format de date invalide dans la config.");
        }
        lastListName.clear();
        lastHeaderFooter.clear();
    }

    /** Supprime les équipes laissées par un arrêt brutal. */
    public void cleanStale() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : new ArrayList<>(sb.getTeams())) {
            if (t.getName().matches("vt\\d{3}[0-9a-f]{1,8}")) {
                t.unregister();
            }
        }
    }

    // ---------------------------------------------------------------- grades

    public Rank rank(UUID id) {
        return ranks.get(id);
    }

    public Rank fallback() {
        return new Rank("default", "Default", 0, "", "", defaultColor);
    }

    public Rank resolve(Player p) {
        String group;
        String lpPrefix = "";
        String lpSuffix = "";
        int lpWeight = 0;
        Meta m = plugin.lpMeta(p);
        if (m != null) {
            group = m.group();
            lpPrefix = m.prefix();
            lpSuffix = m.suffix();
            lpWeight = plugin.lpWeight(group);
        } else {
            group = permissionGroup(p);
        }
        ConfigurationSection groups = plugin.getConfig().getConfigurationSection("groups");
        ConfigurationSection gs = groups == null ? null : groups.getConfigurationSection(group);

        String prefix = gs != null && gs.isString("prefix") ? gs.getString("prefix", "") : lpPrefix;
        String suffix = gs != null && gs.isString("suffix") ? gs.getString("suffix", "") : lpSuffix;
        TextColor color = gs != null && gs.isString("color") ? RankColors.parse(gs.getString("color")) : null;
        if (color == null) {
            color = RankColors.fromText(prefix);
        }
        if (color == null) {
            color = defaultColor;
        }
        int weight = gs != null && gs.contains("weight") ? gs.getInt("weight") : lpWeight;
        String display = gs != null && gs.isString("display") ? gs.getString("display", group) : capitalize(group);
        return new Rank(group, display, weight, Text.legacyToMini(prefix), Text.legacyToMini(suffix), color);
    }

    /** Sans LuckPerms : premier grade configuré (par poids décroissant) pour lequel le joueur a velatab.group.<nom>. */
    private String permissionGroup(Player p) {
        ConfigurationSection groups = plugin.getConfig().getConfigurationSection("groups");
        if (groups == null) {
            return "default";
        }
        String best = "default";
        int bestWeight = Integer.MIN_VALUE;
        for (String g : groups.getKeys(false)) {
            ConfigurationSection gs = groups.getConfigurationSection(g);
            int w = gs == null ? 0 : gs.getInt("weight", 0);
            if (w > bestWeight && p.hasPermission("velatab.group." + g)) {
                best = g;
                bestWeight = w;
            }
        }
        return best;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------------------------------------------------------------- mise à jour

    public void refreshAll() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player p : Bukkit.getOnlinePlayers()) {
            refresh(p, sb);
        }
        cleanupTeams();
    }

    public void refresh(Player p) {
        refresh(p, Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void refresh(Player p, Scoreboard sb) {
        Rank r;
        try {
            r = resolve(p);
        } catch (Exception ex) {
            r = fallback();
        }
        ranks.put(p.getUniqueId(), r);
        applyTeam(p, r, sb);
        applyListName(p, r);
        sendHeaderFooter(p, r);
    }

    public void remove(Player p) {
        UUID id = p.getUniqueId();
        ranks.remove(id);
        lastListName.remove(id);
        lastHeaderFooter.remove(id);
        String cur = playerTeam.remove(id);
        if (cur != null) {
            Team t = teams.get(cur);
            if (t != null) {
                t.removeEntry(p.getName());
            }
        }
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playerListName(null);
            p.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }
        for (Team t : new ArrayList<>(teams.values())) {
            try {
                t.unregister();
            } catch (IllegalStateException ignored) {
                // déjà supprimée
            }
        }
        teams.clear();
        teamSig.clear();
        playerTeam.clear();
    }

    // ---------------------------------------------------------------- tag + tri (équipes)

    private String teamName(Rank r) {
        int order = sortByRank ? Math.max(0, Math.min(999, 999 - r.weight())) : 0;
        int h = Objects.hash(r.group(), r.prefixMini(), r.suffixMini(), r.color().asHexString());
        return "vt" + String.format(Locale.ROOT, "%03d", order) + Integer.toHexString(h);
    }

    private void applyTeam(Player p, Rank r, Scoreboard sb) {
        UUID id = p.getUniqueId();
        if (!sortByRank && !nametag) {
            String cur = playerTeam.remove(id);
            if (cur != null && teams.get(cur) != null) {
                teams.get(cur).removeEntry(p.getName());
            }
            return;
        }
        String name = teamName(r);
        Team t = teams.get(name);
        if (t == null) {
            t = sb.getTeam(name);
            if (t == null) {
                t = sb.registerNewTeam(name);
            }
            teams.put(name, t);
        }
        String sig = r.prefixMini() + "|" + r.suffixMini() + "|" + r.color().asHexString() + "|" + nametag + showPrefix + showSuffix + collision;
        if (!sig.equals(teamSig.get(name))) {
            configure(t, r);
            teamSig.put(name, sig);
        }
        String cur = playerTeam.get(id);
        if (!name.equals(cur) || !t.hasEntry(p.getName())) {
            if (cur != null && !cur.equals(name)) {
                Team old = teams.get(cur);
                if (old != null) {
                    old.removeEntry(p.getName());
                }
            }
            t.addEntry(p.getName());
            playerTeam.put(id, name);
        }
    }

    private void configure(Team t, Rank r) {
        if (nametag) {
            t.prefix(showPrefix ? mm.deserialize(r.prefixMini()) : Component.empty());
            t.suffix(showSuffix ? mm.deserialize(r.suffixMini()) : Component.empty());
            t.color(NamedTextColor.nearestTo(r.color()));
        }
        t.setOption(Team.Option.COLLISION_RULE, collision ? Team.OptionStatus.ALWAYS : Team.OptionStatus.NEVER);
    }

    private void cleanupTeams() {
        Iterator<Map.Entry<String, Team>> it = teams.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Team> e = it.next();
            try {
                if (e.getValue().getEntries().isEmpty()) {
                    e.getValue().unregister();
                    teamSig.remove(e.getKey());
                    it.remove();
                }
            } catch (IllegalStateException ex) {
                teamSig.remove(e.getKey());
                it.remove();
            }
        }
    }

    // ---------------------------------------------------------------- nom dans le tab, en-tête et pied de page

    private String fmt(String s, Rank r) {
        return s.replace("<rank-color>", "<" + r.color().asHexString() + ">");
    }

    private TagResolver resolvers(Player p, Rank r) {
        double tps = Bukkit.getTPS().length > 0 ? Math.min(20.0, Bukkit.getTPS()[0]) : 20.0;
        NamedTextColor tpsColor = tps >= 18 ? NamedTextColor.GREEN : (tps >= 15 ? NamedTextColor.YELLOW : NamedTextColor.RED);
        int ping = p.getPing();
        NamedTextColor pingColor = ping < 80 ? NamedTextColor.GREEN : (ping < 150 ? NamedTextColor.YELLOW : NamedTextColor.RED);
        ZonedDateTime now = ZonedDateTime.now(zone);
        return TagResolver.builder()
                .resolver(Placeholder.unparsed("player", p.getName()))
                .resolver(Placeholder.unparsed("name", p.getName()))
                .resolver(Placeholder.parsed("prefix", r.prefixMini()))
                .resolver(Placeholder.parsed("suffix", r.suffixMini()))
                .resolver(Placeholder.unparsed("rank", r.display()))
                .resolver(Placeholder.unparsed("group", r.group()))
                .resolver(Placeholder.unparsed("online", String.valueOf(Bukkit.getOnlinePlayers().size())))
                .resolver(Placeholder.unparsed("max", String.valueOf(Bukkit.getMaxPlayers())))
                .resolver(Placeholder.unparsed("world", p.getWorld().getName()))
                .resolver(Placeholder.component("tps", Component.text(String.format(Locale.ROOT, "%.1f", tps), tpsColor)))
                .resolver(Placeholder.component("ping", Component.text(ping + " ms", pingColor)))
                .resolver(Placeholder.unparsed("time", timeFmt.format(now)))
                .resolver(Placeholder.unparsed("date", dateFmt.format(now)))
                .build();
    }

    private void applyListName(Player p, Rank r) {
        UUID id = p.getUniqueId();
        if (playerFormat == null || playerFormat.isBlank()) {
            if (lastListName.remove(id) != null) {
                p.playerListName(null);
            }
            return;
        }
        Component c = mm.deserialize(plugin.papi(p, fmt(playerFormat, r)), resolvers(p, r));
        if (!c.equals(lastListName.get(id))) {
            p.playerListName(c);
            lastListName.put(id, c);
        }
    }

    private void sendHeaderFooter(Player p, Rank r) {
        if (header.isEmpty() && footer.isEmpty()) {
            return;
        }
        TagResolver res = resolvers(p, r);
        Component h = join(header, p, r, res);
        Component f = join(footer, p, r, res);
        List<Component> now = List.of(h, f);
        if (!now.equals(lastHeaderFooter.get(p.getUniqueId()))) {
            p.sendPlayerListHeaderAndFooter(h, f);
            lastHeaderFooter.put(p.getUniqueId(), now);
        }
    }

    private Component join(List<String> lines, Player p, Rank r, TagResolver res) {
        List<Component> out = new ArrayList<>();
        for (String line : lines) {
            out.add(mm.deserialize(plugin.papi(p, fmt(line, r)), res));
        }
        return Component.join(JoinConfiguration.newlines(), out);
    }
}
