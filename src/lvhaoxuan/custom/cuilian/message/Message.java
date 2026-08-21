package lvhaoxuan.custom.cuilian.message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.List;
import lvhaoxuan.custom.cuilian.NewCustomCuiLianPro;
import org.bukkit.configuration.file.YamlConfiguration;

public class Message {

    public static String MUST_ADD_IN_BAG;
    public static String CANCEL_ADD;
    public static String CAN_NOT_ADD;
    public static String ADD_SUCCESS;
    public static List<String> ADD_MESSAGE;
    public static String SUCCESS;
    public static String SERVER_SUCCESS;
    public static String CUILIAN_FAIL_PROTECT_RUNE;
    public static String CUILIAN_FAIL;
    public static String DELETE_PROTECT_RUNE;
    public static String UNDER_LINE;
    public static String ENABLE_SUIT_EFFECT;
    public static String DISENABLE_SUIT_EFFECT;
    public static String CANNOT_IDENTIFY_LEVEL;
    public static String CUILIAN_CONFIG_ERROR;
    public static String CUILIAN_FAIL_UNCHANGED;
    public static String CUILIAN_PROCESS_LOST;

    public static void loadMessages() {
        if (!NewCustomCuiLianPro.ins.getDataFolder().exists()) {
            NewCustomCuiLianPro.ins.getDataFolder().mkdir();
        }
        File file = new File(NewCustomCuiLianPro.ins.getDataFolder(), "message.yml");
        if (!file.exists()) {
            NewCustomCuiLianPro.ins.saveResource("message.yml", true);
        }
        try {
            InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");
            YamlConfiguration config1 = YamlConfiguration.loadConfiguration(reader);
            MUST_ADD_IN_BAG = config1.getString("MUST_ADD_IN_BAG");
            CANCEL_ADD = config1.getString("CANCEL_ADD");
            CAN_NOT_ADD = config1.getString("CAN_NOT_ADD");
            ADD_SUCCESS = config1.getString("ADD_SUCCESS");
            ADD_MESSAGE = config1.getStringList("ADD_MESSAGE");
            SUCCESS = config1.getString("SUCCESS");
            SERVER_SUCCESS = config1.getString("SERVER_SUCCESS");
            CUILIAN_FAIL_PROTECT_RUNE = config1.getString("CUILIAN_FAIL_PROTECT_RUNE");
            CUILIAN_FAIL = config1.getString("CUILIAN_FAIL");
            DELETE_PROTECT_RUNE = config1.getString("DELETE_PROTECT_RUNE");
            UNDER_LINE = config1.getString("UNDER_LINE");
            ENABLE_SUIT_EFFECT = config1.getString("ENABLE_SUIT_EFFECT");
            DISENABLE_SUIT_EFFECT = config1.getString("DISENABLE_SUIT_EFFECT");
            CANNOT_IDENTIFY_LEVEL = getString(config1, "CANNOT_IDENTIFY_LEVEL",
                    "§c无法识别装备当前淬炼等级，已取消本次淬炼并保留原物品。");
            CUILIAN_CONFIG_ERROR = getString(config1, "CUILIAN_CONFIG_ERROR",
                    "§c淬炼配置异常，本次淬炼已取消并保留原物品。");
            CUILIAN_FAIL_UNCHANGED = getString(config1, "CUILIAN_FAIL_UNCHANGED",
                    "§c§l淬炼失败，装备等级保持不变：%s");
            CUILIAN_PROCESS_LOST = getString(config1, "CUILIAN_PROCESS_LOST",
                    "§c淬炼过程记录已失效，本次产出已取消，输入装备已保留，请重新放入淬炼宝石。");
        } catch (FileNotFoundException | UnsupportedEncodingException ex) {
        }
    }

    private static String getString(YamlConfiguration config, String path, String fallback) {
        String value = config.getString(path);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
