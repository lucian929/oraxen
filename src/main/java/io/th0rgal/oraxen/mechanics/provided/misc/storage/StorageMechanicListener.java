package io.th0rgal.oraxen.mechanics.provided.misc.storage;

import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.config.Message;
import io.th0rgal.oraxen.items.OraxenItems;
import io.th0rgal.oraxen.utils.Utils;
import io.th0rgal.oraxen.mechanics.provided.misc.storage.StorageMechanic;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.apache.commons.lang.ArrayUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;

public class StorageMechanicListener implements Listener {

    private final StorageMechanicFactory factory;

    public StorageMechanicListener(StorageMechanicFactory factory) {
        this.factory = factory;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if ((event.getAction() != Action.RIGHT_CLICK))
            return;
        ItemStack item = event.getItem();
        String id = OraxenItems.getIdByItem(item);
        if (factory.isNotImplementedIn(id)) {
            return;
        }
        if (item.getAmount() > 1 || item.getAmount() < 1) {
            Message.MECHANICS_STORAGE.send(event.getPlayer());
            return;
        }
        event.setCancelled(true);
        StorageMechanic mechanic = (StorageMechanic) factory.getMechanic(id);
        //Initilize GUI
        ItemMeta im = is.getItemMeta();
        item.setItemMeta(im);
        ArrayList<ItemStack> contents = get(is);
        Inventory storage = Bukkit.createInventory(event.getPlayer(), (mechanic.getRows() * 9), mechanic.getTitle());
        ArrayList<ItemStack> itemOverflow = new ArrayList<>();
        for (ItemStack itemStack : contents) {
            if (storage.addItem(itemStack).isEmpty()) continue;
                itemOverflow.add(itemStack);
            }
            for (ItemStack itemStack : itemOverflow) {
                 event.getPlayer().getWorld().dropItem(event.getPlayer().getLocation(), itemStack);
            }
        event.getPlayer().openInventory(storage);
        //End of GUI Initialization
        if(mechanic.hasSound())
            event.getPlayer().playSound(event.getPlayer().getLocation(), mechanic.getSound(), 1.0, 1.0);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory dummyInventory = Bukkit.createInventory(event.getPlayer(), 54, "");
        Arrays.stream(event.getInventory().getContents()).filter(Objects::nonNull).forEach(dummyInventory::addItem);
        ArrayList<ItemStack> tidiedContents = new ArrayList<>();
        Arrays.stream(dummyInventory.getContents()).filter(Objects::nonNull).forEach(tidiedContents::add);
        store(event.getPlayer().getInventory().getItemInMainHand(), tidiedContents);
    }

    public static void store(ItemStack storage, List<ItemStack> contents) {
        if (!storage.hasItemMeta()) return;
        ItemMeta itemMeta = storage.getItemMeta();
        PersistentDataContainer data = itemMeta.getPersistentDataContainer();
        if (!data.has(new NamespacedKey(OraxenPlugin.get(), "oraxen"), PersistentDataType.STRING)) {
            data.set(new NamespacedKey(OraxenPlugin.get(), "oraxen"), PersistentDataType.STRING, "");
        }
        if (contents.size() == 0) {
            data.set(new NamespacedKey(OraxenPlugin.get(), "oraxen"), PersistentDataType.STRING, "");
            ArrayList<String> lore = new ArrayList<>();
            storage.setItemMeta(itemMeta);
            return;
        }
    }

    public static ArrayList<ItemStack> get(ItemStack storage) {
        if (!storage.hasItemMeta()) new ArrayList<ItemStack>();
        ItemMeta itemMeta = storage.getItemMeta();
        PersistentDataContainer data = itemMeta.getPersistentDataContainer();
        ArrayList<ItemStack> items = new ArrayList<>();
        String encodedItems = data.get(new NamespacedKey(OraxenPlugin.get(), "oraxen"), PersistentDataType.STRING);
        if (!encodedItems.isEmpty()) {
            byte[] rawData = Base64.getDecoder().decode(encodedItems);
            try {
                ByteArrayInputStream io = new ByteArrayInputStream(rawData);
                BukkitObjectInputStream in = new BukkitObjectInputStream(io);
                int count = in.readInt();
                for (int i = 0; i < count; i++) { items.add((ItemStack) in.readObject()); }
                in.close();
            } catch (Exception ex) { ex.printStackTrace(); }
        }
        return items;
    }
}