package me.seaicedragon.lostdropedit.drop;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import me.seaicedragon.lostdropedit.LostDropEditPlugin;
import me.seaicedragon.lostdropedit.mythic.MythicBridge;
import me.seaicedragon.lostdropedit.storage.CustomDropEntry;
import me.seaicedragon.lostdropedit.storage.DropMode;
import me.seaicedragon.lostdropedit.storage.SQLiteStorage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class DropService implements Listener {

    private static final int CHANCE_SCALE = 1_000_000;

    private final LostDropEditPlugin plugin;
    private final SQLiteStorage storage;
    private final MythicBridge mythicBridge;

    public DropService(LostDropEditPlugin plugin, SQLiteStorage storage, MythicBridge mythicBridge) {
        this.plugin = plugin;
        this.storage = storage;
        this.mythicBridge = mythicBridge;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVanillaDeath(EntityDeathEvent event) {
        if (mythicBridge.isMythicMob(event.getEntity())) {
            return;
        }

        String mobKey = "vanilla:" + event.getEntity().getType().name();
        applyDrops(mobKey, event.getEntity().getLocation(), event.getDrops(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMythicDeath(MythicMobDeathEvent event) {
        String mobKey = "mythic:" + event.getMobType().getInternalName();
        applyDrops(mobKey, event.getEntity().getLocation(), event.getDrops(), true);
    }

    private void applyDrops(String mobKey, Location location, List<ItemStack> existingDrops, boolean allowReplace) {
        DropMode mode = storage.getMode(mobKey);
        if (allowReplace && mode == DropMode.REPLACE) {
            existingDrops.clear();
        }

        List<ItemStack> rolledDrops = rollDrops(storage.getDrops(mobKey));
        if (rolledDrops.isEmpty()) {
            return;
        }

        Location dropLocation = location.clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> spawnPhysicalDrops(dropLocation, rolledDrops));
    }

    private List<ItemStack> rollDrops(List<CustomDropEntry> entries) {
        List<ItemStack> results = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (CustomDropEntry entry : entries) {
            if (!entry.enabled() || entry.item().getType().isAir()) {
                continue;
            }
            if (random.nextInt(CHANCE_SCALE) >= entry.chancePerMillion()) {
                continue;
            }

            int amount = random.nextInt(entry.minAmount(), entry.maxAmount() + 1);
            ItemStack rolled = entry.item();
            rolled.setAmount(amount);
            results.add(rolled);
        }

        return results;
    }

    private void spawnPhysicalDrops(Location location, List<ItemStack> drops) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        for (ItemStack drop : drops) {
            splitAndSpawn(world, location, drop);
        }
    }

    private void splitAndSpawn(World world, Location location, ItemStack stack) {
        int remaining = stack.getAmount();
        int maxStackSize = Math.max(1, stack.getMaxStackSize());

        while (remaining > 0) {
            int pieceAmount = Math.min(remaining, maxStackSize);
            ItemStack piece = stack.clone();
            piece.setAmount(pieceAmount);
            Item item = world.dropItemNaturally(location, piece);
            item.setCanMobPickup(false);
            remaining -= pieceAmount;
        }
    }
}
