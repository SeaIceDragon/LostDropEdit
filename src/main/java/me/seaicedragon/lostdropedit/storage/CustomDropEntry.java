package me.seaicedragon.lostdropedit.storage;

import org.bukkit.inventory.ItemStack;

public final class CustomDropEntry {

    private final long id;
    private final String mobKey;
    private final ItemStack item;
    private final int chancePerMillion;
    private final int minAmount;
    private final int maxAmount;
    private final boolean enabled;
    private final int sortOrder;

    public CustomDropEntry(long id, String mobKey, ItemStack item, int chancePerMillion, int minAmount, int maxAmount, boolean enabled, int sortOrder) {
        this.id = id;
        this.mobKey = mobKey;
        this.item = item.clone();
        this.chancePerMillion = chancePerMillion;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
    }

    public long id() {
        return id;
    }

    public String mobKey() {
        return mobKey;
    }

    public ItemStack item() {
        return item.clone();
    }

    public int chancePerMillion() {
        return chancePerMillion;
    }

    public int minAmount() {
        return minAmount;
    }

    public int maxAmount() {
        return maxAmount;
    }

    public boolean enabled() {
        return enabled;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public CustomDropEntry withItem(ItemStack updatedItem) {
        return new CustomDropEntry(id, mobKey, updatedItem, chancePerMillion, minAmount, maxAmount, enabled, sortOrder);
    }

    public CustomDropEntry withChancePerMillion(int updatedChance) {
        return new CustomDropEntry(id, mobKey, item, updatedChance, minAmount, maxAmount, enabled, sortOrder);
    }

    public CustomDropEntry withMinAmount(int updatedMinAmount) {
        return new CustomDropEntry(id, mobKey, item, chancePerMillion, updatedMinAmount, maxAmount, enabled, sortOrder);
    }

    public CustomDropEntry withMaxAmount(int updatedMaxAmount) {
        return new CustomDropEntry(id, mobKey, item, chancePerMillion, minAmount, updatedMaxAmount, enabled, sortOrder);
    }

    public CustomDropEntry withEnabled(boolean updatedEnabled) {
        return new CustomDropEntry(id, mobKey, item, chancePerMillion, minAmount, maxAmount, updatedEnabled, sortOrder);
    }

    public CustomDropEntry withSortOrder(int updatedSortOrder) {
        return new CustomDropEntry(id, mobKey, item, chancePerMillion, minAmount, maxAmount, enabled, updatedSortOrder);
    }
}
