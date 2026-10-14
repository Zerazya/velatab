package fr.velatab;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conversion des codes de couleur classiques (&a, &#ff8800, §c) vers des balises MiniMessage. */
public final class Text {

    /** Code de couleur : &x, §x, &#rrggbb. */
    public static final Pattern LEGACY = Pattern.compile("(?i)[&§](#[0-9a-f]{6}|[0-9a-fk-or])");

    private static final Map<Character, String> TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"), Map.entry('3', "dark_aqua"),
            Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"), Map.entry('6', "gold"), Map.entry('7', "gray"),
            Map.entry('8', "dark_gray"), Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"), Map.entry('f', "white"),
            Map.entry('k', "obfuscated"), Map.entry('l', "bold"), Map.entry('m', "strikethrough"),
            Map.entry('n', "underlined"), Map.entry('o', "italic"), Map.entry('r', "reset"));

    private Text() {
    }

    public static String legacyToMini(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher m = LEGACY.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String code = m.group(1);
            String tag;
            if (code.startsWith("#")) {
                tag = "<" + code + ">";
            } else {
                String name = TAGS.get(Character.toLowerCase(code.charAt(0)));
                tag = name == null ? "" : "<" + name + ">";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(tag));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Nom de balise MiniMessage d'un code (&a -> green), null si inconnu. */
    public static String tagName(char c) {
        return TAGS.get(Character.toLowerCase(c));
    }

    public static boolean isColorCode(String code) {
        return code.length() == 1 && "0123456789abcdefABCDEF".indexOf(code.charAt(0)) >= 0;
    }
}
