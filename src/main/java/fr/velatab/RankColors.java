package fr.velatab;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Détection et lecture des couleurs de grade. */
final class RankColors {

    private static final Pattern MINI = Pattern.compile("<(#[0-9a-fA-F]{6}|[a-z_]+)>");

    private RankColors() {
    }

    /** "red", "dark_aqua", "#ff8800" ou "&c" / "&#ff8800". */
    static TextColor parse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String v = s.trim();
        if (v.startsWith("#")) {
            return TextColor.fromHexString(v);
        }
        if (v.startsWith("&") || v.startsWith("§")) {
            return fromText(v);
        }
        return NamedTextColor.NAMES.value(v.toLowerCase(Locale.ROOT));
    }

    /** Dernière couleur utilisée dans un texte (codes &a, &#rrggbb ou balises MiniMessage), sinon null. */
    static TextColor fromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        TextColor best = null;
        int bestPos = -1;

        Matcher legacy = Text.LEGACY.matcher(text);
        while (legacy.find()) {
            TextColor c = legacyColor(legacy.group(1));
            if (c != null) {
                best = c;
                bestPos = legacy.start();
            }
        }
        Matcher mini = MINI.matcher(text);
        while (mini.find()) {
            String name = mini.group(1);
            TextColor c = name.startsWith("#") ? TextColor.fromHexString(name) : NamedTextColor.NAMES.value(name);
            if (c != null && mini.start() > bestPos) {
                best = c;
                bestPos = mini.start();
            }
        }
        return best;
    }

    private static TextColor legacyColor(String code) {
        if (code.startsWith("#")) {
            return TextColor.fromHexString(code);
        }
        String tag = Text.tagName(code.charAt(0));
        return tag == null ? null : NamedTextColor.NAMES.value(tag);
    }
}
