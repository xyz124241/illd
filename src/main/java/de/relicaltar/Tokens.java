package de.relicaltar;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Token = eindeutige Item-Kennung fuer Rezepte und Limits:
 * "mc:MATERIAL", "aci:id", "relic:sense", "relic:altar".
 */
final class Tokens {
    static final String SENSE = "relic:sense";
    static final String ALTAR = "relic:altar";

    private final RelicAltarPlugin plugin;

    Tokens(RelicAltarPlugin plugin) {
        this.plugin = plugin;
    }

    String tokenOf(ItemStack st) {
        if (st == null || st.getType().isAir()) return null;
        if (plugin.sense().isSense(st)) return SENSE;
        if (plugin.altars().isAltarItem(st)) return ALTAR;
        String id = plugin.aci().idOf(st);
        if (id != null) return "aci:" + id;
        return "mc:" + st.getType().name();
    }

    ItemStack create(String token, int amount) {
        if (token == null) return new ItemStack(Material.AIR);
        int a = Math.max(1, amount);
        if (token.equals(SENSE)) return plugin.sense().create();
        if (token.equals(ALTAR)) return plugin.altars().createAltarItem(a);
        if (token.startsWith("aci:")) return plugin.aci().create(token.substring(4), a);
        Material m = Material.matchMaterial(token.startsWith("mc:") ? token.substring(3) : token);
        if (m == null || !m.isItem()) return Ui.button(Material.BARRIER, "&cUngueltig: " + token);
        return new ItemStack(m, Math.min(a, m.getMaxStackSize()));
    }

    String plainName(String token) {
        if (token == null) return "-";
        if (token.equals(SENSE)) return "Sense";
        if (token.equals(ALTAR)) return "Altar";
        if (token.startsWith("aci:")) return plugin.aci().plainName(token.substring(4));
        return Ui.pretty(token.startsWith("mc:") ? token.substring(3) : token);
    }

    boolean valid(String token) {
        if (token == null) return false;
        if (token.equals(SENSE) || token.equals(ALTAR)) return true;
        if (token.startsWith("aci:")) return plugin.aci().exists(token.substring(4));
        Material m = Material.matchMaterial(token.startsWith("mc:") ? token.substring(3) : token);
        return m != null && m.isItem();
    }

    /** Custom-Item = Sense, Altar, ACI oder irgendein Item mit eigenen Daten / eigenem item_model. */
    boolean isCustom(ItemStack st) {
        if (st == null || st.getType().isAir()) return false;
        String t = tokenOf(st);
        if (t != null && !t.startsWith("mc:")) return true;
        ItemMeta meta = st.getItemMeta();
        if (meta == null) return false;
        return !meta.getPersistentDataContainer().isEmpty() || meta.hasItemModel();
    }

    /** Alle waehlbaren Custom-Tokens (fuer den Ergebnis-Picker). */
    List<String> customTokens() {
        List<String> out = new ArrayList<>();
        out.add(SENSE);
        out.add(ALTAR);
        for (String id : plugin.aci().ids()) out.add("aci:" + id);
        return out;
    }
}
