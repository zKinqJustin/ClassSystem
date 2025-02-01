package de.zkinqjustin.classSystem;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class ClassPlaceholders extends PlaceholderExpansion {

    private ClassSystem plugin;

    public ClassPlaceholders(ClassSystem plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "classsystem";
    }

    @Override
    public String getAuthor() {
        return "YourName";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }

        if (identifier.equals("class")) {
            return plugin.getPlayerClass(player);
        }

        if (identifier.equals("level")) {
            return String.valueOf(plugin.getPlayerLevel(player));
        }

        if (identifier.equals("mana")) {
            return String.valueOf(plugin.getPlayerMana(player));
        }

        return null;
    }
}

