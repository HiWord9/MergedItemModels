package com.hiword9.merged_item_models;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.PlaceholderLookupProvider;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class MergingContext {
    private static final ThreadLocal<MergingContext> CURRENT = new ThreadLocal<>();

    public final List<Resource> resources;
    public final Identifier identifier;
    public final PlaceholderLookupProvider lookupProvider;
    private int layer = 0;

    public MergingContext(
            List<Resource> resources,
            Identifier identifier,
            PlaceholderLookupProvider lookupProvider
    ) {
        this.resources = Collections.unmodifiableList(resources);
        this.identifier = identifier;
        this.lookupProvider = lookupProvider;
    }

    public static @Nullable MergingContext current() {
        return CURRENT.get();
    }

    public @Nullable ClientItem load(DynamicOps<JsonElement> ops) {
        CURRENT.set(this);
        try {
            return MergedClientItemInfoLoader.getClientItem(ops, this);
        } finally {
            CURRENT.remove();
        }
    }

    public @Nullable Resource push() {
        if (layer >= resources.size()) return null;
        layer++;
        return getCurrentResource();
    }

    public void pop() {
        layer--;
    }

    public Resource getCurrentResource() {
        return resources.get(resources.size() - layer);
    }
}
