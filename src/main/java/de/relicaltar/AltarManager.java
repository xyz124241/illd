package de.relicaltar;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Altar-Rezepte, Craft-Limits, Altar-Bloecke, schwebende Items und Drops. */
final class AltarManager {

    static final class Recipe {
        final String id;
        String result;
        int amount = 1;
        final String[] grid = new String[9];

        Recipe(String id) { this.id = id; }
    }

    static final class Limit {
        int max = -1;      // -1 = unbegrenzt
        int crafted = 0;
    }

    static final class Altar {
        final String world;
        final int x, y, z;
        String busyToken;
        int busyAmount = 1;
        long endAt;
        String by = "";
        UUID baseDisplay, floatDisplay;
        int phase;

        Altar(String world, int x, int y, int z) {
            this.world = world; this.x = x; this.y = y; this.z = z;
        }

        boolean busy() { return busyToken != null; }
        String key() { return world + ";" + x + ";" + y + ";" + z; }
    }

    private final RelicAltarPlugin plugin;
    private final File recipeFile;
    private final File altarFile;
    private final NamespacedKey altarItemKey;
    private final NamespacedKey dropKey;
    private final NamespacedKey craftKey;

    private final Map<String, Recipe> recipes = new LinkedHashMap<>();
    private final Map<String, Limit> limits = new LinkedHashMap<>();
    private final Map<String, Altar> altars = new LinkedHashMap<>();
    private int tickCounter = 0;

    AltarManager(RelicAltarPlugin plugin) {
        this.plugin = plugin;
        this.recipeFile = new File(plugin.getDataFolder(), "altar-recipes.yml");
        this.altarFile = new File(plugin.getDataFolder(), "altars.yml");
        this.altarItemKey = new NamespacedKey(plugin, "altar_item");
        this.dropKey = new NamespacedKey(plugin, "altar_drop");
        this.craftKey = new NamespacedKey(plugin, "altar_item_recipe");
    }

    NamespacedKey dropKey() { return dropKey; }

    // ------------------------------------------------------------ Altar-Item

    ItemStack createAltarItem(int amount) {
        ItemStack it = new ItemStack(plugin.getConfig().getString("altar.block", "OBSIDIAN").equalsIgnoreCase("OBSIDIAN")
                ? Material.OBSIDIAN : matOr("altar.block", Material.OBSIDIAN), Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Ui.c("&5&lAltar"));
        meta.lore(Ui.lore("&7Hier werden Relikte geschmiedet.", "&7Platzieren, dann Rechtsklick.", "&8Kann nicht abgebaut werden."));
        meta.getPersistentDataContainer().set(altarItemKey, PersistentDataType.BYTE, (byte) 1);
        if (plugin.getConfig().getBoolean("altar.use-custom-model", true)) {
            meta.setItemModel(NamespacedKey.fromString("relic:altar"));
        }
        it.setItemMeta(meta);
        return it;
    }

    private Material matOr(String path, Material def) {
        Material m = Material.matchMaterial(plugin.getConfig().getString(path, def.name()));
        return m != null && m.isBlock() ? m : def;
    }

    Material altarBlock() { return matOr("altar.block", Material.OBSIDIAN); }

    boolean isAltarItem(ItemStack st) {
        if (st == null || st.getType().isAir() || !st.hasItemMeta()) return false;
        return st.getItemMeta().getPersistentDataContainer().has(altarItemKey, PersistentDataType.BYTE);
    }

    void registerCraftRecipe() {
        Bukkit.removeRecipe(craftKey);
        if (!plugin.getConfig().getBoolean("altar.recipe.enabled", true)) return;
        try {
            ShapedRecipe r = new ShapedRecipe(craftKey, createAltarItem(1));
            List<String> shape = plugin.getConfig().getStringList("altar.recipe.shape");
            r.shape(shape.toArray(new String[0]));
            ConfigurationSection ing = plugin.getConfig().getConfigurationSection("altar.recipe.ingredients");
            if (ing != null) {
                for (String k : ing.getKeys(false)) {
                    Material m = Material.matchMaterial(ing.getString(k, ""));
                    if (m != null && !k.isEmpty()) r.setIngredient(k.charAt(0), m);
                }
            }
            Bukkit.addRecipe(r);
        } catch (Exception ex) {
            plugin.getLogger().warning("Altar-Rezept fehlerhaft: " + ex.getMessage());
        }
    }

