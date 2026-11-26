package de.relicaltar;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Verbindung zu ACI: erkennt ACI-Items und erzeugt sie (per API, sonst aus ACI/items.yml). */
final class AciBridge {

    private final RelicAltarPlugin plugin;
    private final NamespacedKey idKey = NamespacedKey.fromString("aci:id");

    AciBridge(RelicAltarPlugin plugin) {
        this.plugin = plugin;
    }

    boolean available() {
        Plugin p = Bukkit.getPluginManager().getPlugin("ACI");
        return p != null && p.isEnabled();
    }

    String idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    private YamlConfiguration yml() {
        File f = new File(plugin.getDataFolder().getParentFile(), "ACI/items.yml");
        return f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
    }

    List<String> ids() {
        ConfigurationSection s = yml().getConfigurationSection("items");
        return s == null ? new ArrayList<>() : new ArrayList<>(s.getKeys(false));
    }

    boolean exists(String id) {
        return ids().contains(id);
    }

    String plainName(String id) {
        String n = yml().getString("items." + id + ".name", id);
        try {
            return Ui.plain(Ui.mm(n));
        } catch (Exception ex) {
            return id;
        }
    }

    ItemStack create(String id, int amount) {
        // 1) ACI-API per Reflection
        try {
            Plugin aci = Bukkit.getPluginManager().getPlugin("ACI");
            if (aci != null && aci.isEnabled()) {
                Object items = aci.getClass().getMethod("items").invoke(aci);
                Method get = null;
                for (Method m : items.getClass().getMethods()) {
                    if (m.getName().equals("get") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) get = m;
                }
                if (get != null) {
                    Object ci = get.invoke(items, id);
                    if (ci != null) {
                        for (Method m : items.getClass().getMethods()) {
                            if (m.getName().equals("create") && m.getParameterCount() == 2
                                    && m.getParameterTypes()[0].isInstance(ci) && m.getParameterTypes()[1] == int.class) {
                                Object res = m.invoke(items, ci, amount);
                                if (res instanceof ItemStack st) return st;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fallback unten
        }
        // 2) Fallback: aus items.yml nachbauen
        ConfigurationSection s = yml().getConfigurationSection("items." + id);
        if (s == null) return Ui.button(Material.BARRIER, "&cACI-Item fehlt: " + id);
        Material mat = Material.matchMaterial(s.getString("material", "AMETHYST_SHARD"));
        if (mat == null || !mat.isItem()) mat = Material.AMETHYST_SHARD;
        ItemStack st = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = st.getItemMeta();
        try {
            meta.displayName(Ui.mm(s.getString("name", id)));
        } catch (Exception ex) {
            meta.displayName(Ui.c("&f" + id));
        }
        meta.setItemModel(NamespacedKey.fromString("aci:" + s.getString("texture", id)));
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        st.setItemMeta(meta);
        return st;
    }
}
