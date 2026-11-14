package com.createmotorsport.item;

import com.createmotorsport.CreateMotorsport;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

public class Piston {
    public static final DeferredItem<Item> PISTON =
            CreateMotorsport.ITEMS.registerSimpleItem(
                    "piston",
                    new Item.Properties().stacksTo(64));

    public static void init() {
    }
}