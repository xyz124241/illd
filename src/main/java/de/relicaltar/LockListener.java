package de.relicaltar;

import org.bukkit.Keyed;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Entfernt gesperrte Items ueberall, wo sie entstehen koennen. */
final class LockListener implements Listener {

    private final RelicAltarPlugin plugin;
    private final LockManager locks;

    LockListener(RelicAltarPlugin plugin, LockManager locks) {
        this.plugin = plugin;
        this.locks = locks;
    }

    /** Vom Altar gedroppte Items sind immer erlaubt (Custom-Rezepte duerfen gesperrte Items herstellen). */
    private boolean fromAltar(Item item) {
        return item.getPersistentDataContainer().has(plugin.altars().dropKey(), PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        Item item = e.getEntity();
        if (item.getThrower() != null || fromAltar(item)) return;
        if (locks.isLocked(item.getItemStack())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMobDeath(EntityDeathEvent e) {
        e.getDrops().removeIf(locks::isLocked);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent e) {
        e.getItems().removeIf(i -> locks.isLocked(i.getItemStack()));
    }

    /** Truhen, Fass, Loot-Loren, Schiffswracks, Strukturen ... */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent e) {
        List<ItemStack> filtered = new ArrayList<>(e.getLoot());
        filtered.removeIf(locks::isLocked);
        e.setLoot(filtered);
    }

    /** Trial Vaults / Trial Spawner. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVaultLoot(BlockDispenseLootEvent e) {
        List<ItemStack> filtered = new ArrayList<>(e.getDispensedLoot());
        filtered.removeIf(locks::isLocked);
        e.setDispensedLoot(filtered);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBarter(PiglinBarterEvent e) {
        e.getOutcome().removeIf(locks::isLocked);
    }

    // ---------------------------------------------------------------- Crafting

    /** Nur Vanilla-Rezepte (Namespace "minecraft") werden gesperrt, Plugin-Rezepte bleiben erlaubt. */
    private boolean isVanilla(Recipe r) {
        return !(r instanceof Keyed k) || k.getKey().getNamespace().equals("minecraft");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        if (r == null) return;
        if (isVanilla(r) && locks.isLocked(r.getResult())) {
            e.getInventory().setResult(null);
            return;
        }
        // Altar-Item darf nicht als normaler Obsidian in fremden Rezepten verbraucht werden
        if (!(r instanceof Keyed k && k.getKey().getNamespace().equals(plugin.getName().toLowerCase()))) {
            for (ItemStack s : e.getInventory().getMatrix()) {
                if (plugin.altars().isAltarItem(s)) {
                    e.getInventory().setResult(null);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        Recipe r = e.getRecipe();
        if (isVanilla(r) && locks.isLocked(r.getResult())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent e) {
        if (locks.isLocked(e.getResult())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCook(BlockCookEvent e) {
        if (locks.isLocked(e.getResult())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithing(PrepareSmithingEvent e) {
        if (locks.isLocked(e.getResult())) e.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent e) {
        if (locks.isLocked(e.getResult())) e.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResultClick(InventoryClickEvent e) {
        if (e.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (e.getInventory().getHolder() instanceof AltarGui) return;
        InventoryType t = e.getInventory().getType();
        if (t == InventoryType.WORKBENCH || t == InventoryType.CRAFTING) return;
        if (locks.isLocked(e.getCurrentItem())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrade(VillagerAcquireTradeEvent e) {
        if (locks.isLocked(e.getRecipe().getResult())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        Item item = e.getItem();
        if (fromAltar(item)) return;
        if (locks.isLocked(item.getItemStack())) {
            e.setCancelled(true);
            if (plugin.getConfig().getBoolean("remove-from-inventories")) item.remove();
        }
    }

    /** Bereits vorhandene gesperrte Items in Kisten beim Oeffnen entfernen (nur mit remove-from-inventories). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!plugin.getConfig().getBoolean("remove-from-inventories")) return;
        Inventory inv = e.getInventory();
        if (inv.getHolder() instanceof Menu || inv.getHolder() instanceof AltarGui) return;
        purge(inv);
    }

    void purge(Inventory inv) {
        ItemStack[] c = inv.getContents();
        for (int i = 0; i < c.length; i++) {
            if (locks.isLocked(c[i])) inv.setItem(i, null);
        }
    }

    void purgePlayer(Player p) {
        purge(p.getInventory());
        purge(p.getEnderChest());
    }
}
