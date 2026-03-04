package com.hiword9.merged_item_models;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.PlaceholderLookupProvider;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import static net.minecraft.client.resources.model.ClientItemInfoLoader.*;

public class MergedClientItemInfoLoader {
    private static final Map<DynamicOps<?>, MergingContext> OPS_TO_CONTEXT = new ConcurrentHashMap<>();

    public static MergingContext getContext(DynamicOps<?> ops) {
        return OPS_TO_CONTEXT.get(ops);
    }

    public static CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> scheduleLoad(ResourceManager resourceManager, Executor executor) {
        var registryAccess = ClientRegistryLayer.createRegistryAccess().compositeAccess();
        return CompletableFuture.supplyAsync(() -> LISTER.listMatchingResourceStacks(resourceManager), executor)
                .thenCompose(idToResources -> {
                            var list = new ArrayList<CompletableFuture<PendingLoad>>(idToResources.size());
                            idToResources.forEach((identifier, resources) -> list.add(
                                    CompletableFuture.supplyAsync(
                                            () -> getPendingLoad(identifier, resources, registryAccess),
                                            executor
                                    )
                            ));
                            return Util.sequence(list)
                                    .thenApply(f -> {
                                        OPS_TO_CONTEXT.clear();
                                        return f;
                                    })
                                    .thenApply(MergedClientItemInfoLoader::getLoadedClientInfos);
                        }
                );
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

        var clientItem = getClientItem(
                resource,
                dynamicOps,
                context.identifier,
                context.lookupProvider
        );

        context.pop();
        return clientItem;
    }

    private static @Nullable ClientItem getClientItem(
            Resource resource,
            DynamicOps<JsonElement> dynamicOps,
            Identifier identifier,
            PlaceholderLookupProvider lookupProvider
    ) {
        ClientItem clientItem;
        try (Reader reader = resource.openAsReader()) {
            clientItem = ClientItem.CODEC
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
                            ci -> lookupProvider.hasRegisteredPlaceholders()
                                    ? ci.withRegistrySwapper(lookupProvider.createSwapper())
                                    : ci
                    )
                    .orElse(null);
        } catch (Exception exception) {
            LOGGER.error("Failed to open item model {} from pack '{}'", identifier, resource.sourcePackId(), exception);
            return null;
        }
        return clientItem;
    }

    private static LoadedClientInfos getLoadedClientInfos(List<PendingLoad> pendingLoadList) {
        var map = new HashMap<Identifier, ClientItem>();

        for (PendingLoad pendingload : pendingLoadList) {
            if (pendingload.clientItemInfo() != null) {
                map.put(pendingload.id(), pendingload.clientItemInfo());
            }
        }

        return new LoadedClientInfos(map);
    }
}
