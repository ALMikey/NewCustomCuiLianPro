package lvhaoxuan.custom.cuilian.api;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lvhaoxuan.custom.cuilian.NewCustomCuiLianPro;
import lvhaoxuan.custom.cuilian.message.Message;
import lvhaoxuan.custom.cuilian.object.Level;
import lvhaoxuan.custom.cuilian.object.ProtectRune;
import lvhaoxuan.custom.cuilian.object.Stone;
import lvhaoxuan.llib.api.LLibAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;

public class CuiLianAPI {

    private static final String BAOSHI_MARKER = "\u00a70[baoshi:";
    public static boolean hasOffHand;

    static {
        hasOffHand = NewCustomCuiLianPro.judgeOffHand;
        if (hasOffHand) {
            try {
                EntityEquipment.class.getMethod("getItemInOffHand");
            } catch (NoSuchMethodException | SecurityException ex) {
                hasOffHand = false;
            }
        }
    }

    public static boolean canCuiLian(ItemStack item) {
        return getItemType(item) != null;
    }

    public static NewCustomCuiLianPro.ItemType getItemType(ItemStack item) {
        if (LLibAPI.checkItemNull(item)) {
            for (NewCustomCuiLianPro.ItemType type : NewCustomCuiLianPro.types) {
                if (type.matches(item)) {
                    return type;
                }
            }
        }
        return null;
    }

    public static ItemStack cuilian(Stone stone, ItemStack item, Player p) {
        if (canCuiLian(item)) {
            Level basicLevelObj = Level.byItemStack(item);
            int basicLevel = (basicLevelObj != null ? basicLevelObj.value : 0);
            Level toLevel;
            double probability = LLibAPI.getRandom(0, 100);
            boolean success = probability <= stone.chance.get(Level.levels.get(basicLevel + stone.riseLevel));
            String sendMessage = null;
            if (success) {
                toLevel = Level.levels.get(basicLevel + stone.riseLevel);
                item = setItemLevel(item, toLevel);
                sendMessage = Message.SUCCESS.replace("%s", toLevel.lore.get(0));
                if (toLevel.value >= 5) {
                    Bukkit.broadcastMessage(Message.SERVER_SUCCESS.replace("%p", p.getDisplayName()).replace("%d", stone.item.getItemMeta().getDisplayName()).replace("%s", toLevel.lore.get(0)));
                }
            } else {
                int dropLevel = stone.dropLevel.toInteger();
                Level protectRune = Level.byProtectRune(item);
                if (protectRune != null) {
                    if (protectRune.value <= basicLevel) {
                        if (basicLevel - protectRune.value <= dropLevel) {
                            dropLevel = basicLevel - protectRune.value != 0 ? LLibAPI.getRandom(0, basicLevel - protectRune.value) : 0;
                        }
                        toLevel = Level.levels.get(basicLevel - dropLevel);
                        item = setItemLevel(item, toLevel);
                        sendMessage = Message.CUILIAN_FAIL_PROTECT_RUNE.replace("%s", toLevel.lore.get(0)).replace("%d", String.valueOf(dropLevel));
                    } else {
                        toLevel = Level.levels.get(basicLevel - dropLevel);
                        item = setItemLevel(item, toLevel);
                        sendMessage = Message.CUILIAN_FAIL.replace("%s", toLevel.lore.get(0)).replace("%d", String.valueOf(dropLevel));
                    }
                } else {
                    toLevel = Level.levels.get(basicLevel - dropLevel);
                    item = setItemLevel(item, toLevel);
                    sendMessage = Message.CUILIAN_FAIL.replace("%s", toLevel != null ? toLevel.lore.get(0) : "§c§l淬炼消失").replace("%d", String.valueOf(dropLevel));
                }
            }
            if (p != null) {
                p.sendMessage(sendMessage);
            }
        }
        return item;
    }

