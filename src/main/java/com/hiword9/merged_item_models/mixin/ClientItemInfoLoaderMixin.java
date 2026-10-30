package com.hiword9.merged_item_models.mixin;

import com.hiword9.merged_item_models.MergedClientItemInfoLoader;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.client.resources.model.ClientItemInfoLoader.LoadedClientInfos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

@Mixin(ClientItemInfoLoader.class)
public class ClientItemInfoLoaderMixin {
    // vanilla parses only the top file of each item, all of them are merged instead
    @ModifyArg(
            method = "scheduleLoad",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;thenCompose(Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;",
                    ordinal = 0,
                    remap = false
            )
    )
    private static Function<Map<Identifier, Resource>, CompletionStage<LoadedClientInfos>> loadMerged(
            Function<Map<Identifier, Resource>, CompletionStage<LoadedClientInfos>> load,
            @Local(argsOnly = true) ResourceManager resourceManager,
            @Local(argsOnly = true) Executor executor,
            @Local RegistryAccess.Frozen registryAccess
    ) {
        return files -> MergedClientItemInfoLoader.load(files.keySet(), resourceManager, executor, registryAccess);
    }
}
