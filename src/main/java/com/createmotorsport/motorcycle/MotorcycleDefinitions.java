package com.createmotorsport.motorcycle;

import com.createmotorsport.CreateMotorsport;
import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.Sable;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.*;

public final class MotorcycleDefinitions extends SimpleJsonResourceReloadListener {
    private static volatile Map<String,MotorcycleDefinition> designs=Map.of("createmotorsport:standard",MotorcycleDefinition.DEFAULT);
    private MotorcycleDefinitions(){super(new Gson(),"motorcycles");}
    public static void onReloadListeners(AddReloadListenerEvent event){event.addListener(new MotorcycleDefinitions());}
    @Override protected void apply(Map<ResourceLocation,JsonElement> values,ResourceManager manager,ProfilerFiller profiler){
        Map<String,MotorcycleDefinition> next=new HashMap<>();next.put("createmotorsport:standard",MotorcycleDefinition.DEFAULT);
        values.forEach((id,value)->{try{next.put(id.toString(),MotorcycleDefinition.parse(value.toString()));}catch(RuntimeException e){CreateMotorsport.LOGGER.warn("Ignoring invalid motorcycle definition {}: {}",id,e.getMessage());}});
        designs=Map.copyOf(next);
    }
    public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("motorcycle").requires(s->s.hasPermission(2))
            .then(Commands.literal("design").then(Commands.argument("part",BlockPosArgument.blockPos()).then(Commands.argument("definition",net.minecraft.commands.arguments.ResourceLocationArgument.id())
                .suggests((c,b)->SharedSuggestionProvider.suggest(designs.keySet(),b)).executes(c->{
                    String id=net.minecraft.commands.arguments.ResourceLocationArgument.getId(c,"definition").toString();var design=designs.get(id);
                    if(design==null){c.getSource().sendFailure(Component.literal("Unknown motorcycle definition"));return 0;}
                    var level=c.getSource().getLevel();BlockPos pos=BlockPosArgument.getLoadedBlockPos(c,"part");var selected=level.getBlockEntity(pos);
                    if(!(selected instanceof MotorcyclePart)){c.getSource().sendFailure(Component.literal("Select a motorcycle part"));return 0;}
                    var body=Sable.HELPER.getContaining(selected);int count=0;
                    for(BlockPos p:BlockPos.betweenClosed(pos.offset(-8,-5,-8),pos.offset(8,5,8))){
                        if(!level.hasChunkAt(p))continue;var be=level.getBlockEntity(p);
                        if(be instanceof MotorcyclePart part && be instanceof SmartBlockEntity smart && (body!=null?Sable.HELPER.getContaining(be)==body:p.equals(pos))){part.appearance().set(id,design);smart.notifyUpdate();count++;}
                    }
                    final int n=count;c.getSource().sendSuccess(()->Component.literal("Applied "+id+" to "+n+" motorcycle parts. Dismount/remount to update rider anchors."),false);return count;
                }))))
            .then(Commands.literal("panel").then(Commands.argument("part",BlockPosArgument.blockPos()).then(Commands.argument("socket",StringArgumentType.word())
                .suggests((c,b)->SharedSuggestionProvider.suggest(MotorcycleDefinition.DEFAULT.names(),b))
                .then(Commands.literal("clear").executes(c->panel(c,null)))
                .then(Commands.argument("workspace",BlockPosArgument.blockPos()).executes(c->panel(c,BlockPosArgument.getLoadedBlockPos(c,"workspace"))))))));
    }
    private static int panel(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,BlockPos workspace) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var level=c.getSource().getLevel();var be=level.getBlockEntity(BlockPosArgument.getLoadedBlockPos(c,"part"));
        if(!(be instanceof MotorcyclePart part)||!(be instanceof SmartBlockEntity smart)){c.getSource().sendFailure(Component.literal("Select a motorcycle part"));return 0;}
        String socket=StringArgumentType.getString(c,"socket");
        try{
            part.appearance().definition.socket(socket);
            var pieces=workspace==null?new net.minecraft.nbt.ListTag():QModelingPanels.capture(level.getBlockEntity(workspace));
            part.appearance().panel(socket,pieces);smart.notifyUpdate();
            c.getSource().sendSuccess(()->Component.literal("Saved "+pieces.size()+" panel blocks at "+socket),false);return 1;
        }catch(Exception e){c.getSource().sendFailure(Component.literal("Panel unchanged: "+e.getMessage()));return 0;}
    }
}
