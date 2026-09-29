package com.hiword9.merged_item_models;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.item.BlockModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.stream.Stream;

import static com.hiword9.merged_item_models.MergedItemModels.MOD_ID;

public class MergedItemModelCodec extends MapCodec<ItemModel.Unbaked> {
    public static final MapCodec<ItemModel.Unbaked> INSTANCE = new MergedItemModelCodec();

    private static final List<String> DEFAULT_MODEL_PREFIXES = List.of("item/", "block/");

    private MergedItemModelCodec() {}

    @Override
    public <T> Stream<T> keys(DynamicOps<T> ops) {
        return BlockModelWrapper.Unbaked.MAP_CODEC.keys(ops);
    }

    @Override
    public <T> DataResult<ItemModel.Unbaked> decode(DynamicOps<T> ops, MapLike<T> input) {
        var vanillaDataResult = decodeVanilla(ops, input);
        var vanillaModel = vanillaDataResult.result().orElse(null);

        var context = MergingContext.current();

        var settings = decodeSettings(ops, input).result().orElse(null);

        if (
                vanillaModel != null
                && context != null
                && shouldMerge(vanillaModel, context, settings)
        ) {
            var lowerModel = context.lowerModel();
            if (lowerModel.isPresent()) return DataResult.success(lowerModel.get());
        }

        return vanillaDataResult.map(model -> model);
    }

    private <T> DataResult<BlockModelWrapper.Unbaked> decodeVanilla(DynamicOps<T> ops, MapLike<T> input) {
        return BlockModelWrapper.Unbaked.MAP_CODEC.decode(ops, input);
    }

    private <T> DataResult<Settings> decodeSettings(DynamicOps<T> ops, MapLike<T> input) {
        return Settings.CODEC_AS_FIELD.decode(ops, input);
    }

    @Override
    public <T> RecordBuilder<T> encode(ItemModel.Unbaked input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
        return BlockModelWrapper.Unbaked.MAP_CODEC.encode((BlockModelWrapper.Unbaked) input, ops, prefix);
    }

    private boolean shouldMerge(BlockModelWrapper.Unbaked vanillaModel, MergingContext context, Settings settings) {
        if (settings != null && settings.merge != null) return settings.merge;
        return isDefaultModel(vanillaModel.model(), context.itemId());
    }

    private boolean isDefaultModel(Identifier model, Identifier item) {
        return DEFAULT_MODEL_PREFIXES.stream().anyMatch(prefix -> model.equals(item.withPrefix(prefix)));
    }

    public record Settings(Boolean merge) {
        private static final MapCodec<Settings> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Codec.BOOL.optionalFieldOf("merge", null)
                                .forGetter(Settings::merge)
                    )
                    .apply(instance, Settings::new)
        );

        private static final MapCodec<Settings> CODEC_AS_FIELD
                = CODEC.fieldOf("%s:settings".formatted(MOD_ID));
    }
}
