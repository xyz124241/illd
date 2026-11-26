package de.relicaltar;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Die Altar-GUI: 3x3 Raster -> Ergebnis, nur Custom-Rezepte. Hintergrund = eigene Textur aus dem Pack. */
final class AltarGui implements InventoryHolder {

    static final int[] CRAFT = {11, 12, 13, 20, 21, 22, 29, 30, 31};
    static final int RESULT = 24;
    static final int INFO = 8;
    static final int SIZE = 45;

    private final RelicAltarPlugin plugin;
    private final AltarManager.Altar altar;
    private final Player player;
    private Inventory inv;

    AltarGui(RelicAltarPlugin plugin, AltarManager.Altar altar, Player player) {
        this.plugin = plugin;
        this.altar = altar;
        this.player = player;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    static boolean isCraftSlot(int s) {
        for (int c : CRAFT) if (c == s) return true;
        return false;
    }

    void open() {
        Component title;
        if (plugin.getConfig().getBoolean("altar.gui-texture", true)) {
            title = Component.text("\uF808\uE000", NamedTextColor.WHITE).font(Key.key("relic", "altar"));
        } else {
            title = Component.text("Altar");
        }
        inv = Bukkit.createInventory(this, SIZE, title);
        ItemStack fill = Ui.filler();
        for (int i = 0; i < SIZE; i++) {
            if (!isCraftSlot(i)) inv.setItem(i, fill);
        }
        refresh();
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 0.8f);
    }

    private String[] cells() {
        String[] t = new String[9];
        for (int i = 0; i < 9; i++) t[i] = plugin.tokens().tokenOf(inv.getItem(CRAFT[i]));
        return t;
    }

    /** Ergebnis-Vorschau und Info-Item neu berechnen. */
    void refresh() {
        List<String> info = new ArrayList<>();
        info.add("&7Lege ein Rezept in das Raster.");
        info.add("&7Nur Custom-Rezepte funktionieren.");
        if (altar.busy()) {
            long s = Math.max(0, (altar.endAt - System.currentTimeMillis()) / 1000);
            info.add("");
            info.add("&5Der Altar schmiedet gerade: &f" + plugin.tokens().plainName(altar.busyToken));
            info.add("&7Noch &f" + (s / 60) + "m " + (s % 60) + "s");
        }
        inv.setItem(INFO, Ui.button(Material.PAPER, "&fCraft powerful items!", info.toArray(new String[0])));

        AltarManager.Recipe r = plugin.altars().match(cells());
        if (r == null) {
            inv.setItem(RESULT, Ui.filler());
            return;
        }
        int left = plugin.altars().left(r.result);
        if (left == 0) {
            inv.setItem(RESULT, Ui.button(Material.BARRIER, "&cLimit erreicht",
                    "&7" + plugin.tokens().plainName(r.result) + " kann nicht mehr", "&7gecraftet werden."));
            return;
        }
        if (altar.busy()) {
            inv.setItem(RESULT, Ui.button(Material.BARRIER, "&cAltar ist beschaeftigt", "&7Warte bis das Relikt fertig ist."));
            return;
        }
        ItemStack res = plugin.tokens().create(r.result, r.amount);
        res.setAmount(Math.max(1, Math.min(r.amount, res.getMaxStackSize())));
        ItemMeta meta = res.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        lore.add(Ui.c("&5&lKlicken zum Schmieden"));
        lore.add(Ui.c(left < 0 ? "&7Craft-Limit: &funbegrenzt" : "&7Noch &f" + left + " &7Mal craftbar"));
        lore.add(Ui.c("&8Schwebt danach " + (plugin.altars().delayMillis() / 60000L) + " Min. ueber dem Altar."));
        meta.lore(lore);
        res.setItemMeta(meta);
        inv.setItem(RESULT, res);
    }

    void click(InventoryClickEvent e) {
        int raw = e.getRawSlot();
        boolean top = raw >= 0 && raw < SIZE;

        if (!top) {
            // Klick im eigenen Inventar
            if (e.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
                return;
            }
            if (e.isShiftClick()) {
                e.setCancelled(true);
                ItemStack cur = e.getCurrentItem();
                if (cur == null || cur.getType().isAir()) return;
                for (int s : CRAFT) {
                    ItemStack in = inv.getItem(s);
                    if (in == null || in.getType().isAir()) {
                        ItemStack one = cur.clone();
                        one.setAmount(1);
                        inv.setItem(s, one);
                        cur.setAmount(cur.getAmount() - 1);
                        e.setCurrentItem(cur.getAmount() <= 0 ? null : cur);
                        break;
                    }
                }
                schedule();
            }
            return;
        }

        if (raw == RESULT) {
            e.setCancelled(true);
            craft();
            return;
        }
        if (!isCraftSlot(raw)) {
            e.setCancelled(true);
            return;
        }
        schedule();
    }

    void schedule() {
        Bukkit.getScheduler().runTask(plugin, this::refresh);
    }

    private void craft() {
        if (altar.busy()) {
            player.sendMessage(Ui.c("&8[&5Altar&8] &cDer Altar ist gerade beschaeftigt."));
            return;
        }
        AltarManager.Recipe r = plugin.altars().match(cells());
        if (r == null) return;
        if (plugin.altars().left(r.result) == 0) {
            player.sendMessage(Ui.c("&8[&5Altar&8] &cDieses Item kann nicht mehr gecraftet werden."));
            return;
        }
        for (int s : CRAFT) {
            ItemStack in = inv.getItem(s);
            if (in == null || in.getType().isAir()) continue;
            if (in.getAmount() <= 1) inv.setItem(s, null);
            else {
                in.setAmount(in.getAmount() - 1);
                inv.setItem(s, in);
            }
        }
        plugin.altars().startCraft(altar, player, r);
        player.closeInventory();
    }

    /** Beim Schliessen alle Raster-Items zurueckgeben. */
    void returnItems() {
        for (int s : CRAFT) {
            ItemStack in = inv.getItem(s);
            if (in == null || in.getType().isAir()) continue;
            inv.setItem(s, null);
            for (ItemStack rest : player.getInventory().addItem(in).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
    }
}
