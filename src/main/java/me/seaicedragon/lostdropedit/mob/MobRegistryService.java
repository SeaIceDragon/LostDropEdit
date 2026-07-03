package me.seaicedragon.lostdropedit.mob;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.events.MythicPostReloadedEvent;
import me.seaicedragon.lostdropedit.LostDropEditPlugin;
import me.seaicedragon.lostdropedit.mythic.MythicBridge;
import me.seaicedragon.lostdropedit.storage.SQLiteStorage;
import me.seaicedragon.lostdropedit.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class MobRegistryService implements Listener {

    private final LostDropEditPlugin plugin;
    private final MythicBridge mythicBridge;
    private final SQLiteStorage storage;
    private final Map<String, MobDescriptor> mobsByKey = new ConcurrentHashMap<>();
    private volatile List<MobDescriptor> sortedMobs = List.of();

    public MobRegistryService(LostDropEditPlugin plugin, MythicBridge mythicBridge, SQLiteStorage storage) {
        this.plugin = plugin;
        this.mythicBridge = mythicBridge;
        this.storage = storage;
        refresh();
    }

    public void refresh() {
        Map<String, MobDescriptor> rebuilt = new ConcurrentHashMap<>();

        for (EntityType entityType : EntityType.values()) {
            if (!entityType.isAlive() || !entityType.isSpawnable() || entityType == EntityType.PLAYER || entityType == EntityType.UNKNOWN) {
                continue;
            }

            String key = "vanilla:" + entityType.name();
            String displayName = Text.toTurkishMobName(entityType.name());
            rebuilt.put(key, new MobDescriptor(
                key,
                MobSource.VANILLA,
                entityType.name(),
                displayName,
                iconFromEntityName(entityType.name()),
                normalize(displayName + " " + entityType.name() + " " + key)
            ));
        }

        for (MythicMob mythicMob : mythicBridge.getMobs()) {
            String internalName = mythicMob.getInternalName();
            String rawDisplay = mythicMob.getConfig().getString("Display", internalName);
            String displayName = Text.stripFormatting(rawDisplay).isBlank() ? internalName : Text.stripFormatting(rawDisplay);
            String key = "mythic:" + internalName;
            rebuilt.put(key, new MobDescriptor(
                key,
                MobSource.MYTHIC,
                internalName,
                displayName,
                iconFromEntityName(mythicMob.getEntityTypeString()),
                normalize(displayName + " " + internalName + " " + key)
            ));
        }

        this.mobsByKey.clear();
        this.mobsByKey.putAll(rebuilt);
        this.sortedMobs = rebuilt.values().stream()
            .sorted(Comparator.comparing(MobDescriptor::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(MobDescriptor::internalName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public List<MobDescriptor> getAll() {
        return sortedMobs;
    }

    public List<MobDescriptor> search(String query) {
        if (query == null || query.isBlank()) {
            return getAll();
        }
        String normalized = normalize(query);
        return sortedMobs.stream()
            .filter(mob -> mob.searchText().contains(normalized))
            .toList();
    }

    public Optional<MobDescriptor> getMob(String mobKey) {
        return Optional.ofNullable(mobsByKey.get(mobKey));
    }

    public Map<String, Integer> getDropCounts() {
        return storage.getDropCounts();
    }

    private Material iconFromEntityName(String entityName) {
        if (entityName == null || entityName.isBlank()) {
            return Material.NETHER_STAR;
        }

        Material spawnEgg = Material.matchMaterial(entityName.toUpperCase(Locale.ROOT) + "_SPAWN_EGG");
        if (spawnEgg != null) {
            return spawnEgg;
        }

        return switch (entityName.toUpperCase(Locale.ROOT)) {
            case "WITHER" -> Material.NETHER_STAR;
            case "ENDER_DRAGON" -> Material.DRAGON_HEAD;
            case "IRON_GOLEM" -> Material.IRON_BLOCK;
            case "SNOWMAN" -> Material.CARVED_PUMPKIN;
            default -> Material.NAME_TAG;
        };
    }

    private String normalize(String input) {
        return Text.stripFormatting(input)
            .toLowerCase(Locale.ROOT)
            .replace("_", "")
            .replace("-", "")
            .replace(" ", "");
    }

    @EventHandler
    public void onMythicReload(MythicPostReloadedEvent event) {
        refresh();
        plugin.getLogger().info("MythicMobs yeniden yüklendikten sonra mob listesi yenilendi.");
    }
}