    void unregisterCraftRecipe() { Bukkit.removeRecipe(craftKey); }

    // ------------------------------------------------------------ Laden / Speichern

    void load() {
        recipes.clear();
        limits.clear();
        altars.clear();

        if (!recipeFile.exists()) {
            seedDefaults();
        } else {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(recipeFile);
            for (Map<?, ?> m : y.getMapList("limits")) {
                Limit l = new Limit();
                l.max = m.get("max") instanceof Number n ? n.intValue() : -1;
                l.crafted = m.get("crafted") instanceof Number n ? n.intValue() : 0;
                limits.put(String.valueOf(m.get("token")), l);
            }
            ConfigurationSection sec = y.getConfigurationSection("recipes");
            if (sec != null) {
                for (String id : sec.getKeys(false)) {
                    ConfigurationSection s = sec.getConfigurationSection(id);
                    if (s == null) continue;
                    Recipe r = new Recipe(id);
                    String res = s.getString("result", "");
                    r.result = res.isEmpty() ? null : res;
                    r.amount = Math.max(1, s.getInt("amount", 1));
                    List<String> g = s.getStringList("grid");
                    for (int i = 0; i < 9 && i < g.size(); i++) r.grid[i] = g.get(i).isEmpty() ? null : g.get(i);
                    recipes.put(id, r);
                }
            }
        }

        if (altarFile.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(altarFile);
            for (Map<?, ?> m : y.getMapList("altars")) {
                Altar a = new Altar(String.valueOf(m.get("world")), num(m.get("x")), num(m.get("y")), num(m.get("z")));
                Object bt = m.get("busy-token");
                if (bt != null) {
                    a.busyToken = String.valueOf(bt);
                    a.busyAmount = Math.max(1, num(m.get("busy-amount")));
                    a.endAt = m.get("end-at") instanceof Number n ? n.longValue() : 0L;
                    a.by = String.valueOf(m.get("by"));
                }
                altars.put(a.key(), a);
            }
        }
    }

    private void seedDefaults() {
        Recipe r = new Recipe("sense");
        r.result = Tokens.SENSE;
        r.grid[0] = "mc:NETHERITE_AXE";
        r.grid[1] = "mc:NETHERITE_SWORD";
        r.grid[2] = "mc:MACE";
        r.grid[4] = "mc:NETHER_STAR";
        recipes.put(r.id, r);
        Limit l = new Limit();
        l.max = 3;
        limits.put(Tokens.SENSE, l);
        saveRecipes();
    }

    private static int num(Object o) { return o instanceof Number n ? n.intValue() : 0; }

