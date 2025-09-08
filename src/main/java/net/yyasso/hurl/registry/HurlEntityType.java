package net.yyasso.hurl.registry;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.yyasso.hurl.Hurl;
import net.yyasso.hurl.mace.MaceEntity;

public class HurlEntityType {
    public static final EntityType<MaceEntity> MACE = register(
            "mace",
            EntityType.Builder.<MaceEntity>create(MaceEntity::new, SpawnGroup.MISC)
                    .dropsNothing()
                    .dimensions(MaceEntity.MACE_WIDTH, MaceEntity.MACE_HEIGHT)
                    .eyeHeight(0.13F)
                    .maxTrackingRange(4)
                    .trackingTickInterval(20)
    );

    public static <T extends Entity> EntityType<T> register(String path, EntityType.Builder<T> type) {
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Hurl.MOD_ID, path));
        return Registry.register(Registries.ENTITY_TYPE, key, type.build(key));
    }
    public static void initialize() {}
}
