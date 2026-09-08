package lvhaoxuan.custom.cuilian.listener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
    public static final String REFINING_METADATA = "NewCustomCuiLianPro:refining";
    private final Map<Location, ModFurnaceProcess> trackedModFurnaces = new HashMap<>();
    private final Map<Location, VanillaFurnaceProcess> trackedFurnaces = new HashMap<>();
    private final Set<Location> missingProcessWarnings = new HashSet<>();
    private final FurnaceNmsBridge furnaceNmsBridge = new FurnaceNmsBridge();

    public FurnaceListener() {
        // Forge 1.7.10 does not consistently re-check dynamic furnace recipes after a
        // Mod tool's Damage NBT changes. Process configured Mod equipment here instead.
        Bukkit.getScheduler().runTaskTimer(NewCustomCuiLianPro.ins, new Runnable() {
            @Override
            public void run() {
                tickNativeProcesses();
                tickTrackedModFurnaces();
            }
        }, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void PlayerInteractEvent(PlayerInteractEvent e) {
        if (e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && e.hasBlock()
                && isFurnaceMaterial(e.getClickedBlock().getType())) {
            Player p = e.getPlayer();
            Furnace furnace = (Furnace) e.getClickedBlock().getState();
            rememberFurnaceOwner(furnace, p.getName());
            trackModFurnace(furnace, p.getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
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
            if (smelt.getAmount() == 1 && stone != null && Level.levels.get((level != null ? level.value : 0) + stone.riseLevel) != null) {
                trackedFurnaces.put(furnace.getLocation(),
                        new VanillaFurnaceProcess(stone, smelt, fuel, getFurnaceOwner(furnace)));
                markRefining(furnace);
                furnace.setCookTime((short) 0);
                missingProcessWarnings.remove(furnace.getLocation());
                e.setBurning(true);
                e.setBurnTime(200);
            } else {
                endNativeProcess(furnace);
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void FurnaceSmeltEvent(FurnaceSmeltEvent e) {
        ItemStack smelt = e.getSource();
        Furnace furnace = (Furnace) e.getBlock().getState();
        if (isConfiguredModItem(smelt)) {
            e.setCancelled(true);
            trackModFurnace(furnace, getFurnaceOwner(furnace));
            return;
        }
        if (!CuiLianAPI.canCuiLian(smelt)) {
            if (trackedFurnaces.containsKey(furnace.getLocation())) {
                endNativeProcess(furnace);
            }
            return;
        }
        VanillaFurnaceProcess process = trackedFurnaces.remove(furnace.getLocation());
        if (process != null && (!process.matches(smelt) || !process.confirmDebit(furnace.getInventory().getFuel()))) {
            process = null;
        }
        // 记录在结算前移除；燃料槽里有宝石不等于本次已经支付。
        if (process != null && CuiLianAPI.canCuiLian(smelt)) {
            try {
                Player p = resolveFurnacePlayer(furnace, process.owner);
                ItemStack result = CuiLianAPI.cuilian(process.stone, smelt.clone(), p);
                e.setResult(result);
                missingProcessWarnings.remove(furnace.getLocation());
                logFurnaceStage("NATIVE_COMMIT", furnace, process.owner, smelt,
                        furnace.getInventory().getFuel(), result, "debitConfirmed=true,creditConsumed=true");
            } catch (RuntimeException ex) {
                e.setCancelled(true);
                throw ex;
            } finally {
                endNativeProcess(furnace);
            }
        } else if (CuiLianAPI.canCuiLian(smelt)) {
            // Never silently turn a lost process into an unchanged output item. Keep the
            // input in place so the player can retry with a new stone.
            e.setCancelled(true);
            endNativeProcess(furnace);
            notifyMissingProcess(furnace);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void observeBurn(FurnaceBurnEvent e) {
        if ((e.isCancelled() || !e.isBurning() || e.getBurnTime() <= 0)
                && trackedFurnaces.containsKey(e.getBlock().getLocation())) {
            endNativeProcess((Furnace) e.getBlock().getState());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void observeSmelt(FurnaceSmeltEvent e) {
        if (e.isCancelled() && trackedFurnaces.containsKey(e.getBlock().getLocation())) {
            endNativeProcess((Furnace) e.getBlock().getState());
        }
    }

    private void markRefining(Furnace furnace) {
        furnace.setMetadata(REFINING_METADATA, new FixedMetadataValue(NewCustomCuiLianPro.ins, true));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDisable(org.bukkit.event.server.PluginDisableEvent event) {
        if (event.getPlugin() != NewCustomCuiLianPro.ins) {
            return;
        }
        Set<Location> locations = new HashSet<Location>(trackedFurnaces.keySet());
        locations.addAll(trackedModFurnaces.keySet());
        for (Location location : locations) {
            if (location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                    && location.getBlock().getState() instanceof Furnace) {
                stopModFurnaceBurning((Furnace) location.getBlock().getState());
            }
        }
        trackedFurnaces.clear();
        trackedModFurnaces.clear();
    }

    private void endNativeProcess(Furnace furnace) {
        trackedFurnaces.remove(furnace.getLocation());
        furnace.removeMetadata("FurnaceFuel", NewCustomCuiLianPro.ins);
        furnace.removeMetadata("FurnaceSource", NewCustomCuiLianPro.ins);
        stopModFurnaceBurning(furnace);
    }

    private void tickNativeProcesses() {
        for (Location location : new java.util.ArrayList<Location>(trackedFurnaces.keySet())) {
            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                continue;
            }
            if (!(location.getBlock().getState() instanceof Furnace)) {
                trackedFurnaces.remove(location);
                location.getBlock().removeMetadata(REFINING_METADATA, NewCustomCuiLianPro.ins);
                continue;
            }
            Furnace furnace = (Furnace) location.getBlock().getState();
            VanillaFurnaceProcess process = trackedFurnaces.get(location);
            if (!process.matches(furnace.getInventory().getSmelting())
                    || !process.confirmDebit(furnace.getInventory().getFuel())
                    || !isEmpty(furnace.getInventory().getResult()) || ++process.age > 1200) {
                logFurnaceStage("NATIVE_CANCEL", furnace, process.owner,
                        furnace.getInventory().getSmelting(), furnace.getInventory().getFuel(),
                        furnace.getInventory().getResult(), "invalidInputOrDebitOrTimeout=true");
                endNativeProcess(furnace);
            }
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
            process = new ModFurnaceProcess(owner);
            trackedModFurnaces.put(location, process);
            logFurnaceStage("TRACK", furnace, process.owner,
                    furnace.getInventory().getSmelting(), furnace.getInventory().getFuel(),
                    furnace.getInventory().getResult(), "configuredNumericId=true");
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
            if (!entry.getKey().getWorld().isChunkLoaded(entry.getKey().getBlockX() >> 4, entry.getKey().getBlockZ() >> 4)) {
                continue;
            }
            if (!(entry.getKey().getBlock().getState() instanceof Furnace)) {
                entry.getKey().getBlock().removeMetadata(REFINING_METADATA, NewCustomCuiLianPro.ins);
                iterator.remove();
                continue;
            }
            Furnace furnace = (Furnace) entry.getKey().getBlock().getState();
            ItemStack smelt = furnace.getInventory().getSmelting();
            if (!isConfiguredModItem(smelt)) {
                if (entry.getValue().started) {
                    stopModFurnaceBurning(furnace);
                }
                iterator.remove();
                continue;
            }

            ItemStack fuel = furnace.getInventory().getFuel();
            Stone stone = Stone.byItemStack(fuel);
            Level level = Level.byItemStack(smelt);
            ItemStack currentResult = furnace.getInventory().getResult();
            if (smelt.getAmount() != 1 || stone == null || Level.levels.get((level != null ? level.value : 0) + stone.riseLevel) == null
                    || !isEmpty(currentResult)) {
                resetModProcess(furnace, entry.getValue(), smelt.getAmount() != 1 ? "stackedInput" : stone == null ? "invalidStone"
                        : (!isEmpty(currentResult) ? "outputOccupied" : "targetLevelMissing"));
                continue;
            }

            ModFurnaceProcess process = entry.getValue();
            if (!process.matches(smelt, stone)) {
                process.capture(smelt, stone);
                markRefining(furnace);
                logFurnaceStage("START", furnace, process.owner, smelt, fuel, currentResult,
                        "stone=" + stone.id + ",level=" + (level == null ? 0 : level.value));
            }

            process.cookTicks++;
            if (process.cookTicks < MOD_SMELT_TICKS) {
                int remainingBurnTicks = Math.max(2, MOD_SMELT_TICKS - process.cookTicks);
                boolean realBurnState = furnaceNmsBridge.apply(furnace, true,
                        remainingBurnTicks, process.cookTicks);
                if (!realBurnState) {
                    // Keep the old progress behaviour as a compatibility fallback. The
                    // bridge logs its first failure with enough details for diagnosis.
                    furnace.setBurnTime((short) remainingBurnTicks);
                    furnace.setCookTime((short) process.cookTicks);
                }
                if (!process.burnStateLogged) {
                    process.burnStateLogged = true;
                    logFurnaceStage("BURN_STATE", furnace, process.owner, smelt, fuel,
                            currentResult, "realBurnState=" + realBurnState);
                }
                continue;
            }

            ItemStack result = smelt.clone();
            result.setAmount(1);
            Player player = resolveFurnacePlayer(furnace, process.owner);
            result = CuiLianAPI.cuilian(stone, result, player);

            // Write and verify the output before consuming either input. Uranium can
            // reject a refined Forge ItemStack after its Lore/NBT changes; consuming
            // first caused the old silent stone-loss failure.
            furnace.getInventory().setResult(result.clone());
            ItemStack writtenResult = furnace.getInventory().getResult();
            if (isEmpty(writtenResult) || !writtenResult.isSimilar(result)) {
                furnace.getInventory().setResult(null);
                logFurnaceStage("COMMIT_FAILED", furnace, process.owner, smelt, fuel,
                        writtenResult, "outputWriteRejected=true");
                resetModProcess(furnace, process, "outputWriteRejected");
                continue;
            }

            furnace.getInventory().setSmelting(null);
            ItemStack liveFuel = furnace.getInventory().getFuel();
            if (liveFuel == null || liveFuel.getAmount() <= 1) {
                furnace.getInventory().setFuel(null);
            } else {
                ItemStack remainingFuel = liveFuel.clone();
                remainingFuel.setAmount(liveFuel.getAmount() - 1);
                furnace.getInventory().setFuel(remainingFuel);
            }
            furnace.setCookTime((short) 0);
            furnace.setBurnTime((short) 0);
            stopModFurnaceBurning(furnace);
            logFurnaceStage("COMMIT", furnace, process.owner,
                    furnace.getInventory().getSmelting(), furnace.getInventory().getFuel(),
                    furnace.getInventory().getResult(), "stone=" + stone.id);

            final Location committedLocation = furnace.getLocation();
            final String committedOwner = process.owner;
            final ItemStack expectedResult = result.clone();
            iterator.remove();
            scheduleCommitVerification(committedLocation, committedOwner, expectedResult);
        }
    }

    private void resetModProcess(Furnace furnace, ModFurnaceProcess process, String reason) {
        if (process.started || process.cookTicks > 0) {
            logFurnaceStage("RESET", furnace, process.owner,
                    furnace.getInventory().getSmelting(), furnace.getInventory().getFuel(),
                    furnace.getInventory().getResult(), "reason=" + reason);
        }
        process.reset();
        furnace.setCookTime((short) 0);
        furnace.setBurnTime((short) 0);
        stopModFurnaceBurning(furnace);
    }

    private void stopModFurnaceBurning(Furnace furnace) {
        furnace.setCookTime((short) 0);
        furnace.setBurnTime((short) 0);
        furnaceNmsBridge.apply(furnace, false, 0, 0);
        furnace.removeMetadata(REFINING_METADATA, NewCustomCuiLianPro.ins);
    }

    private static boolean isFurnaceMaterial(Material material) {
        return material == Material.FURNACE || material == Material.BURNING_FURNACE;
    }

    private void scheduleCommitVerification(final Location location, final String owner,
            final ItemStack expectedResult) {
        Bukkit.getScheduler().runTask(NewCustomCuiLianPro.ins, new Runnable() {
            @Override
            public void run() {
                if (!(location.getBlock().getState() instanceof Furnace)) {
                    NewCustomCuiLianPro.ins.getLogger().warning("[CuiLianFurnaceDebug] stage=VERIFY"
                            + " location=" + describeLocation(location) + " furnaceMissing=true");
                    return;
                }
                Furnace furnace = (Furnace) location.getBlock().getState();
                ItemStack output = furnace.getInventory().getResult();
                boolean present = !isEmpty(output) && output.isSimilar(expectedResult);
                logFurnaceStage("VERIFY", furnace, owner,
                        furnace.getInventory().getSmelting(), furnace.getInventory().getFuel(), output,
                        "expectedPresent=" + present);
                for (HumanEntity viewer : furnace.getInventory().getViewers()) {
                    if (viewer instanceof Player) {
                        ((Player) viewer).updateInventory();
                    }
                }
            }
        });
    }

    private void logFurnaceStage(String stage, Furnace furnace, String owner,
            ItemStack input, ItemStack fuel, ItemStack output, String detail) {
        NewCustomCuiLianPro.ins.getLogger().info("[CuiLianFurnaceDebug] stage=" + stage
                + " player=" + (owner == null || owner.isEmpty() ? "<unknown>" : owner)
                + " location=" + describeLocation(furnace.getLocation())
                + " input=" + describeItem(input)
                + " fuel=" + describeItem(fuel)
                + " output=" + describeItem(output)
                + (detail == null || detail.isEmpty() ? "" : " " + detail));
    }

    private static String describeLocation(Location location) {
        return (location.getWorld() == null ? "<unknown>" : location.getWorld().getName())
                + ":" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private static String describeItem(ItemStack item) {
        if (isEmpty(item)) {
            return "<empty>";
        }
        return item.getTypeId() + ":" + item.getDurability() + "x" + item.getAmount();
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private static final class ModFurnaceProcess {

        private String owner;
        private int cookTicks;
        private ItemStack source;
        private String stoneId;
        private boolean started;
        private boolean burnStateLogged;

        private ModFurnaceProcess(String owner) {
            this.owner = owner == null ? "" : owner;
        }

        private boolean matches(ItemStack item, Stone stone) {
            return started && source != null && item != null && source.isSimilar(item)
                    && stone != null && stone.id.equals(stoneId);
        }

        private void capture(ItemStack item, Stone stone) {
            source = item.clone();
            stoneId = stone.id;
            cookTicks = 0;
            started = true;
            burnStateLogged = false;
        }

        private void reset() {
            source = null;
            stoneId = null;
            cookTicks = 0;
            started = false;
            burnStateLogged = false;
        }
    }

    /**
     * Updates only the live 1.7.10 furnace TileEntity counters and the vanilla
     * burning/unlit block state. It deliberately never calls CraftFurnace#update:
     * forcing a block-state snapshot update on Uranium can overwrite the live
     * inventory and was the cause of the old missing-output/stone-loss bug.
     */
    private static final class FurnaceNmsBridge {

        private static final String[] BURN_TIME_FIELDS = {
                "furnaceBurnTime", "field_145956_a"
        };
        private static final String[] CURRENT_BURN_TIME_FIELDS = {
                "currentItemBurnTime", "field_145963_i"
        };
        private static final String[] COOK_TIME_FIELDS = {
                "furnaceCookTime", "field_145961_j"
        };

        private Method getWorldHandleMethod;
        private Method updateFurnaceStateMethod;
        private Field inventoryHandleField;
        private Field burnTimeField;
        private Field currentBurnTimeField;
        private Field cookTimeField;
        private boolean failed;
        private boolean failureLogged;

        private boolean apply(Furnace furnace, boolean burning, int burnTicks, int cookTicks) {
            if (furnace == null || failed) {
                return false;
            }
            try {
                Location location = furnace.getLocation();
                Object nmsWorld = getNmsWorld(location);
                ensureBlockStateMethod(nmsWorld);
                // 在任何方块变更前确认所有反射字段可用。
                Object tileEntity = getLiveTileEntity(location);
                ensureTileFields(tileEntity);

                Material currentType = location.getBlock().getType();
                Material expectedType = burning ? Material.BURNING_FURNACE : Material.FURNACE;
                if (currentType != expectedType) {
                    updateFurnaceStateMethod.invoke(null, burning, nmsWorld,
                            location.getBlockX(), location.getBlockY(), location.getBlockZ());
                }

                burnTimeField.setInt(tileEntity, Math.max(0, burnTicks));
                currentBurnTimeField.setInt(tileEntity, burning ? MOD_SMELT_TICKS : 0);
                cookTimeField.setInt(tileEntity, Math.max(0, cookTicks));
                return true;
            } catch (Throwable throwable) {
                failed = true;
                if (!failureLogged) {
                    failureLogged = true;
                    NewCustomCuiLianPro.ins.getLogger().warning(
                            "[CuiLianFurnaceDebug] 无法写入 Uranium 实时熔炉燃烧状态，已回退到 Bukkit 计时: "
                                    + throwable.getClass().getName() + ": " + throwable.getMessage());
                }
                return false;
            }
        }

        private Object getNmsWorld(Location location) throws Exception {
            Object craftWorld = location.getWorld();
            if (getWorldHandleMethod == null) {
                getWorldHandleMethod = craftWorld.getClass().getMethod("getHandle");
                getWorldHandleMethod.setAccessible(true);
            }
            return getWorldHandleMethod.invoke(craftWorld);
        }

        private void ensureBlockStateMethod(Object nmsWorld) throws Exception {
            if (updateFurnaceStateMethod != null) {
                return;
            }
            Class<?> blockFurnaceClass = Class.forName("net.minecraft.block.BlockFurnace");
            for (Method method : blockFurnaceClass.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (Modifier.isStatic(method.getModifiers()) && parameters.length == 5
                        && parameters[0] == boolean.class
                        && parameters[1].isAssignableFrom(nmsWorld.getClass())
                        && parameters[2] == int.class && parameters[3] == int.class
                        && parameters[4] == int.class) {
                    method.setAccessible(true);
                    updateFurnaceStateMethod = method;
                    return;
                }
            }
            throw new NoSuchMethodException("BlockFurnace.updateFurnaceBlockState");
        }

        private Object getLiveTileEntity(Location location) throws Exception {
            Furnace liveFurnace = (Furnace) location.getBlock().getState();
            Object craftInventory = liveFurnace.getInventory();
            if (inventoryHandleField == null
                    || !inventoryHandleField.getDeclaringClass().isAssignableFrom(craftInventory.getClass())) {
                inventoryHandleField = findField(craftInventory.getClass(), "inventory");
            }
            Object tileEntity = inventoryHandleField.get(craftInventory);
            if (tileEntity == null || !tileEntity.getClass().getName().endsWith("TileEntityFurnace")) {
                throw new IllegalStateException("live TileEntityFurnace not found");
            }
            return tileEntity;
        }

        private void ensureTileFields(Object tileEntity) throws Exception {
            if (burnTimeField == null || !burnTimeField.getDeclaringClass().isAssignableFrom(tileEntity.getClass())) {
                burnTimeField = findField(tileEntity.getClass(), BURN_TIME_FIELDS);
                currentBurnTimeField = findField(tileEntity.getClass(), CURRENT_BURN_TIME_FIELDS);
                cookTimeField = findField(tileEntity.getClass(), COOK_TIME_FIELDS);
            }
        }

        private static Field findField(Class<?> type, String... names) throws NoSuchFieldException {
            Class<?> current = type;
            while (current != null) {
                for (String name : names) {
                    try {
                        Field field = current.getDeclaredField(name);
                        field.setAccessible(true);
                        return field;
                    } catch (NoSuchFieldException ignored) {
                        // Try the mapped/SRG name and then the superclass.
                    }
                }
                current = current.getSuperclass();
            }
            throw new NoSuchFieldException(type.getName() + " " + java.util.Arrays.toString(names));
        }
    }

    private static final class VanillaFurnaceProcess {

        private final Stone stone;
        private final ItemStack source;
        private final ItemStack fuelBefore;
        private boolean debitConfirmed;
        private int age;
        private String owner;

        private VanillaFurnaceProcess(Stone stone, ItemStack source, ItemStack fuel, String owner) {
            this.stone = stone;
            this.source = source.clone();
            this.fuelBefore = fuel.clone();
            this.owner = owner == null ? "" : owner;
        }

        private boolean matches(ItemStack item) {
            return item != null && item.getAmount() == 1 && source.isSimilar(item);
        }

        private boolean confirmDebit(ItemStack liveFuel) {
            if (!debitConfirmed) {
                debitConfirmed = fuelBefore.getAmount() == 1 ? isEmpty(liveFuel)
                        : !isEmpty(liveFuel) && fuelBefore.isSimilar(liveFuel)
                        && liveFuel.getAmount() == fuelBefore.getAmount() - 1;
            }
            return debitConfirmed;
        }
    }
}
