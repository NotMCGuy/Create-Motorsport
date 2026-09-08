package com.createmotorsport.trailer;

import com.createmotorsport.CreateMotorsport;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

public final class TrailerRegistry {
    public static final DeferredBlock<EquipmentBlock> FIFTH_WHEEL=block("fifth_wheel",CouplingRules.Kind.FIFTH_WHEEL);
    public static final DeferredBlock<EquipmentBlock> LARGE_FIFTH_WHEEL=block("large_fifth_wheel",CouplingRules.Kind.FIFTH_WHEEL,2.0);
    public static final DeferredBlock<EquipmentBlock> KINGPIN=block("kingpin",CouplingRules.Kind.KINGPIN);
    public static final DeferredBlock<EquipmentBlock> WIDE_KINGPIN=block("wide_kingpin",CouplingRules.Kind.KINGPIN,2.0);
    public static final DeferredBlock<EquipmentBlock> TOW_BALL=block("tow_ball",CouplingRules.Kind.TOW_BALL);
    public static final DeferredBlock<EquipmentBlock> COUPLING_HEAD=block("coupling_head",CouplingRules.Kind.COUPLING_HEAD);
    public static final DeferredBlock<EquipmentBlock> LANDING_LEGS=block("landing_legs",CouplingRules.Kind.LANDING_LEGS);
    public static final DeferredBlock<EquipmentBlock> CONTROL_PANEL=block("trailer_control_panel",CouplingRules.Kind.CONTROL_PANEL);
    public static final DeferredItem<EquipmentLinkerItem> LINKER=CreateMotorsport.ITEMS.register("equipment_linker",()->new EquipmentLinkerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<EquipmentBlockEntity>> EQUIPMENT_ENTITY=
            CreateMotorsport.BLOCK_ENTITY_TYPES.register("trailer_equipment",()->BlockEntityType.Builder.of(EquipmentBlockEntity::new,
                    FIFTH_WHEEL.get(),LARGE_FIFTH_WHEEL.get(),KINGPIN.get(),WIDE_KINGPIN.get(),TOW_BALL.get(),COUPLING_HEAD.get(),LANDING_LEGS.get(),CONTROL_PANEL.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<TrailerMenu>> PANEL_MENU=CreateMotorsport.MENUS.register("trailer_control_panel",
            ()->IMenuTypeExtension.create((id,inv,buf)->new TrailerMenu(id,inv,buf.readBlockPos())));
    private static DeferredBlock<EquipmentBlock> block(String name,CouplingRules.Kind kind) {
        return block(name,kind,1.0);
    }
    private static DeferredBlock<EquipmentBlock> block(String name,CouplingRules.Kind kind,double scale) {
        var block=CreateMotorsport.BLOCKS.register(name,()->new EquipmentBlock(kind,scale,BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4,8).noOcclusion()));
        CreateMotorsport.ITEMS.registerSimpleBlockItem(name,block);return block;
    }
    public static void init() {}
    private TrailerRegistry() {}
}
