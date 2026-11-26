package de.relicaltar;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Liste der gesperrten Materialien (locked.yml). */
final class LockManager {

    private final RelicAltarPlugin plugin;
    private final File file;
    private final Set<Material> locked = EnumSet.noneOf(Material.class);

    LockManager(RelicAltarPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "locked.yml");
        migrate();
        load();
    }

    /** Uebernimmt die Sperrliste des alten ItemLock-Plugins. */
    private void migrate() {
        File old = new File(plugin.getDataFolder().getParentFile(), "ItemLock/locked.yml");
        if (!file.exists() && old.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                Files.copy(old.toPath(), file.toPath());
                plugin.getLogger().info("Sperrliste aus plugins/ItemLock/locked.yml uebernommen.");
            } catch (IOException ex) {
                plugin.getLogger().warning("Migration fehlgeschlagen: " + ex.getMessage());
            }
        }
    }

    void load() {
        locked.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String name : yml.getStringList("locked")) {
            Material m = Material.matchMaterial(name);
            if (m != null) locked.add(m);
        }
    }

    void save() {
        YamlConfiguration yml = new YamlConfiguration();
        List<String> names = new ArrayList<>();
        for (Material m : locked) names.add(m.name());
        Collections.sort(names);
        yml.set("locked", names);
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("locked.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }

    boolean toggle(Material m) {
        boolean now;
        if (locked.contains(m)) { locked.remove(m); now = false; }
        else { locked.add(m); now = true; }
        save();
        return now;
    }

    boolean isLocked(Material m) {
        return locked.contains(m);
    }

    Set<Material> all() {
        return Collections.unmodifiableSet(locked);
    }

    /** Gesperrt = Material gesperrt UND kein (geschuetztes) Custom-Item. */
    boolean isLocked(ItemStack stack) {
        if (stack == null) return false;
        Material type = stack.getType();
        if (type.isAir() || !locked.contains(type)) return false;
        return !(plugin.getConfig().getBoolean("protect-custom-items", true) && plugin.tokens().isCustom(stack));
    }
}
