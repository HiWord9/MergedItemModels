<p align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/a9cc56e18fe178afa4f66b3b737dbfd1d3d3bf40.png" alt="Merged Item Models" width="700"/>
</p>

<p align="center">
  <a href="https://discord.miros.love"><img src="https://img.shields.io/badge/discord-5865f2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord"/></a>
</p>

Since 1.21.4, resource packs customize items with **item model definitions** (`assets/<namespace>/items/<item>.json`): renames, custom model data, variants and more. Vanilla reads this file **only from the topmost pack**, so when two packs change the same item, everything the lower one added is gone.

**Merged Item Models** merges these files from all enabled resource packs, the right way.

![Renames from two packs](https://cdn.modrinth.com/data/cached_images/a68ef17a0daf2bf1788811eda62014d1a4400ffc.png)

![Food from two packs](https://cdn.modrinth.com/data/cached_images/d5939403b337b13f7fcbcc4767c65f69e4a5a4a6.png)

## How it works

Packs are merged from top to bottom. Wherever an upper pack falls back to the item's default look, the definition from the pack below is used instead. A fallback is detected automatically when it is:

- the item's default model, e.g. `{"type": "minecraft:model", "model": "minecraft:item/diamond_sword"}`, unless the pack replaces that model with its own;
- an exact copy of the vanilla definition, as packs often do for compasses, clocks, shields and other complex items.

Also:

- item properties (`hand_animation_on_swap`, `oversized_in_gui`, `swap_animation_scale`) come from the highest merged pack that sets them;
- a broken file in one pack is skipped, the packs below keep working.

The mod only changes how item model files are read. The result is a regular vanilla item model, so mods that read, change or render item models work with it as usual.

## For resource pack authors

In most cases nothing needs to be done. To control merging explicitly, add `mim:properties` to any model:

```json
{
  "type": "minecraft:model",
  "model": "minecraft:item/my_sword",
  "mim:properties": { "merge": true }
}
```

- `"merge": true` replaces this model with the definition from the pack below;
- `"merge": false` keeps it as is, even if it looks like a fallback.

Without the mod the field is simply ignored.

## For mod developers

`MergedItemModelsApi.getSourcePack` returns the id of the resource pack a model was read from, e.g. `vanilla` or `file/Pack.zip`. Useful to show which pack a rename comes from.

```java
Optional<String> fromModel = MergedItemModelsApi.getSourcePack(model);     // ItemModel.Unbaked
Optional<String> fromItem = MergedItemModelsApi.getSourcePack(clientItem); // ClientItem
```

`getSourcePackInfo` returns `PackLocationInfo`, with the pack's id, title, source and known pack info:

```java
Optional<PackLocationInfo> fromModel = MergedItemModelsApi.getSourcePackInfo(model);
Optional<PackLocationInfo> fromItem = MergedItemModelsApi.getSourcePackInfo(clientItem);
```

Both methods return empty for models not loaded by the mod and for model instances shared between packs.
For `ClientItem`, the source is that of the root model; merged models inside may come from other packs.

## Building

Minecraft 1.21.11 on Fabric, NeoForge and Forge. Client-side only, no dependencies.

```
./gradlew build
```

The jars are written to `fabric/build/libs`, `neoforge/build/libs` and `forge/build/libs`.

## License

[Team Durt License 1.0](LICENSE)
