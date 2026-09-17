package lvhaoxuan.custom.cuilian.movelevel;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import lvhaoxuan.custom.cuilian.NewCustomCuiLianPro;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

/** 只显示装备副本，原装备始终留在背包；主线程校验并扣费。 */
public final class MoveLevelHandle implements Listener {
    public static String moveLevelInvTitle;
    public static boolean enableMoveLevel = true;
    private static Settings settings;
    private static final Map<UUID,Session> sessions=new HashMap<>();
    public static void load() {
        try {
            File file=new File(NewCustomCuiLianPro.ins.getDataFolder(),"movelevel.yml");
            if(!file.exists()) NewCustomCuiLianPro.ins.saveResource("movelevel.yml",false);
            YamlConfiguration config=new YamlConfiguration(); config.load(file);
            Settings candidate=new Settings(config); closeAll(); settings=candidate;
        } catch(Exception ex) {
            NewCustomCuiLianPro.ins.getLogger().log(Level.SEVERE,"movelevel.yml 无效，保留上次配置；首次加载失败则禁用移星。",ex);
        }
    }
    public static void open(Player player) {
        if(settings==null || !settings.enabled || !enableMoveLevel) { player.sendMessage("§c移星功能当前不可用。"); return; }
        if(!player.hasPermission("cuilian.movelevel")) { player.sendMessage("§c你没有移星权限。"); return; }
        Session session=new Session(player,settings);
        player.openInventory(session.inventory); sessions.put(player.getUniqueId(),session);
        player.sendMessage("§e先左键选择背包装备A，再选择B；点击确认会覆盖B的星级、保护符和宝石。");
    }
    private static Session session(Inventory inventory) {
        return inventory.getHolder() instanceof Session ? (Session)inventory.getHolder() : null;
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void click(InventoryClickEvent event) {
        final Session session=session(event.getInventory());
        if(session==null) return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player) || !session.player.getUniqueId().equals(event.getWhoClicked().getUniqueId())
                || !session.active || session.busy || !"LEFT".equals(event.getClick().name())) return;
        int raw=event.getRawSlot(); if(raw<0) return;
        if(raw<session.cfg.size) {
            if(raw==session.cfg.sourceSlot) { session.selectA=true; session.player.sendMessage("§e请左键背包装备A（来源）。"); }
            else if(raw==session.cfg.targetSlot) { session.selectA=false; session.player.sendMessage("§e请左键背包装备B（覆盖目标）。"); }
            else if(raw==session.cfg.confirmSlot) {
                session.busy=true;
                Bukkit.getScheduler().runTask(NewCustomCuiLianPro.ins,new Runnable() {
                    @Override public void run() { commit(session); }
                });
            }
            return;
        }
        int slot=event.getSlot(); if(slot<0 || slot>=36) return;
        ItemStack item=session.player.getInventory().getItem(slot);
        try { TransferPlan.check(item); }
        catch(IllegalArgumentException ex) { session.player.sendMessage("§c"+ex.getMessage()); return; }
        if(slot==(session.selectA?session.targetSlot:session.sourceSlot)) { session.player.sendMessage("§cA与B不能是同一件装备。"); return; }
        if(session.selectA) { session.sourceSlot=slot; session.source=item.clone(); session.selectA=false; }
        else { session.targetSlot=slot; session.target=item.clone(); }
        session.paint();
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void drag(InventoryDragEvent event) { if(session(event.getInventory())!=null) event.setCancelled(true); }
    @EventHandler public void close(InventoryCloseEvent event) {
        Session session=session(event.getInventory());
        if(session!=null) { session.active=false; sessions.remove(session.player.getUniqueId(),session); }
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        Session session=sessions.remove(event.getPlayer().getUniqueId()); if(session!=null) session.active=false;
    }
    @EventHandler public void disable(PluginDisableEvent event) { if(event.getPlugin()==NewCustomCuiLianPro.ins) closeAll(); }
    private static void closeAll() {
        for(Session session:new ArrayList<>(sessions.values())) { session.active=false; session.player.closeInventory(); }
        sessions.clear();
    }
    static boolean unchanged(Session session) {
        return session.sourceSlot>=0 && session.targetSlot>=0 && session.sourceSlot!=session.targetSlot
                && session.source.equals(session.player.getInventory().getItem(session.sourceSlot))
                && session.target.equals(session.player.getInventory().getItem(session.targetSlot));
    }
    private static void commit(Session session) {
        if(!session.active || !session.player.isOnline() || session.cfg!=settings) return;
        Player player=session.player;
        PointsPayment payment=null; boolean paid=false, writing=false;
        try {
            if(!player.hasPermission("cuilian.movelevel")) throw new IllegalArgumentException("你没有移星权限。");
            if(!unchanged(session)) throw new IllegalArgumentException("未选择两件装备，或装备已发生变化，请重新选择。");
            TransferPlan plan=TransferPlan.build(session.source,session.target,session.cfg.sameCategory);
            if(session.cfg.price>0) {
                payment=new PointsPayment(player);
                if(!payment.debit(session.cfg.price)) throw new IllegalArgumentException("点券不足或PlayerPoints拒绝扣款，移星未执行。");
                paid=true;
            }
            // 点券插件可能同步触发其他事件，扣款后再次检查。
            if(!session.active || session.cfg!=settings || !unchanged(session)) throw new IllegalArgumentException("扣款期间装备或界面发生变化，已取消移星。");
            writing=true;
            player.getInventory().setItem(session.sourceSlot,plan.source);
            player.getInventory().setItem(session.targetSlot,plan.target);
            if(!plan.source.equals(player.getInventory().getItem(session.sourceSlot))
                    || !plan.target.equals(player.getInventory().getItem(session.targetSlot))) throw new IllegalStateException("装备写入校验失败");
            writing=false; paid=false; session.active=false;
            NewCustomCuiLianPro.ins.getLogger().info("[MoveLevel] SUCCESS player="+player.getName()+" points="+session.cfg.price
                    +" sourceSlot="+session.sourceSlot+" targetSlot="+session.targetSlot);
            player.sendMessage("§a移星成功，已扣除 "+session.cfg.price+" 点券。A的星级、保护符和宝石已转移到B。");
            player.closeInventory(); player.updateInventory();
        } catch(Exception ex) {
            if(payment!=null && payment.uncertain) {
                session.active=false; player.closeInventory();
                player.sendMessage("§c点券接口异常，装备未转移；扣款状态不确定，请联系管理员核对，勿重复支付。");
                NewCustomCuiLianPro.ins.getLogger().log(Level.SEVERE,"[MoveLevel] UNKNOWN_DEBIT player="+player.getName()+" points="+session.cfg.price,ex);
                return;
            }
            if(writing) {
                try { player.getInventory().setItem(session.sourceSlot,session.source); player.getInventory().setItem(session.targetSlot,session.target); }
                catch(Exception rollback) { NewCustomCuiLianPro.ins.getLogger().log(Level.SEVERE,"[MoveLevel] 装备恢复失败 player="+player.getName(),rollback); }
            }
            boolean refunded=!paid;
            if(paid) try { refunded=payment.refund(session.cfg.price); }
            catch(Exception refund) { NewCustomCuiLianPro.ins.getLogger().log(Level.SEVERE,"[MoveLevel] 退款异常 player="+player.getName(),refund); }
            if(!refunded) {
                NewCustomCuiLianPro.ins.getLogger().severe("[MoveLevel] 需要人工退款 player="+player.getName()+" points="+session.cfg.price);
                player.sendMessage("§c移星失败且点券退款失败，请联系管理员核对余额。");
            } else player.sendMessage("§c"+(ex instanceof IllegalArgumentException?ex.getMessage():"移星失败，请联系管理员检查接口与日志。")+(paid?" 点券已退还。":""));
            if(!(ex instanceof IllegalArgumentException)) NewCustomCuiLianPro.ins.getLogger().log(Level.WARNING,"[MoveLevel] FAILED player="+player.getName()+" points="+session.cfg.price+"（若扣款API异常请核对余额）",ex);
            session.busy=false;
        }
    }
    static final class Session implements InventoryHolder {
        final Player player; final Settings cfg; final Inventory inventory;
        boolean active=true, busy=false, selectA=true;
        int sourceSlot=-1,targetSlot=-1; ItemStack source,target;
        Session(Player player,Settings cfg) {
            this.player=player; this.cfg=cfg; inventory=Bukkit.createInventory(this,cfg.size,cfg.title); paint();
        }
        public Inventory getInventory() { return inventory; }
        void paint() {
            for(int i=0;i<cfg.size;i++) inventory.setItem(i,cfg.background.clone());
            inventory.setItem(cfg.sourceSlot,source==null?cfg.source.clone():source.clone());
            inventory.setItem(cfg.targetSlot,target==null?cfg.target.clone():target.clone());
            inventory.setItem(cfg.confirmSlot,cfg.confirm.clone());
        }
    }
    static final class Settings {
        final boolean enabled,sameCategory; final int size,sourceSlot,targetSlot,confirmSlot,price;
        final String title; final ItemStack source,target,confirm,background;
        Settings(YamlConfiguration config) {
            enabled=config.getBoolean("enabled",true); sameCategory=config.getBoolean("same-category",true);
            price=config.getInt("cost",100); int rows=config.getInt("ui.rows",3);
            if(config.contains("cost") && (!(config.get("cost") instanceof Number)
                    || ((Number)config.get("cost")).doubleValue()!=price)) throw new IllegalArgumentException("cost必须是0至2147483647的整数");
            if(price<0 || rows<1 || rows>6) throw new IllegalArgumentException("cost必须非负，ui.rows必须1至6");
            size=rows*9; title=color(config.getString("ui.title","&6淬炼移星"));
            sourceSlot=config.getInt("ui.source.slot",11); targetSlot=config.getInt("ui.target.slot",15); confirmSlot=config.getInt("ui.confirm.slot",22);
            Set<Integer> slots=new HashSet<>(Arrays.asList(sourceSlot,targetSlot,confirmSlot));
            if(slots.size()!=3 || Collections.min(slots)<0 || Collections.max(slots)>=size) throw new IllegalArgumentException("UI槽位重复或超出界面大小");
            source=icon(config,"source","ANVIL","&e装备A：点击后选择来源");
            target=icon(config,"target","ANVIL","&e装备B：点击后选择目标");
            confirm=icon(config,"confirm","EMERALD","&a确认覆盖移星（%cost%点券）");
            background=icon(config,"background","STAINED_GLASS_PANE"," ");
        }
        ItemStack icon(YamlConfiguration config,String key,String material,String name) {
            String path="ui."+key+".";
            Material type=Material.getMaterial(config.getString(path+"material",material).toUpperCase(Locale.ROOT));
            int data=config.getInt(path+"data",0);
            if(type==null || type==Material.AIR || data<0 || data>32767) throw new IllegalArgumentException("无效UI材质/Data："+key);
            ItemStack item=new ItemStack(type,1,(short)data); ItemMeta meta=item.getItemMeta();
            meta.setDisplayName(text(config.getString(path+"name",name)));
            List<String> lore=new ArrayList<>(); for(String line:config.getStringList(path+"lore")) lore.add(text(line));
            meta.setLore(lore); item.setItemMeta(meta); return item;
        }
        String text(String value) { return color(value.replace("%cost%",String.valueOf(price))); }
        static String color(String value) { return ChatColor.translateAlternateColorCodes('&',value); }
    }
}
