package com.createmotorsport.motorcycle;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.List;

public final class QModelingPanels {
    public static ListTag capture(BlockEntity workspace) throws ReflectiveOperationException {
        if(workspace==null||!workspace.getClass().getName().equals("qwxon.qmodeling.block.entity.WorkspaceBlockEntity"))throw new IllegalArgumentException("Source must be a qModeling workspace (1.0.6b)");
        var pieces=(List<?>)workspace.getClass().getMethod("getPartsView").invoke(workspace);
        ListTag out=new ListTag();
        for(Object part:pieces){
            Class<?> type=part.getClass();if(!(Boolean)type.getMethod("isVisible").invoke(part))continue;
            BlockState state=(BlockState)type.getMethod("getBlockState").invoke(part);
            if(state.isAir()||state.hasBlockEntity()||(Boolean)type.getMethod("isBillboard").invoke(part)
                    ||type.getMethod("getParentBoneId").invoke(part)!=null)throw new IllegalArgumentException("Import static unparented block parts only; remove displays, bones and block entities");
            CompoundTag tag=new CompoundTag();tag.put("State",NbtUtils.writeBlockState(state));
            for(String name:List.of("Position","Rotation","Scale")){
                Vec3 v=(Vec3)type.getMethod("get"+name).invoke(part);
                double bound=name.equals("Rotation")?360:3;
                if(!Double.isFinite(v.x)||!Double.isFinite(v.y)||!Double.isFinite(v.z)||Math.max(Math.abs(v.x),Math.max(Math.abs(v.y),Math.abs(v.z)))>bound)throw new IllegalArgumentException(name+" exceeds panel limits");
                if(name.equals("Scale")&&(v.x<.01||v.y<.01||v.z<.01))throw new IllegalArgumentException("Panel scale must be positive; bake mirrored parts before import");
                ListTag vec=new ListTag();vec.add(DoubleTag.valueOf(v.x));vec.add(DoubleTag.valueOf(v.y));vec.add(DoubleTag.valueOf(v.z));tag.put(name,vec);
            }
            out.add(tag);if(out.size()>96)throw new IllegalArgumentException("Maximum 96 static blocks per component");
        }
        return out;
    }
}
