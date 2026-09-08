package lvhaoxuan.custom.cuilian.api;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import lvhaoxuan.custom.cuilian.NewCustomCuiLianPro;
import lvhaoxuan.custom.cuilian.object.Stone;
public class CuiLianAPI {
    public static int calls;
    public static boolean mod;
    public static boolean unchanged;
    public static boolean canCuiLian(ItemStack item) { return item != null && item.getType() == Material.DIAMOND_SWORD; }
    public static NewCustomCuiLianPro.ItemType getItemType(ItemStack item) {
        return canCuiLian(item) ? new NewCustomCuiLianPro.ItemType() : null;
    }
    public static ItemStack cuilian(Stone stone, ItemStack item, Player player) {
        calls++;
        if (!unchanged) item.setDurability((short)(item.getDurability() + 1));
        return item;
    }
}
