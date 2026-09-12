package lvhaoxuan.custom.cuilian.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import lvhaoxuan.custom.cuilian.NewCustomCuiLianPro;
import lvhaoxuan.custom.cuilian.NewCustomCuiLianPro.ItemType;
import lvhaoxuan.custom.cuilian.movelevel.MoveLevelHandle;
import lvhaoxuan.custom.cuilian.object.Level;
import lvhaoxuan.custom.cuilian.object.Stone;
import lvhaoxuan.custom.cuilian.object.BuiltinAttribute;
import lvhaoxuan.custom.cuilian.object.BuiltinAttribute.AttributeType;
import org.bukkit.configuration.file.YamlConfiguration;

public class Loader {

    public static void loadLevels() {
        Level.levels.clear();
        if (!NewCustomCuiLianPro.ins.getDataFolder().exists()) {
            NewCustomCuiLianPro.ins.getDataFolder().mkdir();
        }
        File file = new File(NewCustomCuiLianPro.ins.getDataFolder(), "cuilian.yml");
        if (!file.exists()) {
            NewCustomCuiLianPro.ins.saveResource("cuilian.yml", true);
        }
        try {
            InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
            for (String key : config.getKeys(false)) {
                Level level = Level.deserialize(config, key);
                Level.levels.put(level.value, level);
            }
        } catch (FileNotFoundException | UnsupportedEncodingException ex) {
        }
    }

    public static void loadStones() {
        Stone.stones.clear();
        if (!NewCustomCuiLianPro.ins.getDataFolder().exists()) {
            NewCustomCuiLianPro.ins.getDataFolder().mkdir();
        }
        File file = new File(NewCustomCuiLianPro.ins.getDataFolder(), "stone.yml");
        if (!file.exists()) {
            NewCustomCuiLianPro.ins.saveResource("stone.yml", true);
        }
        try {
            InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
            for (String key : config.getKeys(false)) {
                Stone stone = Stone.deserialize(config, key);
                Stone.stones.put(key, stone);
            }
        } catch (FileNotFoundException | UnsupportedEncodingException ex) {
        }
    }

    public static void loadItems() {
        NewCustomCuiLianPro.types.clear();
        if (!NewCustomCuiLianPro.ins.getDataFolder().exists()) {
            NewCustomCuiLianPro.ins.getDataFolder().mkdir();
        }
        File file = new File(NewCustomCuiLianPro.ins.getDataFolder(), "items.yml");
        if (!file.exists()) {
            NewCustomCuiLianPro.ins.saveResource("items.yml", true);
        }
        try {
            InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
            for (String key : config.getKeys(false)) {
                for (String strType : config.getStringList(key)) {
                    ItemType type = new ItemType(key, strType);
                    NewCustomCuiLianPro.types.add(type);
                    if (type.type != null) {
                        NewCustomCuiLianPro.typesInBag.put(type.type, type.typeInBag);
                    }
                }
            }
        } catch (FileNotFoundException | UnsupportedEncodingException ex) {
        }
    }

    public static void loadConfig() {
        if (!NewCustomCuiLianPro.ins.getDataFolder().exists()) {
            NewCustomCuiLianPro.ins.getDataFolder().mkdir();
        }
        File file = new File(NewCustomCuiLianPro.ins.getDataFolder(), "config.yml");
        if (!file.exists()) {
            NewCustomCuiLianPro.ins.saveResource("config.yml", true);
        }
        try {
            InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
            NewCustomCuiLianPro.otherEntitySuitEffect = config.getBoolean("OtherEntitySuitEffect");
            NewCustomCuiLianPro.refinementDebug = config.getBoolean("RefinementDebug", false);
            NewCustomCuiLianPro.maxRefineLevel = Math.max(1, config.getInt("MaxRefineLevel", 18));
            // 夜视提前续期，避免进入客户端临近到期的闪烁阶段。
            NewCustomCuiLianPro.nightVisionRefreshTicks = Math.max(300,
                    Math.min(72000, config.getInt("SuitPotion.NightVisionRefreshTicks", 400)));
            NewCustomCuiLianPro.nightVisionDurationTicks = Math.max(
                    NewCustomCuiLianPro.nightVisionRefreshTicks + 40,
                    Math.min(72040, config.getInt("SuitPotion.NightVisionDurationTicks", 1200)));
            NewCustomCuiLianPro.PROTECT_RUNE_JUDGE = getNonEmptyString(config, "PROTECT_RUNE_JUDGE", "§a§l保护符: ");
            NewCustomCuiLianPro.LEVEL_JUDGE = getNonEmptyString(config, "LEVEL_JUDGE", "§e§l淬炼属性: ");
            String starPrefix = config.getString("LEVEL_STAR_DISPLAY_PREFIX");
            NewCustomCuiLianPro.LEVEL_STAR_DISPLAY_PREFIX = starPrefix == null ? "" : starPrefix;
            MoveLevelHandle.moveLevelInvTitle = config.getString("MoveLevelInvTitle");
            NewCustomCuiLianPro.judgeOffHand = config.getBoolean("JudgeOffHand");
            NewCustomCuiLianPro.displayNameFormat = config.getInt("DisplayNameFormat");
            NewCustomCuiLianPro.replaceLore = config.getStringList("ReplaceLore");
        } catch (FileNotFoundException | UnsupportedEncodingException ex) {
        }
    }

