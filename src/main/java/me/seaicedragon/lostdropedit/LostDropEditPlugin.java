package me.seaicedragon.lostdropedit;

import me.seaicedragon.lostdropedit.drop.DropService;
import me.seaicedragon.lostdropedit.gui.MenuService;
import me.seaicedragon.lostdropedit.input.ChatInputService;
import me.seaicedragon.lostdropedit.mob.MobRegistryService;
import me.seaicedragon.lostdropedit.mythic.MythicBridge;
import me.seaicedragon.lostdropedit.storage.SQLiteStorage;
import me.seaicedragon.lostdropedit.util.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class LostDropEditPlugin extends JavaPlugin implements TabExecutor {

    private SQLiteStorage storage;
    private MythicBridge mythicBridge;
    private MobRegistryService mobRegistryService;
    private ChatInputService chatInputService;
    private MenuService menuService;
    private Lang lang;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.lang = new Lang(this);

        this.storage = new SQLiteStorage(this);
        storage.open();

        this.mythicBridge = new MythicBridge();
        this.mobRegistryService = new MobRegistryService(this, mythicBridge, storage);
        this.chatInputService = new ChatInputService(this, lang);
        this.menuService = new MenuService(this, storage, mythicBridge, mobRegistryService, chatInputService, lang);

        DropService dropService = new DropService(this, storage, mythicBridge);

        getServer().getPluginManager().registerEvents(chatInputService, this);
        getServer().getPluginManager().registerEvents(menuService, this);
        getServer().getPluginManager().registerEvents(mobRegistryService, this);
        getServer().getPluginManager().registerEvents(dropService, this);

        if (getCommand("mobdrops") != null) {
            getCommand("mobdrops").setExecutor(this);
            getCommand("mobdrops").setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        if (chatInputService != null) {
            chatInputService.clear();
        }
        if (storage != null) {
            storage.close();
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(lang.get("messages.command.only-players"), NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("lostdropedit.admin")) {
            player.sendMessage(Component.text(lang.get("messages.command.no-permission"), NamedTextColor.RED));
            return true;
        }

        menuService.openBrowser(player, null, 0);
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }

    public SQLiteStorage storage() {
        return storage;
    }

    public MythicBridge mythicBridge() {
        return mythicBridge;
    }

    public MobRegistryService mobRegistryService() {
        return mobRegistryService;
    }

    public @Nullable MenuService menuService() {
        return menuService;
    }

    public Lang lang() {
        return lang;
    }
}
