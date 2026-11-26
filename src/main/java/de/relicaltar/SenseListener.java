package de.relicaltar;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.util.Vector;

final class SenseListener implements Listener {

    private final RelicAltarPlugin plugin;
    private final SenseItem sense;
    private final Shockwave shockwave;

    SenseListener(RelicAltarPlugin plugin, SenseItem sense) {
        this.plugin = plugin;
        this.sense = sense;
        this.shockwave = new Shockwave(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() != DamageCause.ENTITY_ATTACK) return;
        if (!sense.isSense(p.getInventory().getItemInMainHand())) return;

        // 1) Schild brechen (wie eine Axt)
        if (e.getEntity() instanceof Player victim && victim.isBlocking()) {
            victim.clearActiveItem();
            victim.setCooldown(Material.SHIELD, plugin.getConfig().getInt("sense.shield-cooldown-ticks", 100));
            victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 1f);
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1.2, 0), 25, 0.4, 0.4, 0.4, 0.3);
            victim.getWorld().spawnParticle(Particle.END_ROD, victim.getLocation().add(0, 1.2, 0), 10, 0.3, 0.3, 0.3, 0.05);
        }

        // 2) Mace-Smash: Bonus-Schaden nach Fallhoehe (gleiche Formel wie die echte Mace)
        double fall = p.getFallDistance();
        if (fall <= 1.5 || p.isGliding() || p.isFlying() || p.getAttackCooldown() < 0.85f) return;

        double bonus;
        if (fall <= 3) bonus = 4 * fall;
        else if (fall <= 8) bonus = 12 + 2 * (fall - 3);
        else bonus = 22 + (fall - 8);
        bonus *= plugin.getConfig().getDouble("sense.smash-multiplier", 1.0);

        e.setDamage(e.getDamage() + bonus);
        p.setFallDistance(0f); // kein Fallschaden nach dem Smash, wie bei der Mace

        Entity target = e.getEntity();
        Location loc = target.getLocation();
        shockwave.play(loc);
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
        loc.getWorld().spawnParticle(Particle.CRIT, loc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.2);
        loc.getWorld().playSound(loc, fall > 5 ? Sound.ITEM_MACE_SMASH_GROUND_HEAVY : Sound.ITEM_MACE_SMASH_GROUND, 1f, 1f);

        if (plugin.getConfig().getBoolean("sense.smash-knockback", true)) {
            for (Entity near : target.getNearbyEntities(3.5, 2, 3.5)) {
                if (near == p || !(near instanceof LivingEntity)) continue;
                Vector dir = near.getLocation().toVector().subtract(loc.toVector()).setY(0);
                if (dir.lengthSquared() < 0.0001) continue;
                near.setVelocity(dir.normalize().multiply(0.6).setY(0.4));
            }
        }
    }
}
