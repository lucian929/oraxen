package io.th0rgal.oraxen.mechanics.provided.gameplay.noteblock;

import com.jeff_media.customblockdata.CustomBlockData;
import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.compatibilities.provided.lightapi.WrappedLightAPI;
import io.th0rgal.oraxen.events.OraxenNoteBlockBreakEvent;
import io.th0rgal.oraxen.events.OraxenNoteBlockInteractEvent;
import io.th0rgal.oraxen.items.OraxenItems;
import io.th0rgal.oraxen.mechanics.MechanicFactory;
import io.th0rgal.oraxen.mechanics.provided.gameplay.noteblock.directional.DirectionalBlock;
import io.th0rgal.oraxen.utils.Utils;
import io.th0rgal.oraxen.utils.breaker.BreakerSystem;
import io.th0rgal.oraxen.utils.breaker.HardnessModifier;
import io.th0rgal.protectionlib.ProtectionLib;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

import static io.th0rgal.oraxen.mechanics.provided.gameplay.noteblock.NoteBlockMechanic.FARMBLOCK_KEY;

public class NoteBlockMechanicListener implements Listener {
    private final MechanicFactory factory;

    public NoteBlockMechanicListener(final NoteBlockMechanicFactory factory) {
        this.factory = factory;
        BreakerSystem.MODIFIERS.add(getHardnessModifier());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonPush(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> block.getType().equals(Material.NOTE_BLOCK)))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonPull(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> block.getType().equals(Material.NOTE_BLOCK)))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(final BlockPhysicsEvent event) {
        final Block aboveBlock = event.getBlock().getRelative(BlockFace.UP);
        if (aboveBlock.getType() == Material.NOTE_BLOCK) {
            updateAndCheck(event.getBlock().getLocation());
            event.setCancelled(true);
        }
        if (event.getBlock().getType() == Material.NOTE_BLOCK) {
            event.setCancelled(true);
            event.getBlock().getState().update(true, false);
        }
    }

    public void updateAndCheck(final Location loc) {
        final Block block = loc.add(0, 1, 0).getBlock();
        if (block.getType() == Material.NOTE_BLOCK)
            block.getState().update(true, true);
        final Location nextBlock = block.getLocation().add(0, 1, 0);
        if (nextBlock.getBlock().getType() == Material.NOTE_BLOCK)
            updateAndCheck(block.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || block.getType() != Material.NOTE_BLOCK)
            return;

        NoteBlockMechanic noteBlockMechanic = getNoteBlockMechanic(block);

        if (noteBlockMechanic != null) {
            if (noteBlockMechanic.isDirectional())
                noteBlockMechanic = (NoteBlockMechanic) factory.getMechanic(noteBlockMechanic.getDirectional().getParentBlock());

            OraxenNoteBlockInteractEvent noteBlockInteractEvent = new OraxenNoteBlockInteractEvent(noteBlockMechanic, block, event.getItem(), event.getPlayer());
            OraxenPlugin.get().getServer().getPluginManager().callEvent(noteBlockInteractEvent);
            if (noteBlockInteractEvent.isCancelled()) {
                event.setCancelled(true);
                return;
            }
            noteBlockMechanic.runClickActions(player);
        }

        event.setCancelled(true);
        if (item == null) return;

        Block above = block.getRelative(event.getBlockFace());
        Material type = item.getType();
        if (type == Material.AIR) return;
        if (type == Material.BUCKET && above.isLiquid()) {
            final Sound sound;
            if (above.getType() == Material.WATER) sound = Sound.ITEM_BUCKET_FILL;
            else sound = Sound.valueOf("ITEM_BUCKET_FILL_" + above.getType());

            if (player.getGameMode() != GameMode.CREATIVE)
                item.setType(Material.getMaterial(above.getType() + "_BUCKET"));

            player.playSound(above.getLocation(), sound, 1.0f, 1.0f);
            above.setType(Material.AIR, true);
            return;
        }

        final boolean bucketCheck = type.toString().endsWith("_BUCKET");
        final String bucketBlock = type.toString().replace("_BUCKET", "");
        EntityType bucketEntity;
        try {
            bucketEntity = EntityType.valueOf(bucketBlock);
        } catch (IllegalArgumentException e) {
            bucketEntity = null;
        }

        if (bucketCheck && type != Material.MILK_BUCKET) {
            if (bucketEntity == null)
                type = Material.getMaterial(bucketBlock);
            else {
                type = Material.WATER;
                player.getWorld().spawnEntity(above.getLocation().add(0.5, 0.0, 0.5), bucketEntity);
            }
        }

        if (type.isBlock())
            makePlayerPlaceBlock(player, event.getHand(), item, block, event.getBlockFace(), Bukkit.createBlockData(type));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onNotePlayed(final NotePlayEvent event) {
        if (event.getInstrument() != Instrument.PIANO)
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreakingCustomBlock(final BlockBreakEvent event) {
        final Block block = event.getBlock();
        if (block.getType() != Material.NOTE_BLOCK || event.isCancelled() || !event.isDropItems())
            return;
        NoteBlockMechanic noteBlockMechanic = getNoteBlockMechanic(block);
        if (noteBlockMechanic == null)
            return;
        if (noteBlockMechanic.isDirectional())
            noteBlockMechanic = (NoteBlockMechanic) factory.getMechanic(noteBlockMechanic.getDirectional().getParentBlock());
        final Player player = event.getPlayer();
        if (block.getType() != Material.NOTE_BLOCK || event.isCancelled() || !event.isDropItems()) return;
        if (noteBlockMechanic == null) return;

        OraxenNoteBlockBreakEvent noteBlockBreakEvent = new OraxenNoteBlockBreakEvent(noteBlockMechanic, block, player);
        OraxenPlugin.get().getServer().getPluginManager().callEvent(noteBlockBreakEvent);
        if (noteBlockBreakEvent.isCancelled()) {
            event.setCancelled(true);
            return;
        }

        if (noteBlockMechanic.hasBreakSound())
            block.getWorld().playSound(block.getLocation(), noteBlockMechanic.getBreakSound(), 1.0f, 0.8f);
        if (noteBlockMechanic.getLight() != -1)
            WrappedLightAPI.removeBlockLight(block.getLocation());
        if (player.getGameMode() != GameMode.CREATIVE)
            noteBlockMechanic.getDrop().spawns(block.getLocation(), player.getInventory().getItemInMainHand());
        event.setDropItems(false);
    }

    @EventHandler
    public void onExplosionDestroy(EntityExplodeEvent event) {
        List<Block> blockList = event.blockList().stream().filter(block -> block.getType().equals(Material.NOTE_BLOCK)).toList();
        blockList.forEach(block -> {
            NoteBlockMechanic noteBlockMechanic = getNoteBlockMechanic(block);
            if (noteBlockMechanic == null)
                return;
            if (noteBlockMechanic.isDirectional())
                noteBlockMechanic = (NoteBlockMechanic) factory.getMechanic(noteBlockMechanic.getDirectional().getParentBlock());

            noteBlockMechanic.getDrop().spawns(block.getLocation(), new ItemStack(Material.AIR));
            block.setType(Material.AIR, false);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlacingBlock(final BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.NOTE_BLOCK
                || OraxenItems.exists(OraxenItems.getIdByItem(event.getItemInHand())))
            return;

        event.getBlock().setBlockData(Bukkit.createBlockData(Material.NOTE_BLOCK), false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrePlacingCustomBlock(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        final ItemStack item = event.getItem();
        final String itemID = OraxenItems.getIdByItem(item);
        if (factory.isNotImplementedIn(itemID))
            return;

        final Player player = event.getPlayer();
        final Block placedAgainst = event.getClickedBlock();

        // determines the new block data of the block
        NoteBlockMechanic mechanic = (NoteBlockMechanic) factory.getMechanic(itemID);
        int customVariation = mechanic.getCustomVariation();
        BlockFace face = event.getBlockFace();

        if (mechanic.isDirectional() && mechanic.getDirectional().isParentBlock()) {
            DirectionalBlock directional = mechanic.getDirectional();

            if (face == BlockFace.NORTH || face == BlockFace.SOUTH)
                customVariation = ((NoteBlockMechanic) factory.getMechanic(directional.getXBlock())).getCustomVariation();
            else if (face == BlockFace.WEST || face == BlockFace.EAST)
                customVariation = ((NoteBlockMechanic) factory.getMechanic(directional.getZBlock())).getCustomVariation();
            else if (face == BlockFace.UP || face == BlockFace.DOWN)
                customVariation = ((NoteBlockMechanic) factory.getMechanic(directional.getYBlock())).getCustomVariation();
        }

        assert placedAgainst != null;
        Block placedBlock = makePlayerPlaceBlock(player, event.getHand(), event.getItem(),
                placedAgainst, face, NoteBlockMechanicFactory.createNoteBlockData(customVariation));
        if (placedBlock != null) {
            if (mechanic.hasPlaceSound())
                placedBlock.getWorld().playSound(placedBlock.getLocation(), mechanic.getPlaceSound(), 1.0f, 0.8f);

            if (mechanic.getLight() != -1)
                WrappedLightAPI.createBlockLight(placedBlock.getLocation(), mechanic.getLight());
            event.setCancelled(true);
            if (mechanic.hasDryout() && mechanic.getDryout().isFarmBlock()) {
                final PersistentDataContainer customBlockData = new CustomBlockData(placedBlock, OraxenPlugin.get());
                customBlockData.set(FARMBLOCK_KEY, PersistentDataType.STRING, mechanic.getItemID());
            }
        }
    }

    private HardnessModifier getHardnessModifier() {
        return new HardnessModifier() {

            @Override
            public boolean isTriggered(final Player player, final Block block, final ItemStack tool) {
                if (block.getType() != Material.NOTE_BLOCK)
                    return false;

                NoteBlockMechanic noteBlockMechanic = getNoteBlockMechanic(block);
                if (noteBlockMechanic.isDirectional())
                    noteBlockMechanic = (NoteBlockMechanic) factory.getMechanic(noteBlockMechanic.getDirectional().getParentBlock());

                return noteBlockMechanic != null && noteBlockMechanic.hasHardness;
            }

            @Override
            public void breakBlock(final Player player, final Block block, final ItemStack tool) {
                block.setType(Material.AIR);
            }

            @Override
            public long getPeriod(final Player player, final Block block, final ItemStack tool) {
                NoteBlockMechanic noteBlockMechanic = getNoteBlockMechanic(block);
                if (noteBlockMechanic.isDirectional())
                    noteBlockMechanic = (NoteBlockMechanic) factory.getMechanic(noteBlockMechanic.getDirectional().getParentBlock());

                final long period = noteBlockMechanic.getPeriod();
                double modifier = 1;
                if (noteBlockMechanic.getDrop().canDrop(tool)) {
                    modifier *= 0.4;
                    final int diff = noteBlockMechanic.getDrop().getDiff(tool);
                    if (diff >= 1)
                        modifier *= Math.pow(0.9, diff);
                }
                return (long) (period * modifier);
            }
        };
    }

    private boolean isStandingInside(final Player player, final Block block) {
        final Location playerLocation = player.getLocation();
        final Location blockLocation = block.getLocation();
        return playerLocation.getBlockX() == blockLocation.getBlockX()
                && (playerLocation.getBlockY() == blockLocation.getBlockY()
                || playerLocation.getBlockY() + 1 == blockLocation.getBlockY())
                && playerLocation.getBlockZ() == blockLocation.getBlockZ();
    }

    private Block makePlayerPlaceBlock(final Player player, final EquipmentSlot hand, final ItemStack item,
                                       final Block placedAgainst, final BlockFace face, final BlockData newBlock) {
        final Block target;
        final Sound sound;
        final Material type = placedAgainst.getType();

        if (Utils.REPLACEABLE_BLOCKS.contains(type))
            target = placedAgainst;

        else {
            target = placedAgainst.getRelative(face);
            if (!target.getType().isAir() && !target.isLiquid() && target.getType() != Material.LIGHT) return null;
        }

        if (isStandingInside(player, target) || !ProtectionLib.canBuild(player, target.getLocation())) return null;

        final BlockData curentBlockData = target.getBlockData();
        final boolean isFlowing = (newBlock.getMaterial() == Material.WATER || newBlock.getMaterial() == Material.LAVA);
        target.setBlockData(newBlock, isFlowing);
        final BlockState currentBlockState = target.getState();

        final BlockPlaceEvent blockPlaceEvent = new BlockPlaceEvent(target, currentBlockState, placedAgainst, item, player, true, hand);
        Bukkit.getPluginManager().callEvent((Event) blockPlaceEvent);

        if (!blockPlaceEvent.canBuild() || blockPlaceEvent.isCancelled()) {
            target.setBlockData(curentBlockData, false); // false to cancel physic
            return null;
        }

        if (isFlowing) {
            if (newBlock.getMaterial() == Material.WATER) sound = Sound.ITEM_BUCKET_EMPTY;
            else sound = Sound.valueOf("ITEM_BUCKET_EMPTY_" + newBlock.getMaterial());
        } else sound = newBlock.getSoundGroup().getPlaceSound();

        if (!player.getGameMode().equals(GameMode.CREATIVE)) {
            if (item.getType().toString().toLowerCase().contains("bucket")) item.setType(Material.BUCKET);
            else item.setAmount(item.getAmount() - 1);
        }

        player.playSound(target.getLocation(), sound, 1.0F, 1.0F);
        Utils.sendAnimation(player, hand);

        return target;
    }

    public static NoteBlockMechanic getNoteBlockMechanic(Block block) {
        final NoteBlock noteBlok = (NoteBlock) block.getBlockData();
        return NoteBlockMechanicFactory
                .getBlockMechanic((int) (noteBlok.getInstrument().getType()) * 25
                        + (int) noteBlok.getNote().getId() + (noteBlok.isPowered() ? 400 : 0) - 26);
    }
}
