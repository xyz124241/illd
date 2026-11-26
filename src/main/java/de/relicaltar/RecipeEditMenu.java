package de.relicaltar;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Rezept-Editor. Item auf den Cursor nehmen und auf ein Feld klicken = Zutat setzen
 * (das Item wird nicht verbraucht). Leere Hand + Klick = Feld leeren.
 */
final class RecipeEditMenu extends Menu {

    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int RESULT = 24;

    private final RelicAltarPlugin plugin;
    private final Player player;
    private final AltarManager.Recipe recipe;
    private boolean confirmDelete = false;

    RecipeEditMenu(RelicAltarPlugin plugin, Player player, AltarManager.Recipe recipe) {
        this.plugin = plugin;
        this.player = player;
        this.recipe = recipe;
    }

    void open() {
        inv = Bukkit.createInventory(this, 54, Component.text("Rezept: " + recipe.id));
        render();
        player.openInventory(inv);
    }

    private void render() {
        inv.clear();
        ItemStack fill = Ui.button(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        for (int i = 0; i < 9; i++) {
            String t = recipe.grid[i];
            if (t == null) inv.setItem(GRID[i], Ui.button(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&7Leer",
                    "&8Item auf den Cursor nehmen", "&8und hier klicken"));
            else if (!plugin.tokens().valid(t)) inv.setItem(GRID[i], Ui.button(Material.BARRIER, "&cUngueltig: " + t));
            else inv.setItem(GRID[i], plugin.tokens().create(t, 1));
        }
        inv.setItem(23, Ui.button(Material.ARROW, "&7=>"));

        if (recipe.result != null && plugin.tokens().valid(recipe.result)) {
            ItemStack res = plugin.tokens().create(recipe.result, recipe.amount);
            res.setAmount(Math.max(1, Math.min(recipe.amount, res.getMaxStackSize())));
            inv.setItem(RESULT, res);
        } else {
            inv.setItem(RESULT, Ui.button(Material.STRUCTURE_VOID, "&eErgebnis waehlen",
                    "&7Item auf den Cursor + Klick", "&7oder leere Hand + Klick fuer die Liste"));
        }
        inv.setItem(25, Ui.button(Material.BOOK, "&bErgebnis aus Liste", "&7Sense, Altar und alle ACI-Items"));
        inv.setItem(34, Ui.button(Material.GOLD_NUGGET, "&6Menge: &f" + recipe.amount,
                "&7Linksklick +1, Rechtsklick -1", "&7Shift: +/-8"));

        if (recipe.result != null && plugin.tokens().valid(recipe.result)) {
            AltarManager.Limit l = plugin.altars().limit(recipe.result);
            inv.setItem(37, Ui.button(Material.CLOCK, "&dMax. Crafts: &f" + (l.max < 0 ? "unbegrenzt" : String.valueOf(l.max)),
                    "&7Bereits gecraftet: &f" + l.crafted,
                    "&7Gilt fuer &f" + plugin.tokens().plainName(recipe.result) + " &7(alle Rezepte)",
                    "&7Linksklick +1, Rechtsklick -1 (unter 0 = unbegrenzt)", "&7Shift: +/-10"));
            inv.setItem(38, Ui.button(Material.SPONGE, "&eZaehler zuruecksetzen", "&7Gecraftet: " + l.crafted + " -> 0"));
        } else {
            inv.setItem(37, Ui.button(Material.CLOCK, "&8Max. Crafts", "&7Erst ein Ergebnis waehlen."));
        }
        inv.setItem(43, Ui.button(confirmDelete ? Material.TNT : Material.RED_CONCRETE,
                confirmDelete ? "&4&lWIRKLICH LOESCHEN?" : "&cRezept loeschen", confirmDelete ? "&7Nochmal klicken" : "&7Mit Bestaetigung"));
        inv.setItem(45, Ui.button(Material.ARROW, "&e<< Zur Liste"));
    }

    @Override
    void click(InventoryClickEvent e) {
        if (e.getClickedInventory() != inv) return;
        int slot = e.getRawSlot();
        ItemStack cursor = e.getCursor();
        boolean hasCursor = cursor != null && !cursor.getType().isAir();

        if (slot != 43) confirmDelete = false;

        for (int i = 0; i < 9; i++) {
            if (GRID[i] != slot) continue;
            recipe.grid[i] = hasCursor ? plugin.tokens().tokenOf(cursor) : null;
            saved();
            return;
        }
        switch (slot) {
            case RESULT -> {
                if (hasCursor) {
                    recipe.result = plugin.tokens().tokenOf(cursor);
                    saved();
                } else {
                    openPicker();
                }
            }
            case 25 -> openPicker();
            case 34 -> {
                int step = e.isShiftClick() ? 8 : 1;
                recipe.amount = Math.max(1, Math.min(64, recipe.amount + (e.isRightClick() ? -step : step)));
                saved();
            }
            case 37 -> {
                if (recipe.result == null) return;
                AltarManager.Limit l = plugin.altars().limit(recipe.result);
                int step = e.isShiftClick() ? 10 : 1;
                l.max = Math.max(-1, l.max + (e.isRightClick() ? -step : step));
                saved();
            }
            case 38 -> {
                if (recipe.result == null) return;
                plugin.altars().limit(recipe.result).crafted = 0;
                saved();
            }
            case 43 -> {
                if (!confirmDelete) {
                    confirmDelete = true;
                    render();
                } else {
                    plugin.altars().deleteRecipe(recipe);
                    new RecipeListMenu(plugin, player).open();
                }
            }
            case 45 -> new RecipeListMenu(plugin, player).open();
            default -> { }
        }
    }

    private void saved() {
        plugin.altars().saveRecipes();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        render();
    }

    private void openPicker() {
        new ResultPickerMenu(plugin, player, token -> {
            recipe.result = token;
            plugin.altars().saveRecipes();
            open();
        }, this::open).open();
    }
}
