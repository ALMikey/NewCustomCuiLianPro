package lvhaoxuan.custom.cuilian.movelevel;

import java.util.*;
import lvhaoxuan.custom.cuilian.NewCustomCuiLianPro;
import lvhaoxuan.custom.cuilian.api.CuiLianAPI;
import lvhaoxuan.custom.cuilian.message.Message;
import lvhaoxuan.custom.cuilian.object.Level;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class TransferPlan {
    final ItemStack source, target;
    TransferPlan(ItemStack source, ItemStack target) { this.source=source; this.target=target; }
    static List<String> lore(ItemStack item) {
        ItemMeta meta=item.getItemMeta(); return meta!=null && meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<String>();
    }
    static void check(ItemStack item) {
        if (item==null || item.getAmount()!=1 || !CuiLianAPI.canCuiLian(item)) throw new IllegalArgumentException("请选择单件可淬炼装备，不能使用堆叠物品。");
    }
    static Level readLevel(ItemStack item) {
        Level level=Level.byItemStack(item);
        if (level==null && Level.hasRefinementData(item)) throw new IllegalArgumentException("装备淬炼等级无法识别，已取消。");
        return level;
    }
    static Level readProtection(ItemStack item) {
        Level level=Level.byProtectRune(item);
        for (String line:lore(item)) if (line.contains(NewCustomCuiLianPro.PROTECT_RUNE_JUDGE) && level==null)
            throw new IllegalArgumentException("装备保护符无法识别，已取消。");
        return level;
    }
    static TransferPlan build(ItemStack a, ItemStack b, boolean sameCategory) throws Exception {
        check(a); check(b);
        if (sameCategory && !CuiLianAPI.getItemType(a).typeInBag.equals(CuiLianAPI.getItemType(b).typeInBag))
            throw new IllegalArgumentException("当前设置只允许相同装备分类移星。");
        Level from=readLevel(a), old=readLevel(b), protect=readProtection(a), oldProtect=readProtection(b);
        BaoshiTransfer gems=new BaoshiTransfer();
        List<String> aLore=lore(a), bLore=lore(b);
        Map<String,Object> fromGems=gems.read(aLore), oldGems=gems.read(bLore);
        if (from==null && protect==null && fromGems.isEmpty()) throw new IllegalArgumentException("装备A没有可转移的星级、保护符或宝石。");
        ItemStack newA=a.clone(), newB=b.clone();
        List<String> gemLore=gems.render(fromGems,newB);
        gems.clean(newA,aLore,fromGems); gems.clean(newB,bLore,oldGems);
        stripOwned(aLore,from,protect); stripOwned(bLore,old,oldProtect);
        List<String> owned=new ArrayList<>();
        if (from!=null) {
            if (Message.UNDER_LINE!=null && !Message.UNDER_LINE.isEmpty()) owned.add(Message.UNDER_LINE);
            for (String line:from.lore) owned.add(NewCustomCuiLianPro.LEVEL_STAR_DISPLAY_PREFIX+line+NewCustomCuiLianPro.createLevelMarker(from.value));
            List<String> attributes=from.attribute.get(CuiLianAPI.getItemType(b).typeInBag);
            if (attributes==null) throw new IllegalArgumentException("目标装备分类没有该星级属性配置。");
            for (String line:attributes) owned.add(NewCustomCuiLianPro.LEVEL_JUDGE+line);
        }
        if (protect!=null) owned.add(NewCustomCuiLianPro.PROTECT_RUNE_JUDGE+protect.protectRune.lore);
        owned.addAll(gemLore); bLore.addAll(0,owned);
        write(newA,aLore,0); write(newB,bLore,from==null?0:from.value);
        return new TransferPlan(newA,newB);
    }
    static void stripOwned(List<String> lore, Level level, Level protect) {
        CuiLianAPI.cleanLevel(lore); CuiLianAPI.cleanProtectRune(lore);
        // 老版本可能没有星级隐藏标记，只删除精确匹配的旧星级/保护符行。
        Iterator<String> iterator=lore.iterator();
        while(iterator.hasNext()) {
            String plain=ChatColor.stripColor(iterator.next());
            boolean remove=protect!=null && plain.equals(ChatColor.stripColor(protect.protectRune.lore));
            if (level!=null) for(String line:level.lore) if(plain.equals(ChatColor.stripColor(line))) remove=true;
            if(remove) iterator.remove();
        }
    }
    static void write(ItemStack item,List<String> lore,int level) {
        ItemMeta meta=item.getItemMeta(); meta.setLore(lore);
        CuiLianAPI.setDisplayName(item.getType(),meta,level); item.setItemMeta(meta);
    }
}
