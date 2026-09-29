package com.hiword9.merged_item_models.mixin;

import com.hiword9.merged_item_models.MergedClientItemInfoLoader;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ClientItemInfoLoader.class)
public class ClientItemInfoLoaderMixin {
    @Inject(
            method = "scheduleLoad",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void onScheduleLoad(
            ResourceManager resourceManager, Executor executor,
            CallbackInfoReturnable<CompletableFuture<ClientItemInfoLoader.LoadedClientInfos>> cir
    ) {
        cir.setReturnValue(MergedClientItemInfoLoader.scheduleLoad(resourceManager, executor));
    }
}