    public static void loadAttributes() {
        BuiltinAttribute.attributes.clear();
        NewCustomCuiLianPro.builtinAttributeEnable = false;
        NewCustomCuiLianPro.builtinAttributeDebug = false;
        NewCustomCuiLianPro.builtinCriticalMultiplier = 2.0D;
        NewCustomCuiLianPro.builtinModSharpnessCompatibility = true;
        NewCustomCuiLianPro.builtinSharpnessDamagePerLevel = 1.25D;
        if (!NewCustomCuiLianPro.ins.getDataFolder().exists()) {
            NewCustomCuiLianPro.ins.getDataFolder().mkdir();
        }
        File file = new File(NewCustomCuiLianPro.ins.getDataFolder(), "attribute.yml");
        if (!file.exists()) {
            NewCustomCuiLianPro.ins.saveResource("attribute.yml", true);
        }
        try {
            InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
            NewCustomCuiLianPro.builtinAttributeEnable = config.getBoolean("enabled", false);
            NewCustomCuiLianPro.builtinAttributeDebug = config.getBoolean("debug", false);
            double criticalMultiplier = config.getDouble("critical.multiplier", 2.0D);
            if (Double.isNaN(criticalMultiplier) || Double.isInfinite(criticalMultiplier)
                    || criticalMultiplier < 1.0D) {
                NewCustomCuiLianPro.ins.getLogger().warning("attribute.yml 的 critical.multiplier 无效，已使用默认值 2.0");
                criticalMultiplier = 2.0D;
            }
            NewCustomCuiLianPro.builtinCriticalMultiplier = criticalMultiplier;
            NewCustomCuiLianPro.builtinModSharpnessCompatibility =
                    config.getBoolean("sharpness.mod-compatibility", true);
            double sharpnessDamagePerLevel = config.getDouble("sharpness.damage-per-level", 1.25D);
            if (Double.isNaN(sharpnessDamagePerLevel) || Double.isInfinite(sharpnessDamagePerLevel)
                    || sharpnessDamagePerLevel < 0.0D) {
                NewCustomCuiLianPro.ins.getLogger().warning(
                        "attribute.yml 的 sharpness.damage-per-level 无效，已使用 1.7.10 默认值 1.25");
                sharpnessDamagePerLevel = 1.25D;
            }
            NewCustomCuiLianPro.builtinSharpnessDamagePerLevel = sharpnessDamagePerLevel;
            if (NewCustomCuiLianPro.builtinAttributeEnable) {
                NewCustomCuiLianPro.ins.getServer().getConsoleSender().sendMessage("§7[§e" + NewCustomCuiLianPro.ins.getName() + "§7]§a内置属性模块已加载");
                if (NewCustomCuiLianPro.builtinAttributeDebug) {
                    NewCustomCuiLianPro.ins.getLogger().info("[AttrDebug] sharpness modCompatibility="
                            + NewCustomCuiLianPro.builtinModSharpnessCompatibility
                            + " damagePerLevel=" + NewCustomCuiLianPro.builtinSharpnessDamagePerLevel);
                }
                org.bukkit.configuration.ConfigurationSection cs = config.getConfigurationSection("attributes");
                if (cs != null) {
                    for (String key : cs.getKeys(false)) {
                        String keyword = config.getString("attributes." + key + ".keyword");
                        String typeStr = config.getString("attributes." + key + ".type");
                        if (keyword != null && !keyword.trim().isEmpty() && typeStr != null) {
                            try {
                                AttributeType type = AttributeType.valueOf(typeStr.toUpperCase());
                                BuiltinAttribute attribute = new BuiltinAttribute(keyword, type);
                                BuiltinAttribute.attributes.add(attribute);
                                if (NewCustomCuiLianPro.builtinAttributeDebug) {
                                    NewCustomCuiLianPro.ins.getLogger().info("[AttrDebug] loaded keyword="
                                            + attribute.keyword + " type=" + attribute.type);
                                }
                            } catch (IllegalArgumentException ex) {
                                NewCustomCuiLianPro.ins.getLogger().warning("attribute.yml 中属性 " + key
                                        + " 的 type 无效: " + typeStr
                                        + "，可用值为 ATTACK、DEFENSE 或 CRITICAL_CHANCE");
                            }
                        }
                    }
                }
            }
        } catch (FileNotFoundException | UnsupportedEncodingException ex) {
        }
    }

    private static String getNonEmptyString(YamlConfiguration config, String path, String defaultValue) {
        String value = config.getString(path);
        if (value == null || value.trim().isEmpty()) {
            NewCustomCuiLianPro.ins.getLogger().warning("config.yml 的 " + path
                    + " 不能为空，已使用默认识别前缀以保护物品 Lore。");
            return defaultValue;
        }
        return value;
    }
}
