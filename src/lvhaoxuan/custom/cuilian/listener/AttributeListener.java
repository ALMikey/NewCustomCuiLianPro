package lvhaoxuan.custom.cuilian.listener;

import java.util.ArrayList;
import java.util.List;
import lvhaoxuan.custom.cuilian.NewCustomCuiLianPro;
import lvhaoxuan.custom.cuilian.object.BuiltinAttribute;
import lvhaoxuan.custom.cuilian.object.BuiltinAttribute.AttributeType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
        double oldDamage = event.getDamage();
        double damage = oldDamage + attackValue;
        damage = Math.max(0, damage - defenseValue);
        event.setDamage(damage);
        if (NewCustomCuiLianPro.builtinAttributeDebug
                && (attacker instanceof Player || defender instanceof Player)) {
            NewCustomCuiLianPro.ins.getLogger().info("[AttrDebug] event attacker="
                    + getEntityName(attacker) + " defender=" + getEntityName(defender)
                    + " atk=" + attackValue + " def=" + defenseValue
                    + " damage=" + oldDamage + " -> " + damage);
        }
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
}
