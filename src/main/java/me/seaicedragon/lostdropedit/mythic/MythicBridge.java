package me.seaicedragon.lostdropedit.mythic;

import io.lumine.mythic.api.adapters.AbstractItemStack;
import io.lumine.mythic.api.drops.IItemDrop;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.drops.Drop;
import io.lumine.mythic.core.drops.DropMetadataImpl;
import io.lumine.mythic.core.drops.DropTable;
import io.lumine.mythic.core.drops.droppables.CustomDrop;
import io.lumine.mythic.core.drops.droppables.DropTableDrop;
import io.lumine.mythic.core.drops.droppables.MythicItemDrop;
import io.lumine.mythic.core.items.MythicItem;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.core.mobs.MobExecutor;
import me.seaicedragon.lostdropedit.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class MythicBridge {

    private final MythicBukkit mythicBukkit;
    private final MobExecutor mobExecutor;

    public MythicBridge() {
        this.mythicBukkit = MythicBukkit.inst();
        this.mobExecutor = (MobExecutor) mythicBukkit.getMobManager();
    }

    public Collection<MythicMob> getMobs() {
        return mythicBukkit.getMobManager().getMobTypes();
    }

    public Optional<MythicMob> getMob(String internalName) {
        return mythicBukkit.getMobManager().getMythicMob(internalName);
    }

    public boolean isMythicMob(Entity entity) {
        return mobExecutor.isMythicMob(entity);
    }

    public Optional<ActiveMob> getActiveMob(Entity entity) {
        return mobExecutor.getActiveMob(entity.getUniqueId());
    }

    public List<ExistingDropPreview> buildDropPreviews(String internalName) {
        Optional<MythicMob> mobOptional = getMob(internalName);
        if (mobOptional.isEmpty()) {
            return List.of();
        }

        DropTable dropTable = mobOptional.get().getDropTable();
        if (dropTable == null || !dropTable.hasDrops()) {
            return List.of();
        }

        List<ExistingDropPreview> previews = new ArrayList<>();
        int index = 0;
        for (Drop drop : dropTable.getDrops().getView()) {
            previews.add(toPreview(index++, drop));
        }
        return previews;
    }

    private ExistingDropPreview toPreview(int index, Drop drop) {
        ItemStack icon = new ItemStack(Material.PAPER);
        ItemStack importItem = null;
        boolean importable = false;
        String title = Text.toTitleCase(drop.getClass().getSimpleName());
        List<String> description = new ArrayList<>();
        description.add("Ağırlık: " + drop.getWeight());

        if (drop instanceof MythicItemDrop mythicItemDrop) {
            MythicItem item = mythicItemDrop.getItem();
            title = "Mythic Eşya: " + item.getInternalName();
        }
        if (drop instanceof DropTableDrop tableDrop) {
            title = "Drop Tablosu: " + tableDrop.getDropTable().getInternalName();
        }
        if (drop instanceof CustomDrop customDrop) {
            title = "Özel Drop: " + customDrop.getDropArgument();
            description.add("Satır: " + customDrop.getConfigLine());
        }
        if (drop instanceof IItemDrop itemDrop) {
            try {
                AbstractItemStack generated = itemDrop.getDrop(new DropMetadataImpl(null, null), 1.0D);
                if (generated != null) {
                    importItem = BukkitAdapter.adapt(generated);
                    if (importItem != null && importItem.getType() != Material.AIR) {
                        icon = importItem.clone();
                        icon.setAmount(1);
                        importable = true;
                    }
                }
            } catch (Exception exception) {
                description.add("Önizleme hazırlanamadı: " + exception.getClass().getSimpleName());
            }
        }

        return new ExistingDropPreview(
            "existing-" + index,
            title,
            icon,
            description,
            importable,
            importItem
        );
    }
}
