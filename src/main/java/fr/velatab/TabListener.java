package fr.velatab;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Connexions et chat coloré selon le grade. */
public final class TabListener implements Listener {

    private final VelaTabPlugin plugin;
    private final TabManager tab;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final MiniMessage restricted = MiniMessage.builder()
            .tags(TagResolver.builder().resolvers(StandardTags.color(), StandardTags.decorations(),
                    StandardTags.gradient(), StandardTags.rainbow(), StandardTags.reset()).build())
            .build();

    public TabListener(VelaTabPlugin plugin) {
        this.plugin = plugin;
        this.tab = plugin.tab();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        tab.refresh(p);
        // une seconde passe : les données LuckPerms / PlaceholderAPI peuvent arriver juste après
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                tab.refresh(p);
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        tab.remove(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("chat.enabled", true)) {
            return;
        }
        Player p = e.getPlayer();
        Rank r = tab.rank(p.getUniqueId());
        if (r == null) {
            r = tab.fallback();
        }
        Component msg = e.message();
        if (p.hasPermission("velatab.chat.color")) {
            String plain = PlainTextComponentSerializer.plainText().serialize(msg);
            msg = restricted.deserialize(Text.legacyToMini(plain));
        }
        String format = cfg.getString("chat.format", "<prefix><rank-color><name><suffix><dark_gray> » <white><message>")
                .replace("<rank-color>", "<" + r.color().asHexString() + ">");
        Component out = mm.deserialize(format, TagResolver.builder()
                .resolver(Placeholder.parsed("prefix", r.prefixMini()))
                .resolver(Placeholder.parsed("suffix", r.suffixMini()))
                .resolver(Placeholder.unparsed("name", p.getName()))
                .resolver(Placeholder.unparsed("player", p.getName()))
                .resolver(Placeholder.unparsed("rank", r.display()))
                .resolver(Placeholder.unparsed("group", r.group()))
                .resolver(Placeholder.component("message", msg))
                .build());
        e.message(msg);
        e.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) -> out));
    }
}
