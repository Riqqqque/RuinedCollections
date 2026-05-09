package com.rique.ruinedcollections.storage;

import java.util.Map;
import java.util.Set;

public record PlayerData(Map<String, Long> progress, Set<ClaimKey> claimedTiers) {
}
