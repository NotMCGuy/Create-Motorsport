package com.createmotorsport.block;

import com.createmotorsport.CreateMotorsport;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

public class SuspensionMount {
    public static final DeferredBlock<Block> SUSPENSION_MOUNT =
            CreateMotorsport.BLOCKS.registerSimpleBlock(
                    "suspension_mount",
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_GRAY)
                            .strength(2.0f, 6.0f)
                            .noOcclusion());

    public static final DeferredItem<BlockItem> SUSPENSION_MOUNT_ITEM =
            CreateMotorsport.ITEMS.registerSimpleBlockItem(
                    "suspension_mount", SUSPENSION_MOUNT);
}