package fr.velatab;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VelaTabPlugin extends JavaPlugin implements TabExecutor {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private TabManager tab;
    private LuckPermsHook luckPerms;
    private PapiHook papi;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupHooks();
        tab = new TabManager(this);
        tab.cleanStale();
        tab.reloadSettings();
        getServer().getPluginManager().registerEvents(new TabListener(this), this);

        PluginCommand cmd = getCommand("velatab");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        restartTask();
        tab.refreshAll();
    }

    @Override
    public void onDisable() {
        if (tab != null) {
            tab.shutdown();
        }
    }

    private void restartTask() {
        Bukkit.getScheduler().cancelTasks(this);
        long ticks = Math.max(5, getConfig().getLong("refresh-ticks", 20));
        Bukkit.getScheduler().runTaskTimer(this, tab::refreshAll, ticks, ticks);
    }

    private void setupHooks() {
        luckPerms = null;
        papi = null;
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                luckPerms = new LuckPermsHook();
                getLogger().info("LuckPerms détecté : grades, préfixes et poids utilisés.");
            } catch (Throwable t) {
                getLogger().warning("LuckPerms présent mais inutilisable : " + t);
            }
        } else {
            getLogger().info("LuckPerms absent : grades via les permissions velatab.group.<grade>.");
        }
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                papi = new PapiHook();
                getLogger().info("PlaceholderAPI détecté : les placeholders %...% sont activés.");
            } catch (Throwable t) {
                getLogger().warning("PlaceholderAPI présent mais inutilisable : " + t);
            }
        }
    }

    public TabManager tab() {
        return tab;
    }

    /** Données LuckPerms du joueur (null sans LuckPerms). */
    public Meta lpMeta(Player p) {
        if (luckPerms == null) {
            return null;
        }
        try {
            return luckPerms.meta(p);
        } catch (Throwable t) {
            return null;
        }
    }

    public int lpWeight(String group) {
        if (luckPerms == null) {
            return 0;
        }
        try {
            return luckPerms.weight(group);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Placeholders PlaceholderAPI puis codes &a convertis en MiniMessage. */
    public String papi(Player p, String text) {
        String result = text;
        if (papi != null) {
            try {
                result = papi.apply(p, text);
            } catch (Throwable t) {
                getLogger().warning("Erreur PlaceholderAPI : " + t);
            }
        }
        return Text.legacyToMini(result);
    }

    // ---------------------------------------------------------------- messages

    private Component msg(String key, Object... kv) {
        String text = getConfig().getString("messages." + key, "<red>Message manquant : " + key);
        TagResolver.Builder b = TagResolver.builder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            b.resolver(Placeholder.unparsed(String.valueOf(kv[i]), String.valueOf(kv[i + 1])));
        }
        return mm.deserialize(text, b.build());
    }

    private void send(CommandSender to, String key, Object... kv) {
        to.sendMessage(msg(key, kv));
    }

    // ---------------------------------------------------------------- commandes

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("velatab.admin")) {
            send(sender, "no-permission");
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                reloadConfig();
                setupHooks();
                tab.reloadSettings();
                restartTask();
                tab.refreshAll();
                send(sender, "reloaded");
            }
            case "refresh" -> {
                tab.refreshAll();
                send(sender, "refreshed");
            }
            case "info" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    send(sender, "unknown-player", "player", args.length > 1 ? args[1] : "?");
                    return true;
                }
                Rank r = tab.rank(target.getUniqueId());
                if (r == null) {
                    r = tab.resolve(target);
                }
                TextColor color = r.color();
                sender.sendMessage(msg("info", "player", target.getName(), "group", r.group(),
                        "display", r.display(), "weight", r.weight())
                        .append(Component.text(" ■■■ " + color.asHexString(), color)));
            }
            default -> send(sender, "help");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("velatab.admin")) {
            return out;
        }
        if (args.length == 1) {
            out.addAll(List.of("reload", "refresh", "info"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
