package com.hiword9.merged_item_models;

import com.google.gson.JsonElement;
import com.mojang.serialization.*;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;

import java.util.stream.Stream;

import static com.hiword9.merged_item_models.MergedClientItemInfoLoader.*;

public class MergedItemModelCodec extends MapCodec<ItemModel.Unbaked> {
    public static final MapCodec<ItemModel.Unbaked> INSTANCE = new MergedItemModelCodec();

    private MergedItemModelCodec() {}

    @Override
    public <T> Stream<T> keys(DynamicOps<T> ops) {
        return BlockModelWrapper.Unbaked.MAP_CODEC.keys(ops);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> DataResult<ItemModel.Unbaked> decode(DynamicOps<T> ops, MapLike<T> input) {
        var vanillaDataResult = decodeVanilla(ops, input);
        var vanillaModel = vanillaDataResult.result().orElse(null);

        var context = MergedClientItemInfoLoader.getContext(ops);

        if (
                vanillaModel != null
                && vanillaModel.model().getPath().endsWith(context.identifier.getPath())
        ) {
            var clientItem = getClientItem((DynamicOps<JsonElement>) ops, context);
            if (clientItem != null) return DataResult.success(clientItem.model());
        }

        return (DataResult<ItemModel.Unbaked>) (Object) vanillaDataResult;
    }

    private <T> DataResult<BlockModelWrapper.Unbaked> decodeVanilla(DynamicOps<T> ops, MapLike<T> input) {
        return BlockModelWrapper.Unbaked.MAP_CODEC.decode(ops, input);
    }

    @Override
    public <T> RecordBuilder<T> encode(ItemModel.Unbaked input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
        return BlockModelWrapper.Unbaked.MAP_CODEC.encode((BlockModelWrapper.Unbaked) input, ops, prefix);
    }
}
