package me.seaicedragon.lostdropedit.drop;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import me.seaicedragon.lostdropedit.LostDropEditPlugin;
import me.seaicedragon.lostdropedit.mythic.MythicBridge;
import me.seaicedragon.lostdropedit.storage.CustomDropEntry;
import me.seaicedragon.lostdropedit.storage.DropMode;
import me.seaicedragon.lostdropedit.storage.MobXpSettings;
import me.seaicedragon.lostdropedit.storage.SQLiteStorage;
import me.seaicedragon.lostdropedit.valhalla.ValhallaBridge;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
    private final ValhallaBridge valhallaBridge;

    public DropService(LostDropEditPlugin plugin, SQLiteStorage storage, MythicBridge mythicBridge, ValhallaBridge valhallaBridge) {
        this.plugin = plugin;
        this.storage = storage;
        this.mythicBridge = mythicBridge;
        this.valhallaBridge = valhallaBridge;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVanillaDeath(EntityDeathEvent event) {
        if (mythicBridge.isMythicMob(event.getEntity())) {
            return;
        }

        String mobKey = "vanilla:" + event.getEntity().getType().name();
        applyDrops(mobKey, event.getEntity().getLocation(), event.getDrops(), killerOf(event.getEntity()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMythicDeath(MythicMobDeathEvent event) {
        String mobKey = "mythic:" + event.getMobType().getInternalName();
        Player killer = event.getEntity() instanceof LivingEntity living ? killerOf(living) : null;
        applyDrops(mobKey, event.getEntity().getLocation(), event.getDrops(), killer);
    }

    private Player killerOf(LivingEntity entity) {
        return entity.getKiller();
    }

    private void applyDrops(String mobKey, Location location, List<ItemStack> existingDrops, Player killer) {
        DropMode mode = storage.getMode(mobKey);
        if (mode == DropMode.REPLACE) {
            existingDrops.clear();
        }

        double raceBonus = valhallaBridge.getDropChanceBonus(killer);
        List<ItemStack> rolledDrops = rollDrops(storage.getDrops(mobKey), raceBonus);
        int xp = rollXp(storage.getXpSettings(mobKey));

        if (rolledDrops.isEmpty() && xp <= 0) {
            return;
        }

        Location dropLocation = location.clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            spawnPhysicalDrops(dropLocation, rolledDrops);
            if (xp > 0) {
                spawnExperience(dropLocation, xp);
            }
        });
    }

    private List<ItemStack> rollDrops(List<CustomDropEntry> entries, double raceBonus) {
        List<ItemStack> results = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double multiplier = dropChanceMultiplier(raceBonus);

        for (CustomDropEntry entry : entries) {
            if (!entry.enabled() || entry.item().getType().isAir()) {
                continue;
            }

            int effectiveChance = applyMultiplier(entry.chancePerMillion(), multiplier);
            if (random.nextInt(CHANCE_SCALE) >= effectiveChance) {
                continue;
            }

            int amount = random.nextInt(entry.minAmount(), entry.maxAmount() + 1);
            ItemStack rolled = entry.item();
            rolled.setAmount(amount);
            results.add(rolled);
        }

        return results;
    }

    /**
     * Converts a ValhallaMMO loot bonus into a drop-chance multiplier, clamped to the
     * configured maximum so a race boost can raise but never wildly inflate the admin's chance.
     */
    private double dropChanceMultiplier(double raceBonus) {
        double maxMultiplier = Math.max(1.0, plugin.getConfig().getDouble("race-boost.max-multiplier", 2.0));
        double multiplier = 1.0 + Math.max(0.0, raceBonus);
        return Math.min(multiplier, maxMultiplier);
    }

    private int applyMultiplier(int chancePerMillion, double multiplier) {
        long scaled = Math.round(chancePerMillion * multiplier);
        if (scaled < 0) {
            return 0;
        }
        return (int) Math.min(scaled, CHANCE_SCALE);
    }

    private int rollXp(MobXpSettings settings) {
        if (!settings.grantsXp()) {
            return 0;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextInt(CHANCE_SCALE) >= settings.xpChancePerMillion()) {
            return 0;
        }
        int min = Math.max(0, settings.xpMin());
        int max = Math.max(min, settings.xpMax());
        return min == max ? max : random.nextInt(min, max + 1);
    }

    private void spawnExperience(Location location, int amount) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        world.spawn(location, ExperienceOrb.class, orb -> orb.setExperience(amount));
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
