package lvhaoxuan.custom.cuilian.object;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;

public class BuiltinAttribute {

    public final String keyword;
    public final AttributeType type;
    public final Pattern pattern;

    public BuiltinAttribute(String keyword, AttributeType type) {
        this.keyword = keyword.trim();
        this.type = type;
        String separator = this.keyword.endsWith(":") ? "\\s*" : "\\s*:\\s*";
        this.pattern = Pattern.compile(Pattern.quote(this.keyword)
                + separator + "([+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+))");
    }

    public static List<BuiltinAttribute> attributes = new ArrayList<>();

    public static double getTotalValue(List<String> lore, AttributeType targetType) {
        if (lore == null || lore.isEmpty()) {
            return 0;
        }
        double total = 0;
        for (String line : lore) {
            if (line == null) {
                continue;
            }
            String plainLine = ChatColor.stripColor(line);
            if (plainLine == null) {
                continue;
            }
            for (BuiltinAttribute attr : attributes) {
                if (attr.type == targetType) {
                    java.util.regex.Matcher matcher = attr.pattern.matcher(plainLine);
                    if (matcher.find()) {
                        double value = Double.parseDouble(matcher.group(1));
                        if (!Double.isInfinite(value) && !Double.isNaN(value)) {
                            total += value;
                        }
                    }
                }
            }
        }
        return total;
    }

    public enum AttributeType {
        ATTACK,
        DEFENSE
    }
}
