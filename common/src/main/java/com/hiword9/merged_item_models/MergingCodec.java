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
        DataResult<ItemModel.Unbaked> result = codec.decode(ops, input).map(model -> model);

        var context = MergingContext.current();
        if (context == null) return result;

        return result.map(
                model -> model instanceof BlockModelWrapper.Unbaked wrapper
                        && shouldMerge(wrapper, Settings.decode(ops, input), context)
                        ? context.lowerModel().orElse(model)
                        : model
        );
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

    private boolean shouldMerge(BlockModelWrapper.Unbaked model, Settings settings, MergingContext context) {
        return settings.merge().orElseGet(() -> isDefaultModel(model.model(), context.itemId()));
    }

    private boolean isDefaultModel(Identifier model, Identifier item) {
        return DEFAULT_MODEL_PREFIXES.stream().anyMatch(prefix -> model.equals(item.withPrefix(prefix)));
    }

    private record Settings(Optional<Boolean> merge) {
        private static final Settings DEFAULT = new Settings(Optional.empty());

        private static final MapCodec<Settings> CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(
                        Codec.BOOL.optionalFieldOf("merge").forGetter(Settings::merge)
                ).apply(instance, Settings::new)
        );

        private static final MapCodec<Settings> FIELD_CODEC
                = CODEC.codec().optionalFieldOf("%s:settings".formatted(MOD_ID), DEFAULT);

        private static <T> Settings decode(DynamicOps<T> ops, MapLike<T> input) {
            return FIELD_CODEC.decode(ops, input).result().orElse(DEFAULT);
        }
    }
}
