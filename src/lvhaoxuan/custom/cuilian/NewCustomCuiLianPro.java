package lvhaoxuan.custom.cuilian;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import lvhaoxuan.custom.cuilian.commander.Commander;
import lvhaoxuan.custom.cuilian.message.Message;
import lvhaoxuan.custom.cuilian.listener.FurnaceListener;
import lvhaoxuan.custom.cuilian.listener.ProtectRuneListener;
import lvhaoxuan.custom.cuilian.listener.AttributeListener;
import lvhaoxuan.custom.cuilian.loader.Loader;
import lvhaoxuan.custom.cuilian.metrics.Metrics;
import lvhaoxuan.custom.cuilian.movelevel.MoveLevelHandle;
import lvhaoxuan.custom.cuilian.runnable.ScriptRunnable;
import lvhaoxuan.custom.cuilian.runnable.SyncEffectRunnable;
import lvhaoxuan.custom.cuilian.util.NevermineItemExporter;
import lvhaoxuan.llib.util.MathUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.java.JavaPlugin;

public class NewCustomCuiLianPro extends JavaPlugin {

    public static String PROTECT_RUNE_JUDGE;
    public static String LEVEL_JUDGE;
    public static NewCustomCuiLianPro ins;
    public static HashMap<Material, String> typesInBag = new HashMap<>();
    public static List<ItemType> types = new ArrayList<>();
    public static boolean otherEntitySuitEffect;
    public static boolean judgeOffHand;
    public static int displayNameFormat;
    public static List<String> replaceLore;
    public static boolean apEnable = false;
    public static boolean sxv2Enable = false;
    public static boolean sxv3Enable = false;
    public static boolean builtinAttributeEnable = false;
    public static boolean builtinAttributeDebug = false;

    @Override
    public void onEnable() {
        ins = this;
        new Metrics(this, 7315);
        this.getServer().getConsoleSender().sendMessage("§7[§e" + this.getName() + "§7]§a作者lvhaoxuan(隔壁老吕)|QQ3295134931");
        if (this.getServer().getPluginManager().getPlugin("AttributePlus") != null) {
            this.getServer().getConsoleSender().sendMessage("§7[§e" + this.getName() + "§7]§a检测到AttributePlus插件，属性模块加载");
            apEnable = true;
        }
        if (this.getServer().getPluginManager().getPlugin("SX-Attribute") != null) {
            if (this.getServer().getPluginManager().getPlugin("SX-Attribute").getDescription().getVersion().startsWith("2")) {
                sxv2Enable = true;
                this.getServer().getConsoleSender().sendMessage("§7[§e" + this.getName() + "§7]§a检测到SX-AttributeV2.X插件，属性模块加载");
            } else if (this.getServer().getPluginManager().getPlugin("SX-Attribute").getDescription().getVersion().startsWith("3")) {
                this.getServer().getConsoleSender().sendMessage("§7[§e" + this.getName() + "§7]§a检测到SX-AttributeV3.X插件，属性模块加载");
                sxv3Enable = true;
            }
        }
        enableConfig();
        this.getServer().getPluginCommand("cuilian").setExecutor(new Commander());
        this.getServer().getPluginManager().registerEvents(new FurnaceListener(), this);
        this.getServer().getPluginManager().registerEvents(new ProtectRuneListener(), this);
        this.getServer().getPluginManager().registerEvents(new AttributeListener(), this);
        setRecipe();
        Bukkit.getScheduler().runTask(this, new Runnable() {
            @Override
            public void run() {
                NevermineItemExporter.export();
            }
        });
        Bukkit.getScheduler().runTaskTimerAsynchronously(NewCustomCuiLianPro.ins, new ScriptRunnable(), 0, 2);
        Bukkit.getScheduler().runTaskTimerAsynchronously(NewCustomCuiLianPro.ins, new SyncEffectRunnable(), 0, 10);
    }

    public static void enableConfig() {
        Message.loadMessages();
        Loader.loadConfig();
        Loader.loadItems();
        Loader.loadLevels();
        Loader.loadStones();
        MoveLevelHandle.init();
        Loader.loadAttributes();
    }

    public static void setRecipe() {
        for (ItemType type : types) {
            if (!type.canUseBukkitRecipe()) {
                continue;
            }
            FurnaceRecipe recipe = new FurnaceRecipe(type.toItemStack(), type.mData);
            int minDurability = type.hasData ? type.data : 0;
            int maxDurability = type.hasData ? type.data : type.type.getMaxDurability();
            for (int durability = minDurability; durability <= maxDurability; durability++) {
                recipe.setInput(type.type, durability);
                try {
                    ins.getServer().addRecipe(recipe);
                } catch (IllegalStateException ex) {
                }
            }
        }
    }

    public static class ItemType {

        public String typeInBag;
        public String baseType;
        public Material type;
        public MaterialData mData;
        public int itemId;
        public short data;
        public boolean hasData;

        public ItemType(String typeInBag, String baseType) {
            this.typeInBag = typeInBag;
            this.baseType = baseType;
            String[] args = baseType.split(":", 2);
            if (MathUtil.isNumeric(args[0])) {
                itemId = Integer.parseInt(args[0]);
                hasData = args.length == 2;
                data = hasData ? Short.parseShort(args[1]) : 0;
                type = Material.getMaterial(itemId);
            } else {
                type = Material.getMaterial(baseType);
                if (type == null) {
                    throw new IllegalArgumentException("未知物品类型: " + baseType);
                }
                itemId = type.getId();
                hasData = false;
                data = 0;
            }
            if (type != null) {
                mData = new MaterialData(type, (byte) data);
            }
        }

        public ItemStack toItemStack() {
            return mData.toItemStack(1);
        }

        public boolean matches(ItemStack item) {
            return item != null && item.getTypeId() == itemId
                    // Forge tool damage shares Bukkit's durability field. For raw Mod IDs,
                    // treating an optional :Data suffix as a strict metadata value would make
                    // a worn tool disappear from the configured item list.
                    && (!hasData || type == null || item.getDurability() == data);
        }

        public boolean canUseBukkitRecipe() {
            return type != null && mData != null;
        }
    }
}
