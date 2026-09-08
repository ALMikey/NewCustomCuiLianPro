import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.*;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.*;
import lvhaoxuan.custom.cuilian.NewCustomCuiLianPro;
import lvhaoxuan.custom.cuilian.api.CuiLianAPI;
import lvhaoxuan.custom.cuilian.listener.FurnaceListener;
import com.almikey.superfurnace.service.RefinementGuard;
import com.almikey.superfurnace.service.SuperFurnaceTicker;

/** 运行真实熔炉监听器；替身仅负责世界、物品元数据和随机升降星。 */
public class FurnaceRegression {
    static ItemStack input, fuel, output;
    static int burn, cook;
    static final Map<String, List<MetadataValue>> metadata = new HashMap<>();
    static final List<Runnable> scheduled = new ArrayList<>();
    static final Logger logger = Logger.getLogger("furnace-regression");
    static World world;
    static Block block;
    static Furnace furnace;
    static FurnaceInventory inventory;
    static Location location;

    interface Call { Object call(Method method, Object[] args) throws Exception; }
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type, Call call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p,m,a) -> {
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            Object value = call.call(m,a == null ? new Object[0] : a);
            if (value != null || !m.getReturnType().isPrimitive()) return value;
            if (m.getReturnType() == boolean.class) return false;
            if (m.getReturnType() == short.class) return (short)0;
            if (m.getReturnType() == void.class) return null;
            return 0;
        });
    }
    static class Stack extends ItemStack {
        Stack(Material type, int amount) { super(type, amount); }
        public Stack clone() { Stack copy = new Stack(getType(), getAmount()); copy.setDurability(getDurability()); return copy; }
        public boolean isSimilar(ItemStack other) { return other != null && getType() == other.getType() && getDurability() == other.getDurability(); }
    }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void tick(FurnaceListener listener) throws Exception {
        for (String name : new String[]{"tickNativeProcesses", "tickTrackedModFurnaces"}) {
            Method method = FurnaceListener.class.getDeclaredMethod(name);
            method.setAccessible(true); method.invoke(listener);
        }
    }
    static FurnaceListener fresh(int stones) {
        metadata.clear(); scheduled.clear(); CuiLianAPI.calls = 0; CuiLianAPI.mod = false; CuiLianAPI.unchanged = false;
        input = new Stack(Material.DIAMOND_SWORD, 1); fuel = new Stack(Material.COAL, stones); output = null; burn = cook = 0;
        return new FurnaceListener();
    }
    static void ignite(FurnaceListener listener, boolean pay) {
        FurnaceBurnEvent e = new FurnaceBurnEvent(block, fuel, 200);
        listener.FurnaceBurnEvent(e); listener.observeBurn(e);
        check(!e.isCancelled(), "valid ignition rejected"); burn = e.getBurnTime();
        if (pay) { fuel = fuel.getAmount() == 1 ? null : new Stack(Material.COAL, fuel.getAmount()-1); }
    }
    static FurnaceSmeltEvent smelt(FurnaceListener listener) {
        FurnaceSmeltEvent e = new FurnaceSmeltEvent(block, input, input.clone());
        listener.FurnaceSmeltEvent(e); listener.observeSmelt(e); return e;
    }
    public static void main(String[] args) throws Exception {
        world = proxy(World.class,(m,a)->m.getName().equals("getName") ? "test" : m.getName().equals("isChunkLoaded") ? true : m.getName().equals("getBlockAt") ? block : null);
        location = new Location(world,0,64,0);
        inventory = proxy(FurnaceInventory.class,(m,a)->{
            switch(m.getName()) {
                case "getSmelting": return input; case "getFuel": return fuel; case "getResult": return output;
                case "setSmelting": input=(ItemStack)a[0]; break; case "setFuel": fuel=(ItemStack)a[0]; break;
                case "setResult": output=(ItemStack)a[0]; break;
                case "getViewers": return Collections.emptyList(); case "getHolder": return furnace;
            } return null;
        });
        Call shared = (m,a)->{
            switch(m.getName()) {
                case "getState": return furnace; case "getLocation": return location; case "getWorld": return world;
                case "getBlock": return block; case "getType": return Material.FURNACE;
                case "getInventory": return inventory; case "getBurnTime": return (short)burn; case "getCookTime": return (short)cook;
                case "setBurnTime": burn=((Short)a[0]); break; case "setCookTime": cook=((Short)a[0]); break;
                case "setMetadata": metadata.put((String)a[0],Collections.singletonList((MetadataValue)a[1])); break;
                case "hasMetadata": return metadata.containsKey(a[0]);
                case "getMetadata": return metadata.getOrDefault(a[0],Collections.emptyList());
                case "removeMetadata": metadata.remove(a[0]); break;
            } return null;
        };
        block = proxy(Block.class,shared); furnace = proxy(Furnace.class,shared);
        Plugin plugin = proxy(Plugin.class,(m,a)->m.getName().equals("getName") ? "NewCustomCuiLianPro" : m.getName().equals("isEnabled") ? true : m.getName().equals("getLogger") ? logger : null);
        NewCustomCuiLianPro.ins=plugin;
        PluginManager manager=proxy(PluginManager.class,(m,a)->m.getName().equals("getPlugin") ? plugin : null);
        BukkitScheduler scheduler=proxy(BukkitScheduler.class,(m,a)->{ if (a.length>1 && a[1] instanceof Runnable) scheduled.add((Runnable)a[1]); return null; });
        Bukkit.setServer(proxy(Server.class,(m,a)->{
            switch(m.getName()) {
                case "getLogger": return logger; case "getName": case "getVersion": case "getBukkitVersion": return "regression";
                case "getScheduler": return scheduler; case "getPluginManager": return manager;
            } return null;
        }));
        for (int stones : new int[]{1,64}) for (boolean unchanged : new boolean[]{false,true}) {
            FurnaceListener listener=fresh(stones); CuiLianAPI.unchanged=unchanged;
            check(RefinementGuard.isRefining(block),"pre-ignition gap permits acceleration");
            ignite(listener,true); tick(listener);
            FurnaceSmeltEvent first=smelt(listener); check(!first.isCancelled() && CuiLianAPI.calls==1,"paid attempt must settle once");
            check(burn==0 && cook==0,"residual heat remains");
            input=first.getResult();
            for(int i=0;i<3;i++) { burn=150; cook=199; check(smelt(listener).isCancelled(),"reinsert exploited residual heat"); }
            check(CuiLianAPI.calls==1,"one debit generated multiple settlements");
            check(stones==1 ? fuel==null : fuel.getAmount()==63,"unexpected fuel debit");
            if(stones>1) { ignite(listener,true); tick(listener); check(!smelt(listener).isCancelled() && CuiLianAPI.calls==2,"fresh stone should authorize new attempt"); }
        }
        FurnaceListener unpaid=fresh(5); ignite(unpaid,false);
        check(smelt(unpaid).isCancelled() && CuiLianAPI.calls==0,"unpaid ignition accepted");
        FurnaceListener cancelled=fresh(5); FurnaceBurnEvent e=new FurnaceBurnEvent(block,fuel,200);
        cancelled.FurnaceBurnEvent(e); e.setCancelled(true); cancelled.observeBurn(e);
        check(smelt(cancelled).isCancelled(),"cancelled ignition retained credit");
        FurnaceListener changed=fresh(5); ignite(changed,true); input.setDurability((short)3); tick(changed);
        check(smelt(changed).isCancelled(),"replaced input inherited credit");
        FurnaceListener occupied=fresh(5); ignite(occupied,true); output=new Stack(Material.IRON_INGOT,1); tick(occupied);
        check(smelt(occupied).isCancelled(),"occupied output retained credit");
        FurnaceListener timeout=fresh(5); ignite(timeout,true); for(int i=0;i<1201;i++) tick(timeout);
        check(smelt(timeout).isCancelled() && !metadata.containsKey(FurnaceListener.REFINING_METADATA),"expired transaction retained credit/lock");
        FurnaceListener disabled=fresh(5); ignite(disabled,true);
        disabled.onDisable(new org.bukkit.event.server.PluginDisableEvent(plugin));
        check(burn==0 && !metadata.containsKey(FurnaceListener.REFINING_METADATA),"plugin disable retained heat/lock");
        FurnaceListener mod=fresh(3); CuiLianAPI.mod=true;
        FurnaceBurnEvent me=new FurnaceBurnEvent(block,fuel,200); mod.FurnaceBurnEvent(me); check(me.isCancelled(),"mod must bypass native fuel consumption");
        for(int i=0;i<200;i++) tick(mod);
        check(CuiLianAPI.calls==1 && fuel.getAmount()==2 && input==null && output!=null,"mod debit/output transaction failed");
        FurnaceListener stacked=fresh(3); CuiLianAPI.mod=true; input.setAmount(2);
        stacked.FurnaceBurnEvent(new FurnaceBurnEvent(block,fuel,200)); for(int i=0;i<205;i++) tick(stacked);
        check(CuiLianAPI.calls==0 && input.getAmount()==2 && fuel.getAmount()==3,"stacked mod input lost");
        // 真实加速方法：倍率 2/3/20 对淬炼无效，普通配方最多推进到 199。
        Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); uf.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)uf.get(null);
        Object ticker=unsafe.allocateInstance(SuperFurnaceTicker.class);
        Field extra=SuperFurnaceTicker.class.getDeclaredField("extraCookTicks"); extra.setAccessible(true);
        Method accelerate=SuperFurnaceTicker.class.getDeclaredMethod("accelerate",World.class,com.almikey.superfurnace.service.FurnaceKey.class); accelerate.setAccessible(true);
        Object key=new com.almikey.superfurnace.service.FurnaceKey(UUID.randomUUID(),0,64,0);
        for(int multiplier:new int[]{2,3,20}) {
            fresh(1); burn=100; cook=80; extra.setInt(ticker,multiplier-1); accelerate.invoke(ticker,world,key);
            check(cook==80,"refinement accelerated at multiplier "+multiplier);
            input=new Stack(Material.IRON_ORE,1); cook=198; accelerate.invoke(ticker,world,key); check(cook==199,"native completion boundary exceeded");
        }
        System.out.println("PASS: paid-once, reinsert x3, success/failure, fresh debit, cancelled/unpaid ignition, input change, mod debit/stack protection, multipliers 2/3/20");
    }
}
