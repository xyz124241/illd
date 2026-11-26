package de.relicaltar;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Die Sense: Schwert-Schaden + Axt-Schildbruch + Mace-Fallschaden. Basis: Netherite-Schwert. */
final class SenseItem {

    private final RelicAltarPlugin plugin;
    private final NamespacedKey idKey;

    SenseItem(RelicAltarPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "sense");
    }

    ItemStack create() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Ui.c("&5&lSense"));
        meta.lore(Ui.lore(
                "&7Schwert, Axt und Mace in einer Waffe.",
                "",
                "&8- &fNormaler Schwert-Schaden",
                "&8- &fBricht Schilde (Axt-Effekt)",
                "&8- &fMehr Schaden je hoeher du faellst (Mace)",
                "&8- &fSchockwelle beim Smash"));
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.BYTE, (byte) 1);
        meta.setEnchantmentGlintOverride(true);
        if (plugin.getConfig().getBoolean("sense.use-custom-model", true)) {
            meta.setItemModel(NamespacedKey.fromString("sensepack:sense"));
        }
        item.setItemMeta(meta);
        return item;
    }

    boolean isSense(ItemStack stack) {
        if (stack == null || stack.getType() != Material.NETHERITE_SWORD || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(idKey, PersistentDataType.BYTE);
    }
}
