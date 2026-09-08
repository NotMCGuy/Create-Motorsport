package com.createmotorsport.trailer;

import com.createmotorsport.CreateMotorsport;
import com.createmotorsport.block.entity.SuspensionBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.joml.Vector3d;
import java.util.List;

public final class TrailerTestRig {
    private TrailerTestRig() {}
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("createmotorsport").requires(source->source.hasPermission(2))
                .then(Commands.literal("trailer_rig").executes(context->{
                    var source=context.getSource();
                    var level=source.getLevel();
                    var origin=BlockPos.containing(source.getPosition()).offset(8,0,8);
                    if(dev.ryanhcode.sable.Sable.HELPER.getContaining(level,origin)!=null) {
                        source.sendFailure(Component.literal("Place the trailer rig in the main world."));return 0;
                    }
                    for(BlockPos pos:BlockPos.betweenClosed(origin,origin.offset(31,8,23))) {
                        if(!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) {
                            source.sendFailure(Component.literal("The rig needs a loaded, empty 32 x 9 x 24 area starting eight blocks east and south of you."));return 0;
                        }
                    }
                    for(BlockPos pos:BlockPos.betweenClosed(origin,origin.offset(31,0,23))) {
                        level.setBlock(pos,(pos.getX()%4==0?Blocks.YELLOW_CONCRETE:Blocks.SMOOTH_STONE).defaultBlockState(),3);
                    }
                    build(level,origin,source.getEntity() instanceof ServerPlayer player?player:null);
                    source.sendSuccess(()->Component.literal("Trailer rig placed at "+origin.toShortString()+". Four independent Sable bodies; use the linked panels and Equipment Linker. See docs/trailer-couplings.md for driving and safety tests."),true);
                    return 1;
                })));
    }
    public static ServerSubLevel body(ServerLevel level,Vector3d position) {
        Pose3d pose=new Pose3d();pose.position().set(position);
        var body=(ServerSubLevel)SubLevelContainer.getContainer(level).allocateNewSubLevel(pose);
        var center=body.getPlot().getCenterChunk();
        for(int x=-1;x<=0;x++)for(int z=-1;z<=0;z++)body.getPlot().newEmptyChunk(new ChunkPos(center.x+x,center.z+z));
        return body;
    }
    public static void place(ServerSubLevel body,BlockPos offset,BlockState state) {
        body.getPlot().getEmbeddedLevelAccessor().setBlock(offset,state,3);
    }
    public static void finishPlacement(ServerSubLevel body,Vector3d origin) {
        body.updateMergedMassData(1.0f);
        var center=body.getPlot().getCenterBlock();
        var mass=body.getMassTracker().getCenterOfMass();
        var position=new Vector3d(origin).add(mass).sub(center.getX(),center.getY(),center.getZ());
        SubLevelContainer.getContainer(body.getLevel()).physicsSystem().getPipeline().teleport(body,position,body.logicalPose().orientation());
        body.updateLastPose();
    }
    public static EquipmentBlockEntity equipment(ServerSubLevel body,BlockPos offset,EquipmentBlock block) {
        place(body,offset,block.defaultBlockState());
        return (EquipmentBlockEntity)body.getLevel().getBlockEntity(body.getPlot().getCenterBlock().offset(offset));
    }
    private static void axle(ServerSubLevel body,int y,int z) {
        BlockPos pos=new BlockPos(4,y,z);place(body,pos,CreateMotorsport.SUSPENSION.get().defaultBlockState());
        var be=(SuspensionBlockEntity)body.getLevel().getBlockEntity(body.getPlot().getCenterBlock().offset(pos));
        for(var side:SuspensionBlockEntity.WheelSide.values())be.installTire(side,new ItemStack(CreateMotorsport.RACING_TIRE_1.get()));
    }
    private static void vehicle(ServerSubLevel body,boolean powered,boolean semi) {
        int top=semi?1:0;
        for(int z=1;z<=7;z++)place(body,new BlockPos(4,top,z),Blocks.IRON_BLOCK.defaultBlockState());
        axle(body,semi?-1:0,6);axle(body,semi?-1:0,semi?7:1);
        if(semi)for(int z=6;z<=7;z++)place(body,new BlockPos(4,0,z),Blocks.IRON_BLOCK.defaultBlockState());
        if(powered) {
            place(body,new BlockPos(4,1,2),CreateMotorsport.ENGINE_BLOCK.get().defaultBlockState());
            place(body,new BlockPos(4,1,1),CreateMotorsport.STEERING_WHEEL.get().defaultBlockState());
            BlockPos tankPos=new BlockPos(5,1,2);
            place(body,tankPos,CreateMotorsport.FUEL_TANK.get().defaultBlockState());
            var tank=(com.createmotorsport.block.entity.FuelTankBlockEntity)body.getLevel().getBlockEntity(body.getPlot().getCenterBlock().offset(tankPos));
            tank.tank.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA,8000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        }
    }
    private static void panel(ServerSubLevel body,EquipmentBlockEntity hitch,EquipmentBlockEntity legs,ServerPlayer player) {
        var panel=equipment(body,new BlockPos(5,1,3),TrailerRegistry.CONTROL_PANEL.get());
        place(body,new BlockPos(5,0,3),Blocks.IRON_BLOCK.defaultBlockState());
        place(body,new BlockPos(6,0,3),Blocks.IRON_BLOCK.defaultBlockState());
        place(body,new BlockPos(6,1,3),Blocks.LEVER.defaultBlockState());
        if(player!=null) {panel.setOwner(player.getUUID());panel.link(hitch,player);if(legs!=null)panel.link(legs,player);}
    }
    public static List<ServerSubLevel> build(ServerLevel level,BlockPos floor,ServerPlayer player) {
        Vector3d base=new Vector3d(floor.getX()+2,floor.getY()+2,floor.getZ()+2);
        var tractor=body(level,base);vehicle(tractor,true,false);
        var semi=body(level,new Vector3d(base).add(0,33.0/32,6));vehicle(semi,false,true);
        var car=body(level,new Vector3d(base).add(16,0,0));vehicle(car,true,false);
        var light=body(level,new Vector3d(base).add(16,1.0/16,6+43.0/64));vehicle(light,false,false);
        var fifth=equipment(tractor,new BlockPos(4,1,6),TrailerRegistry.FIFTH_WHEEL.get());
        var pin=equipment(semi,new BlockPos(4,0,0),TrailerRegistry.KINGPIN.get());
        place(semi,new BlockPos(4,1,0),Blocks.IRON_BLOCK.defaultBlockState());
        var legs=equipment(semi,new BlockPos(4,0,2),TrailerRegistry.LANDING_LEGS.get());
        var ball=equipment(car,new BlockPos(4,0,7),TrailerRegistry.TOW_BALL.get());
        var head=equipment(light,new BlockPos(4,0,1),TrailerRegistry.COUPLING_HEAD.get());
        for(var be:List.of(fifth,pin,legs,ball,head))if(player!=null)be.setOwner(player.getUUID());
        panel(tractor,fifth,legs,player);panel(semi,pin,legs,player);panel(car,ball,null,player);
        finishPlacement(tractor,base);finishPlacement(semi,new Vector3d(base).add(0,33.0/32,6));
        finishPlacement(car,new Vector3d(base).add(16,0,0));finishPlacement(light,new Vector3d(base).add(16,1.0/16,6+43.0/64));
        if(player!=null)player.addItem(new ItemStack(TrailerRegistry.LINKER.get()));
        return List.of(tractor,semi,car,light);
    }
}
