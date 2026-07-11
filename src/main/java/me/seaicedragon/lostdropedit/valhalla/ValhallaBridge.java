package me.seaicedragon.lostdropedit.valhalla;

import me.seaicedragon.lostdropedit.LostDropEditPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;

/**
 * Soft, reflection-based bridge to ValhallaMMO. Reads a killer's accumulated loot/drop
 * stat(s) so custom drop chances can be boosted for players whose race (or any other
 * ValhallaMMO source) grants a drop bonus.
 *
 * <p>No compile-time dependency on ValhallaMMO is required: if the plugin (or the expected
 * API class/method) is missing, the bridge stays disabled and always returns a zero bonus,
 * so LostDropEdit keeps working on servers without ValhallaMMO.
 */
public final class ValhallaBridge {

    private static final List<String> DEFAULT_STAT_KEYS = List.of("ENTITY_DROPS", "ENTITY_DROP_LUCK");
    private static final String STAT_MANAGER_CLASS = "me.athlaeos.valhallammo.playerstats.AccumulativeStatManager";

    private final boolean enabled;
    private final List<String> statKeys;
    private final double influence;
    private final long cacheMs;

    private final boolean available;
    private final Method getCachedStats;

    public ValhallaBridge(LostDropEditPlugin plugin) {
        this.enabled = plugin.getConfig().getBoolean("race-boost.enabled", true);
        List<String> configuredKeys = plugin.getConfig().getStringList("race-boost.stat-keys");
        this.statKeys = configuredKeys.isEmpty() ? DEFAULT_STAT_KEYS : List.copyOf(configuredKeys);
        this.influence = plugin.getConfig().getDouble("race-boost.influence", 1.0);
        this.cacheMs = plugin.getConfig().getLong("race-boost.cache-ms", 10_000L);

        Method resolved = null;
        boolean ready = false;
        if (enabled && plugin.getServer().getPluginManager().getPlugin("ValhallaMMO") != null) {
            try {
                Class<?> statManager = Class.forName(STAT_MANAGER_CLASS);
                // public static double getCachedStats(String stat, Entity entity, long refreshAfter, boolean use)
                resolved = statManager.getMethod("getCachedStats", String.class, Entity.class, long.class, boolean.class);
                ready = true;
                plugin.getLogger().info("ValhallaMMO detected; race loot boost is active.");
            } catch (ClassNotFoundException | NoSuchMethodException exception) {
                plugin.getLogger().log(Level.WARNING,
                    "ValhallaMMO is installed but its loot stat API was not found; race loot boost is disabled.", exception);
            }
        }
        this.available = ready;
        this.getCachedStats = resolved;
    }

    /**
     * Returns the additive drop-chance bonus for the given killer (e.g. 0.3 == +30%).
     * Never negative and never throws; returns 0 when unavailable, disabled, or the killer is null.
     */
    public double getDropChanceBonus(Player killer) {
        if (!available || killer == null) {
            return 0.0;
        }
        try {
            double sum = 0.0;
            for (String statKey : statKeys) {
                Object value = getCachedStats.invoke(null, statKey, killer, cacheMs, true);
                if (value instanceof Number number) {
                    sum += number.doubleValue();
                }
            }
            double bonus = sum * influence;
            return bonus > 0.0 ? bonus : 0.0;
        } catch (Throwable throwable) {
            // Never let a ValhallaMMO API change or error break the drop flow.
            return 0.0;
        }
    }

    public boolean isActive() {
        return available;
    }
}
