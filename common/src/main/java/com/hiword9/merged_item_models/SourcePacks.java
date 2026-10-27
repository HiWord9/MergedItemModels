package com.hiword9.merged_item_models;

import com.google.common.collect.MapMaker;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.server.packs.PackLocationInfo;

import java.util.Map;
import java.util.Optional;

public final class SourcePacks {
    // weak keys are compared by identity, so equal models from different packs stay apart
    private static final Map<ItemModel.Unbaked, Optional<PackLocationInfo>> PACKS = new MapMaker().weakKeys().makeMap();

    private SourcePacks() {}

    public static Optional<PackLocationInfo> get(ItemModel.Unbaked model) {
        return PACKS.getOrDefault(model, Optional.empty());
    }

    static void put(ItemModel.Unbaked model, PackLocationInfo pack) {
        // a model instance shared between packs belongs to none of them
        PACKS.merge(model, Optional.of(pack), (current, added) ->
                current.map(PackLocationInfo::id).equals(added.map(PackLocationInfo::id)) ? current : Optional.empty());
    }
}