    void saveRecipes() {
        YamlConfiguration y = new YamlConfiguration();
        List<Map<String, Object>> lim = new ArrayList<>();
        for (Map.Entry<String, Limit> e : limits.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("token", e.getKey());
            m.put("max", e.getValue().max);
            m.put("crafted", e.getValue().crafted);
            lim.add(m);
        }
        y.set("limits", lim);
        for (Recipe r : recipes.values()) {
            String p = "recipes." + r.id + ".";
            y.set(p + "result", r.result == null ? "" : r.result);
            y.set(p + "amount", r.amount);
            List<String> g = new ArrayList<>();
            for (String t : r.grid) g.add(t == null ? "" : t);
            y.set(p + "grid", g);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(recipeFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("altar-recipes.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }

    void saveAltars() {
        YamlConfiguration y = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Altar a : altars.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", a.world);
            m.put("x", a.x);
            m.put("y", a.y);
            m.put("z", a.z);
            if (a.busy()) {
                m.put("busy-token", a.busyToken);
                m.put("busy-amount", a.busyAmount);
                m.put("end-at", a.endAt);
                m.put("by", a.by);
            }
            list.add(m);
        }
        y.set("altars", list);
        try {
            plugin.getDataFolder().mkdirs();
            y.save(altarFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("altars.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ Rezepte / Limits

    java.util.Collection<Recipe> recipes() { return recipes.values(); }

    Recipe recipe(String id) { return recipes.get(id); }

    Recipe newRecipe() {
        int i = 1;
        while (recipes.containsKey("recipe" + i)) i++;
        Recipe r = new Recipe("recipe" + i);
        recipes.put(r.id, r);
        saveRecipes();
        return r;
    }

    void deleteRecipe(Recipe r) {
        recipes.remove(r.id);
        saveRecipes();
    }

    Limit limit(String token) {
        return limits.computeIfAbsent(token, k -> {
            Limit l = new Limit();
            l.max = plugin.getConfig().getInt("altar.default-max-crafts", -1);
            return l;
        });
    }

    /** Verbleibende Crafts, -1 = unbegrenzt. */
    int left(String token) {
        Limit l = limit(token);
        return l.max < 0 ? -1 : Math.max(0, l.max - l.crafted);
    }

    private static String canon(String[] g, boolean mirror) {
        int minR = 3, maxR = -1, minC = 3, maxC = -1;
        for (int i = 0; i < 9; i++) {
            if (g[i] == null) continue;
            minR = Math.min(minR, i / 3); maxR = Math.max(maxR, i / 3);
            minC = Math.min(minC, i % 3); maxC = Math.max(maxC, i % 3);
        }
        if (maxR < 0) return "";
        StringBuilder sb = new StringBuilder((maxR - minR + 1) + "x" + (maxC - minC + 1) + ":");
        for (int r = minR; r <= maxR; r++) {
            for (int c = minC; c <= maxC; c++) {
                int cc = mirror ? (maxC - (c - minC)) : c;
                String t = g[r * 3 + cc];
                sb.append(t == null ? "_" : t).append(';');
            }
        }
        return sb.toString();
    }

    Recipe match(String[] cells) {
        String c = canon(cells, false);
        if (c.isEmpty()) return null;
        for (Recipe r : recipes.values()) {
            if (r.result == null || !plugin.tokens().valid(r.result)) continue;
            if (canon(r.grid, false).equals(c) || canon(r.grid, true).equals(c)) return r;
        }
        return null;
    }

    // ------------------------------------------------------------ Altar-Bloecke

    Altar altarAt(Block b) {
        return b == null ? null : altars.get(b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ());
    }

    java.util.Collection<Altar> altars() { return altars.values(); }

    Altar register(Block b) {
        Altar a = new Altar(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        altars.put(a.key(), a);
        saveAltars();
        ensureDisplays(a);
        return a;
    }

    void unregister(Altar a) {
        removeEntity(a.baseDisplay);
        removeEntity(a.floatDisplay);
        altars.remove(a.key());
        saveAltars();
    }

    private void removeEntity(UUID id) {
        if (id == null) return;
        Entity e = Bukkit.getEntity(id);
        if (e != null) e.remove();
    }

    Location center(Altar a) {
        World w = Bukkit.getWorld(a.world);
        return w == null ? null : new Location(w, a.x + 0.5, a.y + 0.5, a.z + 0.5);
    }

    boolean tooClose(World w, int x, int z, int dist) {
        long d2 = (long) dist * dist;
        for (Altar a : altars.values()) {
            if (!a.world.equals(w.getName())) continue;
            long dx = a.x - x, dz = a.z - z;
            if (dx * dx + dz * dz < d2) return true;
        }
        return false;
    }

    /** Natuerlichen Altar in einem Chunk platzieren (kleine Plattform + Fackeln). */
    void placeNatural(World w, int cx, int cz) {
        int x = cx * 16 + 8, z = cz * 16 + 8;
        if (!w.isChunkLoaded(cx, cz)) return;
        if (tooClose(w, x, z, plugin.getConfig().getInt("altar.generation.min-distance", 300))) return;
        int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Block ground = w.getBlockAt(x, y, z);
        if (ground.isLiquid() || !ground.getType().isSolid() || y < w.getMinHeight() + 2 || y > w.getMaxHeight() - 6) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.getBlockAt(x + dx, y, z + dz).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
                for (int h = 1; h <= 3; h++) w.getBlockAt(x + dx, y + h, z + dz).setType(Material.AIR, false);
            }
        }
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) w.getBlockAt(x + dx, y + 1, z + dz).setType(Material.SOUL_TORCH, false);
        }
        Block altar = w.getBlockAt(x, y + 1, z);
        altar.setType(altarBlock(), false);
        register(altar);
        plugin.getLogger().info("Natuerlicher Altar bei " + w.getName() + " " + x + " " + (y + 1) + " " + z);
    }

    // ------------------------------------------------------------ Displays

    private ItemDisplay display(UUID id) {
        if (id == null) return null;
        Entity e = Bukkit.getEntity(id);
        return e instanceof ItemDisplay d && d.isValid() ? d : null;
    }

    void ensureDisplays(Altar a) {
        Location c = center(a);
        if (c == null || !c.getWorld().isChunkLoaded(a.x >> 4, a.z >> 4)) return;

        if (display(a.baseDisplay) == null && plugin.getConfig().getBoolean("altar.use-custom-model", true)) {
            ItemStack model = new ItemStack(Material.PAPER);
            ItemMeta meta = model.getItemMeta();
            meta.setItemModel(NamespacedKey.fromString("relic:altar"));
            model.setItemMeta(meta);
            ItemDisplay d = c.getWorld().spawn(c, ItemDisplay.class, disp -> {
                disp.setItemStack(model);
                disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                disp.setPersistent(false);
                disp.setViewRange(1.5f);
                disp.setBrightness(new Display.Brightness(10, 10));
                disp.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.01f, 1.01f, 1.01f), new Quaternionf()));
            });
            a.baseDisplay = d.getUniqueId();
        }

        if (a.busy() && display(a.floatDisplay) == null) {
            Location fl = c.clone().add(0, 1.55, 0);
            ItemStack shown = plugin.tokens().create(a.busyToken, 1);
            ItemDisplay d = c.getWorld().spawn(fl, ItemDisplay.class, disp -> {
                disp.setItemStack(shown);
                disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                disp.setPersistent(false);
                disp.setGlowing(true);
                disp.setViewRange(2f);
                disp.setBrightness(new Display.Brightness(15, 15));
                disp.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.1f, 1.1f, 1.1f), new Quaternionf()));
            });
            a.floatDisplay = d.getUniqueId();
        }
    }

    // ------------------------------------------------------------ Craft-Ablauf

    long delayMillis() {
        return Math.max(1, plugin.getConfig().getInt("altar.drop-delay-seconds", 300)) * 1000L;
    }

    String fmt(String template, Altar a, String itemName) {
        long min = Math.max(1, delayMillis() / 60000L);
        return template.replace("{x}", String.valueOf(a.x)).replace("{y}", String.valueOf(a.y)).replace("{z}", String.valueOf(a.z))
                .replace("{world}", a.world).replace("{item}", itemName).replace("{minutes}", String.valueOf(min));
    }

    void startCraft(Altar a, Player p, Recipe r) {
        a.busyToken = r.result;
        a.busyAmount = r.amount;
        a.endAt = System.currentTimeMillis() + delayMillis();
        a.by = p.getName();
        limit(r.result).crafted++;
        saveRecipes();
        saveAltars();
        ensureDisplays(a);

        Location c = center(a);
        if (c != null) {
            c.getWorld().playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.7f);
            c.getWorld().spawnParticle(Particle.END_ROD, c.clone().add(0, 1.5, 0), 40, 0.4, 0.6, 0.4, 0.05);
        }
        if (plugin.getConfig().getBoolean("altar.leak-coordinates", true)) {
            Component msg = Ui.c(fmt(plugin.getConfig().getString("altar.messages.started", ""), a, plugin.tokens().plainName(r.result)));
            Bukkit.broadcast(msg);
        }
    }

    private void finish(Altar a) {
        Location c = center(a);
        if (c == null) return;
        String token = a.busyToken;
        ItemStack stack = plugin.tokens().create(token, a.busyAmount);
        if (token.startsWith("mc:")) stack.setAmount(Math.min(a.busyAmount, stack.getMaxStackSize()));
        else stack.setAmount(Math.max(1, a.busyAmount));

        removeEntity(a.floatDisplay);
        a.floatDisplay = null;
        a.busyToken = null;
        saveAltars();

        Location drop = c.clone().add(0, 1.4, 0);
        c.getWorld().dropItem(drop, stack, it -> {
            it.setVelocity(new Vector(0, 0.15, 0));
            it.setGlowing(true);
            it.setInvulnerable(true);
            it.setUnlimitedLifetime(true);
            it.setPickupDelay(30);
            it.getPersistentDataContainer().set(dropKey, PersistentDataType.BYTE, (byte) 1);
        });
        c.getWorld().playSound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);
        c.getWorld().playSound(c, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1f);
        c.getWorld().spawnParticle(Particle.END_ROD, drop, 80, 0.5, 0.5, 0.5, 0.15);
        Bukkit.broadcast(Ui.c(fmt(plugin.getConfig().getString("altar.messages.dropped", ""), a, plugin.tokens().plainName(token))));
    }

    /** Alle 2 Ticks: Displays sicherstellen, animieren, Timer pruefen. */
    void tick() {
        tickCounter += 2;
        long now = System.currentTimeMillis();
        for (Altar a : new ArrayList<>(altars.values())) {
            World w = Bukkit.getWorld(a.world);
            if (w == null || !w.isChunkLoaded(a.x >> 4, a.z >> 4)) continue;

            if (tickCounter % 20 == 0) {
                ensureDisplays(a);
                if (a.baseDisplay == null && !plugin.getConfig().getBoolean("altar.use-custom-model", true)) { /* kein Modell */ }
            }
            if (!a.busy()) continue;

            Location c = center(a);
            if (tickCounter % 8 == 0 && c != null) {
                double ang = tickCounter / 6.0;
                for (int i = 0; i < 3; i++) {
                    double t = ang + i * Math.PI * 2 / 3;
                    w.spawnParticle(Particle.ENCHANT, c.clone().add(Math.cos(t) * 0.9, 0.9, Math.sin(t) * 0.9), 2, 0.05, 0.3, 0.05, 0.3);
                }
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, c.clone().add(0, 1.5, 0), 1, 0.15, 0.15, 0.15, 0.01);
            }
            if (tickCounter % 10 == 0) {
                ItemDisplay d = display(a.floatDisplay);
                if (d != null) {
                    a.phase++;
                    float rot = (float) (a.phase * Math.PI / 2);
                    float bob = (a.phase % 2 == 0) ? 0f : 0.15f;
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(10);
                    d.setTransformation(new Transformation(new Vector3f(0, bob, 0),
                            new Quaternionf(new AxisAngle4f(rot, 0f, 1f, 0f)), new Vector3f(1.1f, 1.1f, 1.1f), new Quaternionf()));
                }
            }
            if (now >= a.endAt) finish(a);
        }
    }

    void shutdown() {
        for (Altar a : altars.values()) {
            removeEntity(a.baseDisplay);
            removeEntity(a.floatDisplay);
            a.baseDisplay = null;
            a.floatDisplay = null;
        }
        saveAltars();
        saveRecipes();
    }
}
