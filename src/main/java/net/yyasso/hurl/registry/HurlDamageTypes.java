package net.yyasso.hurl.registry;

import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.yyasso.hurl.Hurl;

public class HurlDamageTypes {
    public static final RegistryKey<DamageType> FALLING_MACE_DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Hurl.MOD_ID, "falling_mace"));

    public static void initialize() {}
}
