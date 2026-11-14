package com.createmotorsport.item;

import com.createmotorsport.CreateMotorsport;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

public class Wishbone {
    public static final DeferredItem<Item> WISHBONE =
            CreateMotorsport.ITEMS.registerSimpleItem(
                    "wishbone",
                    new Item.Properties().stacksTo(64));

    public static void init() {
    }
}
