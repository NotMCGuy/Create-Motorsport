package com.createmotorsport.engine;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.EngineBlock;
import com.createmotorsport.block.entity.EngineBlockEntity;
import com.createmotorsport.menu.EngineMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("createmotorsport")
@PrefixGameTestTemplate(false)
public final class EngineAttachmentGameTests {
    @GameTest(template = "trailer_empty", timeoutTicks = 40)
    public static void attachmentsPersistAndUpdateModels(GameTestHelper helper) {
        for (var block : new Block[]{CreateMotorsport.ENGINE_BLOCK.get(), CreateMotorsport.PASSENGER_ENGINE_BLOCK.get(), CreateMotorsport.TRUCK_ENGINE_BLOCK.get()}) {
            BlockPos pos = new BlockPos(2,2,2);
            helper.setBlock(pos, block);
            var engine = (EngineBlockEntity) helper.getBlockEntity(pos);
            engine.getInventory().setItem(0, Items.DIAMOND.getDefaultInstance());
            engine.getInventory().setItem(EngineBlockEntity.SLOT_EXHAUST, CreateMotorsport.EXHAUST_MANIFOLD.get().getDefaultInstance());
            engine.getInventory().setItem(EngineBlockEntity.SLOT_INTAKE, CreateMotorsport.AIR_INTAKE.get().getDefaultInstance());
            helper.assertTrue(engine.getBlockState().getValue(EngineBlock.HAS_EXHAUST) && engine.getBlockState().getValue(EngineBlock.HAS_INTAKE), "Both attachment models must be enabled");
            var registry = helper.getLevel().registryAccess();
            CompoundTag saved = engine.saveCustomOnly(registry);
            engine.getInventory().clearContent();
            engine.loadWithComponents(saved, registry);
            helper.assertTrue(engine.hasExhaust() && engine.hasIntake(), "Both attachments must survive reload");
            helper.assertTrue(engine.getInventory().getItem(0).is(Items.DIAMOND), "Wireless frequency must survive reload");
            var drops = Block.getDrops(engine.getBlockState(), helper.getLevel(), engine.getBlockPos(), engine);
            var data = drops.stream().filter(stack -> stack.is(block.asItem())).findFirst().orElseThrow().get(DataComponents.BLOCK_ENTITY_DATA);
            helper.assertTrue(data != null && data.copyTag().getInt("SlotLayout") == 2, "Engine drop must preserve inventory layout");
            engine.getInventory().clearContent();
            engine.loadWithComponents(data.copyTag(), registry);
            helper.assertTrue(engine.hasExhaust() && engine.hasIntake() && engine.getInventory().getItem(0).is(Items.DIAMOND), "Dropped engine must preserve attachments and links together");
            engine.getInventory().removeItem(EngineBlockEntity.SLOT_INTAKE, 1);
            helper.assertTrue(!engine.getBlockState().getValue(EngineBlock.HAS_INTAKE) && engine.hasExhaust(), "Removing intake updates only its model");
        }
        helper.succeed();
    }

    @GameTest(template = "trailer_empty", timeoutTicks = 40)
    public static void attachmentInventoryMigratesBothOldLayouts(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2,2,2), CreateMotorsport.ENGINE_BLOCK.get());
        var engine = (EngineBlockEntity) helper.getBlockEntity(new BlockPos(2,2,2));
        var registry = helper.getLevel().registryAccess();
        for (int layout = 0; layout <= 1; layout++) {
            CompoundTag tag = new CompoundTag();
            var items = NonNullList.withSize(EngineBlockEntity.SLOT_COUNT, ItemStack.EMPTY);
            int offset = layout == 0 ? 2 : 0;
            if (layout == 0) {
                items.set(0, CreateMotorsport.EXHAUST_MANIFOLD.get().getDefaultInstance());
                items.set(1, CreateMotorsport.AIR_INTAKE.get().getDefaultInstance());
            } else tag.putInt("SlotLayout", 1);
            for (int i = 0; i < EngineBlockEntity.CHANNELS.length * 2; i++)
                items.set(i + offset, (i % 2 == 0 ? Items.DIAMOND : Items.EMERALD).getDefaultInstance());
            ContainerHelper.saveAllItems(tag, items, registry);
            engine.loadWithComponents(tag, registry);
            for (int i = 0; i < EngineBlockEntity.CHANNELS.length * 2; i++)
                helper.assertTrue(engine.getInventory().getItem(i).is(i % 2 == 0 ? Items.DIAMOND : Items.EMERALD), "Every channel must survive layout " + layout);
            helper.assertTrue(engine.hasExhaust() == (layout == 0) && engine.hasIntake() == (layout == 0), "Only the original layout contains attachments");
        }
        helper.succeed();
    }

    @GameTest(template = "trailer_empty", timeoutTicks = 40)
    public static void attachmentMenuRoutesShiftClicks(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2,2,2), CreateMotorsport.ENGINE_BLOCK.get());
        var engine = (EngineBlockEntity) helper.getBlockEntity(new BlockPos(2,2,2));
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var menu = new EngineMenu(0, player.getInventory(), engine);
        player.getInventory().setItem(0, new ItemStack(CreateMotorsport.AIR_INTAKE.get(), 3));
        menu.quickMoveStack(player, EngineBlockEntity.SLOT_COUNT + 27);
        helper.assertTrue(engine.hasIntake() && engine.getInventory().getItem(EngineBlockEntity.SLOT_INTAKE).getCount() == 1, "Shift click fits exactly one intake");
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 2 && engine.getInventory().getItem(0).isEmpty(), "Extra parts stay in player inventory, not frequency slots");
        helper.assertTrue(!menu.slots.get(EngineBlockEntity.SLOT_EXHAUST).mayPlace(Items.DIRT.getDefaultInstance()), "Attachment slots reject unrelated items");
        menu.quickMoveStack(player, EngineBlockEntity.SLOT_INTAKE);
        helper.assertTrue(!engine.hasIntake(), "Shift click removes fitted intake");
        menu.removed(player);
        helper.succeed();
    }
}
