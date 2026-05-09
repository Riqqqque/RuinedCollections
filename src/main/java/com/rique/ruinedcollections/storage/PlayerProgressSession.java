package com.rique.ruinedcollections.storage;

import com.rique.ruinedcollections.util.Longs;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerProgressSession {
    private final Map<String, Long> progress = new ConcurrentHashMap<>();
    private final Set<ClaimKey> claimedTiers = ConcurrentHashMap.newKeySet();
    private final Set<ClaimKey> claimingTiers = ConcurrentHashMap.newKeySet();

    public PlayerProgressSession(PlayerData data) {
        progress.putAll(data.progress());
        claimedTiers.addAll(data.claimedTiers());
    }

    public long progress(String collectionId) {
        return progress.getOrDefault(collectionId, 0L);
    }

    public long addProgress(String collectionId, long amount) {
        return progress.merge(collectionId, Math.max(0, amount), Longs::addClamped);
    }

    public void setProgress(String collectionId, long amount) {
        progress.put(collectionId, Math.max(0, amount));
    }

    public boolean hasClaimed(String collectionId, String tierId) {
        return claimedTiers.contains(new ClaimKey(collectionId, tierId));
    }

    public boolean startClaim(String collectionId, String tierId) {
        ClaimKey key = new ClaimKey(collectionId, tierId);
        return !claimedTiers.contains(key) && claimingTiers.add(key);
    }

    public void finishClaim(String collectionId, String tierId) {
        ClaimKey key = new ClaimKey(collectionId, tierId);
        claimingTiers.remove(key);
        claimedTiers.add(key);
    }

    public void cancelClaim(String collectionId, String tierId) {
        claimingTiers.remove(new ClaimKey(collectionId, tierId));
    }
}
