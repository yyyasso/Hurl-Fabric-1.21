package net.yyasso.hurl.mace;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ProjectileItem;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.jetbrains.annotations.Nullable;

public class HurlMaceItem extends Item implements ProjectileItem {
    private static final int ATTACK_DAMAGE_MODIFIER_VALUE = 5;
    private static final float ATTACK_SPEED_MODIFIER_VALUE = -3.4F;
    public static final float MINING_SPEED_MULTIPLIER = 1.5F;
    private static final float HEAVY_SMASH_SOUND_FALL_DISTANCE_THRESHOLD = 5.0F;
    public static final float KNOCKBACK_RANGE = 3.5F;
    private static final float KNOCKBACK_POWER = 0.7F;

    public static final int MIN_DRAW_DURATION = 10;
    public static final float DEFAULT_THROW_SPEED = 0.9F;

    public HurlMaceItem(Item.Settings settings) {
        super(settings);
    }

    public static AttributeModifiersComponent createAttributeModifiers() {
        return AttributeModifiersComponent.builder()
                .add(
                        EntityAttributes.ATTACK_DAMAGE,
                        new EntityAttributeModifier(BASE_ATTACK_DAMAGE_MODIFIER_ID, ATTACK_DAMAGE_MODIFIER_VALUE, EntityAttributeModifier.Operation.ADD_VALUE),
                        AttributeModifierSlot.MAINHAND
                )
                .add(
                        EntityAttributes.ATTACK_SPEED,
                        new EntityAttributeModifier(BASE_ATTACK_SPEED_MODIFIER_ID, ATTACK_SPEED_MODIFIER_VALUE, EntityAttributeModifier.Operation.ADD_VALUE),
                        AttributeModifierSlot.MAINHAND
                )
                .build();
    }

