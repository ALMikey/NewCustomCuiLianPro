package lvhaoxuan.custom.cuilian.movelevel;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** 兼容PlayerPoints旧姓名API和UUID API；接口不可用时禁止收费移星。 */
final class PointsPayment {
    final Object api, identity;
    final Method take, give, look;
    boolean uncertain;
    PointsPayment(Player player) throws Exception {
        Plugin plugin=Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if(plugin==null || !plugin.isEnabled()) throw new IllegalArgumentException("PlayerPoints 未启用，无法扣除点券。");
        api=plugin.getClass().getMethod("getAPI").invoke(plugin);
        Class<?> kind; Object who;
        try { api.getClass().getMethod("take",UUID.class,int.class); kind=UUID.class; who=player.getUniqueId(); }
        catch(NoSuchMethodException ex) { kind=String.class; who=player.getName(); }
        identity=who;
        take=api.getClass().getMethod("take",kind,int.class);
        give=api.getClass().getMethod("give",kind,int.class);
        look=api.getClass().getMethod("look",kind);
        if(take.getReturnType()!=boolean.class || give.getReturnType()!=boolean.class)
            throw new IllegalArgumentException("不支持此PlayerPoints扣费接口，已取消。");
    }
    boolean debit(int price) throws Exception {
        if(((Number)look.invoke(api,identity)).longValue()<price) return false;
        try { return Boolean.TRUE.equals(take.invoke(api,identity,price)); }
        catch(java.lang.reflect.InvocationTargetException ex) { uncertain=true; throw ex; }
    }
    boolean refund(int price) throws Exception { return Boolean.TRUE.equals(give.invoke(api,identity,price)); }
}
