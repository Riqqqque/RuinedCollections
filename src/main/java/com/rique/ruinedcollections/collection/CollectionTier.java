package com.rique.ruinedcollections.collection;

import com.rique.ruinedcollections.reward.RewardAction;

import java.util.List;

public record CollectionTier(String id, long goal, List<RewardAction> rewards) {
}
