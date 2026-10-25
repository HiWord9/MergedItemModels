package com.hiword9.merged_item_models;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.PlaceholderLookupProvider;
import net.minecraft.util.StrictJsonParser;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public class MergingContext {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<MergingContext> CURRENT = new ThreadLocal<>();
    private static final List<String> DEFAULT_MODEL_PREFIXES = List.of("item/", "block/");
    private static final List<String> PROPERTY_KEYS = ClientItem.Properties.MAP_CODEC.keys(JsonOps.INSTANCE)
            .map(JsonElement::getAsString)
            .toList();

    private final Identifier itemId;
    private final List<Resource> resources;
    private final ResourceManager resourceManager;
    private final PlaceholderLookupProvider lookupProvider;
    private final DynamicOps<JsonElement> ops;
    private final List<Identifier> defaultModels;
    private final Map<Integer, Optional<Layer>> layers = new HashMap<>();
    // default models shown by each model being decoded, a bit per default model, from the root down
    private final IntArrayList shownDefaults = new IntArrayList();
    private int currentLayer;
    private boolean mergedLower;
    private boolean merging = true;

    public MergingContext(Identifier itemId, List<Resource> resources, ResourceManager resourceManager, RegistryAccess registryAccess) {
        this.itemId = itemId;
        this.resources = resources;
        this.resourceManager = resourceManager;
        this.defaultModels = DEFAULT_MODEL_PREFIXES.stream().map(itemId::withPrefix).toList();
        this.lookupProvider = new PlaceholderLookupProvider(registryAccess);
        this.ops = lookupProvider.createSerializationContext(JsonOps.INSTANCE);
    }

    public static @Nullable MergingContext current() {
        return CURRENT.get();
    }

    public Optional<ClientItem> load() {
        CURRENT.set(this);
        try {
            return layerFrom(resources.size() - 1).map(layer -> withRegistrySwapper(layer.clientItem()));
        } finally {
            CURRENT.remove();
        }
    }

    public String sourcePack() {
        return resources.get(currentLayer).sourcePackId();
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
        lower.ifPresent(layer -> {
            mergedLower = true;
            shownDefaults.set(shownDefaults.size() - 1, layer.shownDefaults());
        });
        return lower.map(layer -> layer.clientItem().model());
    }

    // a model passes what it shows to its parent, a model merged with the pack below shows what that pack shows
    public DataResult<ItemModel.Unbaked> trackShownDefaults(Supplier<DataResult<ItemModel.Unbaked>> decode) {
        if (!merging) return decode.get();

        shownDefaults.push(0);
        try {
            var result = decode.get();
            result.ifSuccess(model -> {
                if (model instanceof BlockModelWrapper.Unbaked wrapper) showDefaults(defaultBit(wrapper.model()));
            });
            return result;
        } finally {
            showDefaults(shownDefaults.popInt());
        }
    }

    public boolean isDefaultModel(Identifier model) {
        return defaultModels.contains(model);
    }

    // compared in encoded form, as some models hold runtime state and don't implement equals
    public boolean isSameDefinition(ItemModel.Unbaked first, ItemModel.Unbaked second) {
        var encoded = encode(first);
        return encoded.isPresent() && encoded.equals(encode(second));
    }

    // a model not overridden by any pack looks the same however it's reached
    public boolean isBaseModel(Identifier model) {
        return resourceManager.getResource(model.withPath(path -> "models/" + path + ".json"))
                .map(resource -> resource.sourcePackId().equals(resources.getFirst().sourcePackId()))
                .orElse(true);
    }

    public boolean lowerShowsDefaultModel(Identifier model) {
        var bit = defaultBit(model);
        return layerFrom(currentLayer - 1).filter(layer -> (layer.shownDefaults() & bit) != 0).isPresent();
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
        shownDefaults.push(0);
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
                    .map(clientItem -> createLayer(clientItem, json.getAsJsonObject(), shownDefaults.topInt()));
        } catch (Exception exception) {
            LOGGER.error("Failed to open item model {} from pack '{}'", itemId, resource.sourcePackId(), exception);
            return Optional.empty();
        } finally {
            currentLayer = outerLayer;
            mergedLower = outerMergedLower;
            shownDefaults.popInt();
        }
    }

    // properties are read from json, as decoded ones can't tell an omitted field from a default value
    private Layer createLayer(ClientItem clientItem, JsonObject json, int shownDefaults) {
        var properties = new JsonObject();
        PROPERTY_KEYS.stream().filter(json::has).forEach(key -> properties.add(key, json.get(key)));
        if (!mergedLower) return new Layer(clientItem, properties, shownDefaults);

        layerFrom(currentLayer - 1).ifPresent(lower -> lower.properties().entrySet().stream()
                .filter(property -> !properties.has(property.getKey()))
                .forEach(property -> properties.add(property.getKey(), property.getValue())));

        var mergedProperties = ClientItem.Properties.MAP_CODEC.codec()
                .parse(ops, properties)
                .result()
                .orElse(clientItem.properties());
        return new Layer(new ClientItem(clientItem.model(), mergedProperties, clientItem.registrySwapper()), properties, shownDefaults);
    }

    private Optional<JsonElement> encode(ItemModel.Unbaked model) {
        return ItemModels.CODEC.encodeStart(ops, model).result();
    }

    private ClientItem withRegistrySwapper(ClientItem clientItem) {
        return lookupProvider.hasRegisteredPlaceholders()
                ? clientItem.withRegistrySwapper(lookupProvider.createSwapper())
                : clientItem;
    }

    private void showDefaults(int defaults) {
        shownDefaults.set(shownDefaults.size() - 1, shownDefaults.topInt() | defaults);
    }

    private int defaultBit(Identifier model) {
        var index = defaultModels.indexOf(model);
        return index < 0 ? 0 : 1 << index;
    }

    private record Layer(ClientItem clientItem, JsonObject properties, int shownDefaults) {}
}
