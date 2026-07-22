package lvhaoxuan.custom.cuilian.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lvhaoxuan.custom.cuilian.NewCustomCuiLianPro;
import org.bukkit.configuration.file.YamlConfiguration;

/** Exports live Nevermine item IDs because Forge IDs are assigned at server startup. */
public final class NevermineItemExporter {

    private NevermineItemExporter() {
    }

    public static void export() {
        try {
            Class<?> itemClass = Class.forName("net.minecraft.item.Item");
            Class<?> gameRegistry = Class.forName("cpw.mods.fml.common.registry.GameRegistry");
            Class<?> gameData = Class.forName("cpw.mods.fml.common.registry.GameData");
            Method itemRegistryMethod = gameData.getMethod("getItemRegistry");
            Object registry = itemRegistryMethod.invoke(null);
            if (!(registry instanceof Iterable)) {
                throw new IllegalStateException("Item registry is not iterable");
            }
            Method identifierMethod = gameRegistry.getMethod("findUniqueIdentifierFor", itemClass);
            Method idMethod = findIdMethod(registry.getClass(), itemClass);

            List<ItemEntry> armor = new ArrayList<>();
            List<ItemEntry> swords = new ArrayList<>();
            List<ItemEntry> scythes = new ArrayList<>();
            for (Object item : (Iterable<?>) registry) {
                if (item == null || !itemClass.isInstance(item)) {
                    continue;
                }
                Object identifier = identifierMethod.invoke(null, item);
                if (identifier == null) {
                    continue;
                }
                String registryName = identifier.toString();
                if (!registryName.startsWith("nevermine:")) {
                    continue;
                }
                String className = item.getClass().getName();
                ItemEntry entry = new ItemEntry(((Integer) idMethod.invoke(registry, item)).intValue(), registryName, className);
                if (className.contains(".item.armor.")) {
                    armor.add(entry);
                } else if (className.contains(".item.weapon.scythe.")) {
                    scythes.add(entry);
                } else if (className.contains(".item.weapon.sword.")
                        || className.contains(".item.weapon.greatblade.")
                        || className.contains(".item.weapon.claymore.")) {
                    swords.add(entry);
                }
            }
            writeExport(armor, swords, scythes);
        } catch (ClassNotFoundException ex) {
            // Nevermine is optional; no export is expected when it is absent.
        } catch (Exception ex) {
            NewCustomCuiLianPro.ins.getLogger().warning("Nevermine 物品 ID 导出失败: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private static Method findIdMethod(Class<?> registryClass, Class<?> itemClass) throws NoSuchMethodException {
        for (Method method : registryClass.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (!Modifier.isStatic(method.getModifiers()) && method.getReturnType() == Integer.TYPE
                    && parameters.length == 1 && parameters[0].isAssignableFrom(itemClass)) {
                return method;
            }
        }
        throw new NoSuchMethodException("Item registry numeric ID method");
    }

    private static void writeExport(List<ItemEntry> armor, List<ItemEntry> swords, List<ItemEntry> scythes) throws IOException {
        sort(armor);
        sort(swords);
        sort(scythes);
        YamlConfiguration config = new YamlConfiguration();
        config.set("generated-at", System.currentTimeMillis());
        config.set("note", "ID 来自当前服务器的 Forge 注册表；更换 Mod、增删 Mod 或修改加载顺序后请重新导出。");
        config.set("armor", serialize(armor));
        config.set("swords", serialize(swords));
        config.set("scythes", serialize(scythes));
        File file = new File(NewCustomCuiLianPro.ins.getDataFolder(), "nevermine-items.yml");
        config.save(file);
        NewCustomCuiLianPro.ins.getLogger().info("导出 Nevermine 物品 ID: 防具 " + armor.size()
                + "，剑系 " + swords.size() + "，镰刀 " + scythes.size());
    }

    private static void sort(List<ItemEntry> entries) {
        Collections.sort(entries, new Comparator<ItemEntry>() {
            @Override
            public int compare(ItemEntry first, ItemEntry second) {
                return Integer.compare(first.id, second.id);
            }
        });
    }

    private static List<Map<String, Object>> serialize(List<ItemEntry> entries) {
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (ItemEntry entry : entries) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", entry.id);
            value.put("registry", entry.registry);
            value.put("class", entry.className);
            serialized.add(value);
        }
        return serialized;
    }

    private static class ItemEntry {

        private final int id;
        private final String registry;
        private final String className;

        private ItemEntry(int id, String registry, String className) {
            this.id = id;
            this.registry = registry;
            this.className = className;
        }
    }
}
