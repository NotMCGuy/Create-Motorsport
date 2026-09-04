package com.createmotorsport;

import com.createmotorsport.block.EngineBlock;
import com.createmotorsport.block.SteeringWheelBlock;
import com.createmotorsport.block.SuspensionBlock;
import com.createmotorsport.block.DownFlapBlock;
import com.createmotorsport.block.entity.DownFlapBlockEntity;
import com.createmotorsport.block.entity.EngineBlockEntity;
import com.createmotorsport.block.entity.SteeringWheelBlockEntity;
import com.createmotorsport.block.entity.SuspensionBlockEntity;
import com.createmotorsport.item.SuspensionWrenchItem;
import com.createmotorsport.menu.EngineMenu;
import com.createmotorsport.menu.SteeringWheelMenu;
import com.createmotorsport.menu.SuspensionMenu;
import com.createmotorsport.network.AdjustLiftPacket;
import com.createmotorsport.network.SetDriveModePacket;
import com.createmotorsport.network.SetDrivingPacket;
import com.createmotorsport.network.SetSteeringKeyPacket;
import com.createmotorsport.network.StartTelemetryLogPacket;
import com.createmotorsport.network.ToggleAxleEndPacket;
import com.createmotorsport.network.ToggleEngineDirectionPacket;
import com.createmotorsport.network.SteeringInputPacket;
import com.createmotorsport.network.TelemetryLinePacket;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import dev.ryanhcode.offroad.content.components.TireLike;
import dev.ryanhcode.offroad.index.OffroadDataComponents;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(CreateMotorsport.MODID)
public class CreateMotorsport {
    public static final String MODID = "createmotorsport";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);

    // tire's "design load", Value = midpoint_weight_kg * 9.81 / 4 wheels
    public static final DataComponentType<Float> TIRE_DESIGN_LOAD = DataComponentType.<Float>builder()
            .persistent(Codec.FLOAT)
            .networkSynchronized(ByteBufCodecs.FLOAT)
            .build();
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> TIRE_DESIGN_LOAD_HOLDER =
            DATA_COMPONENTS.register("tire_design_load", () -> TIRE_DESIGN_LOAD);

    public static final DeferredItem<Item> RACING_COMPONENT = ITEMS.registerSimpleItem(
            "racing_component",
            new Item.Properties()
    );

    // 5 different design loads of tires, using the offroad small-tire model (radius 0.75)
    // I got these weights from a table I made of 'optimal engine specs to vehicle mass', so these should line up with 5 tiers of engine is the goal
    public static final DeferredItem<Item> RACING_TIRE_1 = registerTire("racing_tire_1", 20);
    public static final DeferredItem<Item> RACING_TIRE_2 = registerTire("racing_tire_2", 36);
    public static final DeferredItem<Item> RACING_TIRE_3 = registerTire("racing_tire_3", 65);
    public static final DeferredItem<Item> RACING_TIRE_4 = registerTire("racing_tire_4", 117);
    public static final DeferredItem<Item> RACING_TIRE_5 = registerTire("racing_tire_5", 210);
    public static final DeferredItem<Item> AIR_INTAKE = ITEMS.registerSimpleItem(
            "air_intake",
            new Item.Properties()
    );
    public static final DeferredItem<Item> EXHAUST_MANIFOLD = ITEMS.registerSimpleItem(
            "exhaust_manifold",
            new Item.Properties()
    );
    public static final DeferredItem<SuspensionWrenchItem> SUSPENSION_WRENCH = ITEMS.register(
            "suspension_wrench",
            () -> new SuspensionWrenchItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredBlock<EngineBlock> ENGINE_BLOCK = BLOCKS.register(
            "engine_block",
            () -> new EngineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops())
    );
    public static final DeferredItem<BlockItem> ENGINE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(
            "engine_block",
            ENGINE_BLOCK
    );
    public static final DeferredBlock<SuspensionBlock> SUSPENSION = BLOCKS.register(
            "suspension",
            () -> new SuspensionBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops())
    );
    public static final DeferredItem<com.createmotorsport.item.SuspensionBlockItem> SUSPENSION_ITEM = ITEMS.register(
            "suspension",
            () -> new com.createmotorsport.item.SuspensionBlockItem(SUSPENSION.get(), new Item.Properties())
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EngineBlockEntity>> ENGINE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("engine_block", () -> BlockEntityType.Builder.of(
                    EngineBlockEntity::new,
                    ENGINE_BLOCK.get()
            ).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuspensionBlockEntity>> SUSPENSION_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("suspension", () -> BlockEntityType.Builder.of(
                    SuspensionBlockEntity::new,
                    SUSPENSION.get()
            ).build(null));
    public static final DeferredBlock<SteeringWheelBlock> STEERING_WHEEL = BLOCKS.register(
            "steering_wheel",
            () -> new SteeringWheelBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.5F, 6.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops())
    );
    public static final DeferredItem<BlockItem> STEERING_WHEEL_ITEM = ITEMS.registerSimpleBlockItem(
            "steering_wheel",
            STEERING_WHEEL
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteeringWheelBlockEntity>> STEERING_WHEEL_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("steering_wheel", () -> BlockEntityType.Builder.of(
                    SteeringWheelBlockEntity::new,
                    STEERING_WHEEL.get()
            ).build(null));
    public static final DeferredBlock<DownFlapBlock> DOWN_FLAP_BLOCK = BLOCKS.register(
            "down_flap",
            () -> new DownFlapBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2F, 6.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops())
    );
    public static final DeferredItem<BlockItem> DOWN_FLAP_ITEM = ITEMS.registerSimpleBlockItem(
            "down_flap",
            DOWN_FLAP_BLOCK
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DownFlapBlockEntity>> DOWN_FLAP_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("down_flap", () -> BlockEntityType.Builder.of(
                    DownFlapBlockEntity::new,
                    DOWN_FLAP_BLOCK.get()
            ).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<EngineMenu>> ENGINE_MENU = MENUS.register(
            "engine",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new EngineMenu(id, inv, buf.readBlockPos()))
    );
    public static final DeferredHolder<MenuType<?>, MenuType<SuspensionMenu>> SUSPENSION_MENU = MENUS.register(
            "suspension",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new SuspensionMenu(id, inv, buf.readBlockPos()))
    );
    public static final DeferredHolder<MenuType<?>, MenuType<SteeringWheelMenu>> STEERING_WHEEL_MENU = MENUS.register(
            "steering_wheel",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new SteeringWheelMenu(id, inv, buf.readBlockPos()))
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_IDLE = registerSound("engine_idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_LOW = registerSound("engine_low");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_MID = registerSound("engine_mid");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_FAST = registerSound("engine_fast");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MOTORSPORT_TAB =
            CREATIVE_MODE_TABS.register("motorsport", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createmotorsport"))
                    .withTabsBefore(CreativeModeTabs.REDSTONE_BLOCKS)
                    .icon(() -> ENGINE_BLOCK_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ENGINE_BLOCK_ITEM.get());
                        output.accept(SUSPENSION_ITEM.get());
                        output.accept(STEERING_WHEEL_ITEM.get());
                        output.accept(DOWN_FLAP_ITEM.get());
                        output.accept(AIR_INTAKE.get());
                        output.accept(EXHAUST_MANIFOLD.get());
                        output.accept(SUSPENSION_WRENCH.get());
                        output.accept(RACING_COMPONENT.get());
                        output.accept(RACING_TIRE_1.get());
                        output.accept(RACING_TIRE_2.get());
                        output.accept(RACING_TIRE_3.get());
                        output.accept(RACING_TIRE_4.get());
                        output.accept(RACING_TIRE_5.get());
                    })
                    .build());

    public CreateMotorsport(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        MENUS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);


        // how offroad applies the wheel mount force-- wheel impulses batched per physics substep and applied right before the step
        SableEventPlatform.INSTANCE.onPhysicsTick((physicsSystem, timeStep) ->
                SuspensionBlockEntity.flushBatchedForces(physicsSystem.getLevel(), timeStep));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        if (Config.ENABLE_DEBUG_LOGGING.getAsBoolean()) {
            LOGGER.info("Create: Motorsport common setup complete");
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SteeringInputPacket.TYPE, SteeringInputPacket.CODEC, SteeringInputPacket::handle);
        registrar.playToServer(SetSteeringKeyPacket.TYPE, SetSteeringKeyPacket.CODEC, SetSteeringKeyPacket::handle);
        registrar.playToServer(SetDrivingPacket.TYPE, SetDrivingPacket.CODEC, SetDrivingPacket::handle);
        registrar.playToServer(StartTelemetryLogPacket.TYPE, StartTelemetryLogPacket.CODEC, StartTelemetryLogPacket::handle);
        registrar.playToServer(SetDriveModePacket.TYPE, SetDriveModePacket.CODEC, SetDriveModePacket::handle);
        registrar.playToServer(ToggleAxleEndPacket.TYPE, ToggleAxleEndPacket.CODEC, ToggleAxleEndPacket::handle);
        registrar.playToServer(AdjustLiftPacket.TYPE, AdjustLiftPacket.CODEC, AdjustLiftPacket::handle);
        registrar.playToServer(ToggleEngineDirectionPacket.TYPE, ToggleEngineDirectionPacket.CODEC,
                ToggleEngineDirectionPacket::handle);
        registrar.playToClient(TelemetryLinePacket.TYPE, TelemetryLinePacket.CODEC, TelemetryLinePacket::handle);
        registrar.playToClient(com.createmotorsport.network.SkidmarkPacket.TYPE,
                com.createmotorsport.network.SkidmarkPacket.CODEC,
                com.createmotorsport.network.SkidmarkPacket::handle);
    }


    // register 'design load' tires
    private static DeferredItem<Item> registerTire(String name, double midpointKg) {
        float designLoad = (float) (midpointKg * 9.81 / 4.0);
        return ITEMS.register(name, () -> new Item(new Item.Properties()
                .stacksTo(16)
                .component(OffroadDataComponents.TIRE, new TireLike(12.0f / 16.0f,
                        net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3.ZERO, (ResourceLocation) null))
                .component(TIRE_DESIGN_LOAD, designLoad)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MODID, name)
        ));
    }
}
