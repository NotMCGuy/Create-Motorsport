package com.createmotorsport.motorcycle.client;

import com.createmotorsport.block.entity.SuspensionBlockEntity;
import com.createmotorsport.block.entity.SuspensionBlockEntity.WheelSide;
import com.createmotorsport.motorcycle.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.ryanhcode.offroad.index.OffroadDataComponents;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import java.util.*;

public final class MotorcycleRenderer {
    private static final int[] SIDES={-1,1};
    private static final List<String> CLAMPS=List.of("triple_upper","triple_lower"), LEGS=List.of("fork_left","fork_right"), GRIPS=List.of("grip_left","grip_right"), SPROCKETS=List.of("final_drive_front","rear_sprocket");
    private static final Map<String,PartialModel> PARTS=new HashMap<>();
    private static final Map<ResourceLocation,PartialModel> TIRES=new HashMap<>();
    static {
        for(String name:List.of("stem","clamp","fork_outer","fork_inner","axle","disc","caliper","arm_rail","shock_body","shock_shaft","eye","bar_tube","riser","grip","lever","dashboard","sprocket","chain_run","belt_run","shaft_run","gearbox","seat","peg","pedal"))
            PARTS.put(name,PartialModel.of(ResourceLocation.fromNamespaceAndPath("createmotorsport","block/motorcycle/"+name)));
    }
    public static void init() { }
    static void clear(){TIRES.clear();}
    private static void orient(PoseStack p,Direction facing){p.translate(.5,0,.5);p.mulPose(Axis.YP.rotationDegrees(180-facing.toYRot()));}
    private static void draw(String name,BlockState state,PoseStack p,MultiBufferSource b,int light){CachedBuffers.partial(PARTS.get(name),state).light(light).renderInto(p,b.getBuffer(RenderType.cutoutMipped()));}

