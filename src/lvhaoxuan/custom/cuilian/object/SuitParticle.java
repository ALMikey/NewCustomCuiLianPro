package lvhaoxuan.custom.cuilian.object;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 套装粒子的配置模型。取代原先依赖 Nashorn 的 script/*.js。
 *
 * 配置示例（cuilian.yml 中 SuitEffect.Particle）：
 * <pre>
 *   Particle:
 *     Type: WING          # 程序化火翼
 *     Size: 0.72
 *
 *   Particle:
 *     Type: PATTERN       # 二维点阵，数字 = Effects 下标（从 1 起），0 = 空
 *     Effect: MOBSPAWNER_FLAMES     # 单个效果（旧格式）
 *     Scale: 0.20                    # 格子间距（格），越大图案越大
 *     Back: 1.5                      # 图案在玩家身后的距离（格）
 *     Height: 1.15                   # 图案中心高度（相对玩家脚底，格）
 *     Pattern:
 *       - "1,1,1,1,0,0,0,0,1,1,1,1"
 *       - "0,1,1,1,0,0,0,0,1,1,1,0"
 * </pre>
 *
 * 点阵布局：行 0 在最上、行末在最下；列从左到右；整体画成一块竖直的、始终在玩家身后的形状。
 * {@code Scale}/{@code Back}/{@code Height} 均可选，缺省用默认值。
 */
public class SuitParticle {

    public enum Type {
        NONE, WING, PATTERN
    }

    public Type type = Type.NONE;
    public double wingSize = 1.0;
    /** 点阵用到的粒子效果列表（下标从 1 开始，对应点阵里的数字） */
    public List<String> effects = new ArrayList<>();
    public int[][] pattern = new int[0][0];
    /** 点阵格子间距（格） */
    public double scale = 0.20;
    /** 点阵在玩家身后的距离（格） */
    public double back = 1.5;
    /** 点阵中心高度（相对玩家脚底，格） */
    public double height = 1.15;

    public boolean isEnabled() {
        return type != Type.NONE;
    }

    public static SuitParticle deserialize(YamlConfiguration config, String path) {
        SuitParticle particle = new SuitParticle();
        if (config.get(path) == null) {
            return particle;
        }
        String typeStr = config.getString(path + ".Type");
        if (typeStr == null) {
            return particle;
        }
        if (typeStr.equalsIgnoreCase("WING")) {
            particle.type = Type.WING;
            particle.wingSize = config.getDouble(path + ".Size", 1.0);
        } else if (typeStr.equalsIgnoreCase("PATTERN")) {
            particle.type = Type.PATTERN;
            particle.effects = config.getStringList(path + ".Effects");
            if (particle.effects.isEmpty()) {
                // 兼容旧格式：只有单个 Effect
                particle.effects.add(config.getString(path + ".Effect", "MOBSPAWNER_FLAMES"));
            }
            particle.scale = config.getDouble(path + ".Scale", 0.20);
            particle.back = config.getDouble(path + ".Back", 1.5);
            particle.height = config.getDouble(path + ".Height", 1.15);
            List<int[]> rows = new ArrayList<>();
            for (String row : config.getStringList(path + ".Pattern")) {
                String[] cells = row.split(",");
                int[] values = new int[cells.length];
                for (int i = 0; i < cells.length; i++) {
                    values[i] = Integer.parseInt(cells[i].trim());
                }
                rows.add(values);
            }
            particle.pattern = rows.toArray(new int[0][]);
        }
        return particle;
    }
}
