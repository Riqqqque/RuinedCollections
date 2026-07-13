package com.rique.ruinedcollections.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class DetailMenuHolder implements InventoryHolder {
    private final UUID ownerId;
    private final String collectionId;
    private final int page;
    private Inventory inventory;

    public DetailMenuHolder(UUID ownerId, String collectionId, int page) {
        this.ownerId = ownerId;
        this.collectionId = collectionId;
        this.page = page;
    }

    public UUID ownerId() {
        return ownerId;
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
