package net.yyasso.hurl.mixin;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.yyasso.hurl.mace.HurlMaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.function.Function;

// -Dmixin.debug.export=true in Run/Debug Configurations at the top

@Mixin(Items.class)
public abstract class MaceItemsMixin {
    @Inject( method = "register(Ljava/lang/String;Ljava/util/function/Function;Lnet/minecraft/item/Item$Settings;)Lnet/minecraft/item/Item;", at = @At("HEAD"), cancellable = true)
    private static void register(String id, Function<Item.Settings, Item> factory, Item.Settings settings, CallbackInfoReturnable<Item> cir) {
        if (Objects.equals(id, "mace")) {
            cir.setReturnValue(Items.register(RegistryKey.of(RegistryKeys.ITEM, Identifier.ofVanilla("mace")), HurlMaceItem::new, settings));
        }
    }
}
