package com.hiword9.merged_item_models;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModels;
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
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static net.minecraft.client.resources.model.ClientItemInfoLoader.LOGGER;

public class MergingContext {
    private static final ThreadLocal<MergingContext> CURRENT = new ThreadLocal<>();

    private final Identifier itemId;
    private final List<Resource> resources;
    private final PlaceholderLookupProvider lookupProvider;
    private final DynamicOps<JsonElement> ops;
    private final Map<Integer, Optional<ClientItem>> layers = new HashMap<>();
    private int currentLayer;
    private boolean merging = true;

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
            return layerFrom(resources.size() - 1).map(this::withRegistrySwapper);
        } finally {
            CURRENT.remove();
        }
    }

    public boolean isMerging() {
        return merging && currentLayer > 0;
    }

    public <T> T unmerged(Supplier<T> action) {
        var outerMerging = merging;
        merging = false;
        try {
            return action.get();
        } finally {
            merging = outerMerging;
        }
    }

    public Optional<ItemModel.Unbaked> lowerModel() {
        return layerFrom(currentLayer - 1).map(ClientItem::model);
    }

    // compared in encoded form, as some models hold runtime state and don't implement equals
    public boolean isSameDefinition(ItemModel.Unbaked first, ItemModel.Unbaked second) {
        var encoded = encode(first);
        return encoded.isPresent() && encoded.equals(encode(second));
    }

    public Optional<ItemModel.Unbaked> baseModel() {
        return IntStream.range(0, currentLayer)
                .mapToObj(this::layer)
                .flatMap(Optional::stream)
                .findFirst()
                .map(ClientItem::model);
    }

    private Optional<ClientItem> layerFrom(int index) {
        return IntStream.iterate(index, i -> i >= 0, i -> i - 1)
                .mapToObj(this::layer)
                .flatMap(Optional::stream)
                .findFirst();
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

    private Optional<JsonElement> encode(ItemModel.Unbaked model) {
        return ItemModels.CODEC.encodeStart(ops, model).result();
    }

    private ClientItem withRegistrySwapper(ClientItem clientItem) {
        return lookupProvider.hasRegisteredPlaceholders()
                ? clientItem.withRegistrySwapper(lookupProvider.createSwapper())
                : clientItem;
    }
}
