package net.yyasso.hurl.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.yyasso.hurl.Hurl;
import net.yyasso.hurl.mace.ThrownMace;

public class HurlEntityType {
    public static final EntityType<ThrownMace> MACE = register(
            "mace",
            EntityType.Builder.<ThrownMace>of(ThrownMace::new, MobCategory.MISC)
                    .noLootTable()
                    .sized(ThrownMace.MACE_WIDTH, ThrownMace.MACE_HEIGHT)
                    .eyeHeight(0.13F)
                    .clientTrackingRange(4)
                    .updateInterval(20)
    );

    public static <T extends Entity> EntityType<T> register(String path, EntityType.Builder<T> type) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Hurl.MOD_ID, path));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type.build(key));
    }
    public static void initialize() {}
}
