package lvhaoxuan.custom.cuilian;
import org.bukkit.plugin.Plugin;
public class NewCustomCuiLianPro {
    public static boolean refinementDebug = false;
    public static Plugin ins;
    public static class ItemType {
        public boolean canUseBukkitRecipe() { return !lvhaoxuan.custom.cuilian.api.CuiLianAPI.mod; }
    }
}
