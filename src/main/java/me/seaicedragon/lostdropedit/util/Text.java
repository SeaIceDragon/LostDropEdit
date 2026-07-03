package me.seaicedragon.lostdropedit.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Text {

    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.######");
    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("(?i)(?:[&§][0-9A-FK-OR])|(?:[&§]x(?:[&§][0-9A-F]){6})");

    private Text() {
    }

    public static String toTitleCase(String input) {
        String[] split = input.toLowerCase(Locale.ROOT).split("_");
        List<String> parts = new ArrayList<>(split.length);
        for (String part : split) {
            if (part.isEmpty()) {
                continue;
            }
            parts.add(Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return String.join(" ", parts);
    }

    public static String stripFormatting(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String withoutLegacy = LEGACY_COLOR_PATTERN.matcher(input).replaceAll("");
        return withoutLegacy
            .replaceAll("<[^>]+>", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    public static String toTurkishMobName(String input) {
        return switch (input.toUpperCase(Locale.ROOT)) {
            case "ALLAY" -> "Allay";
            case "ARMADILLO" -> "Armadillo";
            case "AXOLOTL" -> "Aksolotl";
            case "BAT" -> "Yarasa";
            case "BEE" -> "Arı";
            case "BLAZE" -> "Alev";
            case "BOGGED" -> "Bogged";
            case "BREEZE" -> "Breeze";
            case "CAMEL" -> "Deve";
            case "CAT" -> "Kedi";
            case "CAVE_SPIDER" -> "Mağara Örümceği";
            case "CHICKEN" -> "Tavuk";
            case "COD" -> "Morina";
            case "COW" -> "İnek";
            case "CREEPER" -> "Creeper";
            case "DOLPHIN" -> "Yunus";
            case "DONKEY" -> "Eşek";
            case "DROWNED" -> "Boğulmuş";
            case "ELDER_GUARDIAN" -> "Yaşlı Guardian";
            case "ENDER_DRAGON" -> "Ender Ejderhası";
            case "ENDERMAN" -> "Enderman";
            case "ENDERMITE" -> "Endermite";
            case "EVOKER" -> "Evoker";
            case "FOX" -> "Tilki";
            case "FROG" -> "Kurbağa";
            case "GHAST" -> "Ghast";
            case "GIANT" -> "Dev Zombi";
            case "GLOW_SQUID" -> "Parlayan Mürekkep Balığı";
            case "GOAT" -> "Keçi";
            case "GUARDIAN" -> "Guardian";
            case "HOGLIN" -> "Hoglin";
            case "HORSE" -> "At";
            case "HUSK" -> "Husk";
            case "ILLUSIONER" -> "Illusioner";
            case "IRON_GOLEM" -> "Demir Golem";
            case "LLAMA" -> "Lama";
            case "MAGMA_CUBE" -> "Magma Küp";
            case "MOOSHROOM", "MUSHROOM_COW" -> "Mantar İnek";
            case "MULE" -> "Katır";
            case "OCELOT" -> "Oselot";
            case "PANDA" -> "Panda";
            case "PARROT" -> "Papağan";
            case "PHANTOM" -> "Phantom";
            case "PIG" -> "Domuz";
            case "PIGLIN" -> "Piglin";
            case "PIGLIN_BRUTE" -> "Piglin Brute";
            case "PILLAGER" -> "Pillager";
            case "POLAR_BEAR" -> "Kutup Ayısı";
            case "PUFFERFISH" -> "Balon Balığı";
            case "RABBIT" -> "Tavşan";
            case "RAVAGER" -> "Ravager";
            case "SALMON" -> "Somon";
            case "SHEEP" -> "Koyun";
            case "SHULKER" -> "Shulker";
            case "SILVERFISH" -> "Silverfish";
            case "SKELETON" -> "İskelet";
            case "SKELETON_HORSE" -> "İskelet Atı";
            case "SLIME" -> "Slime";
            case "SNIFFER" -> "Sniffer";
            case "SNOWMAN", "SNOW_GOLEM" -> "Kar Golemi";
            case "SPIDER" -> "Örümcek";
            case "SQUID" -> "Mürekkep Balığı";
            case "STRAY" -> "Stray";
            case "STRIDER" -> "Strider";
            case "TADPOLE" -> "İribaş";
            case "TRADER_LLAMA" -> "Tüccar Laması";
            case "TROPICAL_FISH" -> "Tropik Balık";
            case "TURTLE" -> "Kaplumbağa";
            case "VEX" -> "Vex";
            case "VILLAGER" -> "Köylü";
            case "VINDICATOR" -> "Vindicator";
            case "WANDERING_TRADER" -> "Gezgin Tüccar";
            case "WARDEN" -> "Warden";
            case "WITCH" -> "Cadı";
            case "WITHER" -> "Wither";
            case "WITHER_SKELETON" -> "Wither İskeleti";
            case "WOLF" -> "Kurt";
            case "ZOGLIN" -> "Zoglin";
            case "ZOMBIE" -> "Zombi";
            case "ZOMBIE_HORSE" -> "Zombi Atı";
            case "ZOMBIE_VILLAGER" -> "Zombi Köylü";
            case "ZOMBIFIED_PIGLIN", "PIG_ZOMBIE" -> "Zombileşmiş Piglin";
            default -> toTitleCase(input);
        };
    }

    public static Component text(String text, NamedTextColor color) {
        return Component.text(text, color);
    }

    public static String chanceAsPercent(int chancePerMillion) {
        return PERCENT_FORMAT.format(chancePerMillion / 10_000.0D) + "%";
    }
}
