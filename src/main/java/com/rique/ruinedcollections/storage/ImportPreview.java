package com.rique.ruinedcollections.storage;

import java.util.List;

public record ImportPreview(List<ProgressRow> progressRows, List<ClaimedRow> claimedRows, List<PlayerNameRow> playerNames) {
}
