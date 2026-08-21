package lvhaoxuan.custom.cuilian.runnable;

import github.saukiya.sxattribute.SXAttribute;
import github.saukiya.sxattribute.data.attribute.SXAttributeData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import lvhaoxuan.custom.cuilian.NewCustomCuiLianPro;
import lvhaoxuan.custom.cuilian.api.CuiLianAPI;
import lvhaoxuan.custom.cuilian.message.Message;
import lvhaoxuan.custom.cuilian.object.Level;
import lvhaoxuan.llib.util.ParamGroup;
import lvhaoxuan.llib.util.ReflectUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.serverct.ersha.jd.AttributeAPI;
import org.bukkit.entity.Player;

public class SyncEffectRunnable implements Runnable {

    private static final int SUIT_POTION_DURATION_TICKS = 200;
    private static final int SUIT_POTION_REFRESH_THRESHOLD_TICKS = 40;
    public static HashMap<UUID, Level> tmpMap = new HashMap<>();

    @Override
    public void run() {
        try {
            for (LivingEntity le : getEntities()) {
                sync(le);
            }
        } catch (Throwable t) {
            Logger.getLogger(SyncEffectRunnable.class.getName()).log(java.util.logging.Level.SEVERE, "套装药水/属性任务异常", t);
        }
    }

    public void sync(LivingEntity le) throws ClassNotFoundException {
        Level minLevel = CuiLianAPI.getMinLevel(le, le.getEquipment());
        if (minLevel != null && minLevel.suitEffect != null) {
            for (String potionStr : minLevel.suitEffect.potionEffect) {
                refreshPotionEffect(le, potionStr);
            }
            if (NewCustomCuiLianPro.apEnable && le instanceof Player) {
                AttributeAPI.addAttribute((Player) le, "NewCustomCuiLianPro", minLevel.suitEffect.attribute, false);
            }
            if (NewCustomCuiLianPro.sxv2Enable && le instanceof Player) {
                SXAttributeData data = (SXAttributeData) ReflectUtil.doMethod(SXAttribute.getApi(), "getLoreData", new ParamGroup(null, LivingEntity.class), new ParamGroup(null, Class.forName("github.saukiya.sxattribute.data.condition.SXConditionType")), new ParamGroup(minLevel.suitEffect.attribute));
                SXAttribute.getApi().setEntityAPIData(SyncEffectRunnable.class, le.getUniqueId(), data);
            }
            if (NewCustomCuiLianPro.sxv3Enable && le instanceof Player) {
                SXAttributeData data = (SXAttributeData) ReflectUtil.doMethod(SXAttribute.getApi(), "loadListData", new ParamGroup(minLevel.suitEffect.attribute));
                SXAttribute.getApi().setEntityAPIData(SyncEffectRunnable.class, le.getUniqueId(), data);
            }
            if (!tmpMap.containsKey(le.getUniqueId())) {
                sendSuitMessage(le, Message.ENABLE_SUIT_EFFECT.replace("%s", minLevel.lore.get(0)));
                tmpMap.put(le.getUniqueId(), minLevel);
            } else {
                Level level = tmpMap.get(le.getUniqueId());
                if (level != minLevel) {
                    sendSuitMessage(le, Message.DISENABLE_SUIT_EFFECT.replace("%s", level.lore.get(0)));
                    tmpMap.remove(le.getUniqueId());
                }
            }
        } else if (tmpMap.containsKey(le.getUniqueId())) {
            sendSuitMessage(le, Message.DISENABLE_SUIT_EFFECT.replace("%s", tmpMap.get(le.getUniqueId()).lore.get(0)));
            tmpMap.remove(le.getUniqueId());
            if (NewCustomCuiLianPro.apEnable && le instanceof Player) {
                AttributeAPI.deleteAttribute((Player) le, "NewCustomCuiLianPro");
            }
            if ((NewCustomCuiLianPro.sxv2Enable || NewCustomCuiLianPro.sxv3Enable) && le instanceof Player) {
                SXAttribute.getApi().removeEntityAPIData(SyncEffectRunnable.class, le.getUniqueId());
            }
        }
    }

    public static List<LivingEntity> getEntities() {
        List<LivingEntity> entities = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (!NewCustomCuiLianPro.otherEntitySuitEffect) {
                for (LivingEntity le : world.getPlayers()) {
                    entities.add(le);
                }
            } else {
                for (LivingEntity le : world.getLivingEntities()) {
                    entities.add(le);
                }
            }
        }
        return entities;
    }

    private static void sendSuitMessage(LivingEntity entity, String message) {
        if (entity instanceof Player) {
            ((Player) entity).sendMessage(message);
        }
    }

    private static void refreshPotionEffect(LivingEntity entity, String potionStr) {
        String[] args = potionStr.trim().split("\\s+");
        PotionEffectType type = PotionEffectType.getByName(args[0]);
        int amplifier = Integer.parseInt(args[1]);
        PotionEffect active = findActivePotionEffect(entity, type);
        if (active != null) {
            if (active.getAmplifier() > amplifier) {
                return;
            }
            if (active.getAmplifier() == amplifier
                    && active.getDuration() > SUIT_POTION_REFRESH_THRESHOLD_TICKS) {
                return;
            }
        }
        entity.addPotionEffect(new PotionEffect(type, SUIT_POTION_DURATION_TICKS, amplifier), true);
    }

    private static PotionEffect findActivePotionEffect(LivingEntity entity, PotionEffectType type) {
        for (PotionEffect effect : entity.getActivePotionEffects()) {
            if (effect.getType().equals(type)) {
                return effect;
            }
        }
        return null;
    }
}
