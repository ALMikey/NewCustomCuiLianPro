package lvhaoxuan.custom.cuilian.listener;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

public class FurnaceListener implements Listener {

    private static final short MOD_SMELT_TICKS = 200;
    private final Map<Location, ModFurnaceProcess> trackedModFurnaces = new HashMap<>();

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
            furnace.setMetadata("FurnaceOwner", new FixedMetadataValue(NewCustomCuiLianPro.ins, p.getName()));
            trackModFurnace(furnace, p.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
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
                e.setBurning(true);
                e.setBurnTime(200);
            } else {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void FurnaceSmeltEvent(FurnaceSmeltEvent e) {
        ItemStack smelt = e.getSource();
        Furnace furnace = (Furnace) e.getBlock().getState();
        if (isConfiguredModItem(smelt)) {
            e.setCancelled(true);
            trackModFurnace(furnace, getFurnaceOwner(furnace));
            return;
        }
        if (furnace.hasMetadata("FurnaceFuel")) {
            Stone stone = (Stone) furnace.getMetadata("FurnaceFuel").get(0).value();
            String name = furnace.hasMetadata("FurnaceOwner") ? furnace.getMetadata("FurnaceOwner").get(0).asString() : "";
            Player p = Bukkit.getPlayer(name);
            smelt.setAmount(1);
            smelt = CuiLianAPI.cuilian(stone, smelt, p);
            e.setResult(smelt);
            furnace.removeMetadata("FurnaceFuel", NewCustomCuiLianPro.ins);
        } else if (CuiLianAPI.canCuiLian(smelt)) {
            e.setResult(smelt);
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
        scheduleModFurnaceTracking(e.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void InventoryDragEvent(InventoryDragEvent e) {
        scheduleModFurnaceTracking(e.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void InventoryOpenEvent(InventoryOpenEvent e) {
        scheduleModFurnaceTracking(e.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void PlayerItemDamageEvent(PlayerItemDamageEvent e) {
        // Mod items are identified by ID only; their current durability is irrelevant.
    }

    private void scheduleModFurnaceTracking(final Inventory inventory) {
        if (inventory.getType() != InventoryType.FURNACE) {
            return;
        }
        Bukkit.getScheduler().runTask(NewCustomCuiLianPro.ins, new Runnable() {
            @Override
            public void run() {
                if (inventory.getHolder() instanceof Furnace) {
                    Furnace furnace = (Furnace) inventory.getHolder();
                    trackModFurnace(furnace, getFurnaceOwner(furnace));
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
}
