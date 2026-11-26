package de.relicaltar;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/** Hauptmenue von /relic. */
final class HubMenu extends Menu {

    private final RelicAltarPlugin plugin;
    private final Player player;

    HubMenu(RelicAltarPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    void open() {
        inv = Bukkit.createInventory(this, 27, Component.text("Relic Verwaltung"));
        inv.setItem(10, Ui.button(Material.BARRIER, "&cItem-Sperre", "&7Items sperren / freigeben", "&7(mit Bestaetigung)"));
        inv.setItem(12, Ui.button(Material.CRAFTING_TABLE, "&dAltar-Rezepte", "&7Rezepte erstellen & bearbeiten", "&7Craft-Limits einstellen"));
        inv.setItem(14, Ui.button(Material.OBSIDIAN, "&5Altar erhalten", "&7Platzierbarer Altar"));
        inv.setItem(16, plugin.tokens().create(Tokens.SENSE, 1));
        inv.setItem(22, Ui.button(Material.OAK_DOOR, "&7Schliessen"));
        player.openInventory(inv);
    }

    @Override
    void click(InventoryClickEvent e) {
        if (e.getClickedInventory() != inv) return;
        switch (e.getRawSlot()) {
            case 10 -> new LockMenu(plugin, plugin.locks(), player).open();
            case 12 -> new RecipeListMenu(plugin, player).open();
            case 14 -> player.getInventory().addItem(plugin.altars().createAltarItem(1));
            case 16 -> player.getInventory().addItem(plugin.tokens().create(Tokens.SENSE, 1));
            case 22 -> player.closeInventory();
            default -> { }
        }
    }
}
