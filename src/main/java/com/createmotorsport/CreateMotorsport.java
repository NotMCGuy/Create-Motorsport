package com.createmotorsport;

import com.createmotorsport.registry.SpecRegistries;
import com.createmotorsport.physics.world.Surfaces;
import com.createmotorsport.physics.world.Gravity;
import com.createmotorsport.block.EngineBlock;
import com.createmotorsport.block.SteeringWheelBlock;
import com.createmotorsport.block.SuspensionBlock;
import com.createmotorsport.block.DownFlapBlock;
import com.createmotorsport.block.entity.DownFlapBlockEntity;
import com.createmotorsport.block.entity.EngineBlockEntity;
import com.createmotorsport.block.entity.LapGateBlockEntity;
import com.createmotorsport.block.entity.SteeringWheelBlockEntity;
import com.createmotorsport.block.entity.SuspensionBlockEntity;
import com.createmotorsport.menu.EngineMenu;
import com.createmotorsport.menu.SteeringWheelMenu;
import com.createmotorsport.menu.SuspensionMenu;
import com.createmotorsport.network.SetCarAidPacket;
import com.createmotorsport.network.SetDrivingPacket;
import com.createmotorsport.network.SetSteeringKeyPacket;
import com.createmotorsport.network.StartTelemetryLogPacket;
import com.createmotorsport.network.ToggleAxlePacket;
import com.createmotorsport.network.TunePacket;
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

    public static final DeferredBlock<com.createmotorsport.block.FuelTankBlock> FUEL_TANK = BLOCKS.register(
            "fuel_tank", () -> new com.createmotorsport.block.FuelTankBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).noOcclusion()));
    public static final DeferredItem<BlockItem> FUEL_TANK_ITEM = ITEMS.registerSimpleBlockItem("fuel_tank", FUEL_TANK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.createmotorsport.block.entity.FuelTankBlockEntity>> FUEL_TANK_ENTITY =
            BLOCK_ENTITY_TYPES.register("fuel_tank", () -> BlockEntityType.Builder.of(
                    com.createmotorsport.block.entity.FuelTankBlockEntity::new, FUEL_TANK.get()).build(null));

    public static final DeferredBlock<com.createmotorsport.block.FuelPumpBlock> FUEL_PUMP = BLOCKS.register(
            "fuel_pump", () -> new com.createmotorsport.block.FuelPumpBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.5F, 6.0F).noOcclusion()));
    public static final DeferredItem<BlockItem> FUEL_PUMP_ITEM = ITEMS.registerSimpleBlockItem("fuel_pump", FUEL_PUMP);
    public static final DeferredItem<com.createmotorsport.item.FuelNozzleItem> FUEL_NOZZLE = ITEMS.register(
            "fuel_nozzle", () -> new com.createmotorsport.item.FuelNozzleItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.createmotorsport.block.entity.FuelPumpBlockEntity>> FUEL_PUMP_ENTITY =
            BLOCK_ENTITY_TYPES.register("fuel_pump", () -> BlockEntityType.Builder.of(
                    com.createmotorsport.block.entity.FuelPumpBlockEntity::new, FUEL_PUMP.get()).build(null));

    public static final DataComponentType<Float> TIRE_DESIGN_LOAD = DataComponentType.<Float>builder()
            .persistent(Codec.FLOAT)
            .networkSynchronized(ByteBufCodecs.FLOAT)
            .build();
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> TIRE_DESIGN_LOAD_HOLDER =
            DATA_COMPONENTS.register("tire_design_load", () -> TIRE_DESIGN_LOAD);

    public static final DataComponentType<Float> TIRE_VERTICAL_DEFLECTION = DataComponentType.<Float>builder()
            .persistent(Codec.FLOAT)
            .networkSynchronized(ByteBufCodecs.FLOAT)
            .build();
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> TIRE_VERTICAL_DEFLECTION_HOLDER =
            DATA_COMPONENTS.register("tire_vertical_deflection", () -> TIRE_VERTICAL_DEFLECTION);

    public static final DataComponentType<Float> TIRE_VERTICAL_DAMPING = DataComponentType.<Float>builder()
            .persistent(Codec.FLOAT)
            .networkSynchronized(ByteBufCodecs.FLOAT)
            .build();
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> TIRE_VERTICAL_DAMPING_HOLDER =
            DATA_COMPONENTS.register("tire_vertical_damping", () -> TIRE_VERTICAL_DAMPING);

    public static final DataComponentType<Float> TIRE_MASS = DataComponentType.<Float>builder()
            .persistent(Codec.FLOAT)
            .networkSynchronized(ByteBufCodecs.FLOAT)
            .build();
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> TIRE_MASS_HOLDER =
            DATA_COMPONENTS.register("tire_mass", () -> TIRE_MASS);
    public static final DataComponentType<Float> TIRE_WIDTH = DataComponentType.<Float>builder()
            .persistent(Codec.FLOAT)
            .networkSynchronized(ByteBufCodecs.FLOAT)
            .build();
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> TIRE_WIDTH_HOLDER =
            DATA_COMPONENTS.register("tire_width", () -> TIRE_WIDTH);

    public static final DataComponentType<ResourceLocation> TIRE_COMPOUND = DataComponentType.<ResourceLocation>builder()
            .persistent(ResourceLocation.CODEC)
            .networkSynchronized(ResourceLocation.STREAM_CODEC)
            .build();
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> TIRE_COMPOUND_HOLDER =
            DATA_COMPONENTS.register("tire_compound", () -> TIRE_COMPOUND);

    public static final DataComponentType<net.minecraft.nbt.CompoundTag> TIRE_COMPOUND_OVERRIDES =
            DataComponentType.<net.minecraft.nbt.CompoundTag>builder()
                    .persistent(net.minecraft.nbt.CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
                    .build();
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.nbt.CompoundTag>>
            TIRE_COMPOUND_OVERRIDES_HOLDER = DATA_COMPONENTS.register("tire_compound_overrides",
                    () -> TIRE_COMPOUND_OVERRIDES);

    public static final DataComponentType<ResourceLocation> TIRE = DataComponentType.<ResourceLocation>builder()
            .persistent(ResourceLocation.CODEC)
            .networkSynchronized(ResourceLocation.STREAM_CODEC)
            .build();
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> TIRE_HOLDER =
            DATA_COMPONENTS.register("tire", () -> TIRE);

    private static final float RACING_TIRE_RADIUS = 12.0f / 16.0f;
    private static final float TRUCK_TIRE_RADIUS = 15.5f / 16.0f;
    private static final float SMALL_TIRE_RADIUS = 12.0f / 16.0f;

    private static final net.minecraft.world.phys.Vec3 UPRIGHT = net.minecraft.world.phys.Vec3.ZERO;
    private static final net.minecraft.world.phys.Vec3 STAND_UP = new net.minecraft.world.phys.Vec3(90.0, 0.0, 0.0);

    public static final DeferredItem<Item> RACING_TIRE = registerTire("racing_tire", 65, RACING_TIRE_RADIUS, UPRIGHT);

    public static final DeferredItem<Item> RACING_TIRE_1 = registerTire("racing_tire_1", 20, RACING_TIRE_RADIUS, UPRIGHT);
    public static final DeferredItem<Item> RACING_TIRE_2 = registerTire("racing_tire_2", 36, RACING_TIRE_RADIUS, UPRIGHT);
    public static final DeferredItem<Item> RACING_TIRE_3 = registerTire("racing_tire_3", 65, RACING_TIRE_RADIUS, UPRIGHT);
    public static final DeferredItem<Item> RACING_TIRE_4 = registerTire("racing_tire_4", 117, RACING_TIRE_RADIUS, UPRIGHT);
    public static final DeferredItem<Item> RACING_TIRE_5 = registerTire("racing_tire_5", 210, RACING_TIRE_RADIUS, UPRIGHT);

    public static final DeferredItem<Item> TRUCK_TIRE = registerTire("truck_tire", 200, TRUCK_TIRE_RADIUS, STAND_UP);

    public static final DeferredItem<Item> SMALL_TIRE = registerTire("small_tire", 65, SMALL_TIRE_RADIUS, STAND_UP);

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
    public static final DeferredBlock<EngineBlock> TRUCK_ENGINE_BLOCK = BLOCKS.register(
            "truck_engine_block",
            () -> new EngineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops())
    );
    public static final DeferredItem<BlockItem> TRUCK_ENGINE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(
            "truck_engine_block",
            TRUCK_ENGINE_BLOCK
    );
    public static final DeferredBlock<EngineBlock> PASSENGER_ENGINE_BLOCK = BLOCKS.register(
            "passenger_engine_block",
            () -> new EngineBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops())
    );
    public static final DeferredItem<BlockItem> PASSENGER_ENGINE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(
            "passenger_engine_block",
            PASSENGER_ENGINE_BLOCK
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
    public static final DeferredBlock<SuspensionBlock> PASSENGER_SUSPENSION = BLOCKS.register(
            "passenger_suspension",
            () -> new SuspensionBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops())
    );
    public static final DeferredItem<com.createmotorsport.item.SuspensionBlockItem> PASSENGER_SUSPENSION_ITEM =
            ITEMS.register("passenger_suspension",
                    () -> new com.createmotorsport.item.SuspensionBlockItem(
                            PASSENGER_SUSPENSION.get(), new Item.Properties()));
    public static final DeferredBlock<SuspensionBlock> TRUCK_SUSPENSION = BLOCKS.register(
            "truck_suspension",
            () -> new SuspensionBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops())
    );
    public static final DeferredItem<com.createmotorsport.item.SuspensionBlockItem> TRUCK_SUSPENSION_ITEM =
            ITEMS.register("truck_suspension",
                    () -> new com.createmotorsport.item.SuspensionBlockItem(
                            TRUCK_SUSPENSION.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EngineBlockEntity>> ENGINE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("engine_block", () -> BlockEntityType.Builder.of(
                    EngineBlockEntity::new,
                    ENGINE_BLOCK.get(), PASSENGER_ENGINE_BLOCK.get(), TRUCK_ENGINE_BLOCK.get()
            ).build(null));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> GATE_FIRST_POST =
            DATA_COMPONENTS.register("gate_first_post", () -> DataComponentType.<Long>builder()
                    .persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG)
                    .build());

    public static final DeferredBlock<com.createmotorsport.block.LapGateBlock> LAP_GATE = BLOCKS.register(
            "lap_gate",
            () -> new com.createmotorsport.block.LapGateBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(2.0F, 6.0F)
                    .noOcclusion())
    );
    public static final DeferredItem<com.createmotorsport.item.LapGateBlockItem> LAP_GATE_ITEM = ITEMS.register(
            "lap_gate",
            () -> new com.createmotorsport.item.LapGateBlockItem(LAP_GATE.get(), new Item.Properties())
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LapGateBlockEntity>> LAP_GATE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("lap_gate", () -> BlockEntityType.Builder.of(
                    LapGateBlockEntity::new,
                    LAP_GATE.get()
            ).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuspensionBlockEntity>> SUSPENSION_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("suspension", () -> BlockEntityType.Builder.of(
                    SuspensionBlockEntity::new,
                    SUSPENSION.get(), PASSENGER_SUSPENSION.get(), TRUCK_SUSPENSION.get()
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
            () -> IMenuTypeExtension.create((id, inv, buf) -> new EngineMenu(id, inv, buf.readBlockPos(),
                    buf.readBoolean()))
    );
    public static final DeferredHolder<MenuType<?>, MenuType<SuspensionMenu>> SUSPENSION_MENU = MENUS.register(
            "suspension",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new SuspensionMenu(id, inv, buf.readBlockPos(),
                    buf.readBoolean()))
    );
    public static final DeferredHolder<MenuType<?>, MenuType<SteeringWheelMenu>> STEERING_WHEEL_MENU = MENUS.register(
            "steering_wheel",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new SteeringWheelMenu(id, inv, buf.readBlockPos()))
    );
    public static final DeferredHolder<MenuType<?>, MenuType<com.createmotorsport.menu.LapGateMenu>> LAP_GATE_MENU =
            MENUS.register("lap_gate",
                    () -> IMenuTypeExtension.create((id, inv, buf) ->
                            new com.createmotorsport.menu.LapGateMenu(id, inv, buf.readBlockPos())));
    public static final DeferredItem<Item> EXHAUST_MANIFOLD = ITEMS.registerSimpleItem("exhaust_manifold", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> AIR_INTAKE = ITEMS.registerSimpleItem("air_intake", new Item.Properties().stacksTo(16));

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
                        output.accept(EXHAUST_MANIFOLD.get());
                        output.accept(AIR_INTAKE.get());
                        output.accept(FUEL_PUMP_ITEM.get());
                        output.accept(FUEL_TANK_ITEM.get());
                        output.accept(com.createmotorsport.trailer.TrailerRegistry.FIFTH_WHEEL.get());
                        output.accept(com.createmotorsport.trailer.TrailerRegistry.LARGE_FIFTH_WHEEL.get());
                        output.accept(com.createmotorsport.trailer.TrailerRegistry.KINGPIN.get());
                        output.accept(com.createmotorsport.trailer.TrailerRegistry.WIDE_KINGPIN.get());
                        output.accept(com.createmotorsport.trailer.TrailerRegistry.TOW_BALL.get());
                        output.accept(com.createmotorsport.trailer.TrailerRegistry.COUPLING_HEAD.get());
                        output.accept(com.createmotorsport.trailer.TrailerRegistry.LANDING_LEGS.get());
                        output.accept(com.createmotorsport.trailer.TrailerRegistry.CONTROL_PANEL.get());
                        output.accept(com.createmotorsport.trailer.TrailerRegistry.GLOVEBOX.get());
                        output.accept(com.createmotorsport.trailer.TrailerRegistry.LINKER.get());
                        output.accept(ENGINE_BLOCK_ITEM.get());
                        output.accept(PASSENGER_ENGINE_BLOCK_ITEM.get());
                        output.accept(TRUCK_ENGINE_BLOCK_ITEM.get());
                        output.accept(SUSPENSION_ITEM.get());
                        output.accept(PASSENGER_SUSPENSION_ITEM.get());
                        output.accept(TRUCK_SUSPENSION_ITEM.get());
                        output.accept(STEERING_WHEEL_ITEM.get());
                        output.accept(DOWN_FLAP_ITEM.get());
                        output.accept(LAP_GATE_ITEM.get());
                        acceptTires(parameters, output);
                    })
                    .build());

    private static void acceptTires(CreativeModeTab.ItemDisplayParameters parameters,
                                    CreativeModeTab.Output output) {
        parameters.holders().lookup(com.createmotorsport.registry.SpecRegistries.TIRE)
                .ifPresent(lookup -> lookup.listElements()
                        .sorted(java.util.Comparator.comparing(
                                (net.minecraft.core.Holder.Reference<com.createmotorsport.registry.TireEntry> ref)
                                        -> ref.key().location().toString()))
                        .forEach(ref -> output.accept(
                                ref.value().asStack(ref.key().location()))));
    }

    public CreateMotorsport(IEventBus modEventBus, ModContainer modContainer) {
        com.createmotorsport.trailer.TrailerRegistry.init();
        modContainer.registerConfig(ModConfig.Type.SERVER, com.createmotorsport.trailer.TrailerConfig.SPEC, "createmotorsport-trailers.toml");
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(com.createmotorsport.registry.SpecRegistries::register);

        modEventBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                        FUEL_PUMP_ENTITY.get(), (pump, side) -> pump.fluidAccess));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                com.createmotorsport.physics.world.Surfaces::checkTags);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.createmotorsport.item.FuelNozzleItem::cleanup);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.createmotorsport.item.FuelNozzleItem::logout);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.createmotorsport.trailer.TrailerTestRig::register);
        modEventBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                        FUEL_TANK_ENTITY.get(), (tank, side) -> tank.tank));
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        MENUS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);


        SableEventPlatform.INSTANCE.onPhysicsTick((physicsSystem, timeStep) -> {
            com.createmotorsport.vehicle.CarAssembler.endPhysicsSubstep(physicsSystem.getLevel(), timeStep);
        });
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
        registrar.playToServer(com.createmotorsport.network.RaceControlPacket.TYPE,
                com.createmotorsport.network.RaceControlPacket.CODEC,
                com.createmotorsport.network.RaceControlPacket::handle);
        registrar.playToServer(com.createmotorsport.network.SetEntrantPacket.TYPE,
                com.createmotorsport.network.SetEntrantPacket.CODEC,
                com.createmotorsport.network.SetEntrantPacket::handle);
        registrar.playToClient(com.createmotorsport.network.GhostSyncPacket.TYPE,
                com.createmotorsport.network.GhostSyncPacket.CODEC,
                com.createmotorsport.network.GhostSyncPacket::handle);
        registrar.playToServer(SetDrivingPacket.TYPE, SetDrivingPacket.CODEC, SetDrivingPacket::handle);
        registrar.playToServer(StartTelemetryLogPacket.TYPE, StartTelemetryLogPacket.CODEC, StartTelemetryLogPacket::handle);
        registrar.playToServer(SetCarAidPacket.TYPE, SetCarAidPacket.CODEC, SetCarAidPacket::handle);
        registrar.playToClient(com.createmotorsport.network.OpenPartScreenPacket.TYPE,
                com.createmotorsport.network.OpenPartScreenPacket.CODEC,
                com.createmotorsport.network.OpenPartScreenPacket::handle);
        registrar.playToServer(com.createmotorsport.network.PartStatusRequestPacket.TYPE,
                com.createmotorsport.network.PartStatusRequestPacket.CODEC,
                com.createmotorsport.network.PartStatusRequestPacket::handle);
        registrar.playToClient(com.createmotorsport.network.PartStatusPacket.TYPE,
                com.createmotorsport.network.PartStatusPacket.CODEC,
                com.createmotorsport.network.PartStatusPacket::handle);
        registrar.playToServer(com.createmotorsport.network.OpenWirelessPacket.TYPE,
                com.createmotorsport.network.OpenWirelessPacket.CODEC,
                com.createmotorsport.network.OpenWirelessPacket::handle);
        registrar.playToServer(ToggleAxlePacket.TYPE, ToggleAxlePacket.CODEC, ToggleAxlePacket::handle);
        registrar.playToServer(TunePacket.TYPE, TunePacket.CODEC, TunePacket::handle);
        registrar.playToServer(com.createmotorsport.network.TuneHeldPacket.TYPE,
                com.createmotorsport.network.TuneHeldPacket.CODEC,
                com.createmotorsport.network.TuneHeldPacket::handle);
        registrar.playToClient(com.createmotorsport.network.OpenTuneHeldPacket.TYPE,
                com.createmotorsport.network.OpenTuneHeldPacket.CODEC,
                com.createmotorsport.network.OpenTuneHeldPacket::handle);
        registrar.playToServer(ToggleEngineDirectionPacket.TYPE, ToggleEngineDirectionPacket.CODEC,
                ToggleEngineDirectionPacket::handle);
        registrar.playToClient(TelemetryLinePacket.TYPE, TelemetryLinePacket.CODEC, TelemetryLinePacket::handle);
        registrar.playToClient(com.createmotorsport.network.SkidmarkPacket.TYPE,
                com.createmotorsport.network.SkidmarkPacket.CODEC,
                com.createmotorsport.network.SkidmarkPacket::handle);
    }


    private static DeferredItem<Item> registerTire(String name, double midpointKg, float radius,
                                                   net.minecraft.world.phys.Vec3 rotation) {
        return registerTire(name, midpointKg, radius, rotation, null);
    }

    private static DeferredItem<Item> registerTire(String name, double midpointKg, float radius,
                                                   net.minecraft.world.phys.Vec3 rotation,
                                                   @org.jetbrains.annotations.Nullable ResourceLocation model) {
        float designLoad = (float) (midpointKg * com.createmotorsport.physics.world.Gravity.DEFAULT / 4.0);
        return ITEMS.register(name, () -> new com.createmotorsport.item.TireItem(new Item.Properties()
                .stacksTo(16)
                .component(OffroadDataComponents.TIRE, new TireLike(radius,
                        rotation, net.minecraft.world.phys.Vec3.ZERO, model))
                .component(TIRE_DESIGN_LOAD, designLoad)
                .component(TIRE_VERTICAL_DEFLECTION,
                        com.createmotorsport.registry.TireEntry.DEFAULT_VERTICAL_DEFLECTION)
                .component(TIRE_VERTICAL_DAMPING,
                        com.createmotorsport.registry.TireEntry.DEFAULT_VERTICAL_DAMPING)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MODID, name)
        ));
    }
}