    public static ItemStack setItemLevel(ItemStack item, Level level) {
        NewCustomCuiLianPro.ItemType itemType = getItemType(item);
        if (itemType != null) {
            int basicLevel = (level != null ? level.value : 0);
            ItemMeta meta = item.getItemMeta();
            setDisplayName(item.getType(), meta, basicLevel);
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            List<String> baoshiLore = extractBaoshiLore(lore);
            lore = replaceLore(lore);
            lore = cleanLevel(lore);
            lore = cleanProtectRune(lore);
            List<String> refiningLore = new ArrayList<>();
            if (level != null) {
                if (!Message.UNDER_LINE.isEmpty()) {
                    refiningLore.add(Message.UNDER_LINE);
                }
                for (String line : level.lore) {
                    refiningLore.add(NewCustomCuiLianPro.LEVEL_STAR_DISPLAY_PREFIX + line
                            + NewCustomCuiLianPro.LEVEL_MARKER);
                }
                List<String> attributes = level.attribute.get(itemType.typeInBag);
                if (attributes != null) {
                    for (String line : attributes) {
                        refiningLore.add(NewCustomCuiLianPro.LEVEL_JUDGE + line);
                    }
                }
            }
            Level protectRuneLevel = Level.byProtectRune(item);
            if (protectRuneLevel != null && protectRuneLevel.protectRune != null) {
                refiningLore.add(NewCustomCuiLianPro.PROTECT_RUNE_JUDGE + protectRuneLevel.protectRune.lore);
            }
            // Keep the refinement and gem sections ahead of unrelated plugin descriptions.
            lore.addAll(0, refiningLore);
            lore.addAll(refiningLore.size(), baoshiLore);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void setDisplayName(Material type, ItemMeta meta, int basicLevel) {
        if (NewCustomCuiLianPro.displayNameFormat == 1) {
            String displayName = meta.hasDisplayName() ? meta.getDisplayName() : defaultDisplayName(type);
            displayName = displayName.replaceAll("\\+[0-9]* ", "");
            displayName = "§f+" + basicLevel + " " + displayName;
            meta.setDisplayName(displayName);
        } else if (NewCustomCuiLianPro.displayNameFormat == 2) {
            String displayName = meta.hasDisplayName() ? meta.getDisplayName() : defaultDisplayName(type);
            displayName = displayName.replaceAll(" \\+[0-9]*", "");
            displayName = displayName + " +" + basicLevel;
            meta.setDisplayName(displayName);
        }
    }

    private static String defaultDisplayName(Material type) {
        return type == null ? "§f物品" : chineseDisplayName(type);
    }

    public static ItemStack addProtectRune(ItemStack item, ProtectRune protectRune) {
        if (LLibAPI.checkItemNull(item)) {
            if (protectRune != null) {
                ItemMeta meta = item.getItemMeta();
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                lore = cleanProtectRune(lore);
                lore.add(NewCustomCuiLianPro.PROTECT_RUNE_JUDGE + protectRune.lore);
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    public static List<String> cleanLevel(List<String> lore) {
        String levelJudge = NewCustomCuiLianPro.LEVEL_JUDGE;
        boolean hasLevelJudge = levelJudge != null && !levelJudge.trim().isEmpty();
        Iterator<String> iterator = lore.iterator();
        while (iterator.hasNext()) {
            String line = iterator.next();
            if (line.contains(NewCustomCuiLianPro.LEVEL_MARKER)
                    || (hasLevelJudge && line.contains(levelJudge)) || line.equals(Message.UNDER_LINE)) {
                iterator.remove();
            }
        }
        return lore;
    }

    public static List<String> cleanProtectRune(List<String> lore) {
        String protectRuneJudge = NewCustomCuiLianPro.PROTECT_RUNE_JUDGE;
        if (protectRuneJudge == null || protectRuneJudge.trim().isEmpty()) {
            return lore;
        }
        Iterator<String> iterator = lore.iterator();
        while (iterator.hasNext()) {
            String line = iterator.next();
            if (line.contains(protectRuneJudge)) {
                iterator.remove();
            }
        }
        return lore;
    }

    /**
     * Baoshi keeps its actual effects in hidden lore markers. Remove the entire section
     * before generic attribute replacement, then put it back after the rebuilt refinement
     * section so a ReplaceLore rule can never invalidate a socketed gem.
     */
    private static List<String> extractBaoshiLore(List<String> lore) {
        int firstMarker = -1;
        for (int index = 0; index < lore.size(); index++) {
            if (lore.get(index).contains(BAOSHI_MARKER)) {
                firstMarker = index;
                break;
            }
        }
        if (firstMarker == -1) {
            return new ArrayList<>();
        }
        int header = -1;
        for (int index = firstMarker - 1; index >= 0; index--) {
            if (ChatColor.stripColor(lore.get(index)).contains("宝石镶嵌")) {
                header = index;
                break;
            }
        }
        List<String> result = new ArrayList<>();
        if (header >= 0) {
            result.add(lore.get(header));
        }
        for (String line : lore) {
            if (line.contains(BAOSHI_MARKER)) {
                result.add(line);
            }
        }
        for (int index = lore.size() - 1; index >= 0; index--) {
            if (lore.get(index).contains(BAOSHI_MARKER) || index == header) {
                lore.remove(index);
            }
        }
        return result;
    }

    public static List<String> replaceLore(List<String> lore) {
        Iterator<String> iterator = lore.iterator();
        while (iterator.hasNext()) {
            String line = iterator.next();
            for (String replace : NewCustomCuiLianPro.replaceLore) {
                if (line.contains(replace)) {
                    iterator.remove();
                }
            }
        }
        return lore;
    }

    public static Level getMinLevel(LivingEntity entity, EntityEquipment equipment) {
        int ret = -1;
        for (ItemStack item : equipment.getArmorContents()) {
            Level level = Level.byItemStack(item);
            int basicLevel = (level != null ? level.value : 0);
            ret = (ret == -1 ? basicLevel : Math.min(ret, basicLevel));
        }
        ItemStack item = LLibAPI.getItemInHand(entity);
        Level level = Level.byItemStack(item);
        int basicLevel = (level != null ? level.value : 0);
        ret = (ret == -1 ? basicLevel : Math.min(ret, basicLevel));
        if (hasOffHand) {
            item = LLibAPI.getItemInOffHand(entity);
            level = Level.byItemStack(item);
            basicLevel = (level != null ? level.value : 0);
            ret = (ret == -1 ? basicLevel : Math.min(ret, basicLevel));
        }
        return Level.levels.get(ret);
    }

    public static String chineseDisplayName(Material type) {
        switch (type) {
            case BOW:
                return "§f弓";
            case IRON_SWORD:
                return "§f铁剑";
            case WOOD_SWORD:
                return "§f木剑";
            case STONE_SWORD:
                return "§f石剑";
            case DIAMOND_SWORD:
                return "§f钻石剑";
            case GOLD_SWORD:
                return "§f金剑";
            case LEATHER_HELMET:
                return "§f皮头盔";
            case LEATHER_CHESTPLATE:
                return "§f皮胸甲";
            case LEATHER_LEGGINGS:
                return "§f皮护腿";
            case LEATHER_BOOTS:
                return "§f皮靴子";
            case CHAINMAIL_HELMET:
                return "§f锁链头盔";
            case CHAINMAIL_CHESTPLATE:
                return "§f锁链胸甲";
            case CHAINMAIL_LEGGINGS:
                return "§f锁链护腿";
            case CHAINMAIL_BOOTS:
                return "§f锁链靴子";
            case IRON_HELMET:
                return "§f铁头盔";
            case IRON_CHESTPLATE:
                return "§f铁胸甲";
            case IRON_LEGGINGS:
                return "§f铁护腿";
            case IRON_BOOTS:
                return "§f铁靴子";
            case DIAMOND_HELMET:
                return "§f钻石头盔";
            case DIAMOND_CHESTPLATE:
                return "§f钻石胸甲";
            case DIAMOND_LEGGINGS:
                return "§f钻石护腿";
            case DIAMOND_BOOTS:
                return "§f钻石靴子";
            case GOLD_HELMET:
                return "§f金头盔";
            case GOLD_CHESTPLATE:
                return "§f金胸甲";
            case GOLD_LEGGINGS:
                return "§f金护腿";
            case GOLD_BOOTS:
                return "§f金靴子";
            default:
                return type.name();
        }
    }
}
