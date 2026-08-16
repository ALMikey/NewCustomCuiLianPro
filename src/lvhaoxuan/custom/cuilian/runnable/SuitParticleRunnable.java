package lvhaoxuan.custom.cuilian.runnable;

import java.util.List;
import java.util.logging.Logger;
import lvhaoxuan.custom.cuilian.api.CuiLianAPI;
import lvhaoxuan.custom.cuilian.object.Level;
import lvhaoxuan.custom.cuilian.util.SuitParticleUtil;
import org.bukkit.entity.LivingEntity;

public class SuitParticleRunnable implements Runnable {

    public static boolean particleEnable = true;

    @Override
    public void run() {
        try {
            if (!particleEnable) {
                return;
            }
            List<LivingEntity> entities = SyncEffectRunnable.getEntities();
            for (LivingEntity entity : entities) {
                tick(entity);
            }
        } catch (Throwable t) {
            Logger.getLogger(SuitParticleRunnable.class.getName()).log(java.util.logging.Level.SEVERE, "套装粒子任务异常", t);
        }
    }

    private void tick(LivingEntity entity) {
        Level minLevel = CuiLianAPI.getMinLevel(entity, entity.getEquipment());
        if (minLevel != null && minLevel.suitEffect != null && minLevel.suitEffect.particle != null) {
            SuitParticleUtil.render(entity, minLevel.suitEffect.particle);
        }
    }
}
