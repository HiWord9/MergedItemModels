package com.hiword9.merged_item_models;

import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.resources.model.ClientItemInfoLoader.LoadedClientInfos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class MergedClientItemInfoLoader {
    private static final FileToIdConverter LISTER = FileToIdConverter.json("items");

    public static CompletableFuture<LoadedClientInfos> load(
            Collection<Identifier> files,
            ResourceManager resourceManager,
            Executor executor,
            RegistryAccess.Frozen registryAccess
    ) {
        var list = new ArrayList<CompletableFuture<PendingLoad>>(files.size());
        files.forEach(file -> list.add(
                CompletableFuture.supplyAsync(
                        () -> getPendingLoad(file, resourceManager.getResourceStack(file), registryAccess),
                        executor
                )
        ));
        return Util.sequence(list).thenApply(MergedClientItemInfoLoader::getLoadedClientInfos);
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
            if (pendingLoad.clientItem() != null) {
                map.put(pendingLoad.id(), pendingLoad.clientItem());
            }
        }

        return new LoadedClientInfos(map);
    }

    private record PendingLoad(Identifier id, @Nullable ClientItem clientItem) {}
}
