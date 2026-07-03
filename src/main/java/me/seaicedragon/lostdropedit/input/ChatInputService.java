package me.seaicedragon.lostdropedit.input;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.seaicedragon.lostdropedit.LostDropEditPlugin;
import me.seaicedragon.lostdropedit.util.Lang;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ChatInputService implements Listener {

    private final LostDropEditPlugin plugin;
    private final Lang lang;
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();

    public ChatInputService(LostDropEditPlugin plugin, Lang lang) {
        this.plugin = plugin;
        this.lang = lang;
    }

    public void request(Player player, String prompt, Consumer<String> consumer) {
        pendingInputs.put(player.getUniqueId(), new PendingInput(prompt, consumer));
        player.sendMessage(prompt);
        player.sendMessage(lang.get("messages.chat.type-answer"));
        player.closeInventory();
    }

    public void clear() {
        pendingInputs.clear();
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        PendingInput pendingInput = pendingInputs.remove(event.getPlayer().getUniqueId());
        if (pendingInput == null) {
            return;
        }

        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase(lang.cancelKeyword()) || text.equalsIgnoreCase("cancel")) {
                event.getPlayer().sendMessage(lang.get("messages.chat.input-cancelled"));
                return;
            }
            pendingInput.consumer().accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingInputs.remove(event.getPlayer().getUniqueId());
    }

    private record PendingInput(String prompt, Consumer<String> consumer) {
    }
}
