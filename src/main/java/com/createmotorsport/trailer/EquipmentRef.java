package com.createmotorsport.trailer;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import java.util.UUID;

public record EquipmentRef(String dimension, UUID sublevel, BlockPos offset, UUID endpoint) {
    public static EquipmentRef of(EquipmentBlockEntity be) {
        var sub = Sable.HELPER.getContaining(be);
        return new EquipmentRef(be.getLevel().dimension().location().toString(), sub == null ? null : sub.getUniqueId(),
                sub == null ? be.getBlockPos() : be.getBlockPos().subtract(sub.getPlot().getCenterBlock()), be.identity());
    }
    public EquipmentBlockEntity resolve(Level level) {
        if (!dimension.equals(level.dimension().location().toString())) return null;
        BlockPos pos = offset;
        if (sublevel != null) {
            var container = SubLevelContainer.getContainer(level);
            var sub = container == null ? null : container.getSubLevel(sublevel);
            if (sub == null || sub.isRemoved()) return null;
            pos = sub.getPlot().getCenterBlock().offset(offset);
        }
        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof EquipmentBlockEntity be)) return null;
        return endpoint.equals(be.identity()) && of(be).equals(this) ? be : null;
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", dimension);
        if (sublevel != null) tag.putUUID("Sublevel", sublevel);
        tag.putLong("Offset", offset.asLong());
        tag.putUUID("Endpoint", endpoint);
        return tag;
    }
    public static EquipmentRef load(CompoundTag tag) {
        return tag.hasUUID("Endpoint") && tag.contains("Dimension") && tag.contains("Offset")
                ? new EquipmentRef(tag.getString("Dimension"), tag.hasUUID("Sublevel") ? tag.getUUID("Sublevel") : null,
                        BlockPos.of(tag.getLong("Offset")), tag.getUUID("Endpoint")) : null;
    }
}
