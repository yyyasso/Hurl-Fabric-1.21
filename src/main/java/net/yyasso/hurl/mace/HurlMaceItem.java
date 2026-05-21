package net.yyasso.hurl.mace;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Position;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HurlMaceItem extends Item implements ProjectileItem {
    private static final int DEFAULT_ATTACK_DAMAGE = 5;
    private static final float DEFAULT_ATTACK_SPEED = -3.4F;
    private static final float HEAVY_SMASH_SOUND_FALL_DISTANCE_THRESHOLD = 5.0F;
    public static final float KNOCKBACK_RANGE = 3.5F;
    private static final float KNOCKBACK_POWER = 0.7F;

    public static final int MIN_DRAW_DURATION = 10;
    public static final float DEFAULT_THROW_SPEED = 0.95F;

    public HurlMaceItem(Item.Properties settings) {
        super(settings);
    }

    public static ItemAttributeModifiers createAttributeModifiers() {
        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, DEFAULT_ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND
                )
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, DEFAULT_ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND
                )
                .build();
    }

    public static Tool createToolComponent() {
        return new Tool(List.of(), 1.0F, 2, false);
    }

    @Override
    public @NotNull ItemUseAnimation getUseAnimation(@NotNull ItemStack stack) {
        return ItemUseAnimation.TRIDENT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (user instanceof Player playerEntity) {
            int i = this.getUseDuration(stack, user) - remainingUseTicks;
            if (i < 10) {
                return false;
            } else {
                float densityLevel = EnchantmentHelper.getItemEnchantmentLevel(world.registryAccess().getOrThrow(Enchantments.DENSITY), stack);
                if (stack.nextDamageWillBreak()) {
                    return false;
                } else {
                    Holder<SoundEvent> registryEntry = EnchantmentHelper.pickHighestLevel(stack, EnchantmentEffectComponents.TRIDENT_SOUND)
                            .orElse(SoundEvents.TRIDENT_THROW);
                    playerEntity.awardStat(Stats.ITEM_USED.get(this));
                    if (world instanceof ServerLevel serverWorld) {
                        stack.hurtWithoutBreaking(3, playerEntity);
                        ItemStack itemStack = stack.consumeAndReturn(1, playerEntity);

                        float throwSpeed = DEFAULT_THROW_SPEED - (densityLevel * 0.1F) - (Math.signum(densityLevel) * 0.15F);
                        if ((world.isThundering() && world.canSeeSky(user.blockPosition()) && hasChanneling(serverWorld, stack))) { throwSpeed += 0.85F; }
                        ThrownMace maceEntity = Projectile.spawnProjectileFromRotation(ThrownMace::new, serverWorld, itemStack, playerEntity, 0.0F, throwSpeed, 1.0F);

                        if (playerEntity.hasInfiniteMaterials()) {
                            maceEntity.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                        }

                        world.playSound(null, maceEntity, registryEntry.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
                        return true;
                    }
                    return false;
                }
            }
        } else {
            return false;
        }
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        if (itemStack.nextDamageWillBreak() || user.getOffhandItem().is(Items.WIND_CHARGE)) {
            return InteractionResult.FAIL;
        } else {
            user.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public Projectile asProjectile(Level world, Position pos, ItemStack stack, Direction direction) {
        ThrownMace maceEntity = new ThrownMace(world, pos.x(), pos.y(), pos.z(), stack.copyWithCount(1));
        maceEntity.pickup = AbstractArrow.Pickup.ALLOWED;
        return maceEntity;
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (canSmashAttack(attacker)) {
            ServerLevel level = (ServerLevel)attacker.level();
            attacker.setDeltaMovement(attacker.getDeltaMovement().with(Direction.Axis.Y, 0.01F));
            attacker.setIgnoreFallDamageFromCurrentImpulse(
                    true,
                    attacker.isIgnoringFallDamageFromCurrentImpulse() && attacker.currentImpulseImpactPos.y <= attacker.position().y ? attacker.currentImpulseImpactPos : attacker.position()
            );
            if (attacker instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }

            trySpawnChannelingLightningBolt(stack, target, attacker);

            if (target.onGround()) {
                if (attacker instanceof ServerPlayer serverPlayerEntity) {
                    serverPlayerEntity.setSpawnExtraParticlesOnFall(true);
                }

                SoundEvent soundEvent = attacker.fallDistance > HEAVY_SMASH_SOUND_FALL_DISTANCE_THRESHOLD ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND;
                level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), soundEvent, attacker.getSoundSource(), 1.0F, 1.0F);
            } else {
                level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.MACE_SMASH_AIR, attacker.getSoundSource(), 1.0F, 1.0F);
            }

            knockbackNearbyEntities(level, attacker, target);
        }
    }

    public static boolean hasChanneling(ServerLevel world, ItemStack stack) {
        return EnchantmentHelper.getItemEnchantmentLevel(world.registryAccess().getOrThrow(Enchantments.CHANNELING), stack) != 0;
    }

    public static void trySpawnChannelingLightningBolt (ItemStack stack, LivingEntity target, Entity attacker) {
        trySpawnChannelingLightningBolt(stack, target.blockPosition(), attacker);
    }

    public static void trySpawnChannelingLightningBolt (ItemStack stack, BlockPos pos, Entity attackSource) {
        LivingEntity attacker;
        if (attackSource instanceof ServerPlayer serverPlayerEntity) {
            attacker = serverPlayerEntity;
        } else {
            attacker = (LivingEntity) ((AbstractArrow) attackSource).getOwner();
        }

        if (attacker != null) {
            if (attacker.level() instanceof ServerLevel world) {
                if (world.isThundering() && world.canSeeSky(pos) && hasChanneling(world, stack)) {
                    LightningBolt lightningEntity = (EntityType.LIGHTNING_BOLT).spawn(world, pos, EntitySpawnReason.TRIGGERED);
                    if (lightningEntity != null) {
                        if (attacker instanceof ServerPlayer serverPlayerEntity) {
                            lightningEntity.setCause(serverPlayerEntity);
                            world.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.WEATHER, 10.0F,
                                    1.0f,
                                    false);
                        }

                        if (attacker.getTeam() != null) {
                            attacker.level().getScoreboard().addPlayerToTeam(lightningEntity.getScoreboardName(), attacker.getTeam());
                        }

                        lightningEntity.snapTo(pos.getX(), pos.getY(), pos.getZ(), lightningEntity.getYRot(), lightningEntity.getXRot());
                    }
                }
            }
        }
    }

    private Vec3 getCurrentExplosionImpactPos(ServerPlayer player) {
        return player.isIgnoringFallDamageFromCurrentImpulse()
                && player.currentImpulseImpactPos != null
                && player.currentImpulseImpactPos.y <= player.position().y
                ? player.currentImpulseImpactPos
                : player.position();
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (canSmashAttack(attacker)) {
            attacker.resetFallDistance();
        }
    }

    @Override
    public float getAttackDamageBonus(Entity target, float baseAttackDamage, DamageSource damageSource) {
        if (damageSource.getDirectEntity() instanceof LivingEntity livingEntity) {
            if (!canSmashAttack(livingEntity)) {
                return 0.0F;
            } else {
                double d = 3.0;
                double e = 8.0;
                double f = livingEntity.fallDistance;
                double g;
                if (f <= 3.0) {
                    g = 4.0 * f;
                } else if (f <= 8.0) {
                    g = 12.0 + 2.0 * (f - 3.0);
                } else {
                    g = 22.0 + f - 8.0;
                }

                return livingEntity.level() instanceof ServerLevel serverWorld
                        ? (float)(g + EnchantmentHelper.modifyFallBasedDamage(serverWorld, livingEntity.getWeaponItem(), target, damageSource, 0.0F) * f)
                        : (float)g;
            }
        } else {
            return 0.0F;
        }
    }

    private static void knockbackNearbyEntities(Level world, Entity attacker, Entity attacked) {
        world.levelEvent(LevelEvent.PARTICLES_SMASH_ATTACK, attacked.getOnPos(), 750);
        world.getEntitiesOfClass(LivingEntity.class, attacked.getBoundingBox().inflate(KNOCKBACK_RANGE), getKnockbackPredicate(attacker, attacked)).forEach(entity -> {
            Vec3 vec3d = entity.position().subtract(attacked.position());
            double d = getKnockback(attacker, entity, vec3d);
            Vec3 vec3d2 = vec3d.normalize().scale(d);
            if (d > 0.0) {
                entity.push(vec3d2.x, KNOCKBACK_POWER, vec3d2.z);
                if (entity instanceof ServerPlayer serverPlayerEntity) {
                    serverPlayerEntity.connection.send(new ClientboundSetEntityMotionPacket(serverPlayerEntity));
                }
            }
        });
    }

    public static void knockbackNearbyEntitiesThrown(Level world, AbstractArrow attacker) {
        world.levelEvent(LevelEvent.PARTICLES_SMASH_ATTACK, attacker.getOnPos(), 750);
        world.getEntitiesOfClass(LivingEntity.class, attacker.getBoundingBox().inflate(KNOCKBACK_RANGE), getKnockbackPredicateThrown(attacker)).forEach(entity -> {
            Vec3 vec3d = entity.position().subtract(attacker.position());
            double d = getKnockback(attacker, entity, vec3d);
            Vec3 vec3d2 = vec3d.normalize().scale(d);
            if (d > 0.0) {
                entity.push(vec3d2.x, KNOCKBACK_POWER, vec3d2.z);
                if (entity instanceof ServerPlayer serverPlayerEntity) {
                    serverPlayerEntity.connection.send(new ClientboundSetEntityMotionPacket(serverPlayerEntity));
                }
            }
        });
    }

    private static Predicate<LivingEntity> getKnockbackPredicateThrown(AbstractArrow attacker) {
        return entity -> {
            boolean bl = !entity.isSpectator();
            boolean bl2 = !attacker.isAlliedTo(entity);
            boolean bl3 = attacker.getOwner() != entity;
            boolean bl4 = !(entity instanceof ArmorStand armorStandEntity && armorStandEntity.isMarker());
            return bl && bl2 && bl3 && bl4;
        };
    }

    private static Predicate<LivingEntity> getKnockbackPredicate(Entity attacker, Entity attacked) {
        return entity -> {
            boolean bl = !entity.isSpectator();
            boolean bl2 = entity != attacker && entity != attacked;
            boolean bl3 = !attacker.isAlliedTo(entity);
            boolean bl4 = !(
                    entity instanceof TamableAnimal tameableEntity
                            && attacked instanceof LivingEntity livingEntity
                            && tameableEntity.isTame()
                            && tameableEntity.isOwnedBy(livingEntity)
            );
            boolean bl5 = !(entity instanceof ArmorStand armorStandEntity && armorStandEntity.isMarker());
            boolean bl6 = attacked.distanceToSqr(entity) <= Math.pow(KNOCKBACK_RANGE, 2.0);
            return bl && bl2 && bl3 && bl4 && bl5 && bl6;
        };
    }

    private static double getKnockback(Entity attacker, LivingEntity attacked, Vec3 distance) {
        return (KNOCKBACK_RANGE - distance.length()) * KNOCKBACK_POWER * (attacker.fallDistance > 5.0 ? 2 : 1) * (1.0 - attacked.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
    }

    public static boolean canSmashAttack(LivingEntity attacker) {
        return attacker.fallDistance > 1.5 && !attacker.isFallFlying();
    }

    public @Nullable DamageSource getItemDamageSource(final LivingEntity attacker) {
        return canSmashAttack(attacker) ? attacker.damageSources().mace(attacker) : super.getItemDamageSource(attacker);
    }
}
