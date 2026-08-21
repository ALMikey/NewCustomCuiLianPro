package lvhaoxuan.custom.cuilian.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lvhaoxuan.custom.cuilian.NewCustomCuiLianPro;
import lvhaoxuan.custom.cuilian.message.Message;
import lvhaoxuan.llib.api.LLibAPI;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class Level {

    public static HashMap<Integer, Level> levels = new HashMap<>();
    public Integer value;
    public List<String> lore;
    public HashMap<String, List<String>> attribute;
    public ProtectRune protectRune;
    public SuitEffect suitEffect;

    public Level(Integer value, List<String> lore, HashMap<String, List<String>> attribute, ProtectRune protectRune, SuitEffect suitEffect) {
        this.value = value;
        this.lore = lore;
        this.attribute = attribute;
        this.protectRune = protectRune;
        this.suitEffect = suitEffect;
        if (protectRune != null) {
            protectRune.level = this;
        }
    }

    public static Level byItemStack(ItemStack item) {
        if (LLibAPI.checkItemNull(item)) {
            ItemMeta meta = item.getItemMeta();
            List<String> originalLore = meta.hasLore() ? meta.getLore() : new ArrayList<String>();
            Integer markerLevel = findNumericMarkerLevel(originalLore);
            if (markerLevel != null) {
                return markerLevel == Integer.MIN_VALUE ? null : levels.get(markerLevel);
            }
            List<String> lore = new ArrayList<String>(originalLore);
            for (int i = 0; i < lore.size(); i++) {
                lore.set(i, normalizeLevelLine(lore.get(i)));
            }
            for (Level level : levels.values()) {
                if (lore.containsAll(level.lore)) {
                    return level;
                }
            }
        }
        return null;
    }

    private static Integer findNumericMarkerLevel(List<String> lore) {
        Integer foundLevel = null;
        for (String line : lore) {
            int start = line.indexOf(NewCustomCuiLianPro.LEVEL_MARKER_PREFIX);
            while (start >= 0) {
                int valueStart = start + NewCustomCuiLianPro.LEVEL_MARKER_PREFIX.length();
                int end = line.indexOf(']', valueStart);
                if (end < 0) {
                    return Integer.MIN_VALUE;
                }
                try {
                    int value = Integer.parseInt(line.substring(valueStart, end));
                    if (value <= 0 || !levels.containsKey(value)
                            || (foundLevel != null && foundLevel.intValue() != value)) {
                        return Integer.MIN_VALUE;
                    }
                    foundLevel = value;
                } catch (NumberFormatException ex) {
                    return Integer.MIN_VALUE;
                }
                start = line.indexOf(NewCustomCuiLianPro.LEVEL_MARKER_PREFIX, end + 1);
            }
        }
        return foundLevel;
    }

    private static String normalizeLevelLine(String line) {
        line = removeIfConfigured(line, NewCustomCuiLianPro.LEVEL_JUDGE);
        line = removeIfConfigured(line, NewCustomCuiLianPro.PROTECT_RUNE_JUDGE);
        line = line.replace(NewCustomCuiLianPro.LEVEL_MARKER, "");
        line = removeNumericMarkers(line);
        line = removeIfConfigured(line, NewCustomCuiLianPro.LEVEL_STAR_DISPLAY_PREFIX);
        return line;
    }

    private static String removeIfConfigured(String line, String value) {
        return value == null || value.isEmpty() ? line : line.replace(value, "");
    }

    public static boolean containsLevelMarker(String line) {
        return line != null && (line.contains(NewCustomCuiLianPro.LEVEL_MARKER)
                || line.contains(NewCustomCuiLianPro.LEVEL_MARKER_PREFIX));
    }

    public static String removeNumericMarkers(String line) {
        int start = line.indexOf(NewCustomCuiLianPro.LEVEL_MARKER_PREFIX);
        while (start >= 0) {
            int end = line.indexOf(']', start + NewCustomCuiLianPro.LEVEL_MARKER_PREFIX.length());
            line = end < 0 ? line.substring(0, start) : line.substring(0, start) + line.substring(end + 1);
            start = line.indexOf(NewCustomCuiLianPro.LEVEL_MARKER_PREFIX);
        }
        return line;
    }

    public static boolean hasRefinementData(ItemStack item) {
        if (!LLibAPI.checkItemNull(item)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) {
            return false;
        }
        for (String line : meta.getLore()) {
            String stripped = ChatColor.stripColor(line);
            if (containsLevelMarker(line)
                    || containsConfigured(line, NewCustomCuiLianPro.LEVEL_JUDGE)
                    || containsConfigured(line, NewCustomCuiLianPro.LEVEL_STAR_DISPLAY_PREFIX)
                    || (Message.UNDER_LINE != null && !Message.UNDER_LINE.isEmpty() && line.equals(Message.UNDER_LINE))
                    || (stripped != null && stripped.contains("星淬炼"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsConfigured(String line, String value) {
        return value != null && !value.isEmpty() && line.contains(value);
    }

    public static Level byProtectRune(ItemStack item) {
        if (LLibAPI.checkItemNull(item)) {
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<String>();
            for (int i = 0; i < lore.size(); i++) {
                lore.set(i, normalizeLevelLine(lore.get(i)));
            }
            for (Level level : levels.values()) {
                if (level.protectRune != null && lore.contains(level.protectRune.lore)) {
                    return level;
                }
            }
        }
        return null;
    }

    public static Level deserialize(YamlConfiguration config, String path) {
        HashMap<String, List<String>> map = new HashMap<>();
        map.put("Hand", new ArrayList<>());
        map.put("Helmet", new ArrayList<>());
        map.put("Chestplate", new ArrayList<>());
        map.put("Leggings", new ArrayList<>());
        map.put("Boots", new ArrayList<>());
        ConfigurationSection cs = config.getConfigurationSection(path + ".Attribute");
        if (cs != null) {
            for (String key : cs.getKeys(false)) {
                map.put(key, config.getStringList(path + ".Attribute." + key));
            }
        }
        return new Level(Integer.parseInt(path),
                config.getStringList(path + ".Lore"),
                map,
                ProtectRune.deserialize(config, path + ".ProtectRune"),
                SuitEffect.deserialize(config, path + ".SuitEffect"));
    }
}
