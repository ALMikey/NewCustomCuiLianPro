import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.bukkit.Color;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.*;
import lvhaoxuan.custom.cuilian.NewCustomCuiLianPro;
import lvhaoxuan.custom.cuilian.object.Level;
import lvhaoxuan.custom.cuilian.object.Stone;
import lvhaoxuan.custom.cuilian.runnable.SyncEffectRunnable;

/** 对真实升星规则和药水刷新方法执行离线回归，不模拟整个游戏服务器。 */
public class FeatureRegression {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    static YamlConfiguration read(String path) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(new FileInputStream(path), "UTF-8")) {
            config.load(reader);
        }
        return config;
    }
    static Stone stone(YamlConfiguration cfg, String id) {
        Map<Level, Double> chances = new HashMap<>();
        for (String key : cfg.getConfigurationSection(id + ".Chance").getKeys(false)) {
            chances.put(Level.levels.get(Integer.parseInt(key)), cfg.getDouble(id + ".Chance." + key));
        }
        Stone s = new Stone(null, id, new Stone.LevelDrop(cfg.getString(id + ".dropLevel")),
                cfg.getInt(id + ".riseLevel"), chances);
        s.targetLevel = cfg.getInt(id + ".targetLevel");
        s.bonusRiseLevel = cfg.getInt(id + ".bonusRiseLevel");
        s.bonusRiseChance = cfg.getDouble(id + ".bonusRiseChance");
        return s;
    }
    static class EffectType extends PotionEffectType {
        final String name;
        EffectType(int id, String name) { super(id); this.name = name; }
        public double getDurationModifier() { return 1; }
        public String getName() { return name; }
        public boolean isInstant() { return false; }
        public Color getColor() { return Color.WHITE; }
    }
    static PotionEffect active;
    static int updates;
    public static void main(String[] args) throws Exception {
        String src = args[0];
        YamlConfiguration cfg = read(src + "/config.yml");
        YamlConfiguration levels = read(src + "/cuilian.yml");
        YamlConfiguration stones = read(src + "/stone.yml");
        check(!cfg.getBoolean("RefinementDebug"), "正式服详细日志必须关闭");
        check(!read(src + "/attribute.yml").getBoolean("debug"), "属性调试必须关闭");
        NewCustomCuiLianPro.maxRefineLevel = cfg.getInt("MaxRefineLevel");
        check(NewCustomCuiLianPro.maxRefineLevel == 18, "预备等级意外开放");
        for (int i = 1; i <= 21; i++) {
            List<String> lore = levels.getStringList(i + ".Lore");
            check(!lore.isEmpty(), "星级配置缺失 " + i);
            Level.levels.put(i, new Level(i, lore, new HashMap<>(), null, null));
        }
        check(levels.contains("18.ProtectRune.AddLore"), "18星保护符缺失");
        for (int i = 19; i <= 21; i++) {
            int attack = new int[]{80,90,110}[i-19], defense = new int[]{23,26,30}[i-19];
            check(levels.getStringList(i + ".Attribute.Hand").get(0).equals("§a物理伤害: §e" + attack), "攻击不是累计值");
            for (String slot : new String[]{"Helmet","Chestplate","Leggings","Boots"})
                check(levels.getStringList(i + ".Attribute." + slot).get(0).equals("§a物理防御: §e" + defense), "防御不是累计值");
        }
        Stone xuhe = stone(stones, "xuhe");
        for (int i = 1; i <= 18; i++) {
            check(stones.getDouble("xuhe.Chance."+i) == stones.getDouble("gaodeng.Chance."+i), "虚核概率不同于高等 " + i);
            check(stones.getDouble("muying.Chance."+i) == Math.min(100, stones.getDouble("wanmei.Chance."+i)+5), "暮影初始概率错误");
            check(xuhe.canUpgrade(i-1), "虚核不能正常升星 " + i);
        }
        check(xuhe.dropLevel.min == 0 && xuhe.dropLevel.max == 3, "虚核降级范围错误");
        Stone muying = stone(stones, "muying");
        check(muying.dropLevel.min == 0 && muying.dropLevel.max == 2, "暮影降级范围错误");
        Stone.rnd.setSeed(4431);
        Set<Integer> results = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            results.add(xuhe.getSuccessLevel(10).value);
            check(xuhe.getSuccessLevel(17).value == 18, "额外升星越过18上限");
        }
        check(results.equals(new HashSet<>(Arrays.asList(11,12))), "随机升星目标错误");
        for (String id : stones.getKeys(false)) check(!stone(stones,id).canUpgrade(18), "石头越过上限 " + id);
        for (int n : new int[]{3,6,9,12,15,18}) {
            Stone direct = stone(stones,"zhisheng"+n);
            for (int current = 0; current < n; current++) {
                check(direct.canUpgrade(current), "直升未接受低星装备");
                check(direct.getSuccessLevel(current).value == n, "直升变成了累加星级");
                check(direct.chance.get(direct.getChanceLevel(current)) == 100, "直升不是100%");
            }
            check(!direct.canUpgrade(n) && !direct.canUpgrade(n+1), "直升接受了相同/更高星级");
        }
        xuhe.chance.remove(Level.levels.get(11));
        check(!xuhe.canUpgrade(10), "缺少概率仍然允许扣费");
        // 即使管理员开放21星，未填19-21成功率的旧石头也不能扣费。
        NewCustomCuiLianPro.maxRefineLevel = 21;
        check(!stone(stones,"gaodeng").canUpgrade(18), "缺少未来概率仍然可用");
        NewCustomCuiLianPro.maxRefineLevel = 18;
        PotionEffectType.registerPotionEffectType(new EffectType(16,"NIGHT_VISION"));
        PotionEffectType.registerPotionEffectType(new EffectType(1,"SPEED"));
        Method refresh = SyncEffectRunnable.class.getDeclaredMethod("refreshPotionEffect",LivingEntity.class,String.class);
        refresh.setAccessible(true);
        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(FeatureRegression.class.getClassLoader(),new Class[]{LivingEntity.class},(p,m,a)->{
            if (m.getName().equals("getActivePotionEffects")) return active == null ? Collections.emptyList() : Collections.singletonList(active);
            if (m.getName().equals("addPotionEffect")) { active = (PotionEffect)a[0]; updates++; return true; }
            throw new UnsupportedOperationException(m.getName());
        });
        NewCustomCuiLianPro.nightVisionDurationTicks = cfg.getInt("SuitPotion.NightVisionDurationTicks");
        NewCustomCuiLianPro.nightVisionRefreshTicks = cfg.getInt("SuitPotion.NightVisionRefreshTicks");
        for (int ticks = 0; ticks < 6000; ticks += 20) {
            refresh.invoke(null,entity,"NIGHT_VISION 0");
            check(active.getDuration() > 400, "夜视进入低剩余时间");
            active = new PotionEffect(active.getType(),active.getDuration()-20,active.getAmplifier());
        }
        check(updates == 8, "夜视仍在每秒重加");
        active = new PotionEffect(PotionEffectType.SPEED,100,1); updates = 0;
        refresh.invoke(null,entity,"SPEED 0"); check(updates == 0, "覆盖高级外部药水");
        active = null; refresh.invoke(null,entity,"SPEED 0");
        check(active.getDuration() == 200, "其他套装药水时长改变");
        active = new PotionEffect(PotionEffectType.SPEED,41,0); updates = 0;
        refresh.invoke(null,entity,"SPEED 0"); check(updates == 0, "提前重复刷新速度");
        active = new PotionEffect(PotionEffectType.SPEED,40,0);
        refresh.invoke(null,entity,"SPEED 0"); check(active.getDuration() == 200, "速度未按2秒续期");
        refresh.invoke(null,entity,"SPEED 2"); check(active.getAmplifier() == 2, "高等级套装未覆盖低级药水");
        System.out.println("PASS: 配置、13类石头、直升、概率、18星上限、预备属性、夜视300秒续期和外部药水优先级");
    }
}
