package net.yyasso.hurl.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.yyasso.hurl.Hurl;

public class HurlDamageTypes {
    public static final ResourceKey<DamageType> FALLING_MACE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Hurl.MOD_ID, "falling_mace"));

    public static void initialize() {}
}
