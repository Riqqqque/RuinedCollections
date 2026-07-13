package com.rique.ruinedcollections.storage;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ImportPreview(List<ProgressRow> progressRows, List<ClaimedRow> claimedRows, List<PlayerNameRow> playerNames) {
    public Set<UUID> playerIds() {
        Set<UUID> ids = new LinkedHashSet<>();
        for (ProgressRow row : progressRows) {
            ids.add(row.playerId());
        }
        for (ClaimedRow row : claimedRows) {
            ids.add(row.playerId());
        }
        for (PlayerNameRow row : playerNames) {
            ids.add(row.playerId());
        }
        return ids;
    }
}
