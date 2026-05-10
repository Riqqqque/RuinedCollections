package com.rique.ruinedcollections.leaderboard;

import java.util.UUID;

public record LeaderboardEntry(UUID playerId, String name, long progress) {
}