    public static ToolComponent createToolComponent() {
        return new ToolComponent(List.of(), 1.0F, 2, false);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.SPEAR;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public boolean onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (user instanceof PlayerEntity playerEntity) {
            int i = this.getMaxUseTime(stack, user) - remainingUseTicks;
            if (i < 10) {
                return false;
            } else {
                float densityLevel = EnchantmentHelper.getLevel(world.getRegistryManager().getEntryOrThrow(Enchantments.DENSITY), stack);
                if (stack.willBreakNextUse()) {
                    return false;
                } else {
                    RegistryEntry<SoundEvent> registryEntry = EnchantmentHelper.getEffect(stack, EnchantmentEffectComponentTypes.TRIDENT_SOUND)
                            .orElse(SoundEvents.ITEM_TRIDENT_THROW);
                    playerEntity.incrementStat(Stats.USED.getOrCreateStat(this));
                    if (world instanceof ServerWorld serverWorld) {
                        stack.damage(3, playerEntity);
                        ItemStack itemStack = stack.splitUnlessCreative(1, playerEntity);

                        float throwSpeed = DEFAULT_THROW_SPEED - (densityLevel * 0.1F) - (Math.signum(densityLevel) * 0.1F);
                        if ((world.isThundering() && world.isSkyVisible(user.getBlockPos()) && hasChanneling(serverWorld, stack))) { throwSpeed += 0.9F; }
                        MaceEntity maceEntity = ProjectileEntity.spawnWithVelocity(MaceEntity::new, serverWorld, itemStack, playerEntity, 0.0F, throwSpeed, 1.0F);

                        if (playerEntity.isInCreativeMode()) {
                            maceEntity.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
                        }

                        world.playSoundFromEntity(null, maceEntity, registryEntry.value(), SoundCategory.PLAYERS, 1.0F, 0.6F);
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
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack itemStack = user.getStackInHand(hand);
        if (itemStack.willBreakNextUse() || user.getOffHandStack().isOf(Items.WIND_CHARGE)) {
            return ActionResult.FAIL;
        } else {
            user.setCurrentHand(hand);
            return ActionResult.CONSUME;
        }
    }

    @Override
    public ProjectileEntity createEntity(World world, Position pos, ItemStack stack, Direction direction) {
        MaceEntity maceEntity = new MaceEntity(world, pos.getX(), pos.getY(), pos.getZ(), stack.copyWithCount(1));
        maceEntity.pickupType = PersistentProjectileEntity.PickupPermission.ALLOWED;
        return maceEntity;
    }

    @Override
    public void postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (shouldDealAdditionalDamage(attacker)) {
            ServerWorld serverWorld = (ServerWorld)attacker.getEntityWorld();
            attacker.setVelocity(attacker.getVelocity().withAxis(Direction.Axis.Y, 0.01F));
            if (attacker instanceof ServerPlayerEntity serverPlayerEntity) {
                serverPlayerEntity.currentExplosionImpactPos = this.getCurrentExplosionImpactPos(serverPlayerEntity);
                serverPlayerEntity.setIgnoreFallDamageFromCurrentExplosion(true);
                serverPlayerEntity.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(serverPlayerEntity));
            }

            trySpawnChannelingLightningBolt(stack, target, attacker);

            if (target.isOnGround()) {
                if (attacker instanceof ServerPlayerEntity serverPlayerEntity) {
                    serverPlayerEntity.setSpawnExtraParticlesOnFall(true);
                }

                SoundEvent soundEvent = attacker.fallDistance > HEAVY_SMASH_SOUND_FALL_DISTANCE_THRESHOLD ? SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY : SoundEvents.ITEM_MACE_SMASH_GROUND;
                serverWorld.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), soundEvent, attacker.getSoundCategory(), 1.0F, 1.0F);
            } else {
                serverWorld.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.ITEM_MACE_SMASH_AIR, attacker.getSoundCategory(), 1.0F, 1.0F);
            }

            knockbackNearbyEntities(serverWorld, attacker, target);
        }
    }

    public static boolean hasChanneling(ServerWorld world, ItemStack stack) {
        return EnchantmentHelper.getLevel(world.getRegistryManager().getEntryOrThrow(Enchantments.CHANNELING), stack) != 0;
    }

    public static void trySpawnChannelingLightningBolt (ItemStack stack, LivingEntity target, Entity attacker) {
        trySpawnChannelingLightningBolt(stack, target.getBlockPos(), attacker);
    }

    public static void trySpawnChannelingLightningBolt (ItemStack stack, BlockPos pos, Entity attackSource) {
        LivingEntity attacker;
        if (attackSource instanceof ServerPlayerEntity serverPlayerEntity) {
            attacker = serverPlayerEntity;
        } else {
            attacker = (LivingEntity) ((PersistentProjectileEntity) attackSource).getOwner();
        }

        if (attacker != null) {
            if (attacker.getEntityWorld() instanceof ServerWorld world) {
                if (world.isThundering() && world.isSkyVisible(pos) && hasChanneling(world, stack)) {
                    LightningEntity lightningEntity = (EntityType.LIGHTNING_BOLT).spawn(world, pos, SpawnReason.TRIGGERED);
                    if (lightningEntity != null) {
                        if (attacker instanceof ServerPlayerEntity serverPlayerEntity) {
                            lightningEntity.setChanneler(serverPlayerEntity);
                            world.playSoundClient(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.WEATHER, 10.0F,
                                    1.0f,
                                    false);
                        }

                        if (attacker.getScoreboardTeam() != null) {
                            attacker.getEntityWorld().getScoreboard().addScoreHolderToTeam(lightningEntity.getNameForScoreboard(), attacker.getScoreboardTeam());
                        }

                        lightningEntity.refreshPositionAndAngles(pos.getX(), pos.getY(), pos.getZ(), lightningEntity.getYaw(), lightningEntity.getPitch());
                    }
                }
            }
        }
    }

    private Vec3d getCurrentExplosionImpactPos(ServerPlayerEntity player) {
        return player.shouldIgnoreFallDamageFromCurrentExplosion()
                && player.currentExplosionImpactPos != null
                && player.currentExplosionImpactPos.y <= player.getEntityPos().y
                ? player.currentExplosionImpactPos
                : player.getEntityPos();
    }

    @Override
    public void postDamageEntity(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (shouldDealAdditionalDamage(attacker)) {
            attacker.onLanding();
        }
    }

    @Override
    public float getBonusAttackDamage(Entity target, float baseAttackDamage, DamageSource damageSource) {
        if (damageSource.getSource() instanceof LivingEntity livingEntity) {
            if (!shouldDealAdditionalDamage(livingEntity)) {
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

                return livingEntity.getEntityWorld() instanceof ServerWorld serverWorld
                        ? (float)(g + EnchantmentHelper.getSmashDamagePerFallenBlock(serverWorld, livingEntity.getWeaponStack(), target, damageSource, 0.0F) * f)
                        : (float)g;
            }
        } else {
            return 0.0F;
        }
    }

    private static void knockbackNearbyEntities(World world, Entity attacker, Entity attacked) {
        world.syncWorldEvent(WorldEvents.SMASH_ATTACK, attacked.getSteppingPos(), 750);
        world.getEntitiesByClass(LivingEntity.class, attacked.getBoundingBox().expand(KNOCKBACK_RANGE), getKnockbackPredicate(attacker, attacked)).forEach(entity -> {
            Vec3d vec3d = entity.getEntityPos().subtract(attacked.getEntityPos());
            double d = getKnockback(attacker, entity, vec3d);
            Vec3d vec3d2 = vec3d.normalize().multiply(d);
            if (d > 0.0) {
                entity.addVelocity(vec3d2.x, KNOCKBACK_POWER, vec3d2.z);
                if (entity instanceof ServerPlayerEntity serverPlayerEntity) {
                    serverPlayerEntity.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(serverPlayerEntity));
                }
            }
        });
    }

    public static void knockbackNearbyEntitiesThrown(World world, PersistentProjectileEntity attacker) {
        world.syncWorldEvent(WorldEvents.SMASH_ATTACK, attacker.getSteppingPos(), 750);
        world.getEntitiesByClass(LivingEntity.class, attacker.getBoundingBox().expand(KNOCKBACK_RANGE), getKnockbackPredicateThrown(attacker)).forEach(entity -> {
            Vec3d vec3d = entity.getEntityPos().subtract(attacker.getEntityPos());
            double d = getKnockback(attacker, entity, vec3d);
            Vec3d vec3d2 = vec3d.normalize().multiply(d);
            if (d > 0.0) {
                entity.addVelocity(vec3d2.x, KNOCKBACK_POWER, vec3d2.z);
                if (entity instanceof ServerPlayerEntity serverPlayerEntity) {
                    serverPlayerEntity.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(serverPlayerEntity));
                }
            }
        });
    }

    private static Predicate<LivingEntity> getKnockbackPredicateThrown(PersistentProjectileEntity attacker) {
        return entity -> {
            boolean bl = !entity.isSpectator();
            boolean bl2 = !attacker.isTeammate(entity);
            boolean bl3 = attacker.getOwner() != entity;
            boolean bl4 = !(entity instanceof ArmorStandEntity armorStandEntity && armorStandEntity.isMarker());
            return bl && bl2 && bl3 && bl4;
        };
    }

    private static Predicate<LivingEntity> getKnockbackPredicate(Entity attacker, Entity attacked) {
        return entity -> {
            boolean bl = !entity.isSpectator();
            boolean bl2 = entity != attacker && entity != attacked;
            boolean bl3 = !attacker.isTeammate(entity);
            boolean bl4 = !(
                    entity instanceof TameableEntity tameableEntity
                            && attacked instanceof LivingEntity livingEntity
                            && tameableEntity.isTamed()
                            && tameableEntity.isOwner(livingEntity)
            );
            boolean bl5 = !(entity instanceof ArmorStandEntity armorStandEntity && armorStandEntity.isMarker());
            boolean bl6 = attacked.squaredDistanceTo(entity) <= Math.pow(KNOCKBACK_RANGE, 2.0);
            return bl && bl2 && bl3 && bl4 && bl5 && bl6;
        };
    }

    private static double getKnockback(Entity attacker, LivingEntity attacked, Vec3d distance) {
        return (KNOCKBACK_RANGE - distance.length()) * KNOCKBACK_POWER * (attacker.fallDistance > 5.0 ? 2 : 1) * (1.0 - attacked.getAttributeValue(EntityAttributes.KNOCKBACK_RESISTANCE));
    }

    public static boolean shouldDealAdditionalDamage(LivingEntity attacker) {
        return attacker.fallDistance > 1.5 && !attacker.isGliding();
    }

    @Nullable
    @Override
    public DamageSource getDamageSource(LivingEntity user) {
        return shouldDealAdditionalDamage(user) ? user.getDamageSources().maceSmash(user) : super.getDamageSource(user);
    }
}
