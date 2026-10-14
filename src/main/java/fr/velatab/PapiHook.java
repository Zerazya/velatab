package fr.velatab;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/**
 * Remplacement des placeholders PlaceholderAPI (%player_name%, %vault_eco_balance%, etc.).
 * Cette classe n'est chargée que si PlaceholderAPI est présent.
 */
public final class PapiHook {

    public String apply(Player p, String text) {
        return PlaceholderAPI.setPlaceholders(p, text);
    }
}
