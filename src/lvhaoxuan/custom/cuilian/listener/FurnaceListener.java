package lvhaoxuan.custom.cuilian.listener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.event.EventPriority;
import lvhaoxuan.custom.cuilian.NewCustomCuiLianPro;
import lvhaoxuan.custom.cuilian.api.CuiLianAPI;
import lvhaoxuan.custom.cuilian.object.Level;
import lvhaoxuan.custom.cuilian.object.Stone;
import lvhaoxuan.custom.cuilian.message.Message;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

public class FurnaceListener implements Listener {

    private static final short MOD_SMELT_TICKS = 200;
    private final Map<Location, ModFurnaceProcess> trackedModFurnaces = new HashMap<>();
    private final Map<Location, VanillaFurnaceProcess> trackedFurnaces = new HashMap<>();
    private final Set<Location> missingProcessWarnings = new HashSet<>();

    public FurnaceListener() {
        // Forge 1.7.10 does not consistently re-check dynamic furnace recipes after a
        // Mod tool's Damage NBT changes. Process configured Mod equipment here instead.
        Bukkit.getScheduler().runTaskTimer(NewCustomCuiLianPro.ins, new Runnable() {
            @Override
            public void run() {
                tickTrackedModFurnaces();
            }
        }, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void PlayerInteractEvent(PlayerInteractEvent e) {
        if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && e.hasBlock() && e.getClickedBlock().getType().equals(Material.FURNACE)) {
            Player p = e.getPlayer();
            Furnace furnace = (Furnace) e.getClickedBlock().getState();
            rememberFurnaceOwner(furnace, p.getName());
            trackModFurnace(furnace, p.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void FurnaceBurnEvent(FurnaceBurnEvent e) {
        Furnace furnace = (Furnace) e.getBlock().getState();
        ItemStack fuel = e.getFuel();
        ItemStack smelt = furnace.getInventory().getSmelting();
        if (isConfiguredModItem(smelt)) {
            trackModFurnace(furnace, getFurnaceOwner(furnace));
            // The repeating task performs the actual Mod-item smelting. Cancelling here
            // prevents any legacy/dynamic NMS recipe from consuming the input in parallel.
            e.setCancelled(true);
            return;
        }
        Stone stone = Stone.byItemStack(fuel);
        Level level = Level.byItemStack(smelt);
        if (CuiLianAPI.canCuiLian(smelt)) {
            if (stone != null && Level.levels.get((level != null ? level.value : 0) + stone.riseLevel) != null) {
                furnace.setMetadata("FurnaceFuel", new FixedMetadataValue(NewCustomCuiLianPro.ins, stone));
                furnace.setMetadata("FurnaceSource", new FixedMetadataValue(NewCustomCuiLianPro.ins, smelt.clone()));
                trackedFurnaces.put(furnace.getLocation(),
                        new VanillaFurnaceProcess(stone, smelt, getFurnaceOwner(furnace)));
                missingProcessWarnings.remove(furnace.getLocation());
                e.setBurning(true);
                e.setBurnTime(200);
            } else {
                trackedFurnaces.remove(furnace.getLocation());
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void FurnaceSmeltEvent(FurnaceSmeltEvent e) {
        ItemStack smelt = e.getSource();
        Furnace furnace = (Furnace) e.getBlock().getState();
        if (isConfiguredModItem(smelt)) {
            e.setCancelled(true);
            trackModFurnace(furnace, getFurnaceOwner(furnace));
            return;
        }
        VanillaFurnaceProcess process = trackedFurnaces.remove(furnace.getLocation());
        if (process != null && !process.matches(smelt)) {
            process = null;
            furnace.removeMetadata("FurnaceFuel", NewCustomCuiLianPro.ins);
            furnace.removeMetadata("FurnaceSource", NewCustomCuiLianPro.ins);
        }
        Stone stone = process != null ? process.stone : getStoredStone(furnace);
        if (stone == null) {
            stone = Stone.byItemStack(furnace.getInventory().getFuel());
        }
        if (stone != null) {
            Player p = resolveFurnacePlayer(furnace, process != null ? process.owner : getFurnaceOwner(furnace));
            smelt.setAmount(1);
            smelt = CuiLianAPI.cuilian(stone, smelt, p);
            e.setResult(smelt);
            missingProcessWarnings.remove(furnace.getLocation());
            furnace.removeMetadata("FurnaceFuel", NewCustomCuiLianPro.ins);
            furnace.removeMetadata("FurnaceSource", NewCustomCuiLianPro.ins);
        } else if (CuiLianAPI.canCuiLian(smelt)) {
            // Never silently turn a lost process into an unchanged output item. Keep the
            // input in place so the player can retry with a new stone.
            e.setCancelled(true);
            furnace.setCookTime((short) 0);
            furnace.setBurnTime((short) 0);
            furnace.removeMetadata("FurnaceFuel", NewCustomCuiLianPro.ins);
            furnace.removeMetadata("FurnaceSource", NewCustomCuiLianPro.ins);
            notifyMissingProcess(furnace);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void InventoryClickEvent(InventoryClickEvent e) {
        if (e.getInventory().getType() == InventoryType.FURNACE && e.getSlotType() == InventoryType.SlotType.FUEL && Stone.byItemStack(e.getCursor()) != null) {
            ItemStack cursor = e.getCursor();
            ItemStack currentItem = e.getCurrentItem();
            e.setCursor(currentItem);
            e.setCurrentItem(cursor);
            e.setCancelled(true);
        }
        scheduleFurnaceTracking(e.getInventory(), e.getWhoClicked().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void InventoryDragEvent(InventoryDragEvent e) {
        scheduleFurnaceTracking(e.getInventory(), e.getWhoClicked().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void InventoryOpenEvent(InventoryOpenEvent e) {
        scheduleFurnaceTracking(e.getInventory(), e.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void PlayerItemDamageEvent(PlayerItemDamageEvent e) {
        // Mod items are identified by ID only; their current durability is irrelevant.
    }

    private void scheduleFurnaceTracking(final Inventory inventory, final String owner) {
        if (inventory.getType() != InventoryType.FURNACE) {
            return;
        }
        Bukkit.getScheduler().runTask(NewCustomCuiLianPro.ins, new Runnable() {
            @Override
            public void run() {
                if (inventory.getHolder() instanceof Furnace) {
                    Furnace furnace = (Furnace) inventory.getHolder();
                    rememberFurnaceOwner(furnace, owner);
                    trackModFurnace(furnace, owner);
                }
            }
        });
    }

    private void trackModFurnace(Furnace furnace, String owner) {
        if (furnace == null || !isConfiguredModItem(furnace.getInventory().getSmelting())) {
            return;
        }
        Location location = furnace.getLocation();
        ModFurnaceProcess process = trackedModFurnaces.get(location);
        if (process == null) {
            trackedModFurnaces.put(location, new ModFurnaceProcess(owner));
        } else if (owner != null && !owner.isEmpty()) {
            process.owner = owner;
        }
    }

    private boolean isConfiguredModItem(ItemStack item) {
        NewCustomCuiLianPro.ItemType type = CuiLianAPI.getItemType(item);
        return type != null && !type.canUseBukkitRecipe();
    }

    private String getFurnaceOwner(Furnace furnace) {
        return furnace.hasMetadata("FurnaceOwner")
                ? furnace.getMetadata("FurnaceOwner").get(0).asString() : "";
    }

    private void rememberFurnaceOwner(Furnace furnace, String owner) {
        if (furnace != null && owner != null && !owner.isEmpty()) {
            furnace.setMetadata("FurnaceOwner", new FixedMetadataValue(NewCustomCuiLianPro.ins, owner));
            VanillaFurnaceProcess process = trackedFurnaces.get(furnace.getLocation());
            if (process != null) {
                process.owner = owner;
            }
        }
    }

    private Stone getStoredStone(Furnace furnace) {
        if (!furnace.hasMetadata("FurnaceFuel") || furnace.getMetadata("FurnaceFuel").isEmpty()) {
            return null;
        }
        Object value = furnace.getMetadata("FurnaceFuel").get(0).value();
        if (!(value instanceof Stone)) {
            return null;
        }
        if (furnace.hasMetadata("FurnaceSource") && !furnace.getMetadata("FurnaceSource").isEmpty()) {
            Object source = furnace.getMetadata("FurnaceSource").get(0).value();
            ItemStack current = furnace.getInventory().getSmelting();
            if (source instanceof ItemStack && (current == null || !((ItemStack) source).isSimilar(current))) {
                return null;
            }
        }
        return (Stone) value;
    }

    private Player resolveFurnacePlayer(Furnace furnace, String owner) {
        Player player = owner == null || owner.isEmpty() ? null : Bukkit.getPlayer(owner);
        if (player != null) {
            return player;
        }
        for (HumanEntity viewer : furnace.getInventory().getViewers()) {
            if (viewer instanceof Player) {
                return (Player) viewer;
            }
        }
        return null;
    }

    private void notifyMissingProcess(Furnace furnace) {
        Location location = furnace.getLocation();
        if (!missingProcessWarnings.add(location)) {
            return;
        }
        Player player = resolveFurnacePlayer(furnace, getFurnaceOwner(furnace));
        if (player != null) {
            player.sendMessage(Message.CUILIAN_PROCESS_LOST);
        }
        NewCustomCuiLianPro.ins.getLogger().warning("熔炉淬炼记录丢失，已取消产出并保留输入装备: world="
                + location.getWorld().getName() + ", x=" + location.getBlockX()
                + ", y=" + location.getBlockY() + ", z=" + location.getBlockZ());
    }

    private void tickTrackedModFurnaces() {
        for (java.util.Iterator<Map.Entry<Location, ModFurnaceProcess>> iterator = trackedModFurnaces.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<Location, ModFurnaceProcess> entry = iterator.next();
            if (!(entry.getKey().getBlock().getState() instanceof Furnace)) {
                iterator.remove();
                continue;
            }
            Furnace furnace = (Furnace) entry.getKey().getBlock().getState();
            ItemStack smelt = furnace.getInventory().getSmelting();
            if (!isConfiguredModItem(smelt)) {
                iterator.remove();
                continue;
            }
            ItemStack fuel = furnace.getInventory().getFuel();
            Stone stone = Stone.byItemStack(fuel);
            Level level = Level.byItemStack(smelt);
            ItemStack currentResult = furnace.getInventory().getResult();
            if (stone == null || Level.levels.get((level != null ? level.value : 0) + stone.riseLevel) == null
                    || (currentResult != null && currentResult.getType() != Material.AIR && currentResult.getAmount() > 0)) {
                entry.getValue().cookTicks = 0;
                furnace.setCookTime((short) 0);
                furnace.setBurnTime((short) 0);
                continue;
            }

            ModFurnaceProcess process = entry.getValue();
            process.cookTicks++;
            furnace.setBurnTime(MOD_SMELT_TICKS);
            if (process.cookTicks < MOD_SMELT_TICKS) {
                furnace.setCookTime((short) process.cookTicks);
                continue;
            }

            process.cookTicks = 0;
            furnace.setCookTime((short) 0);
            furnace.setBurnTime((short) 0);
            ItemStack result = smelt.clone();
            result.setAmount(1);
            Player player = Bukkit.getPlayer(process.owner);
            result = CuiLianAPI.cuilian(stone, result, player);
            furnace.getInventory().setSmelting(null);
            if (fuel.getAmount() <= 1) {
                furnace.getInventory().setFuel(null);
            } else {
                fuel.setAmount(fuel.getAmount() - 1);
                furnace.getInventory().setFuel(fuel);
            }
            furnace.getInventory().setResult(result);
            furnace.update(true);
        }
    }

    private static final class ModFurnaceProcess {

        private String owner;
        private int cookTicks;

        private ModFurnaceProcess(String owner) {
            this.owner = owner == null ? "" : owner;
        }
    }

    private static final class VanillaFurnaceProcess {

        private final Stone stone;
        private final ItemStack source;
        private String owner;

        private VanillaFurnaceProcess(Stone stone, ItemStack source, String owner) {
            this.stone = stone;
            this.source = source.clone();
            this.owner = owner == null ? "" : owner;
        }

        private boolean matches(ItemStack item) {
            return item != null && source.isSimilar(item);
        }
    }
}
