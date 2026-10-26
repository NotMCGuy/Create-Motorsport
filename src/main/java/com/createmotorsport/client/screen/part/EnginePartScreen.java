package com.createmotorsport.client.screen.part;

import com.createmotorsport.block.entity.EngineBlockEntity;
import com.createmotorsport.client.widget.Theme;
import com.createmotorsport.network.ToggleEngineDirectionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class EnginePartScreen extends PartScreen {

    public EnginePartScreen(BlockPos pos, boolean canTune) {
        super(Component.translatable("container.createmotorsport.engine"), pos, canTune);
    }

    @Override
    protected List<List<Button>> rows() {
        Button rotation = new Button(() -> {
            EngineBlockEntity engine = blockEntity(EngineBlockEntity.class);
            int dir = engine != null ? engine.getRotationDirection() : 1;
            return Component.literal("Rotation: " + (dir > 0 ? "1" : "-1"));
        }, Theme.NEUTRAL, () -> PacketDistributor.sendToServer(new ToggleEngineDirectionPacket(pos)));
        return List.of(List.of(tune()), List.of(rotation), List.of(new Button(() -> Component.literal("Attachments & Redstone Links"), Theme.NEUTRAL,
                () -> PacketDistributor.sendToServer(new com.createmotorsport.network.OpenWirelessPacket(pos)))));
    }
}
