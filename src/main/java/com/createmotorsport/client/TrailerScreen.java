package com.createmotorsport.client;

import com.createmotorsport.trailer.CouplingRules;
import com.createmotorsport.trailer.TrailerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class TrailerScreen extends AbstractContainerScreen<TrailerMenu> {
    public TrailerScreen(TrailerMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);imageWidth=280;imageHeight=190;
    }
    @Override protected void init() {
        super.init();
        button("couple_release",0,12,126,125);
        button("deploy_retract",1,143,126,125);
        button("clear_links",2,78,154,124);
    }
    private void button(String name,int action,int x,int y,int width) {
        addRenderableWidget(Button.builder(Component.translatable("trailer.createmotorsport."+name),b->{
            if(minecraft!=null && minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);
        }).bounds(leftPos+x,topPos+y,width,20).build());
    }
    private Component state(int value) {
        return Component.translatable("trailer.createmotorsport."+CouplingRules.Status.values()[Math.clamp(value,0,CouplingRules.Status.values().length-1)].name().toLowerCase(java.util.Locale.ROOT));
    }
    @Override protected void renderBg(GuiGraphics g,float pt,int mx,int my) {
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF555555);
        g.fill(leftPos+2,topPos+2,leftPos+imageWidth-2,topPos+imageHeight-2,0xFF202020);
        g.fill(leftPos+12,topPos+107,leftPos+268,topPos+114,0xFF444444);
        g.fill(leftPos+12,topPos+107,leftPos+12+256*menu.value(3)/100,topPos+114,0xFFB89A58);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) {
        g.drawString(font,title,12,10,0xFFE0C080,false);
        g.drawString(font,Component.translatable("trailer.createmotorsport.hitch_status",state(menu.value(1))),12,32,0xFFE0E0E0,false);
        g.drawString(font,Component.translatable("trailer.createmotorsport.leg_status",state(menu.value(2))),12,48,0xFFE0E0E0,false);
        g.drawString(font,state(menu.value(0)),12,64,0xFFFFC080,false);
        g.drawString(font,Component.translatable("trailer.createmotorsport.remote_status",menu.value(4)==1?"ON":"OFF"),12,80,0xFFAAAAAA,false);
        g.drawString(font,Component.translatable("trailer.createmotorsport.redstone_hint"),12,94,0xFFAAAAAA,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float pt) {super.render(g,mx,my,pt);renderTooltip(g,mx,my);}
}
