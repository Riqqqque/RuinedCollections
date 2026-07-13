package com.rique.ruinedcollections.storage;

import java.util.List;

public record SnapshotData(
        List<ProgressRow> progressRows,
        List<ClaimedRow> claimedRows,
        List<PlayerNameRow> playerNames
) {
    public SnapshotData {
        progressRows = List.copyOf(progressRows);
        claimedRows = List.copyOf(claimedRows);
        playerNames = List.copyOf(playerNames);
    }
}
