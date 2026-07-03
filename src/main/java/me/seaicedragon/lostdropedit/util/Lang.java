package me.seaicedragon.lostdropedit.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class Lang {

    private final FileConfiguration config;

    public Lang(JavaPlugin plugin) {
        plugin.saveResource("lang.yml", false);
        File file = new File(plugin.getDataFolder(), "lang.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String path, Object... replacements) {
        return replace(config.getString(path, path), replacements);
    }

    public List<String> getList(String path, Object... replacements) {
        List<String> lines = config.getStringList(path);
        List<String> replaced = new ArrayList<>(lines.size());
        for (String line : lines) {
            replaced.add(replace(line, replacements));
        }
        return replaced;
    }

    public String cancelKeyword() {
        return get("keywords.cancel");
    }

    private String replace(String input, Object... replacements) {
        String output = input == null ? "" : input;
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            output = output.replace("{" + replacements[index] + "}", String.valueOf(replacements[index + 1]));
        }
        return output;
    }
}
