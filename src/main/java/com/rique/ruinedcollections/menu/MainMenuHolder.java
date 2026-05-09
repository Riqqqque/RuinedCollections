package com.rique.ruinedcollections.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class MainMenuHolder implements InventoryHolder {
    private final int page;
    private final Map<Integer, String> collectionSlots = new HashMap<>();
    private Inventory inventory;

    public MainMenuHolder(int page) {
        this.page = page;
    }

    public int page() {
        return page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void setCollection(int slot, String collectionId) {
        collectionSlots.put(slot, collectionId);
    }

    public String collectionAt(int slot) {
        return collectionSlots.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
