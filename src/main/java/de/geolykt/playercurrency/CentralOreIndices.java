package de.geolykt.playercurrency;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class CentralOreIndices implements Listener {

    @NotNull
    private final PlayerCurrency pl;

    @NotNull
    private final Set<ExactPosition> harvestedBlocks = new HashSet<>();

    public CentralOreIndices(@NotNull PlayerCurrency pl) {
        this.pl = pl;
        File f = new File(pl.getDataFolder(), "brokenblocks.dat");
        if (f.exists()) {
            try (FileInputStream fis = new FileInputStream(f)) {
                try (DataInputStream dis = new DataInputStream(fis)) {
                    while (dis.read() == 1) {
                        harvestedBlocks.add(new ExactPosition(new UUID(dis.readLong(), dis.readLong()),
                                dis.readInt(), dis.readInt(), dis.readInt()));
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read list of already broken blocks", e);
            }
        }
    }

    public void saveBrokenBlocksList() {
        File f = new File(pl.getDataFolder(), "brokenblocks.dat");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            try (DataOutputStream dos = new DataOutputStream(fos)) {
                for (ExactPosition pos : harvestedBlocks) {
                    dos.write(1);
                    dos.writeLong(pos.world().getMostSignificantBits());
                    dos.writeLong(pos.world().getLeastSignificantBits());
                    dos.writeInt(pos.x());
                    dos.writeInt(pos.y());
                    dos.writeInt(pos.z());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEvent(BlockDropItemEvent evt) {
        Material m = evt.getBlockState().getType();
        UUID cuid = null;
        if (m == Material.IRON_ORE || m == Material.DEEPSLATE_IRON_ORE) {
            cuid = PlayerCurrency.CENTRAL_IRON_INDEX;
        } else if (m == Material.DIAMOND_ORE || m == Material.DEEPSLATE_DIAMOND_ORE) {
            cuid = PlayerCurrency.CENTRAL_DIAMOND_INDEX;
        } else if (m == Material.COPPER_ORE || m == Material.DEEPSLATE_COPPER_ORE) {
            cuid = PlayerCurrency.CENTRAL_COPPER_INDEX;
        }
        Block block = evt.getBlock();
        if (cuid != null && !harvestedBlocks.contains(new ExactPosition(block.getWorld().getUID(),
                block.getX(), block.getY(), block.getZ()))) {
            // Only pay out cash if the player is not holding a silk touch pickaxe, as otherwise there might
            // be some exploit
            ItemStack usedItemStack = evt.getPlayer().getItemInUse();
            Enchantment silkTouch = Enchantment.SILK_TOUCH;
            if (silkTouch != null && usedItemStack != null && usedItemStack.containsEnchantment(silkTouch)) {
                return;
            }
            Currency c = pl.getCurrency(cuid);
            if (c == null) {
                return;
            }
            pl.addBalanceNoLog(evt.getPlayer().getUniqueId(), c, 1L);
            invalidateBlock(block);
        }
    }

    private void invalidateBlock(Block block) {
        harvestedBlocks.add(new ExactPosition(block.getWorld().getUID(),
                block.getX(), block.getY(), block.getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockMove(BlockPistonExtendEvent evt) {
        for (Block b : evt.getBlocks()) {
            invalidateBlock(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockMove(BlockPistonRetractEvent evt) {
        for (Block b : evt.getBlocks()) {
            invalidateBlock(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent evt) {
        invalidateBlock(evt.getBlockPlaced());
    }
} record ExactPosition(UUID world, int x, int y, int z) { }
