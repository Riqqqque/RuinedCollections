package com.rique.ruinedcollections.storage;

import java.util.UUID;

public record LeaderboardRow(UUID playerId, long progress) {
}
