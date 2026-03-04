package com.hiword9.merged_item_models;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.PlaceholderLookupProvider;

import java.util.Collections;
import java.util.List;

public class MergingContext {
    public final List<Resource> resources;
    public final Identifier identifier;
    public final PlaceholderLookupProvider lookupProvider;
    protected int layer = 0;

    public MergingContext(
            List<Resource> resources,
            Identifier identifier,
            PlaceholderLookupProvider lookupProvider
    ) {
        this.resources = Collections.unmodifiableList(resources);
        this.identifier = identifier;
        this.lookupProvider = lookupProvider;
    }

    public Resource push() {
        if (layer + 1 > resources.size()) return null;
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
