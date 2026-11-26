package de.relicaltar;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RelicAltarPlugin extends JavaPlugin implements Listener {

    private LockManager locks;
    private LockListener lockListener;
    private SenseItem sense;
    private AciBridge aci;
    private Tokens tokens;
    private AltarManager altars;
    private final Map<UUID, LockMenu> awaitingSearch = new HashMap<>();

    LockManager locks() { return locks; }
    SenseItem sense() { return sense; }
    AciBridge aci() { return aci; }
    Tokens tokens() { return tokens; }
    AltarManager altars() { return altars; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        aci = new AciBridge(this);
        sense = new SenseItem(this);
        tokens = new Tokens(this);
        altars = new AltarManager(this);
        locks = new LockManager(this);
        altars.load();
        altars.registerCraftRecipe();

        lockListener = new LockListener(this, locks);
        Bukkit.getPluginManager().registerEvents(lockListener, this);
        Bukkit.getPluginManager().registerEvents(new SenseListener(this, sense), this);
        Bukkit.getPluginManager().registerEvents(new AltarListener(this, altars), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);
        Bukkit.getPluginManager().registerEvents(this, this);

        Bukkit.getScheduler().runTaskTimer(this, altars::tick, 20L, 2L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!getConfig().getBoolean("remove-from-inventories")) return;
            for (Player p : Bukkit.getOnlinePlayers()) lockListener.purgePlayer(p);
        }, 40L, 40L);

        getLogger().info("RelicAltar aktiv - " + locks.all().size() + " Items gesperrt, "
                + altars.recipes().size() + " Altar-Rezepte, " + altars.altars().size() + " Altare, ACI: " + aci.available());
    }

    @Override
    public void onDisable() {
        if (altars != null) {
            altars.unregisterCraftRecipe();
            altars.shutdown();
        }
    }

    void awaitSearch(Player p, LockMenu menu) {
        awaitingSearch.put(p.getUniqueId(), menu);
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        LockMenu menu = awaitingSearch.remove(e.getPlayer().getUniqueId());
        if (menu == null) return;
        e.setCancelled(true);
        String text = Ui.plain(e.message()).trim();
        Bukkit.getScheduler().runTask(this, () -> {
            menu.setSearch(text.equals("-") ? "" : text);
            menu.open();
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        awaitingSearch.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!getConfig().getBoolean("resource-pack.send-on-join")) return;
        String url = getConfig().getString("resource-pack.url", "");
        if (url == null || url.isBlank()) return;
        byte[] hash = null;
        String sha1 = getConfig().getString("resource-pack.sha1", "");
        if (sha1 != null && sha1.length() == 40) {
            hash = new byte[20];
            for (int i = 0; i < 20; i++) hash[i] = (byte) Integer.parseInt(sha1.substring(i * 2, i * 2 + 2), 16);
        }
        Component prompt = Ui.c(getConfig().getString("resource-pack.prompt", ""));
        e.getPlayer().setResourcePack(UUID.nameUUIDFromBytes(("relicaltar:" + url).getBytes()), url, hash, prompt, false);
    }

    // ------------------------------------------------------------------ Commands

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("relicaltar.admin")) {
            sender.sendMessage(Ui.c("&cKeine Berechtigung."));
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("lock")) return lockCommand(sender, args);
        return relicCommand(sender, args);
    }

    private boolean lockCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Nur im Spiel. Konsole: /lock list | reload");
                return true;
            }
            new LockMenu(this, locks, p).open();
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                List<String> names = new ArrayList<>();
                for (Material m : locks.all()) names.add(m.name());
                java.util.Collections.sort(names);
                sender.sendMessage(Ui.c("&8[&bItemLock&8] &7Gesperrt (" + names.size() + "): &f" + (names.isEmpty() ? "-" : String.join(", ", names))));
            }
            case "reload" -> {
                reloadConfig();
                locks.load();
                altars.load();
                altars.registerCraftRecipe();
                sender.sendMessage(Ui.c("&8[&bItemLock&8] &aNeu geladen."));
            }
            default -> sender.sendMessage(Ui.c("&7/lock &8[list|reload]"));
        }
        return true;
    }

    private boolean relicCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Nur im Spiel. Konsole: /relic give|list|reset|reload");
                return true;
            }
            new HubMenu(this, p).open();
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> {
                if (args.length < 2) {
                    sender.sendMessage(Ui.c("&7/relic give <altar|sense|aci:id|mc:MATERIAL> [spieler] [anzahl]"));
                    return true;
                }
                String token = switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "altar" -> Tokens.ALTAR;
                    case "sense" -> Tokens.SENSE;
                    default -> args[1];
                };
                if (!tokens.valid(token)) {
                    sender.sendMessage(Ui.c("&cUnbekanntes Item: " + token));
                    return true;
                }
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(Ui.c("&cSpieler nicht gefunden."));
                    return true;
                }
                int amount = 1;
                if (args.length >= 4) {
                    try { amount = Math.max(1, Integer.parseInt(args[3])); } catch (NumberFormatException ignored) { }
                }
                ItemStack it = tokens.create(token, amount);
                for (ItemStack rest : target.getInventory().addItem(it).values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), rest);
                }
                sender.sendMessage(Ui.c("&8[&5Relic&8] &7" + tokens.plainName(token) + " an &f" + target.getName() + " &7gegeben."));
            }
            case "list" -> {
                sender.sendMessage(Ui.c("&8[&5Relic&8] &7Altare (" + altars.altars().size() + "):"));
                for (AltarManager.Altar a : altars.altars()) {
                    sender.sendMessage(Ui.c("&8- &f" + a.world + " " + a.x + " " + a.y + " " + a.z
                            + (a.busy() ? " &5(schmiedet " + tokens.plainName(a.busyToken) + ")" : "")));
                }
            }
            case "reset" -> {
                if (args.length < 2) {
                    sender.sendMessage(Ui.c("&7/relic reset <sense|altar|aci:id|mc:MATERIAL>"));
                    return true;
                }
                String token = args[1].equalsIgnoreCase("sense") ? Tokens.SENSE : args[1].equalsIgnoreCase("altar") ? Tokens.ALTAR : args[1];
                altars.limit(token).crafted = 0;
                altars.saveRecipes();
                sender.sendMessage(Ui.c("&8[&5Relic&8] &7Craft-Zaehler fuer &f" + tokens.plainName(token) + " &7zurueckgesetzt."));
            }
            case "removealtar" -> {
                if (!(sender instanceof Player p)) return true;
                Block b = p.getTargetBlockExact(6);
                AltarManager.Altar a = altars.altarAt(b);
                if (a == null) {
                    sender.sendMessage(Ui.c("&cSchau einen Altar an (max. 6 Bloecke)."));
                    return true;
                }
                altars.unregister(a);
                b.setType(Material.AIR);
                sender.sendMessage(Ui.c("&8[&5Relic&8] &7Altar entfernt."));
            }
            case "reload" -> {
                reloadConfig();
                locks.load();
                altars.load();
                altars.registerCraftRecipe();
                sender.sendMessage(Ui.c("&8[&5Relic&8] &aNeu geladen."));
            }
            default -> sender.sendMessage(Ui.c("&7/relic &8[give|list|reset|removealtar|reload]"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("lock")) {
            if (args.length == 1) out.addAll(List.of("list", "reload"));
        } else if (args.length == 1) {
            out.addAll(List.of("give", "list", "reset", "removealtar", "reload"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset"))) {
            out.add("altar");
            out.add("sense");
            for (String id : aci.ids()) out.add("aci:" + id);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
