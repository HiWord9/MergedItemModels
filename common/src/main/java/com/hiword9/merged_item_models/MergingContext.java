package com.hiword9.merged_item_models;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
    private static final List<String> PROPERTY_KEYS = ClientItem.Properties.MAP_CODEC.keys(JsonOps.INSTANCE)
            .map(JsonElement::getAsString)
            .toList();

    private final Identifier itemId;
    private final List<Resource> resources;
    private final PlaceholderLookupProvider lookupProvider;
    private final DynamicOps<JsonElement> ops;
    private final Map<Integer, Optional<Layer>> layers = new HashMap<>();
    private int currentLayer;
    private boolean mergedLower;
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
            return layerFrom(resources.size() - 1).map(layer -> withRegistrySwapper(layer.clientItem()));
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

    public Optional<ItemModel.Unbaked> mergeLowerModel() {
        var lower = layerFrom(currentLayer - 1);
        if (lower.isPresent()) mergedLower = true;
        return lower.map(layer -> layer.clientItem().model());
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
                .map(layer -> layer.clientItem().model());
    }

    private Optional<Layer> layerFrom(int index) {
        return IntStream.iterate(index, i -> i >= 0, i -> i - 1)
                .mapToObj(this::layer)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<Layer> layer(int index) {
        // not computeIfAbsent: parsing a layer may parse the layers below it
        var layer = layers.get(index);
        if (layer == null) {
            layer = parse(index);
            layers.put(index, layer);
        }
        return layer;
    }

    private Optional<Layer> parse(int index) {
        var resource = resources.get(index);
        var outerLayer = currentLayer;
        var outerMergedLower = mergedLower;
        currentLayer = index;
        mergedLower = false;
        try (Reader reader = resource.openAsReader()) {
            var json = StrictJsonParser.parse(reader);
            return ClientItem.CODEC
                    .parse(ops, json)
                    .ifError(
                            error -> LOGGER.error(
                                    "Couldn't parse item model '{}' from pack '{}': {}",
                                    itemId,
                                    resource.sourcePackId(),
                                    error.message()
                            )
                    )
                    .result()
                    .map(clientItem -> createLayer(clientItem, json.getAsJsonObject()));
        } catch (Exception exception) {
            LOGGER.error("Failed to open item model {} from pack '{}'", itemId, resource.sourcePackId(), exception);
            return Optional.empty();
        } finally {
            currentLayer = outerLayer;
            mergedLower = outerMergedLower;
        }
    }

    // properties are read from json, as decoded ones can't tell an omitted field from a default value
    private Layer createLayer(ClientItem clientItem, JsonObject json) {
        var properties = new JsonObject();
        PROPERTY_KEYS.stream().filter(json::has).forEach(key -> properties.add(key, json.get(key)));
        if (!mergedLower) return new Layer(clientItem, properties);

        layerFrom(currentLayer - 1).ifPresent(lower -> lower.properties().entrySet().stream()
                .filter(property -> !properties.has(property.getKey()))
                .forEach(property -> properties.add(property.getKey(), property.getValue())));

        var mergedProperties = ClientItem.Properties.MAP_CODEC.codec()
                .parse(ops, properties)
                .result()
                .orElse(clientItem.properties());
        return new Layer(new ClientItem(clientItem.model(), mergedProperties, clientItem.registrySwapper()), properties);
    }

    private Optional<JsonElement> encode(ItemModel.Unbaked model) {
        return ItemModels.CODEC.encodeStart(ops, model).result();
    }

    private ClientItem withRegistrySwapper(ClientItem clientItem) {
        return lookupProvider.hasRegisteredPlaceholders()
                ? clientItem.withRegistrySwapper(lookupProvider.createSwapper())
                : clientItem;
    }

    private record Layer(ClientItem clientItem, JsonObject properties) {}
}
