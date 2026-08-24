package lvhaoxuan.custom.cuilian.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lvhaoxuan.custom.cuilian.NewCustomCuiLianPro;
import lvhaoxuan.custom.cuilian.api.CuiLianAPI;
import lvhaoxuan.custom.cuilian.object.BuiltinAttribute;
import lvhaoxuan.custom.cuilian.object.BuiltinAttribute.AttributeType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.projectiles.ProjectileSource;

public class AttributeListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!NewCustomCuiLianPro.builtinAttributeEnable) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        LivingEntity defender = (LivingEntity) event.getEntity();
        LivingEntity attacker = getAttacker(event);
        if (attacker == null) {
            return;
        }
        double attackValue = scanEquipment(attacker, AttributeType.ATTACK);
        double defenseValue = scanEquipment(defender, AttributeType.DEFENSE);
        double criticalChance = clampChance(scanEquipment(attacker, AttributeType.CRITICAL_CHANCE));
        double oldDamage = event.getDamage();
        SharpnessCompensation sharpness = getModSharpnessCompensation(event, attacker);
        // event.getDamage() already contains the vanilla or Forge/Mod weapon damage.
        // Some Mod weapons bypass the vanilla melee path and expose only their fixed
        // damage to Bukkit. Compensate Sharpness only for configured numeric-ID hand
        // items; vanilla Material weapons already include it and must not be doubled.
        double combinedAttackDamage = oldDamage + sharpness.bonus + attackValue;
        double criticalRoll = ThreadLocalRandom.current().nextDouble(100.0D);
        boolean critical = criticalChance > 0.0D && criticalRoll < criticalChance;
        double damage = critical
                ? combinedAttackDamage * NewCustomCuiLianPro.builtinCriticalMultiplier
                : combinedAttackDamage;
        damage = Math.max(0, damage - defenseValue);
        event.setDamage(damage);
        if (NewCustomCuiLianPro.builtinAttributeDebug
                && (attacker instanceof Player || defender instanceof Player)) {
            NewCustomCuiLianPro.ins.getLogger().info("[AttrDebug] event attacker="
                    + getEntityName(attacker) + " defender=" + getEntityName(defender)
                    + " atk=" + attackValue + " def=" + defenseValue
                    + " criticalChance=" + criticalChance + "% criticalRoll=" + criticalRoll
                    + " critical=" + critical
                    + " multiplier=" + NewCustomCuiLianPro.builtinCriticalMultiplier
                    + " sharpnessLevel=" + sharpness.level
                    + " sharpnessBonus=" + sharpness.bonus
                    + " damage=" + oldDamage + " + " + sharpness.bonus
                    + " + " + attackValue + " -> " + damage);
        }
    }

    private static SharpnessCompensation getModSharpnessCompensation(
            EntityDamageByEntityEvent event, LivingEntity attacker) {
        if (!NewCustomCuiLianPro.builtinModSharpnessCompatibility
                || event.getCause() != DamageCause.ENTITY_ATTACK
                || !(event.getDamager() instanceof LivingEntity)) {
            return SharpnessCompensation.NONE;
        }
        EntityEquipment equipment = attacker.getEquipment();
        ItemStack handItem = equipment == null ? null : equipment.getItemInHand();
        if (handItem == null || handItem.getType() == Material.AIR) {
            return SharpnessCompensation.NONE;
        }
        NewCustomCuiLianPro.ItemType itemType = CuiLianAPI.getItemType(handItem);
        if (itemType == null || !itemType.numericId || !"Hand".equalsIgnoreCase(itemType.typeInBag)) {
            return SharpnessCompensation.NONE;
        }
        int level = handItem.getEnchantmentLevel(Enchantment.DAMAGE_ALL);
        if (level <= 0) {
            return SharpnessCompensation.NONE;
        }
        return new SharpnessCompensation(level,
                level * NewCustomCuiLianPro.builtinSharpnessDamagePerLevel);
    }

    private static double clampChance(double chance) {
        if (Double.isNaN(chance) || Double.isInfinite(chance)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(100.0D, chance));
    }

    private LivingEntity getAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity) {
            return (LivingEntity) event.getDamager();
        }
        if (event.getDamager() instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) event.getDamager()).getShooter();
            if (shooter instanceof LivingEntity) {
                return (LivingEntity) shooter;
            }
        }
        return null;
    }

    public static double scanEquipment(LivingEntity entity, AttributeType type) {
        List<String> allLore = new ArrayList<>();
        EntityEquipment equip = entity.getEquipment();
        if (equip != null) {
            for (ItemStack item : equip.getArmorContents()) {
                addLore(allLore, item);
            }
            ItemStack handItem = equip.getItemInHand();
            if (handItem != null && handItem.getType() != Material.AIR) {
                addLoreDirect(allLore, handItem);
            }
        }
        double result = BuiltinAttribute.getTotalValue(allLore, type);
        if (NewCustomCuiLianPro.builtinAttributeDebug && entity instanceof Player) {
            NewCustomCuiLianPro.ins.getLogger().info("[AttrDebug] scan player=" + ((Player) entity).getName()
                    + " type=" + type + " value=" + result + " lore=" + formatLore(allLore));
        }
        return result;
    }

    private static void addLore(List<String> allLore, ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            addLoreDirect(allLore, item);
        }
    }

    private static void addLoreDirect(List<String> allLore, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasLore()) {
            allLore.addAll(meta.getLore());
        }
    }

    private static String formatLore(List<String> lore) {
        if (lore.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (String line : lore) {
            if (builder.length() > 1) {
                builder.append(" | ");
            }
            builder.append(ChatColor.stripColor(line));
        }
        return builder.append(']').toString();
    }

    private static String getEntityName(LivingEntity entity) {
        if (entity instanceof Player) {
            return ((Player) entity).getName();
        }
        return entity.getType().name();
    }

    private static final class SharpnessCompensation {

        private static final SharpnessCompensation NONE = new SharpnessCompensation(0, 0.0D);
        private final int level;
        private final double bonus;

        private SharpnessCompensation(int level, double bonus) {
            this.level = level;
            this.bonus = bonus;
        }
    }
}
