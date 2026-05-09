package com.rique.ruinedcollections.storage;

import java.util.UUID;

public record ClaimedRow(UUID playerId, String collectionId, String tierId) {
}
