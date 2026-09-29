package com.hiword9.merged_item_models;

import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.resources.model.ClientItemInfoLoader.LoadedClientInfos;
import net.minecraft.client.resources.model.ClientItemInfoLoader.PendingLoad;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static net.minecraft.client.resources.model.ClientItemInfoLoader.LISTER;

public class MergedClientItemInfoLoader {
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
                    return Util.sequence(list).thenApply(MergedClientItemInfoLoader::getLoadedClientInfos);
                });
    }

    private static PendingLoad getPendingLoad(
            Identifier file,
            List<Resource> resources,
            RegistryAccess.Frozen registryAccess
    ) {
        var modelId = LISTER.fileToId(file);
        var clientItem = new MergingContext(modelId, resources, registryAccess).load().orElse(null);
        return new PendingLoad(modelId, clientItem);
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
