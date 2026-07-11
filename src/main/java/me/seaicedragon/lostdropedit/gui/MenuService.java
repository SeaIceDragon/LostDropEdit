package me.seaicedragon.lostdropedit.gui;

import me.seaicedragon.lostdropedit.LostDropEditPlugin;
import me.seaicedragon.lostdropedit.input.ChatInputService;
import me.seaicedragon.lostdropedit.mob.MobDescriptor;
import me.seaicedragon.lostdropedit.mob.MobRegistryService;
import me.seaicedragon.lostdropedit.mob.MobSource;
import me.seaicedragon.lostdropedit.mythic.ExistingDropPreview;
import me.seaicedragon.lostdropedit.mythic.MythicBridge;
import me.seaicedragon.lostdropedit.storage.CustomDropEntry;
import me.seaicedragon.lostdropedit.storage.DropMode;
import me.seaicedragon.lostdropedit.storage.MobXpSettings;
import me.seaicedragon.lostdropedit.storage.SQLiteStorage;
import me.seaicedragon.lostdropedit.util.Lang;
import me.seaicedragon.lostdropedit.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class MenuService implements Listener {

    private static final int BROWSER_PAGE_SIZE = 45;
    private static final int CUSTOM_PAGE_SIZE = 27;
    private static final int EXISTING_PAGE_SIZE = 9;

    private final LostDropEditPlugin plugin;
    private final SQLiteStorage storage;
    private final MythicBridge mythicBridge;
    private final MobRegistryService mobRegistryService;
    private final ChatInputService chatInputService;
    private final Lang lang;

    public MenuService(LostDropEditPlugin plugin, SQLiteStorage storage, MythicBridge mythicBridge, MobRegistryService mobRegistryService, ChatInputService chatInputService, Lang lang) {
        this.plugin = plugin;
        this.storage = storage;
        this.mythicBridge = mythicBridge;
        this.mobRegistryService = mobRegistryService;
        this.chatInputService = chatInputService;
        this.lang = lang;
    }

    public void openBrowser(Player player, @Nullable String query, int requestedPage) {
        List<MobDescriptor> mobs = mobRegistryService.search(query);
        int page = clampPage(mobs.size(), BROWSER_PAGE_SIZE, requestedPage);
        BrowserContext context = new BrowserContext(query, page);
        BrowserHolder holder = new BrowserHolder(context, lang.get("titles.browser"));
        Inventory inventory = holder.inventory();

        fillBackground(inventory, Material.GRAY_STAINED_GLASS_PANE, " ");

        int start = page * BROWSER_PAGE_SIZE;
        int end = Math.min(start + BROWSER_PAGE_SIZE, mobs.size());
        for (int index = start; index < end; index++) {
            MobDescriptor mob = mobs.get(index);
            inventory.setItem(index - start, createMobIcon(mob));
        }

        inventory.setItem(45, button(Material.ARROW, "Önceki Sayfa", List.of("Bir önceki sayfaya git.")));
        inventory.setItem(46, button(Material.COMPASS, "Ara", List.of("Sohbete mob adı veya anahtarı yazmak için tıkla.")));
        inventory.setItem(47, button(Material.BARRIER, "Aramayı Temizle", List.of("Mevcut arama filtresini sıfırla.")));
        inventory.setItem(49, button(Material.SUNFLOWER, "Yenile", List.of("Vanilla ve Mythic mob listesini yenile.")));
        inventory.setItem(51, button(Material.BOOK, "Arama Bilgisi", List.of(
            query == null || query.isBlank() ? "Mevcut filtre: Yok" : "Mevcut filtre: " + query,
            "Sayfa " + (page + 1) + " / " + Math.max(1, totalPages(mobs.size(), BROWSER_PAGE_SIZE)),
            mobs.size() + " mob listelendi"
        )));
        inventory.setItem(52, button(Material.ARROW, "Sonraki Sayfa", List.of("Bir sonraki sayfaya git.")));
        inventory.setItem(53, button(Material.OAK_DOOR, "Kapat", List.of("Menüyü kapat.")));

        player.openInventory(inventory);
    }

    private ItemStack createMobIcon(MobDescriptor mob) {
        int customDrops = storage.getDrops(mob.key()).size();
        DropMode mode = storage.getMode(mob.key());
        return button(mob.icon(), mob.displayName(), List.of(
            "Kaynak: " + sourceLabel(mob.source()),
            "Anahtar: " + mob.key(),
            "Özel drop sayısı: " + customDrops,
            "Mod: " + modeLabel(mode),
            mob.source() == MobSource.MYTHIC ? "Mevcut Mythic dropları önizleyebilir ve içe aktarabilirsin." : "Bu vanilla mobun özel droplarını düzenle.",
            "Drop editörünü açmak için sol tıkla."
        ));
    }

    private void openEditor(Player player, EditorContext context) {
        Optional<MobDescriptor> optionalMob = mobRegistryService.getMob(context.mobKey());
        if (optionalMob.isEmpty()) {
            player.sendMessage(lang.get("messages.error.mob-missing"));
            mobRegistryService.refresh();
            openBrowser(player, context.browserQuery(), context.browserPage());
            return;
        }

        MobDescriptor mob = optionalMob.get();
        List<CustomDropEntry> customDrops = sortedDrops(context.mobKey());
        List<ExistingDropPreview> existingDrops = mob.source() == MobSource.MYTHIC
            ? mythicBridge.buildDropPreviews(mob.internalName())
            : List.of();

        int customPage = clampPage(customDrops.size(), CUSTOM_PAGE_SIZE, context.customPage());
        int existingPage = clampPage(existingDrops.size(), EXISTING_PAGE_SIZE, context.existingPage());
        EditorContext normalizedContext = new EditorContext(context.mobKey(), customPage, existingPage, context.browserQuery(), context.browserPage());

        EditorHolder holder = new EditorHolder(normalizedContext, lang.get("titles.editor"));
        Inventory inventory = holder.inventory();
        fillBackground(inventory, Material.BLACK_STAINED_GLASS_PANE, " ");

        int customStart = customPage * CUSTOM_PAGE_SIZE;
        for (int slot = 0; slot < CUSTOM_PAGE_SIZE; slot++) {
            int absoluteIndex = customStart + slot;
            if (absoluteIndex < customDrops.size()) {
                inventory.setItem(slot, createCustomDropIcon(customDrops.get(absoluteIndex)));
            }
        }

        for (int slot = 27; slot <= 34; slot++) {
            inventory.setItem(slot, button(Material.LIME_STAINED_GLASS_PANE, "Drop Ekle", List.of(
                "İmlecinde bir eşya varken bu slota tıkla,",
                "veya kendi envanterinden shift-tık ile ekle.",
                "Eşya şablon olarak kopyalanır; tüketilmez."
            )));
        }

        inventory.setItem(35, createXpButton(context.mobKey()));

        if (mob.source() == MobSource.MYTHIC) {
            int existingStart = existingPage * EXISTING_PAGE_SIZE;
            for (int slot = 36; slot < 45; slot++) {
                int absoluteIndex = existingStart + (slot - 36);
                if (absoluteIndex < existingDrops.size()) {
                    inventory.setItem(slot, createExistingDropIcon(existingDrops.get(absoluteIndex)));
                }
            }
        } else {
            inventory.setItem(40, button(Material.PAPER, "Vanilla Önizleme", List.of(
                "Vanilla doğal droplar dinamik olduğu için burada önizlenmez.",
                "Sadece bu pluginin kayıtlı özel dropları düzenlenebilir."
            )));
        }

        inventory.setItem(45, button(Material.ARROW, "Özel Önceki", List.of("Bir önceki özel drop sayfasını göster.")));
        inventory.setItem(46, button(Material.ARROW, "Özel Sonraki", List.of("Bir sonraki özel drop sayfasını göster.")));
        inventory.setItem(47, button(Material.SPECTRAL_ARROW, "Mevcut Önceki", List.of("Bir önceki mevcut drop sayfasını göster.")));
        inventory.setItem(48, button(Material.SPECTRAL_ARROW, "Mevcut Sonraki", List.of("Bir sonraki mevcut drop sayfasını göster.")));
        inventory.setItem(49, button(Material.LEVER, "Drop Modu: " + modeLabel(storage.getMode(context.mobKey())), List.of(
            "Mevcutlara Ekle modu, var olan dropları korur ve bu pluginin droplarını ekler.",
            "Tamamen Değiştir modu, var olan dropları siler ve sadece bu pluginin droplarını kullanır.",
            "Bir Mythic drop içe aktarıp çift drop istemiyorsan Tamamen Değiştir modunu kullan.",
            "Değiştirmek için tıkla."
        )));
        inventory.setItem(50, button(Material.SUNFLOWER, "Mob Verisini Yenile", List.of("Mob listesini ve Mythic önizleme panelini yenile.")));
        inventory.setItem(51, button(mob.icon(), mob.displayName(), List.of(
            "Anahtar: " + mob.key(),
            "Özel sayfa " + (customPage + 1) + " / " + Math.max(1, totalPages(customDrops.size(), CUSTOM_PAGE_SIZE)),
            mob.source() == MobSource.MYTHIC
                ? "Mevcut sayfa " + (existingPage + 1) + " / " + Math.max(1, totalPages(existingDrops.size(), EXISTING_PAGE_SIZE))
                : "Vanilla mob editörü",
            "Kayıtlı bir dropu yukarı veya aşağı taşımak için shift-sol/sağ kullan."
        )));
        inventory.setItem(52, button(Material.ARROW, "Geri", List.of("Mob listesine dön.")));
        inventory.setItem(53, button(Material.OAK_DOOR, "Kapat", List.of("Menüyü kapat.")));

        player.openInventory(inventory);
    }

    private ItemStack createCustomDropIcon(CustomDropEntry entry) {
        ItemStack icon = entry.item();
        ItemMeta meta = icon.getItemMeta();
        meta.lore(toLore(List.of(
            "Şans: " + Text.chanceAsPercent(entry.chancePerMillion()),
            "Miktar: " + entry.minAmount() + " - " + entry.maxAmount(),
            "Durum: " + (entry.enabled() ? "Açık" : "Kapalı"),
            "Sıra: " + entry.sortOrder(),
            "Düzenlemek için sol tıkla.",
            "Silmek için sağ tıkla.",
            "Yukarı veya aşağı taşımak için shift-sol/sağ kullan."
        )));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack createXpButton(String mobKey) {
        MobXpSettings xp = storage.getXpSettings(mobKey);
        String status = xp.grantsXp()
            ? xp.xpMin() + " - " + xp.xpMax() + " XP (" + Text.chanceAsPercent(xp.xpChancePerMillion()) + ")"
            : "Kapalı";
        return button(Material.EXPERIENCE_BOTTLE, "XP Ayarları", List.of(
            "Mevcut: " + status,
            "Bu mob ölünce, öldüğü yere tecrübe topu düşer.",
            "Ayarlamak için sol tıkla (sırayla min, maks, şans girilir).",
            "XP'yi kapatmak için sağ tıkla."
        ));
    }

    private ItemStack createExistingDropIcon(ExistingDropPreview preview) {
        ItemStack icon = preview.icon().clone();
        if (icon.getType().isAir()) {
            icon = new ItemStack(Material.PAPER);
        }
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(preview.title(), NamedTextColor.AQUA));
        List<String> lines = new ArrayList<>(preview.description());
        if (preview.importable()) {
            lines.add("Bu eşyayı özel drop sistemine aktarmak için sol tıkla.");
            lines.add("İçe aktarılan eşya varsayılan olarak %100 şansla eklenir; sonra düzenleyebilirsin.");
        } else {
            lines.add("Salt okunur önizleme. Bu drop otomatik içe aktarılamaz.");
        }
        meta.lore(toLore(lines));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        icon.setItemMeta(meta);
        return icon;
    }

    private void openDropEditor(Player player, DropContext context) {
        CustomDropEntry entry = findDrop(context.editorContext().mobKey(), context.dropId());
        if (entry == null) {
            player.sendMessage(lang.get("messages.error.drop-missing"));
            openEditor(player, context.editorContext());
            return;
        }

        DropEditorHolder holder = new DropEditorHolder(context, lang.get("titles.drop-editor"));
        Inventory inventory = holder.inventory();
        fillBackground(inventory, Material.BLUE_STAINED_GLASS_PANE, " ");

        ItemStack preview = entry.item();
        ItemMeta previewMeta = preview.getItemMeta();
        previewMeta.lore(toLore(List.of(
            "Mob anahtarı: " + entry.mobKey(),
            "Drop ID: " + entry.id(),
            "Düşme anında kopyalanacak eşya şablonu budur."
        )));
        preview.setItemMeta(previewMeta);
        inventory.setItem(4, preview);

        inventory.setItem(10, button(Material.NETHER_STAR, "Şans", List.of(
            "Mevcut: " + Text.chanceAsPercent(entry.chancePerMillion()),
            "Sohbette hassas bir yüzde girmek için sol tıkla.",
            "Örnek değerler: 100, 12.5, 0.1, 0.0001"
        )));
        inventory.setItem(12, button(Material.HOPPER, "Minimum Miktar", List.of(
            "Mevcut: " + entry.minAmount(),
            "Yeni minimum miktarı sohbette girmek için sol tıkla."
        )));
        inventory.setItem(14, button(Material.CHEST, "Maksimum Miktar", List.of(
            "Mevcut: " + entry.maxAmount(),
            "Yeni maksimum miktarı sohbette girmek için sol tıkla."
        )));
        inventory.setItem(16, button(entry.enabled() ? Material.LIME_DYE : Material.GRAY_DYE, "Durum", List.of(
            "Mevcut: " + (entry.enabled() ? "Açık" : "Kapalı"),
            "Bu dropu açıp kapatmak için tıkla."
        )));
        inventory.setItem(22, button(Material.ITEM_FRAME, "Ana Eldeki Eşyayla Değiştir", List.of(
            "Yeni eşyayı ana elinde tut ve sonra buraya tıkla.",
            "Tutulan eşya kopyalanır; tüketilmez."
        )));
        inventory.setItem(26, button(Material.BARRIER, "Sil", List.of("Bu özel dropu hemen sil.")));
        inventory.setItem(27, button(Material.ARROW, "Geri", List.of("Mob editörüne dön.")));
        inventory.setItem(31, button(Material.GREEN_CONCRETE, "%100 Yap", List.of("Garantili drop için hızlı ayar.")));
        inventory.setItem(32, button(Material.YELLOW_CONCRETE, "%50 Yap", List.of("Yarı şanslı drop için hızlı ayar.")));
        inventory.setItem(33, button(Material.ORANGE_CONCRETE, "%10 Yap", List.of("Yaygın nadir droplar için hızlı ayar.")));
        inventory.setItem(34, button(Material.RED_CONCRETE, "%1 Yap", List.of("Çok nadir droplar için hızlı ayar.")));
        inventory.setItem(35, button(Material.OAK_DOOR, "Kapat", List.of("Menüyü kapat.")));

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof BaseHolder baseHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (holder instanceof BrowserHolder browserHolder) {
            handleBrowserClick(player, browserHolder.context(), event);
            return;
        }
        if (holder instanceof EditorHolder editorHolder) {
            handleEditorClick(player, editorHolder.context(), event);
            return;
        }
        if (holder instanceof DropEditorHolder dropEditorHolder) {
            handleDropEditorClick(player, dropEditorHolder.context(), event);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BaseHolder) {
            event.setCancelled(true);
        }
    }

    private void handleBrowserClick(Player player, BrowserContext context, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < BROWSER_PAGE_SIZE) {
            List<MobDescriptor> mobs = mobRegistryService.search(context.query());
            int absoluteIndex = context.page() * BROWSER_PAGE_SIZE + rawSlot;
            if (absoluteIndex < mobs.size()) {
                openEditor(player, new EditorContext(mobs.get(absoluteIndex).key(), 0, 0, context.query(), context.page()));
            }
            return;
        }

        switch (rawSlot) {
            case 45 -> openBrowser(player, context.query(), context.page() - 1);
            case 46 -> chatInputService.request(player, lang.get("prompts.search"), input -> openBrowser(player, input, 0));
            case 47 -> openBrowser(player, null, 0);
            case 49 -> {
                mobRegistryService.refresh();
                openBrowser(player, context.query(), context.page());
            }
            case 52 -> openBrowser(player, context.query(), context.page() + 1);
            case 53 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleEditorClick(Player player, EditorContext context, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();

        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory()) && event.isShiftClick()) {
            ItemStack template = resolveTemplateFromEvent(event);
            if (template != null && !template.getType().isAir()) {
                storage.addDrop(context.mobKey(), template);
                openEditor(player, context);
            } else {
                player.sendMessage(lang.get("messages.error.no-item-to-add"));
            }
            return;
        }

        if (rawSlot >= 0 && rawSlot < CUSTOM_PAGE_SIZE) {
            List<CustomDropEntry> drops = sortedDrops(context.mobKey());
            int absoluteIndex = context.customPage() * CUSTOM_PAGE_SIZE + rawSlot;
            if (absoluteIndex >= drops.size()) {
                return;
            }

            CustomDropEntry entry = drops.get(absoluteIndex);
            if (event.getClick() == ClickType.RIGHT) {
                storage.deleteDrop(entry.id());
                openEditor(player, context);
                return;
            }
            if (event.getClick() == ClickType.SHIFT_LEFT) {
                moveDrop(context.mobKey(), entry.id(), -1);
                openEditor(player, context);
                return;
            }
            if (event.getClick() == ClickType.SHIFT_RIGHT) {
                moveDrop(context.mobKey(), entry.id(), 1);
                openEditor(player, context);
                return;
            }

            openDropEditor(player, new DropContext(context, entry.id()));
            return;
        }

        if (rawSlot >= 27 && rawSlot <= 34) {
            ItemStack template = resolveTemplateFromEvent(event);
            if (template != null && !template.getType().isAir()) {
                storage.addDrop(context.mobKey(), template);
                openEditor(player, context);
            } else {
                player.sendMessage(lang.get("messages.error.no-item-to-add"));
            }
            return;
        }

        if (rawSlot == 35) {
            if (event.getClick() == ClickType.RIGHT) {
                MobXpSettings current = storage.getXpSettings(context.mobKey());
                storage.setXpSettings(context.mobKey(), new MobXpSettings(0, 0, current.xpChancePerMillion()));
                openEditor(player, context);
            } else {
                promptXpSettings(player, context);
            }
            return;
        }

        if (rawSlot >= 36 && rawSlot <= 44) {
            Optional<MobDescriptor> mob = mobRegistryService.getMob(context.mobKey());
            if (mob.isEmpty() || mob.get().source() != MobSource.MYTHIC) {
                return;
            }

            List<ExistingDropPreview> previews = mythicBridge.buildDropPreviews(mob.get().internalName());
            int absoluteIndex = context.existingPage() * EXISTING_PAGE_SIZE + (rawSlot - 36);
            if (absoluteIndex < previews.size()) {
                ExistingDropPreview preview = previews.get(absoluteIndex);
                if (preview.importable() && preview.importItem() != null && event.isLeftClick()) {
                    storage.addDrop(context.mobKey(), preview.importItem());
                    openEditor(player, context);
                } else if (event.isLeftClick()) {
                    player.sendMessage(lang.get("messages.error.preview-not-importable"));
                }
            }
            return;
        }

        switch (rawSlot) {
            case 45 -> openEditor(player, new EditorContext(context.mobKey(), context.customPage() - 1, context.existingPage(), context.browserQuery(), context.browserPage()));
            case 46 -> openEditor(player, new EditorContext(context.mobKey(), context.customPage() + 1, context.existingPage(), context.browserQuery(), context.browserPage()));
            case 47 -> openEditor(player, new EditorContext(context.mobKey(), context.customPage(), context.existingPage() - 1, context.browserQuery(), context.browserPage()));
            case 48 -> openEditor(player, new EditorContext(context.mobKey(), context.customPage(), context.existingPage() + 1, context.browserQuery(), context.browserPage()));
            case 49 -> {
                storage.setMode(context.mobKey(), storage.getMode(context.mobKey()).toggle());
                openEditor(player, context);
            }
            case 50 -> {
                mobRegistryService.refresh();
                openEditor(player, context);
            }
            case 52 -> openBrowser(player, context.browserQuery(), context.browserPage());
            case 53 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleDropEditorClick(Player player, DropContext context, InventoryClickEvent event) {
        CustomDropEntry entry = findDrop(context.editorContext().mobKey(), context.dropId());
        if (entry == null) {
            player.sendMessage(lang.get("messages.error.drop-missing"));
            openEditor(player, context.editorContext());
            return;
        }

        switch (event.getRawSlot()) {
            case 10 -> chatInputService.request(player, lang.get("prompts.chance"), input -> {
                Integer parsed = parseChance(input);
                if (parsed == null) {
                    player.sendMessage(lang.get("messages.error.invalid-chance"));
                    openDropEditor(player, context);
                    return;
                }
                storage.updateDrop(entry.withChancePerMillion(parsed));
                openDropEditor(player, context);
            });
            case 12 -> chatInputService.request(player, lang.get("prompts.min-amount"), input -> {
                Integer parsed = parsePositiveInt(input);
                if (parsed == null) {
                    player.sendMessage(lang.get("messages.error.invalid-min-amount"));
                    openDropEditor(player, context);
                    return;
                }
                CustomDropEntry updated = entry.withMinAmount(parsed);
                if (updated.maxAmount() < updated.minAmount()) {
                    updated = updated.withMaxAmount(updated.minAmount());
                }
                storage.updateDrop(updated);
                openDropEditor(player, context);
            });
            case 14 -> chatInputService.request(player, lang.get("prompts.max-amount"), input -> {
                Integer parsed = parsePositiveInt(input);
                if (parsed == null) {
                    player.sendMessage(lang.get("messages.error.invalid-max-amount"));
                    openDropEditor(player, context);
                    return;
                }
                storage.updateDrop(entry.withMaxAmount(Math.max(parsed, entry.minAmount())));
                openDropEditor(player, context);
            });
            case 16 -> {
                storage.updateDrop(entry.withEnabled(!entry.enabled()));
                openDropEditor(player, context);
            }
            case 22 -> {
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                if (mainHand.getType().isAir()) {
                    player.sendMessage(lang.get("messages.error.main-hand-empty"));
                } else {
                    ItemStack replacement = mainHand.clone();
                    replacement.setAmount(1);
                    storage.updateDrop(entry.withItem(replacement));
                }
                openDropEditor(player, context);
            }
            case 26 -> {
                storage.deleteDrop(entry.id());
                openEditor(player, context.editorContext());
            }
            case 27 -> openEditor(player, context.editorContext());
            case 31 -> {
                storage.updateDrop(entry.withChancePerMillion(1_000_000));
                openDropEditor(player, context);
            }
            case 32 -> {
                storage.updateDrop(entry.withChancePerMillion(500_000));
                openDropEditor(player, context);
            }
            case 33 -> {
                storage.updateDrop(entry.withChancePerMillion(100_000));
                openDropEditor(player, context);
            }
            case 34 -> {
                storage.updateDrop(entry.withChancePerMillion(10_000));
                openDropEditor(player, context);
            }
            case 35 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void promptXpSettings(Player player, EditorContext context) {
        String mobKey = context.mobKey();
        chatInputService.request(player, lang.get("prompts.xp-min"), minInput -> {
            Integer min = parseNonNegativeInt(minInput);
            if (min == null) {
                player.sendMessage(lang.get("messages.error.invalid-xp"));
                openEditor(player, context);
                return;
            }
            chatInputService.request(player, lang.get("prompts.xp-max"), maxInput -> {
                Integer max = parseNonNegativeInt(maxInput);
                if (max == null) {
                    player.sendMessage(lang.get("messages.error.invalid-xp"));
                    openEditor(player, context);
                    return;
                }
                int fixedMax = Math.max(min, max);
                chatInputService.request(player, lang.get("prompts.xp-chance"), chanceInput -> {
                    Integer chance = parseChance(chanceInput);
                    if (chance == null) {
                        player.sendMessage(lang.get("messages.error.invalid-chance"));
                        openEditor(player, context);
                        return;
                    }
                    storage.setXpSettings(mobKey, new MobXpSettings(min, fixedMax, chance));
                    openEditor(player, context);
                });
            });
        });
    }

    private void moveDrop(String mobKey, long dropId, int direction) {
        List<CustomDropEntry> drops = new ArrayList<>(sortedDrops(mobKey));
        int currentIndex = -1;
        for (int index = 0; index < drops.size(); index++) {
            if (drops.get(index).id() == dropId) {
                currentIndex = index;
                break;
            }
        }
        if (currentIndex < 0) {
            return;
        }

        int targetIndex = currentIndex + direction;
        if (targetIndex < 0 || targetIndex >= drops.size()) {
            return;
        }

        CustomDropEntry current = drops.get(currentIndex);
        drops.set(currentIndex, drops.get(targetIndex));
        drops.set(targetIndex, current);

        List<CustomDropEntry> reordered = new ArrayList<>(drops.size());
        for (int index = 0; index < drops.size(); index++) {
            reordered.add(drops.get(index).withSortOrder(index));
        }
        storage.saveDropOrder(mobKey, reordered);
    }

    private @Nullable CustomDropEntry findDrop(String mobKey, long dropId) {
        return sortedDrops(mobKey).stream()
            .filter(drop -> drop.id() == dropId)
            .findFirst()
            .orElse(null);
    }

    private List<CustomDropEntry> sortedDrops(String mobKey) {
        return storage.getDrops(mobKey).stream()
            .sorted(Comparator.comparingInt(CustomDropEntry::sortOrder).thenComparingLong(CustomDropEntry::id))
            .toList();
    }

    private @Nullable ItemStack resolveTemplateFromEvent(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            ItemStack template = cursor.clone();
            template.setAmount(1);
            return template;
        }

        if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getWhoClicked().getInventory())) {
            ItemStack current = event.getCurrentItem();
            if (current != null && !current.getType().isAir()) {
                ItemStack template = current.clone();
                template.setAmount(1);
                return template;
            }
        }

        return null;
    }

    private int clampPage(int totalItems, int pageSize, int requestedPage) {
        int maxPage = Math.max(0, totalPages(totalItems, pageSize) - 1);
        if (requestedPage < 0) {
            return 0;
        }
        return Math.min(requestedPage, maxPage);
    }

    private int totalPages(int totalItems, int pageSize) {
        return totalItems <= 0 ? 1 : (int) Math.ceil(totalItems / (double) pageSize);
    }

    private @Nullable Integer parsePositiveInt(String input) {
        try {
            int parsed = Integer.parseInt(input.trim());
            return parsed >= 1 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private @Nullable Integer parseNonNegativeInt(String input) {
        try {
            int parsed = Integer.parseInt(input.trim());
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private @Nullable Integer parseChance(String input) {
        try {
            String normalized = input.trim().replace('%', ' ').replace(',', '.').trim();
            BigDecimal value = new BigDecimal(normalized);
            if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
                return null;
            }
            BigDecimal scaled = value.multiply(BigDecimal.valueOf(10_000));
            return scaled.setScale(0, RoundingMode.HALF_UP).intValueExact();
        } catch (Exception exception) {
            return null;
        }
    }

    private String sourceLabel(MobSource source) {
        return source == MobSource.MYTHIC ? "MythicMobs" : "Vanilla";
    }

    private String modeLabel(DropMode mode) {
        return mode == DropMode.APPEND ? "Mevcutlara Ekle" : "Tamamen Değiştir";
    }

    private void fillBackground(Inventory inventory, Material material, String name) {
        ItemStack filler = button(material, name, List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GOLD));
        meta.lore(toLore(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> toLore(List<String> lines) {
        List<Component> lore = new ArrayList<>(lines.size());
        for (String line : lines) {
            lore.add(Component.text(line, NamedTextColor.GRAY));
        }
        return lore;
    }

    private sealed interface BaseHolder extends InventoryHolder permits BrowserHolder, EditorHolder, DropEditorHolder {
        @Override
        @NotNull Inventory getInventory();
    }

    private static final class BrowserHolder implements BaseHolder {
        private final BrowserContext context;
        private final Inventory inventory;

        private BrowserHolder(BrowserContext context, String title) {
            this.context = context;
            this.inventory = Bukkit.createInventory(this, 54, Component.text(title, NamedTextColor.DARK_AQUA));
        }

        private BrowserContext context() {
            return context;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        private Inventory inventory() {
            return inventory;
        }
    }

    private static final class EditorHolder implements BaseHolder {
        private final EditorContext context;
        private final Inventory inventory;

        private EditorHolder(EditorContext context, String title) {
            this.context = context;
            this.inventory = Bukkit.createInventory(this, 54, Component.text(title, NamedTextColor.DARK_GREEN));
        }

        private EditorContext context() {
            return context;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        private Inventory inventory() {
            return inventory;
        }
    }

    private static final class DropEditorHolder implements BaseHolder {
        private final DropContext context;
        private final Inventory inventory;

        private DropEditorHolder(DropContext context, String title) {
            this.context = context;
            this.inventory = Bukkit.createInventory(this, 36, Component.text(title, NamedTextColor.DARK_PURPLE));
        }

        private DropContext context() {
            return context;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }

        private Inventory inventory() {
            return inventory;
        }
    }

    private record BrowserContext(@Nullable String query, int page) {
    }

    private record EditorContext(String mobKey, int customPage, int existingPage, @Nullable String browserQuery, int browserPage) {
    }

    private record DropContext(EditorContext editorContext, long dropId) {
    }
}
