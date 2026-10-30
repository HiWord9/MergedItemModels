package com.hiword9.merged_item_models.mixin;

import com.hiword9.merged_item_models.MergingCodec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

@Mixin(ItemModels.class)
public class ItemModelsMixin {
    @ModifyArg(
            method = "<clinit>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/serialization/Codec;dispatch(Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;",
                    remap = false
            ),
            index = 1
    )
    private static Function<MapCodec<? extends ItemModel.Unbaked>, MapCodec<? extends ItemModel.Unbaked>> wrapCodecs(
            Function<MapCodec<? extends ItemModel.Unbaked>, MapCodec<? extends ItemModel.Unbaked>> codecs
    ) {
        return codecs.andThen(MergingCodec::new);
    }
}
