package fr.velatab;

import net.kyori.adventure.text.format.TextColor;

/** Grade d'un joueur : préfixe/suffixe en MiniMessage, couleur et poids (ordre dans le tab). */
public record Rank(String group, String display, int weight, String prefixMini, String suffixMini, TextColor color) {
}
