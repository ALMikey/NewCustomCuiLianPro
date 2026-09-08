package lvhaoxuan.custom.cuilian.object;
import java.util.HashMap;
import org.bukkit.inventory.ItemStack;
public class Level {
    public static HashMap<Integer, Level> levels = new HashMap<Integer, Level>();
    static { levels.put(1, new Level()); }
    public int value;
    public static Level byItemStack(ItemStack item) { return null; }
}
