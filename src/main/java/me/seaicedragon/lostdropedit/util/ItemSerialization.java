package me.seaicedragon.lostdropedit.util;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;

public final class ItemSerialization {

    private ItemSerialization() {
    }

    public static String serialize(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static ItemStack deserialize(String value) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(value));
    }
}
