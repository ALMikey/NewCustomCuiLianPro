package lvhaoxuan.custom.cuilian.movelevel;

import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Logger;
import lvhaoxuan.custom.cuilian.NewCustomCuiLianPro;
import lvhaoxuan.custom.cuilian.message.Message;
import lvhaoxuan.custom.cuilian.object.Level;
import lvhaoxuan.custom.cuilian.object.ProtectRune;
import org.bukkit.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;

/** 使用真实淬炼/宝石实现，替代服务器和点券存储；不依赖在线服务器。 */
public class MoveLevelRegression {
    interface Call { Object call(Method m,Object[] a) throws Throwable; }
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type,Call action) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->{
            if(m.getName().equals("equals")) return p==a[0];
            if(m.getName().equals("hashCode")) return System.identityHashCode(p);
            Object value=action.call(m,a==null?new Object[0]:a);
            if(value!=null || !m.getReturnType().isPrimitive()) return value;
            if(m.getReturnType()==boolean.class) return false;
            return 0;
        });
    }
    static void check(boolean value,String msg) { if(!value) throw new AssertionError(msg); }
    static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafe=Class.forName("sun.misc.Unsafe"); Field f=unsafe.getDeclaredField("theUnsafe"); f.setAccessible(true);
        return unsafe.getMethod("allocateInstance",Class.class).invoke(f.get(null),type);
    }
    static void set(Object object,Class<?> owner,String name,Object value) throws Exception {
        Field field=owner.getDeclaredField(name); field.setAccessible(true); field.set(object,value);
    }
    static final class Meta implements InvocationHandler {
        String name; List<String> lore=new ArrayList<>(); Map<Object,Integer> ench=new HashMap<>();
        ItemMeta proxy() { return (ItemMeta)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ItemMeta.class},this); }
        Meta copy() { Meta meta=new Meta();meta.name=name;meta.lore=new ArrayList<>(lore);meta.ench=new HashMap<>(ench);return meta; }
        public Object invoke(Object p,Method m,Object[] a) {
            switch(m.getName()) {
                case "getLore":return new ArrayList<>(lore); case "setLore":lore=a[0]==null?new ArrayList<>():new ArrayList<>((List<String>)a[0]);return null;
                case "hasLore":return !lore.isEmpty(); case "getDisplayName":return name; case "setDisplayName":name=(String)a[0];return null;
                case "hasDisplayName":return name!=null; case "clone":return copy().proxy();
                case "hasEnchants":return !ench.isEmpty(); case "getEnchants":return ench;
                case "equals": if(a[0]==null || !Proxy.isProxyClass(a[0].getClass()))return false;
                    Meta other=(Meta)Proxy.getInvocationHandler(a[0]);return Objects.equals(name,other.name)&&lore.equals(other.lore)&&ench.equals(other.ench);
                case "hashCode":return Objects.hash(name,lore,ench);
            }
            if(m.getReturnType()==boolean.class)return false; if(m.getReturnType()==int.class)return 0; return null;
        }
    }
    static class Gear extends ItemStack {
        Meta meta=new Meta(); String nbt="custom-nbt";
        Gear(Material material,String...lore) {super(material);meta.lore=new ArrayList<>(Arrays.asList(lore));}
        public ItemMeta getItemMeta(){return meta.copy().proxy();}
        public boolean setItemMeta(ItemMeta value){meta=((Meta)Proxy.getInvocationHandler(value)).copy();return true;}
        public boolean hasItemMeta(){return true;}
        public Gear clone(){Gear result=new Gear(getType()); result.meta=meta.copy();result.setDurability(getDurability()); result.setAmount(getAmount());result.nbt=nbt;return result;}
        public boolean equals(Object value){if(!(value instanceof Gear))return false;Gear o=(Gear)value;return getType()==o.getType()&&getAmount()==o.getAmount()&&getDurability()==o.getDurability()&&nbt.equals(o.nbt)&&getItemMeta().equals(o.getItemMeta());}
        public int removeEnchantment(org.bukkit.enchantments.Enchantment enchant){meta.ench.remove(enchant);return 0;}
    }
    public static class PointsPlugin extends JavaPlugin { public Object getAPI(){return apiUsed;} }
    public static class NamePointsApi {
        public int look(String name){check("Tester".equals(name),"姓名API身份错误");return points.balance;}
        public boolean take(String name,int amount){points.balance-=amount;return true;}
        public boolean give(String name,int amount){points.balance+=amount;return true;}
    }
    public static class PointsApi {
        int balance=1000,takes,gives;boolean refuse; Runnable afterTake;
        public int look(UUID player){return balance;}
        public boolean take(UUID player,int amount){takes++;if(refuse||balance<amount)return false;balance-=amount;if(afterTake!=null)afterTake.run();return true;}
        public boolean give(UUID player,int amount){gives++;balance+=amount;return true;}
    }
    static PointsApi points=new PointsApi();
    static Object apiUsed=points;
    static ItemStack[] contents=new ItemStack[36]; static boolean failWrite;
    static List<Runnable> queued=new ArrayList<>();
    static JavaPlugin gemPlugin,pointsPlugin; static Player player;
    static MoveLevelHandle.Settings settings; static Method commit;
    static Gear source(){return new Gear(Material.DIAMOND_SWORD,"装备淬炼","三星淬炼§0[cuilian:level:3]","属性: 攻击6","保护: 三星保护",
            "宝石镶嵌","§0[baoshi:qinglin:1:DAMAGE_ADD:5]","§0[baoshi:naijiu:1:INFINITE_DURABILITY:9]","灵魂绑定: Alice","其他A");}
    static Gear target(){Gear gear=new Gear(Material.WOOD_SWORD,"装备淬炼","六星淬炼§0[cuilian:level:6]","属性: 攻击10","保护: 六星保护",
            "宝石镶嵌","§0[baoshi:qinglin:2:DAMAGE_ADD:10]","灵魂绑定: Bob","其他B");gear.setDurability((short)27);return gear;}
    static MoveLevelHandle.Session ready(){
        contents[0]=source();contents[1]=target();points.balance=1000;points.takes=0;points.gives=0;points.refuse=false;points.afterTake=null;failWrite=false;
        MoveLevelHandle.Session session=new MoveLevelHandle.Session(player,settings);session.sourceSlot=0;session.targetSlot=1;
        session.source=contents[0].clone();session.target=contents[1].clone();return session;
    }
    static InventoryView view(final MoveLevelHandle.Session session){
        return new InventoryView(){
            public Inventory getTopInventory(){return session.inventory;}
            public Inventory getBottomInventory(){return player.getInventory();}
            public HumanEntity getPlayer(){return player;}
            public InventoryType getType(){return InventoryType.CHEST;}
        };
    }
    public static void main(String[] args)throws Exception {
        gemPlugin=(JavaPlugin)allocate(Class.forName("toin.cc.baoshi.ConfigurableBaoshi"));
        Class<?> gemSettings=Class.forName("toin.cc.baoshi.ConfigurableBaoshi$Settings");
        Method gemLoad=gemSettings.getDeclaredMethod("load",FileConfiguration.class,FileConfiguration.class);gemLoad.setAccessible(true);
        set(gemPlugin,gemPlugin.getClass(),"settings",gemLoad.invoke(null,YamlConfiguration.loadConfiguration(new File(args[0]+"/resources/config.yml")),YamlConfiguration.loadConfiguration(new File(args[0]+"/resources/items.yml"))));
        set(gemPlugin,JavaPlugin.class,"isEnabled",true);
        pointsPlugin=(JavaPlugin)allocate(PointsPlugin.class);set(pointsPlugin,JavaPlugin.class,"isEnabled",true);
        ItemFactory factory=proxy(ItemFactory.class,(m,a)->{
            if(m.getName().equals("getItemMeta"))return new Meta().proxy();
            if(m.getName().equals("isApplicable"))return true;
            if(m.getName().equals("asMetaFor"))return a[0];
            if(m.getName().equals("equals"))return Objects.equals(a[0],a[1]);return null;
        });
        PluginManager manager=proxy(PluginManager.class,(m,a)->m.getName().equals("getPlugin")?("baoshi".equals(a[0])?gemPlugin:"PlayerPoints".equals(a[0])?pointsPlugin:null):null);
        Server server=proxy(Server.class,(m,a)->{
            switch(m.getName()) {
                case "getLogger":return Logger.getLogger("move-test");case "getName":case "getVersion":case "getBukkitVersion":return "test";
                case "getPluginManager":return manager;case "getItemFactory":return factory;
                case "getScheduler":return proxy(BukkitScheduler.class,(sm,sa)->{if(sm.getName().equals("runTask"))queued.add((Runnable)sa[1]);return null;});
                case "createInventory":
                    ItemStack[] items=new ItemStack[(Integer)a[1]]; Object holder=a[0];
                    return proxy(Inventory.class,(im,ia)->{switch(im.getName()){
                        case "getHolder":return holder;case "getSize":return items.length;
                        case "setItem":items[(Integer)ia[0]]=(ItemStack)ia[1];break;case "getItem":return items[(Integer)ia[0]];
                    }return null;});
            } return null;
        });
        Bukkit.setServer(server);
        NewCustomCuiLianPro.ins=(NewCustomCuiLianPro)allocate(NewCustomCuiLianPro.class);
        set(NewCustomCuiLianPro.ins,JavaPlugin.class,"server",server);
        set(NewCustomCuiLianPro.ins,JavaPlugin.class,"description",new PluginDescriptionFile("CuiTest","4.4.32","test"));
        set(NewCustomCuiLianPro.ins,JavaPlugin.class,"logger",new PluginLogger(NewCustomCuiLianPro.ins));
        PlayerInventory inventory=proxy(PlayerInventory.class,(m,a)->{
            if(m.getName().equals("getItem"))return contents[(Integer)a[0]];
            if(m.getName().equals("setItem")){if(failWrite&&(Integer)a[0]==1){failWrite=false;throw new IllegalStateException("test write failure");}contents[(Integer)a[0]]=(ItemStack)a[1];}
            return null;
        });
        UUID uuid=UUID.randomUUID();player=proxy(Player.class,(m,a)->{
            switch(m.getName()) {case "getUniqueId":return uuid;case "getName":return "Tester";case "getInventory":return inventory;
                case "isOnline":case "hasPermission":return true;}return null;
        });
        NewCustomCuiLianPro.PROTECT_RUNE_JUDGE="保护: ";NewCustomCuiLianPro.LEVEL_JUDGE="属性: ";NewCustomCuiLianPro.LEVEL_STAR_DISPLAY_PREFIX="";
        NewCustomCuiLianPro.replaceLore=Arrays.asList("灵魂绑定"); Message.UNDER_LINE="装备淬炼";
        NewCustomCuiLianPro.types=new ArrayList<>();for(String name:new String[]{"DIAMOND_SWORD","WOOD_SWORD"})NewCustomCuiLianPro.types.add(new NewCustomCuiLianPro.ItemType("Hand",name));
        NewCustomCuiLianPro.types.add(new NewCustomCuiLianPro.ItemType("Helmet","IRON_HELMET"));
        for(int n:new int[]{3,6}){
            HashMap<String,List<String>> attr=new HashMap<>();attr.put("Hand",Arrays.asList("攻击"+n*2));attr.put("Helmet",Arrays.asList("防御"+n));
            Level.levels.put(n,new Level(n,Arrays.asList(n==3?"三星淬炼":"六星淬炼"),attr,new ProtectRune(null,n==3?"三星保护":"六星保护"),null));
        }
        settings=new MoveLevelHandle.Settings(YamlConfiguration.loadConfiguration(new File("src/movelevel.yml")));set(null,MoveLevelHandle.class,"settings",settings);
        commit=MoveLevelHandle.class.getDeclaredMethod("commit",MoveLevelHandle.Session.class);commit.setAccessible(true);
        Gear a=source(),b=target();TransferPlan plan=TransferPlan.build(a,b,true);
        check(((Gear)plan.source).nbt.equals(a.nbt)&&((Gear)plan.target).nbt.equals(b.nbt),"自定义数据丢失");
        check(plan.source.getItemMeta().getLore().equals(Arrays.asList("灵魂绑定: Alice","其他A")),"来源未清除或绑定丢失");
        check(plan.target.getItemMeta().getLore().containsAll(Arrays.asList("灵魂绑定: Bob","其他B","保护: 三星保护","属性: 攻击6")),"目标属性/保护/绑定错误");
        check(!plan.target.getItemMeta().getLore().contains("保护: 六星保护"),"目标保护未覆盖");
        Map<String,Object> gems=new BaoshiTransfer().read(TransferPlan.lore(plan.target));
        check((Double)BaoshiTransfer.field(gems.get("qinglin"),"value")==5,"宝石未覆盖/数值被改写");
        check((Double)BaoshiTransfer.field(gems.get("naijiu"),"value")==27,"耐久精魄未绑定目标耐久");
        check(Level.byItemStack(plan.target).value==3 && Level.byItemStack(a).value==3 && Level.byItemStack(b).value==6,"等级错误或输入被修改");
        try{TransferPlan.build(a,new Gear(Material.IRON_HELMET),false);throw new AssertionError("武器宝石转到护甲");}catch(IllegalArgumentException expected){}
        MoveLevelHandle.Session s=ready();commit.invoke(null,s);check(points.balance==900&&points.takes==1&&Level.byItemStack(contents[0])==null&&Level.byItemStack(contents[1]).value==3,"付费移星失败");
        commit.invoke(null,s);check(points.takes==1,"重复确认扣费");
        s=ready();points.balance=10;commit.invoke(null,s);check(points.takes==0&&Level.byItemStack(contents[0]).value==3,"余额不足仍转移");
        s=ready();points.refuse=true;commit.invoke(null,s);check(points.balance==1000&&Level.byItemStack(contents[1]).value==6,"扣款拒绝仍转移");
        s=ready();apiUsed=new NamePointsApi();commit.invoke(null,s);check(points.balance==900&&Level.byItemStack(contents[1]).value==3,"旧姓名点券API失败");apiUsed=points;
        s=ready();points.afterTake=()->{throw new IllegalStateException("uncertain debit");};commit.invoke(null,s);
        check(!s.active&&Level.byItemStack(contents[0]).value==3&&points.gives==0,"不确定扣款未停止或盲目退款");
        s=ready();s.active=false;commit.invoke(null,s);check(points.takes==0,"关闭界面仍扣费");
        s=ready();contents[0].setDurability((short)4);commit.invoke(null,s);check(points.takes==0,"装备变动未拒绝");
        s=ready();points.afterTake=()->contents[0].setDurability((short)5);commit.invoke(null,s);check(points.balance==1000&&points.gives==1&&contents[0].getDurability()==5,"扣款回调改变装备未退款");
        s=ready();failWrite=true;commit.invoke(null,s);check(points.balance==1000&&points.gives==1&&Level.byItemStack(contents[0]).value==3&&Level.byItemStack(contents[1]).value==6,"写入异常未回滚退款");
        MoveLevelHandle listener=new MoveLevelHandle();
        s=ready();InventoryView view=view(s);
        InventoryClickEvent first=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,settings.confirmSlot,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        listener.click(first);listener.click(first);check(first.isCancelled()&&queued.size()==1,"UI重复确认或图标未锁定");
        listener.close(new InventoryCloseEvent(view));queued.remove(0).run();check(points.takes==0,"关闭UI仍执行排队确认");
        s=ready();view=view(s);
        for(ClickType click:new ClickType[]{ClickType.SHIFT_LEFT,ClickType.NUMBER_KEY,ClickType.DOUBLE_CLICK,ClickType.DROP}){
            InventoryClickEvent blocked=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,settings.sourceSlot,click,InventoryAction.NOTHING);
            listener.click(blocked);check(blocked.isCancelled()&&points.takes==0,"非法点击未阻止");
        }
        InventoryDragEvent drag=new InventoryDragEvent(view,new ItemStack(Material.STONE),new ItemStack(Material.STONE),false,Collections.singletonMap(settings.sourceSlot,new ItemStack(Material.STONE)));
        listener.drag(drag);check(drag.isCancelled(),"UI拖拽未阻止");
        YamlConfiguration bad=YamlConfiguration.loadConfiguration(new File("src/movelevel.yml"));bad.set("cost",-1);
        try{new MoveLevelHandle.Settings(bad);throw new AssertionError("非法价格未拒绝");}catch(IllegalArgumentException expected){}
        System.out.println("PASS: overwrite, binding, protection, stable gems, durability rebasing, gem targets, debit, rejection, close, duplicate, mutation and rollback");
    }
}
