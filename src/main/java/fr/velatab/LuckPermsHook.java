package fr.velatab;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.platform.PlayerAdapter;
import org.bukkit.entity.Player;

/**
 * Lecture des grades LuckPerms. Cette classe n'est chargée que si LuckPerms est présent
 * (aucune référence directe depuis le reste du plugin tant qu'il est absent).
 */
public final class LuckPermsHook {

    private final LuckPerms lp;
    private final PlayerAdapter<Player> adapter;

    public LuckPermsHook() {
        this.lp = LuckPermsProvider.get();
        this.adapter = lp.getPlayerAdapter(Player.class);
    }

    public Meta meta(Player p) {
        CachedMetaData data = adapter.getMetaData(p);
        String prefix = data.getPrefix();
        String suffix = data.getSuffix();
        String group = data.getPrimaryGroup();
        return new Meta(prefix == null ? "" : prefix, suffix == null ? "" : suffix, group == null ? "default" : group);
    }

    /** Poids du groupe LuckPerms (0 si inconnu). */
    public int weight(String group) {
        Group g = lp.getGroupManager().getGroup(group);
        return g == null ? 0 : g.getWeight().orElse(0);
    }
}
