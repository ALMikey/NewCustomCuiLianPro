package lvhaoxuan.custom.cuilian.object;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;

public class SuitEffect {

    public List<String> potionEffect;
    public List<String> attribute;
    public SuitParticle particle;

    public SuitEffect(List<String> potionEffect, List<String> attribute, SuitParticle particle) {
        this.potionEffect = potionEffect;
        this.attribute = attribute;
        this.particle = particle;
    }

    public static SuitEffect deserialize(YamlConfiguration config, String path) {
        if (config.get(path) == null) {
            return null;
        }
        return new SuitEffect(config.getStringList(path + ".PotionEffect"),
                config.getStringList(path + ".Attribute"),
                SuitParticle.deserialize(config, path + ".Particle"));
    }
}
