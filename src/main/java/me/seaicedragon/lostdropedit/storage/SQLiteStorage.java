package me.seaicedragon.lostdropedit.storage;

import me.seaicedragon.lostdropedit.LostDropEditPlugin;
import me.seaicedragon.lostdropedit.util.ItemSerialization;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SQLiteStorage {

    private static final String CREATE_MOB_SETTINGS = """
        CREATE TABLE IF NOT EXISTS mob_settings (
            mob_key TEXT PRIMARY KEY,
            mode TEXT NOT NULL,
            xp_min INTEGER NOT NULL DEFAULT 0,
            xp_max INTEGER NOT NULL DEFAULT 0,
            xp_chance INTEGER NOT NULL DEFAULT 1000000
        )
        """;

    private static final String CREATE_CUSTOM_DROPS = """
        CREATE TABLE IF NOT EXISTS custom_drops (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            mob_key TEXT NOT NULL,
            item_data TEXT NOT NULL,
            chance_per_million INTEGER NOT NULL,
            min_amount INTEGER NOT NULL,
            max_amount INTEGER NOT NULL,
            enabled INTEGER NOT NULL,
            sort_order INTEGER NOT NULL
        )
        """;

    private final LostDropEditPlugin plugin;
    private final Map<String, DropMode> mobModes = new ConcurrentHashMap<>();
    private final Map<String, MobXpSettings> mobXp = new ConcurrentHashMap<>();
    private final Map<String, List<CustomDropEntry>> dropsByMob = new ConcurrentHashMap<>();
    private Connection connection;

    public SQLiteStorage(LostDropEditPlugin plugin) {
        this.plugin = plugin;
    }

    public void open() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                throw new IllegalStateException("Failed to create plugin data folder.");
            }

            File databaseFile = new File(dataFolder, plugin.getConfig().getString("database.file", "data.db"));
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());

            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=FULL");
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=5000");
                statement.execute(CREATE_MOB_SETTINGS);
                statement.execute(CREATE_CUSTOM_DROPS);
            }

            migrateSchema();
            reloadCaches();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to open SQLite storage.", exception);
        }
    }

    /**
     * Adds columns introduced after the first release to databases created by older versions.
     * Uses PRAGMA table_info to avoid ALTERing columns that already exist.
     */
    private void migrateSchema() throws SQLException {
        List<String> mobSettingsColumns = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(mob_settings)")) {
            while (resultSet.next()) {
                mobSettingsColumns.add(resultSet.getString("name"));
            }
        }

        try (Statement statement = connection.createStatement()) {
            if (!mobSettingsColumns.contains("xp_min")) {
                statement.execute("ALTER TABLE mob_settings ADD COLUMN xp_min INTEGER NOT NULL DEFAULT 0");
            }
            if (!mobSettingsColumns.contains("xp_max")) {
                statement.execute("ALTER TABLE mob_settings ADD COLUMN xp_max INTEGER NOT NULL DEFAULT 0");
            }
            if (!mobSettingsColumns.contains("xp_chance")) {
                statement.execute("ALTER TABLE mob_settings ADD COLUMN xp_chance INTEGER NOT NULL DEFAULT 1000000");
            }
        }
    }

    public void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }

    public void reloadCaches() {
        mobModes.clear();
        mobXp.clear();
        dropsByMob.clear();

        try {
            connection.setAutoCommit(false);
            loadModes();
            loadDrops();
            connection.commit();
        } catch (SQLException exception) {
            tryRollback();
            throw new IllegalStateException("Failed to reload storage caches.", exception);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    private void loadModes() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT mob_key, mode, xp_min, xp_max, xp_chance FROM mob_settings");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String mobKey = resultSet.getString("mob_key");
                mobModes.put(mobKey, DropMode.valueOf(resultSet.getString("mode")));
                mobXp.put(mobKey, new MobXpSettings(
                    resultSet.getInt("xp_min"),
                    resultSet.getInt("xp_max"),
                    resultSet.getInt("xp_chance")
                ));
            }
        }
    }

    private void loadDrops() throws SQLException {
        String sql = "SELECT id, mob_key, item_data, chance_per_million, min_amount, max_amount, enabled, sort_order FROM custom_drops ORDER BY mob_key, sort_order, id";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ItemStack item = ItemSerialization.deserialize(resultSet.getString("item_data"));
                CustomDropEntry entry = new CustomDropEntry(
                    resultSet.getLong("id"),
                    resultSet.getString("mob_key"),
                    item,
                    resultSet.getInt("chance_per_million"),
                    resultSet.getInt("min_amount"),
                    resultSet.getInt("max_amount"),
                    resultSet.getInt("enabled") == 1,
                    resultSet.getInt("sort_order")
                );
                dropsByMob.computeIfAbsent(entry.mobKey(), ignored -> new ArrayList<>()).add(entry);
            }
        }
    }

    public DropMode getMode(String mobKey) {
        return mobModes.getOrDefault(mobKey, DropMode.valueOf(plugin.getConfig().getString("defaults.mode", DropMode.APPEND.name()).toUpperCase()));
    }

    public void setMode(String mobKey, DropMode mode) {
        String sql = "INSERT INTO mob_settings(mob_key, mode) VALUES(?, ?) ON CONFLICT(mob_key) DO UPDATE SET mode = excluded.mode";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, mobKey);
            statement.setString(2, mode.name());
            statement.executeUpdate();
            mobModes.put(mobKey, mode);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save mob mode for " + mobKey, exception);
        }
    }

    public MobXpSettings getXpSettings(String mobKey) {
        MobXpSettings cached = mobXp.get(mobKey);
        if (cached != null) {
            return cached;
        }
        return new MobXpSettings(
            Math.max(0, plugin.getConfig().getInt("xp.default-min", 0)),
            Math.max(0, plugin.getConfig().getInt("xp.default-max", 0)),
            plugin.getConfig().getInt("xp.default-chance-per-million", 1_000_000)
        );
    }

    public void setXpSettings(String mobKey, MobXpSettings settings) {
        String sql = "INSERT INTO mob_settings(mob_key, mode, xp_min, xp_max, xp_chance) VALUES(?, ?, ?, ?, ?) "
            + "ON CONFLICT(mob_key) DO UPDATE SET xp_min = excluded.xp_min, xp_max = excluded.xp_max, xp_chance = excluded.xp_chance";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, mobKey);
            statement.setString(2, getMode(mobKey).name());
            statement.setInt(3, settings.xpMin());
            statement.setInt(4, settings.xpMax());
            statement.setInt(5, settings.xpChancePerMillion());
            statement.executeUpdate();
            mobXp.put(mobKey, settings);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save XP settings for " + mobKey, exception);
        }
    }

    public List<CustomDropEntry> getDrops(String mobKey) {
        List<CustomDropEntry> entries = dropsByMob.getOrDefault(mobKey, Collections.emptyList());
        List<CustomDropEntry> copy = new ArrayList<>(entries.size());
        for (CustomDropEntry entry : entries) {
            copy.add(entry);
        }
        copy.sort(Comparator.comparingInt(CustomDropEntry::sortOrder).thenComparingLong(CustomDropEntry::id));
        return copy;
    }

    public Map<String, Integer> getDropCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (Map.Entry<String, List<CustomDropEntry>> entry : dropsByMob.entrySet()) {
            counts.put(entry.getKey(), entry.getValue().size());
        }
        return counts;
    }

    public CustomDropEntry addDrop(String mobKey, ItemStack item) {
        int defaultChance = plugin.getConfig().getInt("defaults.chance-per-million", 1_000_000);
        int initialAmount = Math.max(1, item.getAmount());
        int defaultMin = initialAmount;
        int defaultMax = initialAmount;
        int sortOrder = dropsByMob.getOrDefault(mobKey, Collections.emptyList()).stream()
            .mapToInt(CustomDropEntry::sortOrder)
            .max()
            .orElse(-1) + 1;
        ItemStack storedItem = item.clone();
        storedItem.setAmount(1);

        String sql = "INSERT INTO custom_drops(mob_key, item_data, chance_per_million, min_amount, max_amount, enabled, sort_order) VALUES(?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, mobKey);
            statement.setString(2, ItemSerialization.serialize(storedItem));
            statement.setInt(3, defaultChance);
            statement.setInt(4, defaultMin);
            statement.setInt(5, defaultMax);
            statement.setInt(6, 1);
            statement.setInt(7, sortOrder);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("SQLite did not return a generated key.");
                }
                CustomDropEntry entry = new CustomDropEntry(keys.getLong(1), mobKey, storedItem, defaultChance, defaultMin, defaultMax, true, sortOrder);
                dropsByMob.computeIfAbsent(mobKey, ignored -> new ArrayList<>()).add(entry);
                sortCachedDrops(mobKey);
                return entry;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to add custom drop for " + mobKey, exception);
        }
    }

    public void updateDrop(CustomDropEntry entry) {
        String sql = "UPDATE custom_drops SET item_data = ?, chance_per_million = ?, min_amount = ?, max_amount = ?, enabled = ?, sort_order = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ItemSerialization.serialize(entry.item()));
            statement.setInt(2, entry.chancePerMillion());
            statement.setInt(3, entry.minAmount());
            statement.setInt(4, entry.maxAmount());
            statement.setInt(5, entry.enabled() ? 1 : 0);
            statement.setInt(6, entry.sortOrder());
            statement.setLong(7, entry.id());
            statement.executeUpdate();
            replaceCachedDrop(entry);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update custom drop " + entry.id(), exception);
        }
    }

    public void deleteDrop(long dropId) {
        String sql = "DELETE FROM custom_drops WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, dropId);
            statement.executeUpdate();

            for (Map.Entry<String, List<CustomDropEntry>> entry : dropsByMob.entrySet()) {
                boolean removed = entry.getValue().removeIf(drop -> drop.id() == dropId);
                if (removed) {
                    sortCachedDrops(entry.getKey());
                    break;
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete custom drop " + dropId, exception);
        }
    }

    public void saveDropOrder(String mobKey, Collection<CustomDropEntry> entries) {
        String sql = "UPDATE custom_drops SET sort_order = ? WHERE id = ?";
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (CustomDropEntry entry : entries) {
                    statement.setInt(1, entry.sortOrder());
                    statement.setLong(2, entry.id());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            connection.commit();
            dropsByMob.put(mobKey, new ArrayList<>(entries));
            sortCachedDrops(mobKey);
        } catch (SQLException exception) {
            tryRollback();
            throw new IllegalStateException("Failed to save drop order for " + mobKey, exception);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    private void replaceCachedDrop(CustomDropEntry updated) {
        List<CustomDropEntry> entries = dropsByMob.get(updated.mobKey());
        if (entries == null) {
            return;
        }
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).id() == updated.id()) {
                entries.set(index, updated);
                break;
            }
        }
        sortCachedDrops(updated.mobKey());
    }

    private void sortCachedDrops(String mobKey) {
        List<CustomDropEntry> entries = dropsByMob.get(mobKey);
        if (entries != null) {
            entries.sort(Comparator.comparingInt(CustomDropEntry::sortOrder).thenComparingLong(CustomDropEntry::id));
        }
    }

    private void tryRollback() {
        try {
            if (connection != null) {
                connection.rollback();
            }
        } catch (SQLException ignored) {
        }
    }
}