    private record Panel(String socket,BlockState state,Matrix4f transform) { }
    private record PanelCache(int revision,List<Panel> parts) { }
    private static final class Assembly {
        final MotorcycleDefinition.Pose motion=new MotorcycleDefinition.Pose();
        final Matrix4f matrix=new Matrix4f();
        final Vector3f a=new Vector3f(),b=new Vector3f(),direction=new Vector3f();
        final Quaternionf rotation=new Quaternionf();
        final WeakHashMap<MotorcycleAppearance,PanelCache> panels=new WeakHashMap<>();
        MotorcycleDefinition d;
        BlockState state; PoseStack p; MultiBufferSource buffers; int light;
        void begin(MotorcycleDefinition definition,BlockState state,PoseStack p,MultiBufferSource buffers,int light,float steer,float spring){
            this.d=definition;this.state=state;this.p=p;this.buffers=buffers;this.light=light;motion.update(d,steer,spring);
        }
        void draw(String name){MotorcycleRenderer.draw(name,state,p,buffers,light);}
        void socket(String name){p.pushPose();p.mulPose(motion.socket(d,name,matrix));}
        void at(String mesh,String socket){socket(socket);draw(mesh);p.popPose();}
        void axle(String socket){socket(socket);p.scale((float)d.forkWidth,1,1);draw("axle");p.popPose();}
        void link(String mesh,Vector3f from,Vector3f to,boolean y,float naturalLength){
            direction.set(to).sub(from);float length=direction.length();if(length<.001f)return;
            rotation.rotationTo(0,y?1:0,y?0:1,direction.x/length,direction.y/length,direction.z/length);
            p.pushPose();p.translate(from.x,from.y,from.z);p.mulPose(rotation);
            if(y)p.scale(1,length/naturalLength,1);else p.scale(1,1,length/naturalLength);
            draw(mesh);p.popPose();
        }
        void links(String mesh,String from,String to,boolean y,float naturalLength){motion.point(d,from,a);motion.point(d,to,b);link(mesh,a,b,y,naturalLength);}
        void wheelHardware(String socket,float spin){
            socket(socket);p.translate(d.forkWidth-.1,0,0);p.mulPose(Axis.XP.rotation(-spin));draw("disc");p.popPose();
            socket(socket);p.translate(d.forkWidth-.1,0,0);draw("caliper");p.popPose();
        }
        void front(){
            socket("steering_axis");p.mulPose(Axis.XP.rotation((float)d.rake));draw("stem");p.popPose();
            for(String clamp:CLAMPS){socket(clamp);p.mulPose(Axis.XP.rotation((float)d.rake));p.scale((float)d.forkWidth,1,1);draw("clamp");p.popPose();}
            for(String leg:LEGS){socket(leg);p.mulPose(Axis.XP.rotation((float)d.rake));draw("fork_outer");p.popPose();}
            for(int side:SIDES){socket("front_axle");p.translate(side*d.forkWidth,0,0);p.mulPose(Axis.XP.rotation((float)d.rake));draw("fork_inner");p.popPose();}
            axle("front_axle");
        }
        void rear(float spin){
            for(int side:SIDES){
                if(d.singleSided&&side>0)continue;
                motion.point(d,"swingarm_pivot",a);motion.point(d,"rear_axle",b);a.x+=side*d.forkWidth;b.x+=side*d.forkWidth;link("arm_rail",a,b,false,1);
            }
            axle("swingarm_pivot");axle("rear_axle");
            p.pushPose();p.mulPose(motion.frames[MotorcycleDefinition.Frame.SWINGARM.ordinal()]);
            var pivot=d.socket("swingarm_pivot").position();p.translate(pivot.x,pivot.y-.08,pivot.z+.12);p.scale((float)d.forkWidth,1,1);draw("axle");p.popPose();
            at("eye","shock_upper");at("eye","shock_lower");
            motion.point(d,"shock_upper",a);motion.point(d,"shock_lower",b);
            direction.set(b).sub(a);float length=direction.length();
            if(length>.05f){
                rotation.rotationTo(0,1,0,direction.x/length,direction.y/length,direction.z/length);
                p.pushPose();p.translate(a.x,a.y,a.z);p.mulPose(rotation);draw("shock_body");p.popPose();
                direction.set(a).sub(b).normalize();rotation.rotationTo(0,1,0,direction.x,direction.y,direction.z);
                p.pushPose();p.translate(b.x,b.y,b.z);p.mulPose(rotation);p.scale(1,Math.max(.05f,length-.22f)/.6f,1);draw("shock_shaft");p.popPose();
            }
            if(d.drive.equals("shaft")){links("shaft_run","final_drive_front","rear_sprocket",false,1);return;}
            for(String socket:SPROCKETS){socket(socket);p.mulPose(Axis.XP.rotation(-spin));draw("sprocket");p.popPose();}
            for(int side:SIDES){motion.point(d,"final_drive_front",a);motion.point(d,"rear_sprocket",b);a.y+=side*.19;b.y+=side*.19;link(d.drive.equals("belt")?"belt_run":"chain_run",a,b,false,1);}
        }
        void handlebars(float throttle){
            links("bar_tube","steering_axis","handlebar_mount",true,1);at("riser","handlebar_mount");at("dashboard","handlebar_mount");
            for(String grip:GRIPS){
                links("bar_tube","handlebar_mount",grip,true,1);
                socket(grip);if(grip.equals("grip_right"))p.mulPose(Axis.XP.rotation(throttle*.35f));draw("grip");p.popPose();
            }
            at("lever","front_brake");at("lever","clutch");
        }
        void panels(MotorcycleAppearance appearance,int overlay){
            PanelCache cache=panels.get(appearance);
            if(cache==null||cache.revision!=appearance.revision){
                List<Panel> decoded=new ArrayList<>();var tags=appearance.panels();
                for(int i=0;i<tags.size();i++){
                    var tag=tags.getCompound(i);String socket=tag.getString("Socket");if(!d.names().contains(socket))continue;
                    var parts=tag.getList("Parts",10);
                    for(int j=0;j<parts.size()&&decoded.size()<96;j++){
                        var part=parts.getCompound(j);var pos=part.getList("Position",6);var rot=part.getList("Rotation",6);var scale=part.getList("Scale",6);
                        if(pos.size()!=3||rot.size()!=3||scale.size()!=3)continue;
                        BlockState block=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),part.getCompound("State"));
                        if(block.isAir()||block.hasBlockEntity())continue;
                        Matrix4f transform=new Matrix4f().translate((float)pos.getDouble(0),(float)pos.getDouble(1),(float)pos.getDouble(2))
                            .rotateZYX((float)Math.toRadians(rot.getDouble(2)),(float)Math.toRadians(rot.getDouble(1)),(float)Math.toRadians(rot.getDouble(0)))
                            .scale((float)scale.getDouble(0),(float)scale.getDouble(1),(float)scale.getDouble(2)).translate(-.5f,-.5f,-.5f);
                        decoded.add(new Panel(socket,block,transform));
                    }
                }
                cache=new PanelCache(appearance.revision,List.copyOf(decoded));panels.put(appearance,cache);
            }
            for(Panel panel:cache.parts){socket(panel.socket);p.mulPose(panel.transform);Minecraft.getInstance().getBlockRenderer().renderSingleBlock(panel.state,p,buffers,light,overlay);p.popPose();}
        }
    }
    public static final class Suspension implements BlockEntityRenderer<MotorcycleSuspensionBlockEntity> {
        private final Assembly rig=new Assembly();
        @Override public void render(MotorcycleSuspensionBlockEntity be,float partial,PoseStack p,MultiBufferSource buffers,int light,int overlay){
            p.pushPose();orient(p,be.getFacing());
            rig.begin(be.appearance().definition,be.getBlockState(),p,buffers,light,be.getLerpedSteer(WheelSide.LEFT,partial),be.getLerpedSpringLength(WheelSide.LEFT,partial));
            float spin=be.getLerpedAngle(WheelSide.LEFT,partial);String axle=be.isFrontAxle()?"front_axle":"rear_axle";
            if(be.isFrontAxle())rig.front();else rig.rear(spin);
            p.pushPose();p.translate(0,0,-be.wheelOffset());
            rig.wheelHardware(axle,spin);
            var stack=be.getTire(WheelSide.LEFT);var tire=stack.get(OffroadDataComponents.TIRE);
            if(tire!=null){
                rig.socket(axle);p.translate(0,(1-be.getLerpedDeploy(WheelSide.LEFT,partial))*.5,0);
                p.mulPose(Axis.YP.rotationDegrees(90));p.mulPose(Axis.ZP.rotation(-spin));
                var rotation=tire.rotation();p.mulPose(Axis.XP.rotationDegrees((float)rotation.x));p.mulPose(Axis.YP.rotationDegrees((float)rotation.y));p.mulPose(Axis.ZP.rotationDegrees((float)rotation.z));p.translate(tire.offset().x,tire.offset().y,tire.offset().z);
                if(tire.model().isPresent())CachedBuffers.partial(TIRES.computeIfAbsent(tire.model().get(),PartialModel::of),be.getBlockState()).light(light).translate(-.5f,0,-.5f).renderInto(p,buffers.getBuffer(RenderType.cutoutMipped()));
                else Minecraft.getInstance().getItemRenderer().renderStatic(stack,ItemDisplayContext.NONE,light,overlay,p,buffers,be.getLevel(),0);
                p.popPose();
            }
            p.popPose();
            rig.panels(be.appearance(),overlay);p.popPose();
        }
    }
    public static final class Handlebars implements BlockEntityRenderer<MotorcycleHandlebarBlockEntity> {
        private final Assembly rig=new Assembly();
        @Override public void render(MotorcycleHandlebarBlockEntity be,float partial,PoseStack p,MultiBufferSource buffers,int light,int overlay){
            var fork=MotorcycleLinks.fork(be);var definition=fork==null?be.appearance().definition:fork.appearance().definition;
            p.pushPose();if(fork!=null){var delta=fork.getBlockPos().subtract(be.getBlockPos());p.translate(delta.getX(),delta.getY(),delta.getZ());}
            orient(p,be.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
            float steer=fork==null?0:fork.getLerpedSteer(WheelSide.LEFT,partial);
            rig.begin(definition,be.getBlockState(),p,buffers,light,steer,(float)SuspensionBlockEntity.REST_LENGTH);rig.handlebars(be.dashboard().throttlePct()/100f);rig.panels(be.appearance(),overlay);
            rig.socket("handlebar_mount");p.translate(0,.134,0);p.mulPose(Axis.XP.rotationDegrees(-90));
            String gear=be.dashboard().gearCode()==0?"R":be.dashboard().gearCode()==1?"N":Integer.toString(be.dashboard().gearCode()-1);
            var font=Minecraft.getInstance().font;
            p.pushPose();p.translate(.075,0,0);p.scale(.009f,-.009f,.009f);
            font.drawInBatch(gear,-font.width(gear)/2f,-font.lineHeight/2f,0xFFCCE8FF,false,p.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0,0xF000F0);
            p.popPose();p.translate(-.045,0,0);p.scale(.0025f,-.0025f,.0025f);
            String speed=be.dashboard().speedKmh()+" km/h";
            font.drawInBatch(speed,-font.width(speed)/2f,-10,0xFF65EDD8,false,p.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0,0xF000F0);
            String rpm=be.dashboard().rpm()+" rpm";
            font.drawInBatch(rpm,-font.width(rpm)/2f,2,0xFFFF8844,false,p.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0,0xF000F0);
            p.popPose();p.popPose();
        }
    }
    public static final class Mount implements BlockEntityRenderer<MotorcycleMountBlockEntity> {
        private final Assembly rig=new Assembly();
        @Override public void render(MotorcycleMountBlockEntity be,float partial,PoseStack p,MultiBufferSource buffers,int light,int overlay){
            p.pushPose();orient(p,be.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
            rig.begin(be.appearance().definition,be.getBlockState(),p,buffers,light,0,(float)SuspensionBlockEntity.REST_LENGTH);
            if(be.getBlockState().getBlock() instanceof MotorcycleSeatBlock){
                rig.at("seat","seat_root");
                rig.at("peg","foot_right");rig.socket("foot_left");p.mulPose(Axis.YP.rotationDegrees(180));rig.draw("peg");p.popPose();
                rig.at("pedal","gear_lever");rig.at("pedal","rear_brake");
            }else{
                rig.socket("gearbox_mount");p.translate(0,-.5,0);rig.draw("gearbox");p.popPose();
                rig.links("shaft_run","engine_mount","drive_input",false,1);
            }
            rig.panels(be.appearance(),overlay);p.popPose();
        }
    }
}

