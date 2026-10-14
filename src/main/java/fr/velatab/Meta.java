package fr.velatab;

/** Informations de grade d'un joueur (venant de LuckPerms, ou vides sans LuckPerms). */
public record Meta(String prefix, String suffix, String group) {

    public static final Meta EMPTY = new Meta("", "", "default");
}
