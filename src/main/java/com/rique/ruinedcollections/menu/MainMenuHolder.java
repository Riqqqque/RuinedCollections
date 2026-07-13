package com.rique.ruinedcollections.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MainMenuHolder implements InventoryHolder {
    private final UUID ownerId;
    private final int page;
    private final Map<Integer, String> collectionSlots = new HashMap<>();
    private Inventory inventory;

    public MainMenuHolder(UUID ownerId, int page) {
        this.ownerId = ownerId;
        this.page = page;
    }

    public UUID ownerId() {
        return ownerId;
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
