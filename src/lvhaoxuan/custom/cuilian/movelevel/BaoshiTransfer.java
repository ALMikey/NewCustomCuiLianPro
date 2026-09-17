package lvhaoxuan.custom.cuilian.movelevel;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** 对接当前可配置宝石插件的已生效规则；接口变化时停止移星，不猜测宝石数据。 */
final class BaoshiTransfer {
    final Object plugin, settings;
    BaoshiTransfer() throws Exception {
        Plugin found = Bukkit.getPluginManager().getPlugin("baoshi");
        plugin = found != null && found.isEnabled() ? found : null;
        settings = plugin == null ? null : field(plugin, "settings");
    }
    static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    Object call(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = plugin.getClass().getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(plugin, args);
    }
    static boolean trace(List<String> lore) {
        for (String line : lore) if (line.contains("[baoshi:") || line.contains("§k§l§m§n§o")
                || ChatColor.stripColor(line).contains("宝石镶嵌") || line.contains("青鳞石")
                || line.contains("落凤石") || line.contains("烟灵玉") || line.contains("水夕石")) return true;
        return false;
    }
    @SuppressWarnings("unchecked") Map<String,Object> read(List<String> lore) throws Exception {
        if (plugin == null) {
            if (trace(lore)) throw new IllegalArgumentException("装备含宝石数据，但 baoshi 未启用，已取消。");
            return new LinkedHashMap<>();
        }
        Map<String,Object> entries = (Map<String,Object>) call("readSocketEntries", new Class[]{List.class}, lore);
        // 不允许损坏的一部分标记被宝石解析器跳过后静默丢失。
        java.util.regex.Pattern visible=java.util.regex.Pattern.compile("§0\\[baoshi:([A-Za-z0-9_-]+):(\\d+):([A-Z_]+):([0-9]+(?:\\.[0-9]+)?)\\]");
        java.util.regex.Pattern invisible=java.util.regex.Pattern.compile("§k§l§m§n§o((?:§[0-9a-f])+)§r");
        for(String line:lore) {
            if(line.contains("§k§l§m§n§o")) {
                java.util.regex.Matcher matcher=invisible.matcher(line); boolean found=false;
                while(matcher.find()) {
                    found=true; String payload=(String)call("decodeInvisibleMarker",new Class[]{String.class},matcher.group(1));
                    String[] parts=payload.split(":",4);
                    if(parts.length!=4 || !entries.containsKey(parts[0])) throw new IllegalArgumentException("宝石隐藏标记损坏，已取消。");
                    Object parsed=entries.get(parts[0]);
                    if(Integer.parseInt(parts[1])!=(Integer)field(parsed,"level")
                            || !parts[2].equals(field(parsed,"effect").toString())
                            || Double.parseDouble(parts[3])!=(Double)field(parsed,"value"))
                        throw new IllegalArgumentException("宝石标记冲突，已取消。");
                }
                if(!found || invisible.matcher(line).replaceAll("").contains("§k§l§m§n§o")) throw new IllegalArgumentException("宝石隐藏标记不完整，已取消。");
            }
            if(line.contains("[baoshi:") && !line.endsWith(":begin]") && !line.endsWith(":end]")) {
                if(visible.matcher(line).replaceAll("").contains("[baoshi:")) throw new IllegalArgumentException("宝石标记损坏，已取消。");
                java.util.regex.Matcher matcher=visible.matcher(line);
                while(matcher.find()) {
                    Object parsed=entries.get(matcher.group(1));
                    if(parsed==null || Integer.parseInt(matcher.group(2))!=(Integer)field(parsed,"level")
                            || !matcher.group(3).equals(field(parsed,"effect").toString())
                            || Double.parseDouble(matcher.group(4))!=(Double)field(parsed,"value"))
                        throw new IllegalArgumentException("宝石标记冲突，已取消。");
                }
            }
        }
        call("migrateLegacyEntries", new Class[]{List.class, Map.class}, lore, entries);
        if (trace(lore) && entries.isEmpty()) throw new IllegalArgumentException("无法完整识别宝石，请先使用 /baoshi refresh 玩家名 整理装备。");
        return entries;
    }
    void clean(ItemStack item, List<String> lore, Map<String,Object> entries) throws Exception {
        if (plugin == null || entries.isEmpty()) return;
        call("removeAllSocketBlocks", new Class[]{List.class}, lore);
        call("removeCompactSocketLine", new Class[]{List.class, String.class}, lore, field(settings,"socketSection"));
        call("removeAllLegacyLines", new Class[]{List.class}, lore);
        for(Iterator<String> it=lore.iterator();it.hasNext();) {
            String plain=ChatColor.stripColor(it.next()).trim();
            if("宝石镶嵌".equals(plain) || "宝石镶嵌:".equals(plain) || "宝石镶嵌：".equals(plain)) it.remove();
        }
        for (Object entry : entries.values()) if ("THORNS".equals(field(entry,"effect").toString())) {
            // 兼容2.5旧宝石附魔；不移动其他普通附魔。
            item.removeEnchantment(Enchantment.THORNS);
        }
    }
    @SuppressWarnings("unchecked") List<String> render(Map<String,Object> entries, ItemStack destination) throws Exception {
        if (entries.isEmpty()) return new ArrayList<>();
        Map<String,Object> families = (Map<String,Object>)field(settings,"gems");
        List<Object> rebased = new ArrayList<>();
        for (Map.Entry<String,Object> pair : entries.entrySet()) {
            Object entry = pair.getValue(), family = families.get(pair.getKey());
            if (family == null) throw new IllegalArgumentException("宝石配置已缺失：" + pair.getKey());
            Method targets = family.getClass().getDeclaredMethod("targets", ItemStack.class, settings.getClass()); targets.setAccessible(true);
            if (!Boolean.TRUE.equals(targets.invoke(family,destination,settings)))
                throw new IllegalArgumentException("目标装备不允许镶嵌宝石：" + pair.getKey());
            int level = (Integer)field(entry,"level");
            if (!((Map<?,?>)field(family,"levels")).containsKey(level)) throw new IllegalArgumentException("宝石等级配置已缺失：" + pair.getKey());
            Object effect = field(entry,"effect");
            if (!effect.equals(field(family,"effect"))) throw new IllegalArgumentException("宝石类型与配置不一致：" + pair.getKey());
            double value = (Double)field(entry,"value");
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("宝石数值异常。");
            if ("THORNS".equals(effect.toString())) {
                // 2.6支持独立反伤；旧版仅拷贝Lore不会产生真实附魔，因此禁止这种静默失效。
                try { plugin.getClass().getDeclaredMethod("onThorns", org.bukkit.event.entity.EntityDamageByEntityEvent.class); }
                catch (NoSuchMethodException ex) { throw new IllegalArgumentException("转移荆棘需要 baoshi 2.6.0 或兼容新版。"); }
            }
            if ("INFINITE_DURABILITY".equals(effect.toString())) value = destination.getDurability();
            Constructor<?> constructor = entry.getClass().getDeclaredConstructor(String.class,int.class,effect.getClass(),double.class);
            constructor.setAccessible(true); rebased.add(constructor.newInstance(pair.getKey(),level,effect,value));
        }
        List<String> result = new ArrayList<>(); result.add((String)field(settings,"socketSection"));
        result.addAll((List<String>)call("socketLoreLines",new Class[]{Collection.class},rebased));
        return result;
    }
}
