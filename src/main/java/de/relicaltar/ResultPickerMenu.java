package de.relicaltar;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/** Auswahl aller Custom-Items (Sense, Altar, ACI). */
final class ResultPickerMenu extends Menu {

    private static final int PER_PAGE = 45;

    private final RelicAltarPlugin plugin;
    private final Player player;
    private final Consumer<String> onPick;
    private final Runnable onBack;
    private List<String> tokens = List.of();
    private int page = 0;

    ResultPickerMenu(RelicAltarPlugin plugin, Player player, Consumer<String> onPick, Runnable onBack) {
        this.plugin = plugin;
        this.player = player;
        this.onPick = onPick;
        this.onBack = onBack;
    }

    void open() {
        inv = Bukkit.createInventory(this, 54, Component.text("Ergebnis waehlen"));
        tokens = plugin.tokens().customTokens();
        int pages = Math.max(1, (int) Math.ceil(tokens.size() / (double) PER_PAGE));
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= tokens.size()) break;
            ItemStack it = plugin.tokens().create(tokens.get(idx), 1);
            inv.setItem(i, it);
        }
        inv.setItem(45, Ui.button(Material.ARROW, "&e<< Zurueck"));
        inv.setItem(49, Ui.button(Material.PAPER, "&fSeite " + (page + 1) + " / " + pages,
                plugin.aci().available() ? "&aACI erkannt" : "&cACI nicht geladen (Items aus items.yml)"));
        inv.setItem(48, Ui.button(Material.SPECTRAL_ARROW, "&e< Seite zurueck"));
        inv.setItem(53, Ui.button(Material.ARROW, "&eWeiter >>"));
        player.openInventory(inv);
    }

    @Override
    void click(InventoryClickEvent e) {
        if (e.getClickedInventory() != inv) return;
        int slot = e.getRawSlot();
        if (slot < PER_PAGE) {
            int idx = page * PER_PAGE + slot;
            if (idx < tokens.size()) onPick.accept(tokens.get(idx));
            return;
        }
        switch (slot) {
            case 45 -> onBack.run();
            case 48 -> { page--; open(); }
            case 53 -> { page++; open(); }
            default -> { }
        }
    }
}
