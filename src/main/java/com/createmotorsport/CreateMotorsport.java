package com.createmotorsport;

import com.mojang.logging.LogUtils;
import com.createmotorsport.block.EngineBlock;
import com.createmotorsport.block.GearboxBlock;
import com.createmotorsport.block.ClutchBlock;
import com.createmotorsport.block.SuspensionBlock;
import com.createmotorsport.block.entity.ClutchBlockEntity;
import com.createmotorsport.block.entity.EngineBlockEntity;
import com.createmotorsport.block.entity.GearboxBlockEntity;
import com.createmotorsport.block.entity.SuspensionBlockEntity;
import com.createmotorsport.item.SuspensionWrenchItem;
import com.createmotorsport.menu.ClutchMenu;
import com.createmotorsport.menu.EngineMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.flag.FeatureFlags;
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

    public static final DeferredItem<Item> RACING_COMPONENT = ITEMS.registerSimpleItem(
            "racing_component",
            new Item.Properties()
    );
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
    public static final DeferredBlock<GearboxBlock> GEARBOX = BLOCKS.register(
            "gearbox",
            () -> new GearboxBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops())
    );
    public static final DeferredItem<BlockItem> GEARBOX_ITEM = ITEMS.registerSimpleBlockItem(
            "gearbox",
            GEARBOX
    );
    public static final DeferredBlock<ClutchBlock> CLUTCH = BLOCKS.register(
            "clutch",
            () -> new ClutchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops())
    );
    public static final DeferredItem<BlockItem> CLUTCH_ITEM = ITEMS.registerSimpleBlockItem(
            "clutch",
            CLUTCH
    );
    public static final DeferredBlock<SuspensionBlock> SUSPENSION = BLOCKS.register(
            "suspension",
            () -> new SuspensionBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops())
    );
    public static final DeferredItem<BlockItem> SUSPENSION_ITEM = ITEMS.registerSimpleBlockItem(
            "suspension",
            SUSPENSION
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EngineBlockEntity>> ENGINE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("engine_block", () -> BlockEntityType.Builder.of(
                    EngineBlockEntity::new,
                    ENGINE_BLOCK.get()
            ).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GearboxBlockEntity>> GEARBOX_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("gearbox", () -> BlockEntityType.Builder.of(
                    GearboxBlockEntity::new,
                    GEARBOX.get()
            ).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ClutchBlockEntity>> CLUTCH_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("clutch", () -> BlockEntityType.Builder.of(
                    ClutchBlockEntity::new,
                    CLUTCH.get()
            ).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuspensionBlockEntity>> SUSPENSION_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("suspension", () -> BlockEntityType.Builder.of(
                    SuspensionBlockEntity::new,
                    SUSPENSION.get()
            ).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<EngineMenu>> ENGINE_MENU = MENUS.register(
            "engine",
            () -> new MenuType<>(EngineMenu::new, FeatureFlags.VANILLA_SET)
    );
    public static final DeferredHolder<MenuType<?>, MenuType<ClutchMenu>> CLUTCH_MENU = MENUS.register(
            "clutch",
            () -> new MenuType<>(ClutchMenu::new, FeatureFlags.VANILLA_SET)
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
                        output.accept(GEARBOX_ITEM.get());
                        output.accept(CLUTCH_ITEM.get());
                        output.accept(SUSPENSION_ITEM.get());
                        output.accept(AIR_INTAKE.get());
                        output.accept(EXHAUST_MANIFOLD.get());
                        output.accept(SUSPENSION_WRENCH.get());
                        output.accept(RACING_COMPONENT.get());
                    })
                    .build());

    public CreateMotorsport(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        MENUS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        if (Config.ENABLE_DEBUG_LOGGING.getAsBoolean()) {
            LOGGER.info("Create: Motorsport common setup complete.");
        }
    }

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MODID, name)
        ));
    }
}
