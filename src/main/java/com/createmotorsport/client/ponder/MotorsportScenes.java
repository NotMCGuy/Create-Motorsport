package com.createmotorsport.client.ponder;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.EngineBlockEntity;
import com.createmotorsport.block.entity.FuelTankBlockEntity;
import com.createmotorsport.block.entity.SuspensionBlockEntity;
import com.createmotorsport.block.entity.SuspensionBlockEntity.WheelSide;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

public final class MotorsportScenes {
    private MotorsportScenes() { }
    private static void start(SceneBuilder scene, String id, String title) {
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(.8f);
        scene.showBasePlate();
        scene.idle(10);
    }
    private static void text(SceneBuilder scene, String key, BlockPos target) {
        scene.overlay().showText(90).sharedText(key).pointAt(Vec3.atCenterOf(target)).placeNearTarget().attachKeyFrame();
    }
    private static void tyres(SceneBuilder scene, BlockPos axle, boolean bike) {
        scene.world().modifyBlockEntity(axle, SuspensionBlockEntity.class, be -> {
            be.installTire(WheelSide.LEFT, CreateMotorsport.RACING_TIRE.get().getDefaultInstance());
            if (!bike) be.installTire(WheelSide.RIGHT, CreateMotorsport.RACING_TIRE.get().getDefaultInstance());
        });
    }
    public static void attachments(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "engine_attachments", "Fitting engine attachments");
        BlockPos engine = new BlockPos(3, 1, 3);
        scene.world().showSection(util.select().position(engine), Direction.DOWN);
        text(scene, "parts_intro", engine);
        scene.overlay().showControls(Vec3.atCenterOf(engine).add(0,1,0), Pointing.DOWN, 55).rightClick().withItem(CreateMotorsport.EXHAUST_MANIFOLD.get().getDefaultInstance());
        scene.idle(30);
        scene.world().modifyBlockEntity(engine, EngineBlockEntity.class, be -> be.getInventory().setItem(EngineBlockEntity.SLOT_EXHAUST, CreateMotorsport.EXHAUST_MANIFOLD.get().getDefaultInstance()));
        scene.world().modifyBlock(engine, state -> state.setValue(com.createmotorsport.block.EngineBlock.HAS_EXHAUST, true), false);
        scene.idle(70);
        text(scene, "parts_intake", engine);
        scene.overlay().showControls(Vec3.atCenterOf(engine).add(0,1,0), Pointing.DOWN, 55).rightClick().withItem(CreateMotorsport.AIR_INTAKE.get().getDefaultInstance());
        scene.idle(30);
        scene.world().modifyBlockEntity(engine, EngineBlockEntity.class, be -> be.getInventory().setItem(EngineBlockEntity.SLOT_INTAKE, CreateMotorsport.AIR_INTAKE.get().getDefaultInstance()));
        scene.world().modifyBlock(engine, state -> state.setValue(com.createmotorsport.block.EngineBlock.HAS_INTAKE, true), false);
        scene.idle(70);
        text(scene, "parts_remove", engine);
        scene.overlay().showControls(Vec3.atCenterOf(engine).add(0,1,0), Pointing.DOWN, 50).rightClick();
        scene.idle(100);
        scene.rotateCameraY(90);
        text(scene, "parts_save", engine);
        scene.idle(100);
        scene.markAsFinished();
    }
    public static void refuelling(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "refuelling", "Refuelling a vehicle");
        BlockPos pump = new BlockPos(1,1,3), tank = new BlockPos(4,1,3), engine = new BlockPos(4,1,2);
        scene.world().showSection(util.select().fromTo(1,1,1,1,1,3), Direction.DOWN);
        text(scene, "fuel_supply", pump);
        scene.idle(100);
        scene.world().showSection(util.select().fromTo(4,1,2,4,1,3), Direction.DOWN);
        text(scene, "fuel_take", pump);
        scene.overlay().showControls(Vec3.atCenterOf(pump).add(0,1,0), Pointing.DOWN, 60).rightClick();
        scene.idle(100);
        text(scene, "fuel_fill", tank);
        scene.overlay().showControls(Vec3.atCenterOf(tank).add(0,1,0), Pointing.DOWN, 60).rightClick().withItem(CreateMotorsport.FUEL_NOZZLE.get().getDefaultInstance());
        scene.overlay().showLine(PonderPalette.GREEN, Vec3.atCenterOf(pump), Vec3.atCenterOf(tank), 90);
        scene.world().modifyBlockEntity(tank, FuelTankBlockEntity.class, be -> be.tank.setFluid(new FluidStack(Fluids.LAVA, 1000)));
        scene.idle(100);
        text(scene, "fuel_return", pump);
        scene.overlay().showControls(Vec3.atCenterOf(pump).add(0,1,0), Pointing.DOWN, 60).rightClick().withItem(CreateMotorsport.FUEL_NOZZLE.get().getDefaultInstance());
        scene.idle(100);
        text(scene, "fuel_types", engine);
        scene.idle(100);
        scene.markAsFinished();
    }
    public static void suspension(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "suspension", "Suspension and tyres");
        BlockPos front = new BlockPos(3,1,1), rear = new BlockPos(3,1,5);
        scene.world().showSection(util.select().fromTo(3,1,1,3,2,5), Direction.DOWN);
        text(scene, "axle_fit", front);
        scene.overlay().showControls(Vec3.atCenterOf(front).add(1,1,0), Pointing.DOWN, 60).rightClick().withItem(CreateMotorsport.RACING_TIRE.get().getDefaultInstance());
        scene.idle(30);
        tyres(scene, front, false); tyres(scene, rear, false);
        scene.idle(70);
        text(scene, "axle_settings", front);
        scene.overlay().showControls(Vec3.atCenterOf(front).add(0,1,0), Pointing.DOWN, 60).rightClick();
        scene.idle(100);
        text(scene, "axle_remove", rear);
        scene.overlay().showControls(Vec3.atCenterOf(rear).add(1,1,0), Pointing.DOWN, 60).rightClick().whileSneaking();
        scene.idle(100);
        text(scene, "axle_body", new BlockPos(3,1,3));
        scene.idle(100);
        scene.markAsFinished();
    }
    public static void motorcycle(SceneBuilder scene, SceneBuildingUtil util) {
        start(scene, "motorcycle", "Building a motorcycle");
        BlockPos engine = new BlockPos(3,1,2), rear = new BlockPos(3,1,4), fork = new BlockPos(3,1,1);
        scene.world().showSection(util.select().fromTo(3,1,2,3,1,4), Direction.DOWN);
        text(scene, "bike_drive", new BlockPos(3,1,3));
        scene.idle(100);
        scene.world().showSection(util.select().position(fork), Direction.DOWN);
        text(scene, "bike_wheels", fork);
        scene.overlay().showControls(Vec3.atCenterOf(fork).add(0,1,0), Pointing.DOWN, 60).rightClick().withItem(CreateMotorsport.RACING_TIRE.get().getDefaultInstance());
        scene.idle(30);
        tyres(scene, fork, true); tyres(scene, rear, true);
        scene.idle(70);
        scene.world().showSection(util.select().position(3,2,1), Direction.DOWN);
        scene.world().showSection(util.select().position(3,2,3), Direction.DOWN);
        text(scene, "bike_controls", new BlockPos(3,2,3));
        scene.idle(100);
        scene.world().showSection(util.select().position(3,2,2), Direction.DOWN);
        text(scene, "bike_fuel", new BlockPos(3,2,2));
        scene.idle(100);
        scene.rotateCameraY(90);
        text(scene, "bike_assemble", engine);
        scene.idle(100);
        scene.markAsFinished();
    }
}
