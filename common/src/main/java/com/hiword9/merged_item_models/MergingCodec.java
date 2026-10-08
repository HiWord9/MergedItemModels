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
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.hiword9.merged_item_models.MergedItemModels.MOD_ID;

public class MergingCodec extends MapCodec<ItemModel.Unbaked> {
    private static final List<String> DEFAULT_MODEL_PREFIXES = List.of("item/", "block/");

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
        var result = decodeModel(ops, input);

        var context = MergingContext.current();
        if (context == null) return result;

        result.ifSuccess(model -> SourcePacks.put(model, context.sourcePack()));
        if (!context.isMerging()) return result;

        return result.map(model -> {
            Supplier<DataResult<ItemModel.Unbaked>> unmerged = () -> context.unmerged(() -> decodeModel(ops, input));
            return shouldMerge(model, Properties.decode(ops, input), context, unmerged)
                    ? context.mergeLowerModel().orElse(model)
                    : model;
        });
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
                () -> isDefaultModel(model, context.itemId()) || isBaseDefinition(model, context, unmerged)
        );
    }

    private boolean isDefaultModel(ItemModel.Unbaked model, Identifier itemId) {
        return model instanceof BlockModelWrapper.Unbaked wrapper
                && DEFAULT_MODEL_PREFIXES.stream().anyMatch(prefix -> wrapper.model().equals(itemId.withPrefix(prefix)));
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
                .isPresent();
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
