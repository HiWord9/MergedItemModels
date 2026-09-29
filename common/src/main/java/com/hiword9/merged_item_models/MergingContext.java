package com.hiword9.merged_item_models;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.PlaceholderLookupProvider;
import net.minecraft.util.StrictJsonParser;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.minecraft.client.resources.model.ClientItemInfoLoader.LOGGER;

public class MergingContext {
    private static final ThreadLocal<MergingContext> CURRENT = new ThreadLocal<>();

    private final Identifier itemId;
    private final List<Resource> resources;
    private final PlaceholderLookupProvider lookupProvider;
    private final DynamicOps<JsonElement> ops;
    private final Map<Integer, Optional<ClientItem>> layers = new HashMap<>();
    private int currentLayer;

    public MergingContext(Identifier itemId, List<Resource> resources, RegistryAccess registryAccess) {
        this.itemId = itemId;
        this.resources = resources;
        this.lookupProvider = new PlaceholderLookupProvider(registryAccess);
        this.ops = lookupProvider.createSerializationContext(JsonOps.INSTANCE);
    }

    public static @Nullable MergingContext current() {
        return CURRENT.get();
    }

    public Identifier itemId() {
        return itemId;
    }

    public Optional<ClientItem> load() {
        CURRENT.set(this);
        try {
            return layer(resources.size() - 1).map(this::withRegistrySwapper);
        } finally {
            CURRENT.remove();
        }
    }

    public Optional<ItemModel.Unbaked> lowerModel() {
        if (currentLayer == 0) return Optional.empty();
        return layer(currentLayer - 1).map(ClientItem::model);
    }

    private Optional<ClientItem> layer(int index) {
        // not computeIfAbsent: parsing a layer may parse the layers below it
        var clientItem = layers.get(index);
        if (clientItem == null) {
            clientItem = parse(index);
            layers.put(index, clientItem);
        }
        return clientItem;
    }

    private Optional<ClientItem> parse(int index) {
        var resource = resources.get(index);
        var outerLayer = currentLayer;
        currentLayer = index;
        try (Reader reader = resource.openAsReader()) {
            return ClientItem.CODEC
                    .parse(ops, StrictJsonParser.parse(reader))
                    .ifError(
                            error -> LOGGER.error(
                                    "Couldn't parse item model '{}' from pack '{}': {}",
                                    itemId,
                                    resource.sourcePackId(),
                                    error.message()
                            )
                    )
                    .result();
        } catch (Exception exception) {
            LOGGER.error("Failed to open item model {} from pack '{}'", itemId, resource.sourcePackId(), exception);
            return Optional.empty();
        } finally {
            currentLayer = outerLayer;
        }
    }

    private ClientItem withRegistrySwapper(ClientItem clientItem) {
        return lookupProvider.hasRegisteredPlaceholders()
                ? clientItem.withRegistrySwapper(lookupProvider.createSwapper())
                : clientItem;
    }
}
