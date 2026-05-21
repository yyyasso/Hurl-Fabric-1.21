package net.yyasso.hurl.mixin;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.yyasso.hurl.mace.HurlMaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(Items.class)
public abstract class MaceItemsMixin {
    @Inject(
            method = "registerItem(Lnet/minecraft/resources/ResourceKey;Ljava/util/function/Function;Lnet/minecraft/world/item/Item$Properties;)Lnet/minecraft/world/item/Item;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void reg(ResourceKey<Item> key, Function<Item.Properties, Item> itemFactory, Item.Properties properties, CallbackInfoReturnable<Item> cir) {
        if ("mace".equals(key.identifier().getPath())) {
            Function<Item.Properties, HurlMaceItem> hurlItemFactory = HurlMaceItem::new;
            Item item = hurlItemFactory.apply(properties.setId(key));

            cir.setReturnValue( (Item) Registry.register(BuiltInRegistries.ITEM, key, item) );
        }
    }
}
