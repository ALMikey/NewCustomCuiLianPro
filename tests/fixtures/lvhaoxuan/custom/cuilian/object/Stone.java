package lvhaoxuan.custom.cuilian.object;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
public class Stone {
    public static final Stone INSTANCE = new Stone();
    public String id = "test-stone";
    public int riseLevel = 1;
    public static boolean upgradeAllowed = true;
    public boolean canUpgrade(int level) { return upgradeAllowed && Level.levels.get(level + riseLevel) != null; }
    public static Stone byItemStack(ItemStack item) {
        return item != null && item.getAmount() > 0 && item.getType() == Material.COAL ? INSTANCE : null;
    }
}
