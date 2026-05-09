package com.rique.ruinedcollections.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class DetailMenuHolder implements InventoryHolder {
    private final String collectionId;
    private Inventory inventory;

    public DetailMenuHolder(String collectionId) {
        this.collectionId = collectionId;
    }

    public String collectionId() {
        return collectionId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
