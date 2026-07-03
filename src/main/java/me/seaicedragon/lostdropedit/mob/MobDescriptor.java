package me.seaicedragon.lostdropedit.mob;

import org.bukkit.Material;

public record MobDescriptor(
    String key,
    MobSource source,
    String internalName,
    String displayName,
    Material icon,
    String searchText
) {
}
