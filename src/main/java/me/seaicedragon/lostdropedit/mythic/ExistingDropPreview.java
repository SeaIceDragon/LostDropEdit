package me.seaicedragon.lostdropedit.mythic;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record ExistingDropPreview(
    String id,
    String title,
    ItemStack icon,
    List<String> description,
    boolean importable,
    ItemStack importItem
) {
}
