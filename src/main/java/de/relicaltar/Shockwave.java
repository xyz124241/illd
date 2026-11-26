package de.relicaltar;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/** Schockwellen-Animation beim Smash: Partikelring + animierte Textur (12 Frames aus dem Resourcepack). */
final class Shockwave {

    private static final int FRAMES = 12;
    private static final int TICKS_PER_FRAME = 1;

    private final RelicAltarPlugin plugin;

    Shockwave(RelicAltarPlugin plugin) {
        this.plugin = plugin;
    }

    void play(Location center) {
        World w = center.getWorld();
        if (w == null) return;
        double size = plugin.getConfig().getDouble("sense.shockwave-size", 6.0);

        // 1) Partikelring (funktioniert immer, auch ohne Resourcepack)
        if (plugin.getConfig().getBoolean("sense.shockwave-particles", true)) {
            for (int i = 0; i < 36; i++) {
                double a = Math.PI * 2 * i / 36;
                Location p = center.clone().add(Math.cos(a) * size / 3, 0.15, Math.sin(a) * size / 3);
                w.spawnParticle(Particle.SMOKE, p, 1, 0.05, 0.02, 0.05, 0.02);
                if (i % 3 == 0) w.spawnParticle(Particle.END_ROD, p, 1, 0, 0.05, 0, 0.03);
            }
        }

        // 2) Animierte Schockwelle als flaches Item-Display (braucht das Resourcepack)
        if (!plugin.getConfig().getBoolean("sense.shockwave-animation", true)) return;

        Location base = center.clone();
        base.setPitch(0f);
        base.setYaw(0f);
        base.add(0, 0.08, 0);

        float s = (float) size;
        Quaternionf flat = new Quaternionf(new AxisAngle4f((float) -Math.PI / 2f, 1f, 0f, 0f));

        ItemDisplay display = w.spawn(base, ItemDisplay.class, d -> {
            d.setItemStack(frame(0));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
            d.setViewRange(1.5f);
            d.setTransformation(new Transformation(new Vector3f(0, 0, 0), flat, new Vector3f(s, s, s), new Quaternionf()));
        });

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                int f = tick / TICKS_PER_FRAME;
                if (!display.isValid() || f >= FRAMES) {
                    display.remove();
                    cancel();
                    return;
                }
                display.setItemStack(frame(f));
                tick++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private ItemStack frame(int f) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString("sensepack:shockwave"));
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setFloats(List.of((float) f));
        meta.setCustomModelDataComponent(cmd);
        item.setItemMeta(meta);
        return item;
    }
}
