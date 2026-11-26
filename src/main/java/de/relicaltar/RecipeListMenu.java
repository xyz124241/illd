package de.relicaltar;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Liste aller Altar-Rezepte. */
final class RecipeListMenu extends Menu {

    private static final int PER_PAGE = 45;

    private final RelicAltarPlugin plugin;
    private final Player player;
    private int page = 0;
    private List<AltarManager.Recipe> shown = new ArrayList<>();

    RecipeListMenu(RelicAltarPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    void open() {
        inv = Bukkit.createInventory(this, 54, Component.text("Altar-Rezepte"));
        shown = new ArrayList<>(plugin.altars().recipes());
        int pages = Math.max(1, (int) Math.ceil(shown.size() / (double) PER_PAGE));
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= shown.size()) break;
            AltarManager.Recipe r = shown.get(idx);
            ItemStack it;
            if (r.result == null || !plugin.tokens().valid(r.result)) {
                it = Ui.button(Material.STRUCTURE_VOID, "&7" + r.id + " &8(kein Ergebnis)", "&eKlicken zum Bearbeiten");
            } else {
                it = plugin.tokens().create(r.result, r.amount);
                ItemMeta meta = it.getItemMeta();
                int max = plugin.altars().limit(r.result).max;
                int crafted = plugin.altars().limit(r.result).crafted;
                List<Component> lore = new ArrayList<>(Ui.lore("", "&8Rezept: &7" + r.id,
                        "&7Limit: &f" + (max < 0 ? "unbegrenzt" : crafted + " / " + max + " gecraftet"),
                        "&eKlicken zum Bearbeiten"));
                if (meta.lore() != null) lore.addAll(0, meta.lore());
                meta.lore(lore);
                it.setItemMeta(meta);
            }
            inv.setItem(i, it);
        }
        inv.setItem(45, Ui.button(Material.ARROW, "&e<< Zurueck"));
        inv.setItem(48, Ui.button(Material.COMPASS, "&dHauptmenue"));
        inv.setItem(49, Ui.button(Material.EMERALD, "&aNeues Rezept", "&7Leeres Rezept anlegen"));
        inv.setItem(50, Ui.button(Material.PAPER, "&fSeite " + (page + 1) + " / " + pages));
        inv.setItem(53, Ui.button(Material.ARROW, "&eWeiter >>"));
        player.openInventory(inv);
    }

    @Override
    void click(InventoryClickEvent e) {
        if (e.getClickedInventory() != inv) return;
        int slot = e.getRawSlot();
        if (slot < PER_PAGE) {
            int idx = page * PER_PAGE + slot;
            if (idx < shown.size()) new RecipeEditMenu(plugin, player, shown.get(idx)).open();
            return;
        }
        switch (slot) {
            case 45 -> { page--; open(); }
            case 53 -> { page++; open(); }
            case 48 -> new HubMenu(plugin, player).open();
            case 49 -> new RecipeEditMenu(plugin, player, plugin.altars().newRecipe()).open();
            default -> { }
        }
    }
}
