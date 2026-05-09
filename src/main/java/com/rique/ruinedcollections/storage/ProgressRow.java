package com.rique.ruinedcollections.storage;

import java.util.UUID;

public record ProgressRow(UUID playerId, String collectionId, long progress) {
}
