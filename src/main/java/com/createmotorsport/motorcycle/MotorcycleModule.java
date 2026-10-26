package com.createmotorsport.motorcycle;

import com.createmotorsport.CreateMotorsport;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CreateMotorsport.MODID)
public final class MotorcycleModule {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateMotorsport.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateMotorsport.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateMotorsport.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, CreateMotorsport.MODID);

    public static final DeferredHolder<Block, MotorcycleSuspensionBlock> FORKS = BLOCKS.register("motorcycle_forks", () -> new MotorcycleSuspensionBlock(properties(), true));
    public static final DeferredHolder<Block, MotorcycleSuspensionBlock> REAR_ARM = BLOCKS.register("motorcycle_rear_arm", () -> new MotorcycleSuspensionBlock(properties(), false));
    public static final DeferredHolder<Block, MotorcycleHandlebarBlock> HANDLEBARS = BLOCKS.register("motorcycle_handlebars", () -> new MotorcycleHandlebarBlock(properties()));
    public static final DeferredHolder<Block, MotorcycleGearboxBlock> GEARBOX = BLOCKS.register("motorcycle_gearbox", () -> new MotorcycleGearboxBlock(properties()));
    public static final DeferredHolder<Block, MotorcycleSeatBlock> SEAT = BLOCKS.register("motorcycle_seat", () -> new MotorcycleSeatBlock(properties()));

    public static final DeferredHolder<Block, MotorcycleFuelTankBlock> FUEL_TANK = BLOCKS.register("motorcycle_fuel_tank_slab", () -> new MotorcycleFuelTankBlock(properties(), false));
    public static final DeferredHolder<Block, MotorcycleFuelTankBlock> CREATIVE_FUEL_TANK = BLOCKS.register("creative_motorcycle_fuel_tank_slab", () -> new MotorcycleFuelTankBlock(properties(), true));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MotorcycleFuelTankBlockEntity>> FUEL_TANK_ENTITY = BLOCK_ENTITIES.register("motorcycle_fuel_tank", () -> BlockEntityType.Builder.of(MotorcycleFuelTankBlockEntity::new, FUEL_TANK.get(), CREATIVE_FUEL_TANK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MotorcycleSuspensionBlockEntity>> SUSPENSION_ENTITY = BLOCK_ENTITIES.register("motorcycle_suspension", () -> BlockEntityType.Builder.of(MotorcycleSuspensionBlockEntity::new, FORKS.get(), REAR_ARM.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MotorcycleHandlebarBlockEntity>> HANDLEBAR_ENTITY = BLOCK_ENTITIES.register("motorcycle_handlebars", () -> BlockEntityType.Builder.of(MotorcycleHandlebarBlockEntity::new, HANDLEBARS.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MotorcycleMountBlockEntity>> MOUNT_ENTITY = BLOCK_ENTITIES.register("motorcycle_mount", () -> BlockEntityType.Builder.of(MotorcycleMountBlockEntity::new, SEAT.get(), GEARBOX.get()).build(null));
    public static final DeferredHolder<EntityType<?>, EntityType<MotorcycleSeatEntity>> SEAT_ENTITY = ENTITIES.register("motorcycle_seat", () -> EntityType.Builder.<MotorcycleSeatEntity>of(MotorcycleSeatEntity::new, MobCategory.MISC).sized(0.25F, 0.35F).clientTrackingRange(8).updateInterval(1).build("createmotorsport:motorcycle_seat"));

    static {
        for (var block : new DeferredHolder[]{FORKS, REAR_ARM, HANDLEBARS, GEARBOX, SEAT, FUEL_TANK, CREATIVE_FUEL_TANK}) {
            ITEMS.register(block.getId().getPath(), () -> new BlockItem((Block) block.get(), new Item.Properties()));
        }
    }

    public MotorcycleModule(IEventBus bus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, MotorcycleBalanceConfig.SPEC, "createmotorsport-motorcycles.toml");
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ENTITIES.register(bus);
        bus.addListener(MotorcycleModule::creativeContents);
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                FUEL_TANK_ENTITY.get(), (tank, side) -> tank.handler()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(MotorcycleDefinitions::onReloadListeners);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(MotorcycleDefinitions::commands);
    }

    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().strength(3.5F, 6.0F).noOcclusion();
    }

    private static void creativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().getNamespace().equals(CreateMotorsport.MODID)) {
            ITEMS.getEntries().forEach(item -> event.accept(item.get()));
        }
    }
}
