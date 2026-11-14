package com.createmotorsport.item;

import com.createmotorsport.CreateMotorsport;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

public class CarbonFibre {
    public static final DeferredItem<Item> CARBON_FIBRE =
            CreateMotorsport.ITEMS.registerSimpleItem(
                    "carbon_fibre",
                    new Item.Properties().stacksTo(64));

    public static void init() {
    }
}