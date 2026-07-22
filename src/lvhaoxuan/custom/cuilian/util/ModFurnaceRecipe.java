package lvhaoxuan.custom.cuilian.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import lvhaoxuan.custom.cuilian.NewCustomCuiLianPro;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/** Registers an identity furnace recipe for Forge items unavailable as Bukkit Materials. */
public final class ModFurnaceRecipe {

    private static final Set<String> REGISTERED = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();

    /** Vanilla/Forge's furnace matcher treats this damage value as a wildcard. */
    private static final short WILDCARD_DAMAGE = Short.MAX_VALUE;

    private ModFurnaceRecipe() {
    }

    public static void register(ItemStack source) {
        if (source == null || source.getTypeId() <= 0) {
            return;
        }
        // Mod tools store their current wear in Bukkit's durability field. Registering an
        // exact recipe for every wear value races Uranium's furnace validation, which is
        // performed before the burn/smelt events. A wildcard input covers all wear states.
        String key = String.valueOf(source.getTypeId());
        synchronized (REGISTERED) {
            if (REGISTERED.contains(key) || FAILED.contains(key)) {
                return;
            }
            try {
                ItemStack inputStack = source.clone();
                inputStack.setAmount(1);
                inputStack.setDurability(WILDCARD_DAMAGE);
                ItemStack outputStack = source.clone();
                outputStack.setAmount(1);
                String craftPackage = Bukkit.getServer().getClass().getPackage().getName();
                Class<?> craftItemStack = Class.forName(craftPackage + ".inventory.CraftItemStack");
                Method asNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
                Object input = asNmsCopy.invoke(null, inputStack);
                Object output = asNmsCopy.invoke(null, outputStack);
                Class<?> furnaceRecipes = Class.forName("net.minecraft.item.crafting.FurnaceRecipes");
                Object recipes = getRecipesInstance(furnaceRecipes);
                Method register = getRegisterMethod(furnaceRecipes, input.getClass());
                register.invoke(recipes, input, output, Float.valueOf(0.0F));
                REGISTERED.add(key);
                if (NewCustomCuiLianPro.builtinAttributeDebug) {
                    NewCustomCuiLianPro.ins.getLogger().info("[CuiLianDebug] registered wildcard mod furnace item " + key);
                }
            } catch (Exception ex) {
                FAILED.add(key);
                NewCustomCuiLianPro.ins.getLogger().warning("无法注册 Mod 物品 " + key
                        + " 的熔炉淬炼配方: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
    }

    /**
     * Kept for binary/source compatibility with older listener code. A wildcard recipe is
     * now registered per item ID, so no individual post-wear recipe is necessary.
     */
    public static void registerAfterWear(ItemStack source, int wear) {
        register(source);
    }

    private static Object getRecipesInstance(Class<?> furnaceRecipes) throws Exception {
        for (Method method : furnaceRecipes.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getParameterTypes().length == 0
                    && furnaceRecipes.isAssignableFrom(method.getReturnType())) {
                return method.invoke(null);
            }
        }
        throw new NoSuchMethodException("FurnaceRecipes singleton method");
    }

    private static Method getRegisterMethod(Class<?> furnaceRecipes, Class<?> nmsItemStack) throws Exception {
        for (Method method : furnaceRecipes.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 3 && parameters[0].isAssignableFrom(nmsItemStack)
                    && parameters[1].isAssignableFrom(nmsItemStack) && parameters[2] == Float.TYPE) {
                return method;
            }
        }
        throw new NoSuchMethodException("FurnaceRecipes ItemStack registration method");
    }
}
