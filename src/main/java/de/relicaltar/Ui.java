package de.relicaltar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

final class Ui {
    private Ui() {}

    static Component c(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    static Component mm(String mini) {
        return MiniMessage.miniMessage().deserialize(mini).decoration(TextDecoration.ITALIC, false);
    }

    static String plain(Component comp) {
        return PlainTextComponentSerializer.plainText().serialize(comp);
    }

    static List<Component> lore(String... lines) {
        List<Component> out = new ArrayList<>();
        for (String l : lines) out.add(c(l));
        return out;
    }

    static ItemStack button(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        if (lore.length > 0) meta.lore(lore(lore));
        it.setItemMeta(meta);
        return it;
    }

    /** Unsichtbarer Platzhalter (transparente Textur aus dem Pack, ohne Tooltip). */
    static ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(NamespacedKey.fromString("relic:blank"));
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    static String pretty(String enumName) {
        StringBuilder sb = new StringBuilder();
        for (String p : enumName.toLowerCase().split("_")) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
