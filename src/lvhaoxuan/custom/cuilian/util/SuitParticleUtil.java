package lvhaoxuan.custom.cuilian.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import lvhaoxuan.custom.cuilian.object.SuitParticle;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * 套装粒子渲染。用 ProtocolLib 发 WORLD_PARTICLES 包（offset=0）做到「精确落在指定位置、不随机偏移」，
 * 失败时回退到 Bukkit 原生 {@code playEffect}。不依赖 Nashorn。
 */
public final class SuitParticleUtil {

    private static volatile ProtocolLibBackend backend;
    private static volatile boolean backendResolved;

    private SuitParticleUtil() {
    }

    public static void render(LivingEntity entity, SuitParticle particle) {
        if (entity == null || particle == null || !particle.isEnabled()) {
            return;
        }
        Location location = entity.getLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }
        if (particle.type == SuitParticle.Type.WING) {
            drawWings(location, particle.wingSize);
        } else if (particle.type == SuitParticle.Type.PATTERN) {
            drawPattern(location, particle);
        }
    }

    private static Effect resolveEffect(String name) {
        try {
            return Effect.valueOf(name);
        } catch (Exception ex) {
            // 1.7.10 没有 FLAME，火焰粒子是 MOBSPAWNER_FLAMES。
            return Effect.MOBSPAWNER_FLAMES;
        }
    }

    /** 精确生成单枚粒子：优先 ProtocolLib（offset=0），失败则回退 playEffect。 */
    private static void spawnParticle(Location location, Effect effect) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        ProtocolLibBackend b = backend();
        if (b != null) {
            try {
                b.spawn(location, toParticleName(effect));
                return;
            } catch (Exception ex) {
                backend = null; // 运行期失败，禁用
            }
        }
        location.getWorld().playEffect(location, effect, 0);
    }

    private static ProtocolLibBackend backend() {
        if (backendResolved) {
            return backend;
        }
        backendResolved = true;
        try {
            backend = new ProtocolLibBackend();
        } catch (Exception ex) {
            backend = null;
        }
        return backend;
    }

    /** Effect 枚举 → 1.7.10 粒子名（WORLD_PARTICLES 包里的字符串）。 */
    private static String toParticleName(Effect effect) {
        switch (effect) {
            case SMOKE:
                return "smoke";
            case HAPPY_VILLAGER:
                return "happyVillager";
            case HEART:
                return "heart";
            case CRIT:
                return "crit";
            case MAGIC_CRIT:
                return "magicCrit";
            case NOTE:
                return "note";
            case PORTAL:
                return "portal";
            case WITCH_MAGIC:
                return "witchMagic";
            case INSTANT_SPELL:
                return "instantSpell";
            case SPELL:
                return "spell";
            case LAVA_POP:
                return "lava";
            case MOBSPAWNER_FLAMES:
            default:
                return "flame";
        }
    }

    // ---- 程序化火翼（WING） ----

    public static void drawWings(Location origin, double size) {
        if (origin == null || origin.getWorld() == null) {
            return;
        }
        double yaw = origin.getYaw() * Math.PI / 180.0;
        int edgePoints = clamp((int) Math.round(size * 4.0), 3, 5);
        int featherCount = edgePoints;
        int featherPoints = clamp((int) Math.round(size * 2.0), 1, 3);
        drawWing(origin, yaw, -1, size, edgePoints, featherCount, featherPoints);
        drawWing(origin, yaw, 1, size, edgePoints, featherCount, featherPoints);
    }

    private static void drawWing(Location origin, double yaw, int direction, double size,
            int edgePoints, int featherCount, int featherPoints) {
        for (int edge = 0; edge < edgePoints; edge++) {
            emit(origin, yaw, direction, edge / (edgePoints - 1.0), 0, size);
        }
        for (int feather = 0; feather < featherCount; feather++) {
            double progress = (feather + 0.55) / (featherCount + 0.15);
            for (int point = 1; point <= featherPoints; point++) {
                emit(origin, yaw, direction, progress, point / (double) featherPoints, size);
            }
        }
    }

    private static void emit(Location origin, double yaw, int direction, double progress, double feather, double size) {
        double side = direction * (0.30 + progress * 1.40 * size + feather * (0.32 + progress * 0.40) * size);
        double height = 1.04 + Math.sin(progress * Math.PI * 0.88) * 0.96 * size
                - progress * progress * 0.20 * size - feather * (0.28 + progress * 0.76) * size;
        double back = 1.30 + progress * 0.50 + feather * 0.20;
        spawnParticle(origin.clone().add(Math.cos(yaw) * side + Math.sin(yaw) * back,
                height, Math.sin(yaw) * side - Math.cos(yaw) * back), Effect.MOBSPAWNER_FLAMES);
    }

    // ---- 二维点阵（PATTERN） ----

    /**
     * 把点阵画成一块竖直的、始终面向玩家身后的“公告板”形状。
     * 列（j）= 左右，行（i）= 上下，行 0 在最上。
     */
    public static void drawPattern(Location playerLocation, SuitParticle particle) {
        if (playerLocation == null || playerLocation.getWorld() == null || particle == null) {
            return;
        }
        int[][] pattern = particle.pattern;
        if (pattern == null || pattern.length == 0) {
            return;
        }
        Effect[] effects = resolveEffects(particle.effects);
        if (effects.length == 0) {
            return;
        }
        double yaw = Math.toRadians(playerLocation.getYaw());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);

        int rows = pattern.length;
        for (int i = 0; i < rows; i++) {
            int[] row = pattern[i];
            int cols = row.length;
            for (int j = 0; j < cols; j++) {
                int value = row[j];
                if (value == 0) {
                    continue;
                }
                // 数字 = Effects 下标（从 1 起），越界则取最后一个
                Effect effect = effects[clamp(value - 1, 0, effects.length - 1)];
                // 局部坐标：lx 左右（居中）、ly 上下（居中）、lz 前后（正 = 身后）
                double lx = (j - (cols - 1) / 2.0) * particle.scale;
                double ly = particle.height + ((rows - 1) / 2.0 - i) * particle.scale;
                double lz = particle.back;

                // 绕 Y 轴旋转到玩家朝向
                double wx = lx * cos + lz * sin;
                double wz = lx * sin - lz * cos;

                spawnParticle(playerLocation.clone().add(wx, ly, wz), effect);
            }
        }
    }

    private static Effect[] resolveEffects(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new Effect[0];
        }
        List<Effect> list = new ArrayList<>();
        for (String name : names) {
            list.add(resolveEffect(name));
        }
        return list.toArray(new Effect[0]);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 通过 ProtocolLib 发 WORLD_PARTICLES 包，offset 全为 0，粒子精确落在指定坐标，不会随机偏移。
     */
    private static final class ProtocolLibBackend {

        private static final double VIEW_DISTANCE_SQUARED = 64.0D * 64.0D;

        private final Object protocolManager;
        private final Object worldParticles;
        private final Method createPacket;
        private final Method sendServerPacket;
        private final Method getStrings;
        private final Method getFloat;
        private final Method getIntegers;

        ProtocolLibBackend() throws Exception {
            Class<?> protocolLibraryClass = Class.forName("com.comphenix.protocol.ProtocolLibrary");
            Class<?> packetTypeClass = Class.forName("com.comphenix.protocol.PacketType");
            Class<?> packetContainerClass = Class.forName("com.comphenix.protocol.events.PacketContainer");
            Class<?> serverPacketTypesClass = Class.forName("com.comphenix.protocol.PacketType$Play$Server");
            protocolManager = protocolLibraryClass.getMethod("getProtocolManager").invoke(null);
            worldParticles = serverPacketTypesClass.getField("WORLD_PARTICLES").get(null);
            createPacket = protocolManager.getClass().getMethod("createPacket", packetTypeClass);
            sendServerPacket = protocolManager.getClass().getMethod("sendServerPacket", Player.class, packetContainerClass);
            getStrings = packetContainerClass.getMethod("getStrings");
            getFloat = packetContainerClass.getMethod("getFloat");
            getIntegers = packetContainerClass.getMethod("getIntegers");
        }

        void spawn(Location location, String particleName) throws Exception {
            World world = location.getWorld();
            if (world == null) {
                return;
            }
            Object packet = createPacket.invoke(protocolManager, worldParticles);
            write(getStrings.invoke(packet), 0, particleName);
            Object floats = getFloat.invoke(packet);
            write(floats, 0, Float.valueOf((float) location.getX()));
            write(floats, 1, Float.valueOf((float) location.getY()));
            write(floats, 2, Float.valueOf((float) location.getZ()));
            write(floats, 3, Float.valueOf(0.0F));
            write(floats, 4, Float.valueOf(0.0F));
            write(floats, 5, Float.valueOf(0.0F));
            write(floats, 6, Float.valueOf(0.0F));
            write(getIntegers.invoke(packet), 0, Integer.valueOf(1));
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(location) <= VIEW_DISTANCE_SQUARED) {
                    sendServerPacket.invoke(protocolManager, player, packet);
                }
            }
        }

        private static void write(Object modifier, int index, Object value) throws Exception {
            Method write = modifier.getClass().getMethod("write", int.class, Object.class);
            write.invoke(modifier, Integer.valueOf(index), value);
        }
    }
}
