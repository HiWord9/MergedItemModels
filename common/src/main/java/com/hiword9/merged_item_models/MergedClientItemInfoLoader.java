package com.hiword9.merged_item_models;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.resources.model.ClientItemInfoLoader.LoadedClientInfos;
import net.minecraft.client.resources.model.ClientItemInfoLoader.PendingLoad;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.PlaceholderLookupProvider;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import static net.minecraft.client.resources.model.ClientItemInfoLoader.LISTER;
import static net.minecraft.client.resources.model.ClientItemInfoLoader.LOGGER;

public class MergedClientItemInfoLoader {
    private static final Map<DynamicOps<?>, MergingContext> OPS_TO_CONTEXT = new ConcurrentHashMap<>();

    public static @Nullable MergingContext getContext(DynamicOps<?> ops) {
        return OPS_TO_CONTEXT.get(ops);
    }

    public static CompletableFuture<LoadedClientInfos> scheduleLoad(ResourceManager resourceManager, Executor executor) {
        var registryAccess = ClientRegistryLayer.createRegistryAccess().compositeAccess();
        return CompletableFuture.supplyAsync(() -> LISTER.listMatchingResourceStacks(resourceManager), executor)
                .thenCompose(idToResources -> {
                    var list = new ArrayList<CompletableFuture<PendingLoad>>(idToResources.size());
                    idToResources.forEach((file, resources) -> list.add(
                            CompletableFuture.supplyAsync(
                                    () -> getPendingLoad(file, resources, registryAccess),
                                    executor
                            )
                    ));
                    return Util.sequence(list)
                            .thenApply(pendingLoads -> {
                                OPS_TO_CONTEXT.clear();
                                return pendingLoads;
                            })
                            .thenApply(MergedClientItemInfoLoader::getLoadedClientInfos);
                });
    }

    private static PendingLoad getPendingLoad(
            Identifier file,
            List<Resource> resources,
            RegistryAccess.Frozen registryAccess
    ) {
        var modelId = LISTER.fileToId(file);

        var lookupProvider = new PlaceholderLookupProvider(registryAccess);
        var dynamicOps = lookupProvider.createSerializationContext(JsonOps.INSTANCE);

        var context = new MergingContext(resources, modelId, lookupProvider);

        OPS_TO_CONTEXT.put(dynamicOps, context);

        var clientItem = getClientItem(dynamicOps, context);

        OPS_TO_CONTEXT.remove(dynamicOps);

        return new PendingLoad(modelId, clientItem);
    }

    public static @Nullable ClientItem getClientItem(
            DynamicOps<JsonElement> dynamicOps,
            MergingContext context
    ) {
        var resource = context.push();
        if (resource == null) return null;

        var clientItem = parseClientItem(
                resource,
                dynamicOps,
                context.identifier,
                context.lookupProvider
        );

        context.pop();
        return clientItem;
    }

    private static @Nullable ClientItem parseClientItem(
            Resource resource,
            DynamicOps<JsonElement> dynamicOps,
            Identifier identifier,
            PlaceholderLookupProvider lookupProvider
    ) {
        try (Reader reader = resource.openAsReader()) {
            return ClientItem.CODEC
                    .parse(dynamicOps, StrictJsonParser.parse(reader))
                    .ifError(
                            error -> LOGGER.error(
                                    "Couldn't parse item model '{}' from pack '{}': {}",
                                    identifier,
                                    resource.sourcePackId(),
                                    error.message()
                            )
                    )
                    .result()
                    .map(
                            clientItem -> lookupProvider.hasRegisteredPlaceholders()
                                    ? clientItem.withRegistrySwapper(lookupProvider.createSwapper())
                                    : clientItem
                    )
                    .orElse(null);
        } catch (Exception exception) {
            LOGGER.error("Failed to open item model {} from pack '{}'", identifier, resource.sourcePackId(), exception);
            return null;
        }
    }

    private static LoadedClientInfos getLoadedClientInfos(List<PendingLoad> pendingLoads) {
        var map = new HashMap<Identifier, ClientItem>();

        for (PendingLoad pendingLoad : pendingLoads) {
            if (pendingLoad.clientItemInfo() != null) {
                map.put(pendingLoad.id(), pendingLoad.clientItemInfo());
            }
        }

        return new LoadedClientInfos(map);
    }
}
