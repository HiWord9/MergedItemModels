package com.hiword9.merged_item_models;

import com.google.common.collect.MapMaker;
import net.minecraft.client.renderer.item.ItemModel;

import java.util.Map;
import java.util.Optional;

public final class SourcePacks {
    // weak keys are compared by identity, so equal models from different packs stay apart
    private static final Map<ItemModel.Unbaked, Optional<String>> PACKS = new MapMaker().weakKeys().makeMap();

    private SourcePacks() {}

    public static Optional<String> get(ItemModel.Unbaked model) {
        return PACKS.getOrDefault(model, Optional.empty());
    }

    static void put(ItemModel.Unbaked model, String packId) {
        // a model instance shared between packs belongs to none of them
        PACKS.merge(model, Optional.of(packId), (current, pack) -> current.equals(pack) ? current : Optional.empty());
    }
}
