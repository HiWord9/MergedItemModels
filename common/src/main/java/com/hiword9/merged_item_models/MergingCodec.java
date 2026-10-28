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

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.hiword9.merged_item_models.MergedItemModels.MOD_ID;

public class MergingCodec extends MapCodec<ItemModel.Unbaked> {
    private final MapCodec<? extends ItemModel.Unbaked> codec;

    public MergingCodec(MapCodec<? extends ItemModel.Unbaked> codec) {
        this.codec = codec;
    }

    @Override
    public <T> Stream<T> keys(DynamicOps<T> ops) {
        return codec.keys(ops);
    }

    @Override
    public <T> DataResult<ItemModel.Unbaked> decode(DynamicOps<T> ops, MapLike<T> input) {
        var context = MergingContext.current();
        if (context == null) return decodeModel(ops, input);

        return context.trackShownDefaults(() -> decodeMerged(ops, input, context));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> RecordBuilder<T> encode(ItemModel.Unbaked input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
        return ((MapCodec<ItemModel.Unbaked>) codec).encode(input, ops, prefix);
    }

    @Override
    public String toString() {
        return "Merging[" + codec + "]";
    }

    private <T> DataResult<ItemModel.Unbaked> decodeMerged(DynamicOps<T> ops, MapLike<T> input, MergingContext context) {
        var result = decodeModel(ops, input);
        result.ifSuccess(model -> SourcePacks.put(model, context.sourcePack()));
        if (!context.isMerging()) return result;

        return result.map(model -> {
            Supplier<DataResult<ItemModel.Unbaked>> unmerged = () -> context.unmerged(() -> decodeModel(ops, input));
            return shouldMerge(model, Properties.decode(ops, input), context, unmerged)
                    ? context.mergeLowerModel().orElse(model)
                    : model;
        });
    }

    private <T> DataResult<ItemModel.Unbaked> decodeModel(DynamicOps<T> ops, MapLike<T> input) {
        return codec.decode(ops, input).map(model -> model);
    }

    private boolean shouldMerge(
            ItemModel.Unbaked model,
            Properties properties,
            MergingContext context,
            Supplier<DataResult<ItemModel.Unbaked>> unmerged
    ) {
        return properties.merge().orElseGet(
                () -> isDefaultModel(model, context) || isBaseDefinition(model, context, unmerged)
        );
    }

    // a custom model with the default name is merged only if the pack below shows it too, so it isn't lost
    private boolean isDefaultModel(ItemModel.Unbaked model, MergingContext context) {
        return model instanceof BlockModelWrapper.Unbaked wrapper
                && context.isDefaultModel(wrapper.model())
                && (context.lowerShowsDefaultModel(wrapper.model()) || context.isBaseModel(wrapper.model()));
    }

    private boolean isBaseDefinition(
            ItemModel.Unbaked model,
            MergingContext context,
            Supplier<DataResult<ItemModel.Unbaked>> unmerged
    ) {
        var base = context.baseModel().filter(baseModel -> baseModel.type() == model.type());
        if (base.isEmpty()) return false;

        // models inside may be merged already, so compare what the pack itself defines
        return unmerged.get().result()
                .filter(definition -> context.isSameDefinition(definition, base.get()))
                .isPresent()
                && (!(model instanceof BlockModelWrapper.Unbaked wrapper) || context.isBaseModel(wrapper.model()));
    }

    private record Properties(Optional<Boolean> merge) {
        private static final Properties DEFAULT = new Properties(Optional.empty());

        private static final MapCodec<Properties> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Codec.BOOL.optionalFieldOf("merge").forGetter(Properties::merge)
                ).apply(instance, Properties::new)
        );

        private static final MapCodec<Properties> FIELD_CODEC
                = CODEC.codec().optionalFieldOf("%s:properties".formatted(MOD_ID), DEFAULT);

        private static <T> Properties decode(DynamicOps<T> ops, MapLike<T> input) {
            return FIELD_CODEC.decode(ops, input).result().orElse(DEFAULT);
        }
    }
}
