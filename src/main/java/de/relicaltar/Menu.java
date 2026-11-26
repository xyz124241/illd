package de.relicaltar;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Basis aller Admin-Menues. Alle Klicks sind abgebrochen, click() entscheidet. */
abstract class Menu implements InventoryHolder {
    protected Inventory inv;

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    abstract void click(InventoryClickEvent e);
}
