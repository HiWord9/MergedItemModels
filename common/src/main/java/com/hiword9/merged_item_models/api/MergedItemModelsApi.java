package com.hiword9.merged_item_models.api;

import com.hiword9.merged_item_models.SourcePacks;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;

import java.util.Optional;

public final class MergedItemModelsApi {
    private MergedItemModelsApi() {}

    /**
     * Id of the resource pack whose item definition the model was read from, e.g. {@code vanilla} or {@code file/Pack.zip}.
     * Empty for models not loaded by this mod, and for model instances shared between packs.
     */
    public static Optional<String> getSourcePack(ItemModel.Unbaked model) {
        return SourcePacks.get(model);
    }

    public static Optional<String> getSourcePack(ClientItem clientItem) {
        return getSourcePack(clientItem.model());
    }
}
