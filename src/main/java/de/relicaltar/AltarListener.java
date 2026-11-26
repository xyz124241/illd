package de.relicaltar;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Altar platzieren, oeffnen, schuetzen, natuerlich generieren; Altar-GUI-Klicks. */
final class AltarListener implements Listener {

    private final RelicAltarPlugin plugin;
    private final AltarManager altars;

    AltarListener(RelicAltarPlugin plugin, AltarManager altars) {
        this.plugin = plugin;
        this.altars = altars;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        AltarManager.Altar a = altars.altarAt(e.getClickedBlock());
        if (a == null) return;
        e.setCancelled(true);
        new AltarGui(plugin, a, e.getPlayer()).open();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!altars.isAltarItem(e.getItemInHand())) return;
        altars.register(e.getBlockPlaced());
        e.getPlayer().sendMessage(Ui.c("&8[&5Altar&8] &7Altar platziert. Rechtsklick zum Benutzen - er kann nicht abgebaut werden."));
    }

    // ---------------------------------------------------------------- Schutz

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        if (altars.altarAt(e.getBlock()) != null) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Ui.c("&cDer Altar kann nicht abgebaut werden."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> altars.altarAt(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> altars.altarAt(b) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (anyAltar(e.getBlocks())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (anyAltar(e.getBlocks())) e.setCancelled(true);
    }

    private boolean anyAltar(List<Block> blocks) {
        for (Block b : blocks) if (altars.altarAt(b) != null) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        if (altars.altarAt(e.getBlock()) != null) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- Natuerliche Altare

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!e.isNewChunk()) return;
        if (!plugin.getConfig().getBoolean("altar.generation.enabled", true)) return;
        World w = e.getWorld();
        if (!plugin.getConfig().getStringList("altar.generation.worlds").contains(w.getName())) return;
        if (ThreadLocalRandom.current().nextDouble() >= plugin.getConfig().getDouble("altar.generation.chance-per-chunk", 0.004)) return;
        int cx = e.getChunk().getX(), cz = e.getChunk().getZ();
        plugin.getServer().getScheduler().runTask(plugin, () -> altars.placeNatural(w, cx, cz));
    }

    // ---------------------------------------------------------------- Altar-GUI

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof AltarGui gui) gui.click(e);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof AltarGui gui)) return;
        for (int raw : e.getRawSlots()) {
            if (raw < AltarGui.SIZE && !AltarGui.isCraftSlot(raw)) {
                e.setCancelled(true);
                return;
            }
        }
        gui.schedule();
    }

    @EventHandler
    public void onGuiClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof AltarGui gui && e.getPlayer() instanceof Player) gui.returnItems();
    }
}
