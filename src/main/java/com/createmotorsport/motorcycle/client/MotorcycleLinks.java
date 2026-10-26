package com.createmotorsport.motorcycle.client;

import com.createmotorsport.motorcycle.*;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

final class MotorcycleLinks {
    private record Link(long tick, WeakReference<MotorcycleSuspensionBlockEntity> fork) { }
    private static final WeakHashMap<MotorcycleHandlebarBlockEntity, Link> LINKS = new WeakHashMap<>();
    static MotorcycleSuspensionBlockEntity fork(MotorcycleHandlebarBlockEntity bars) {
        var level=bars.getLevel(); if(level==null || bars.isRemoved()) return null;
        long tick=level.getGameTime(); var old=LINKS.get(bars);
        if(old!=null && tick>=old.tick && tick-old.tick<10) {
            var found=old.fork.get(); return found!=null&&!found.isRemoved()?found:null;
        }
        MotorcycleSuspensionBlockEntity best=null; double distance=10;
        var facing=bars.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        for(var pos:BlockPos.betweenClosed(bars.getBlockPos().offset(-2,-2,-2),bars.getBlockPos().offset(2,1,2))) {
            if(!level.hasChunkAt(pos)) continue;
            if(level.getBlockEntity(pos) instanceof MotorcycleSuspensionBlockEntity candidate
                    && candidate.isFrontAxle() && candidate.getFacing()==facing
                    && Sable.HELPER.getContaining(candidate)==Sable.HELPER.getContaining(bars)) {
                double d=pos.distSqr(bars.getBlockPos()); if(d<distance){best=candidate;distance=d;}
            }
        }
        LINKS.put(bars,new Link(tick,new WeakReference<>(best)));return best;
    }
    static void clear(){LINKS.clear();}
}
