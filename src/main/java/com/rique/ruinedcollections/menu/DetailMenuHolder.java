package com.rique.ruinedcollections.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class DetailMenuHolder implements InventoryHolder {
    private final String collectionId;
    private final int page;
    private Inventory inventory;

    public DetailMenuHolder(String collectionId, int page) {
        this.collectionId = collectionId;
        this.page = page;
    }

    public String collectionId() {
        return collectionId;
    }

    public int page() {
        return page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
