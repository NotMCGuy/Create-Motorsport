package com.createmotorsport.motorcycle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.*;

public final class MotorcycleAppearance {
    public MotorcycleDefinition definition = MotorcycleDefinition.DEFAULT;
    public String id = "createmotorsport:standard";
    private ListTag panels = new ListTag();
    public int revision;
    public void set(String id,MotorcycleDefinition definition){this.id=id;this.definition=definition;revision++;}
    public ListTag panels(){return panels;}
    public void panel(String socket,ListTag parts){
        ListTag next=new ListTag();for(int i=0;i<panels.size();i++)if(!panels.getCompound(i).getString("Socket").equals(socket))next.add(panels.getCompound(i).copy());
        if(!parts.isEmpty()){CompoundTag panel=new CompoundTag();panel.putString("Socket",socket);panel.put("Parts",parts.copy());next.add(panel);}
        int count=0;for(int i=0;i<next.size();i++)count+=next.getCompound(i).getList("Parts",10).size();
        if(count>96)throw new IllegalArgumentException("Maximum 96 panel blocks per component");
        panels=next;revision++;
    }
    public void write(CompoundTag tag){tag.putString("BikeDesign",definition.json);tag.putString("BikeDesignId",id);tag.put("BikePanels",panels.copy());}
    public void read(CompoundTag tag){
        String json=tag.contains("BikeDesign")?tag.getString("BikeDesign"):"{}";
        if(!definition.json.equals(json)){definition=MotorcycleDefinition.safe(json);revision++;}
        id=tag.contains("BikeDesignId")?tag.getString("BikeDesignId"):"createmotorsport:standard";
        ListTag next=tag.getList("BikePanels",10);if(!next.equals(panels)){panels=next.copy();revision++;}
    }
}
