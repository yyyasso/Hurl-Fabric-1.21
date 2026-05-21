package net.yyasso.hurl.mace;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.yyasso.hurl.registry.HurlDamageTypes;
import net.yyasso.hurl.registry.HurlEntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class ThrownMace extends AbstractArrow {
    public static final float MACE_HEIGHT = 0.55F;
    public static final float MACE_WIDTH = 0.55F;

    private static final EntityDataAccessor<Byte> ID_LOYALTY = SynchedEntityData.defineId(ThrownMace.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> ID_FIRE_ASPECT = SynchedEntityData.defineId(ThrownMace.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> ID_WIND_BURSTS = SynchedEntityData.defineId(ThrownMace.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> ID_FOIL = SynchedEntityData.defineId(ThrownMace.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> lastPeak = SynchedEntityData.defineId(ThrownMace.class, EntityDataSerializers.FLOAT);
    private final Supplier<Byte> defaultWindBurstsSupplier = () -> (byte) 0;
    public boolean dealtDamage = false;
    public int returnTimer;

    public ThrownMace(final EntityType<? extends ThrownMace> type, final Level level) {
        super(type, level);
    }

    public ThrownMace(Level world, LivingEntity owner, ItemStack maceItem) {
        super(HurlEntityType.MACE, owner, world, maceItem, null);
        this.entityData.set(ID_LOYALTY, this.getLoyalty(maceItem));
        this.entityData.set(ID_FIRE_ASPECT, this.getFireAspect(maceItem));
        this.entityData.set(ID_WIND_BURSTS, this.getWindBurstLevel(maceItem));
        this.entityData.set(ID_FOIL, maceItem.hasFoil());
        this.setPeak();
        this.ignite();
    }

    public ThrownMace(Level world, double x, double y, double z, ItemStack maceItem) {
        super(HurlEntityType.MACE, x, y, z, world, maceItem, maceItem);
        this.entityData.set(ID_LOYALTY, this.getLoyalty(maceItem));
        this.entityData.set(ID_FIRE_ASPECT, this.getFireAspect(maceItem));
        this.entityData.set(ID_WIND_BURSTS, this.getWindBurstLevel(maceItem));
        this.entityData.set(ID_FOIL, maceItem.hasFoil());
        this.setPeak();
        this.ignite();
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.@NotNull Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(ID_LOYALTY, (byte)0);
        entityData.define(ID_FIRE_ASPECT, (byte)0);
        entityData.define(ID_WIND_BURSTS, (byte)0);
        entityData.define(lastPeak, (float) 0);
        entityData.define(ID_FOIL, false);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull ValueInput view) {
        super.readAdditionalSaveData(view);
        this.entityData.set(lastPeak, view.read("LastPeak", Codec.FLOAT).orElseGet(() -> (float) this.position().y()));
        this.dealtDamage = view.getBooleanOr("DealtDamage", false);
        this.entityData.set(ID_WIND_BURSTS, view.read("WindBursts", Codec.BYTE).orElseGet(defaultWindBurstsSupplier));
        this.entityData.set(ID_LOYALTY, this.getLoyalty(this.getPickupItemStackOrigin()));
        this.entityData.set(ID_FIRE_ASPECT, this.getFireAspect(this.getPickupItemStackOrigin()));
    }

    @Override
    protected void addAdditionalSaveData(@NotNull ValueOutput view) {
        super.addAdditionalSaveData(view);
        view.putFloat("LastPeak", this.entityData.get(lastPeak));
        view.putByte("WindBursts", this.getWindBursts());
        view.putBoolean("DealtDamage", this.dealtDamage);
    }

    @Override
    public void tick() {
        if (this.isInGround() || this.isNoPhysics()) {
            this.setPeak();
        } else {
            if (this.getDeltaMovement().y > 0 || (this.getDeltaMovement().y > -0.5 && this.isInWater())) {
                this.setPeak();
            }
        }

        if (this.inGroundTime > 4) {
            this.dealtDamage = true;
        }

        Entity currentOwner = this.getOwner();
        int i = this.entityData.get(ID_LOYALTY);
        if (i > 0 && (this.dealtDamage || this.isNoPhysics()) && currentOwner != null) {
            if (!this.isAcceptibleReturnOwner()) {
                Level var4 = this.level();
                if (var4 instanceof ServerLevel level) {
                    if (this.pickup == Pickup.ALLOWED) {
                        this.spawnAtLocation(level, this.getPickupItem(), 0.1F);
                    }
                }

                this.discard();
            } else {
                if (!(currentOwner instanceof Player) && this.position().distanceTo(currentOwner.getEyePosition()) < currentOwner.getBbWidth() + 1.0) {
                    this.discard();
                    return;
                }

                this.setNoPhysics(true);
                Vec3 vec3d = currentOwner.getEyePosition().subtract(this.position());
                this.setPosRaw(this.getX(), this.getY() + vec3d.y * 0.015 * i, this.getZ());
                double d = 0.05 * i;
                this.setDeltaMovement(this.getDeltaMovement().scale(0.95).add(vec3d.normalize().scale(d)));
                if (this.returnTimer == 0) {
                    this.playSound(SoundEvents.TRIDENT_RETURN, 10.0F, 0.9F);
                }

                ++this.returnTimer;
            }
        }

        if (this.getDeltaMovement().y() < 0.0 && this.getDeltaMovement().y() > -0.7 && getWindBursts() > 0) {
            tryWindBurstSkip();
        }

        super.tick();
        if (this.isInWater()) { this.setDeltaMovement(this.getDeltaMovement().multiply(1, 1.15F, 1)); }
    }

    public boolean isAcceptibleReturnOwner() {
        Entity currentOwner = this.getOwner();
        if (currentOwner != null && currentOwner.isAlive()) {
            return !(currentOwner instanceof ServerPlayer) || !currentOwner.isSpectator();
        } else {
            return false;
        }
    }

    public boolean isFoil() {
        return this.entityData.get(ID_FOIL);
    }

    @Nullable
    @Override
    protected EntityHitResult findHitEntity(@NotNull Vec3 currentPosition, @NotNull Vec3 nextPosition) {
        return super.findHitEntity(currentPosition, nextPosition);
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        Entity entity = entityHitResult.getEntity();
        if (entity == this.getOwner() && this.tickCount < 20) { return; }
        float f = 5.0F;
        Entity entity2 = this.getOwner();
        DamageSource damageSource;
        if (this.doMaceSmash()) {
            damageSource = this.damageSources().source(HurlDamageTypes.FALLING_MACE_DAMAGE, this, entity2 == null ? this : entity2);
        } else {
            damageSource = this.damageSources().mace(entity2 == null ? this : entity2);
        }

        if (this.getFallDistance() > 5.0 && this.getDeltaMovement().y() < -0.2) {
            double fDis = this.getFallDistance();

            double g;
            if (fDis <= 15.0) {
                g = f + 2.0 * (fDis - 5);
            } else {
                g = 25.0 + fDis - 15;
            }

            if (this.level() instanceof ServerLevel serverWorld) {
                f = (float)(g + EnchantmentHelper.modifyFallBasedDamage(serverWorld, Objects.requireNonNull(this.getWeaponItem()), entity, damageSource, 0.0F) * fDis * 0.4);
                f = EnchantmentHelper.modifyDamage(serverWorld, this.getWeaponItem(), entity, damageSource, f);
            }
        }

        this.dealtDamage = true;
        // TutorialMod.LOGGER.info("Damage {}", String.valueOf(f));
        if (entity.hurtOrSimulate(damageSource, f)) {
            if (entity.getType() == EntityType.ENDERMAN) {
                return;
            }

            if (this.level() instanceof ServerLevel serverWorld) {
                EnchantmentHelper.doPostAttackEffectsWithItemSourceOnBreak(serverWorld, entity, damageSource, this.getWeaponItem(), item -> this.kill(serverWorld));
                if (serverWorld.isThundering() && serverWorld.canSeeSky(this.blockPosition()) && this.doMaceSmash()) {
                    HurlMaceItem.trySpawnChannelingLightningBolt(this.getWeaponItem(), this.blockPosition(), this);
                }
            }

            if (this.entityData.get(ID_FIRE_ASPECT) > 0) {
                this.onFireAspectEntityHit(entityHitResult);
            }

            if (getWindBursts() > 1) {
                this.onWindBurstEntityHit(entityHitResult);
                this.createWindBurst();
            }

            if (entity instanceof LivingEntity livingEntity) {
                this.doKnockback(livingEntity, damageSource);
                if (getWindBursts() == 1) { this.createWindBurst(); }
                this.doPostHurtEffects(livingEntity);
            }
        }

        this.playSound(SoundEvents.MACE_SMASH_AIR, 0.85F, 1.0F);
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult blockHitResult) {
        Level world = this.level();

        if (this.isInLiquid()) { this.entityData.set(ID_WIND_BURSTS, (byte) 0); }
        if (getWindBursts() > 1 || (getWindBursts() == 1 && blockHitResult.getDirection() != Direction.UP)) {
            this.onWindBurstBlockHit(blockHitResult);
            this.createWindBurst();
            return;
        }

        if (blockHitResult.getDirection() != Direction.UP) {
            this.onNonTopBlockHit(blockHitResult);
            this.playSound(SoundEvents.MACE_SMASH_AIR, 1.0F, 0.85F + (this.random.nextFloat() * 0.3F));
        } else {
            if (doMaceSmash()) {
                HurlMaceItem.knockbackNearbyEntitiesThrown(world, this);
                this.checkSupportingBlock(this.onGround(), this.getKnownMovement());

                if (!this.dealtDamage) {
                    HurlMaceItem.trySpawnChannelingLightningBolt(this.getWeaponItem(), this.blockPosition(), this);
                }
                this.playSound(SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.0F, 0.90F + (this.random.nextFloat() * 0.1F));
            } else {
                this.playSound(SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.6F, 0.90F + (this.random.nextFloat() * 0.2F));
            }

            if (this.entityData.get(ID_FIRE_ASPECT) > 0 && !this.isInLiquid()) {
                this.onFireAspectBlockHit(blockHitResult);
                this.setPeak();
            }
            // Smash through glasslike blocks functionality
            /*
            if (this.doMaceSmash() && world.getBlockState(blockHitResult.getBlockPos()).isIn(TagKey.of(RegistryKeys.BLOCK, Identifier.of(Hurl.MOD_ID, "mace_smashable_blocks")))) {
                if (world.getBlockState(blockHitResult.getBlockPos()).isIn(BlockTags.ICE)) {
                    world.setBlockState(blockHitResult.getBlockPos(), Blocks.WATER.getDefaultState());
                } else {
                    world.setBlockState(blockHitResult.getBlockPos(), Blocks.AIR.getDefaultState());
                }
                this.setVelocity(this.getVelocity().multiply(1, 0.9, 1));
                return;
            }
             */
            super.onHitBlock(blockHitResult);
            this.setSoundEvent(SoundEvents.EMPTY);
            if (getWindBursts() == 1) { this.createWindBurst(); }
            if (world.getBlockState(blockHitResult.getBlockPos()).getBlock() instanceof AirBlock) { this.setInGround(false); }
        }
    }

    protected void onNonTopBlockHit(BlockHitResult blockHitResult) {
        BlockState blockState = this.level().getBlockState(blockHitResult.getBlockPos());
        blockState.onProjectileHit(this.level(), blockState, blockHitResult, this);

        ItemStack itemStack = this.getWeaponItem();
        if (this.level() instanceof ServerLevel serverWorld && itemStack != null) {
            this.hitBlockEnchantmentEffects(serverWorld, blockHitResult, itemStack);
        }

        Direction hitSide = blockHitResult.getDirection();
        Vec3 v = this.getDeltaMovement();
        Vec3 offset = new Vec3(Math.signum(v.x), Math.signum(v.y), Math.signum(v.z));
        this.setPos(this.position().subtract(offset.scale(0.05F)));

        this.bounceMace(hitSide.getUnitVec3().normalize(), 0.6, 0.6);
    }

    protected void onWindBurstBlockHit(BlockHitResult blockHitResult) {
        BlockState blockState = this.level().getBlockState(blockHitResult.getBlockPos());
        blockState.onProjectileHit(this.level(), blockState, blockHitResult, this);

        ItemStack itemStack = this.getWeaponItem();
        if (this.level() instanceof ServerLevel serverWorld && itemStack != null) {
            this.hitBlockEnchantmentEffects(serverWorld, blockHitResult, itemStack);
        }

        Direction hitSide = blockHitResult.getDirection();
        Vec3 v = this.getDeltaMovement();
        Vec3 offset = new Vec3(Math.signum(v.x), Math.signum(v.y), Math.signum(v.z));
        this.setPos(this.position().subtract(offset.scale(0.2F)));

        this.bounceMace(hitSide.getUnitVec3().normalize(), 0.85, this.getWindBurstBounceScaling());

        this.playSound(this.getHitGroundSoundEvent(), 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
        this.shakeTime = 3 * getWindBursts();
    }

    protected void tryWindBurstSkip() {
        Level world = this.level();
        Vec3 footPos = this.position();
        Vec3 headPos = this.position().add(0, MACE_HEIGHT, 0);

        if (world.getFluidState(BlockPos.containing(footPos)).getType() != Fluids.EMPTY
                && world.getFluidState(BlockPos.containing(headPos)).getType() == Fluids.EMPTY) {
            this.bounceMace(Direction.UP.getUnitVec3(), 1.01, 0.99);
            this.createSmallWindBurst();
        }
    }

    protected void onWindBurstEntityHit(EntityHitResult entityHitResult) {
        Entity entity = entityHitResult.getEntity();
        Vec3 center = entity.getBoundingBox().getCenter();

        this.bounceMace(this.position().subtract(center).normalize(), 0.85, this.getWindBurstBounceScaling());

        this.shakeTime = 3 * getWindBursts();
    }

    protected void onFireAspectBlockHit(BlockHitResult blockHitResult) {
        if ( !this.doMaceSmash()) {this.createFireBurst(-2, 0.25F, false); }
        else { this.createFireBurst(-2, this.getFireBurstRadius(), true); }
    }

    protected void onFireAspectEntityHit(EntityHitResult entityHitResult) {
        Entity entity = entityHitResult.getEntity();
        entity.setRemainingFireTicks(this.getFireAspectTicks());
    }

    public float getFallDistance() {
        return (this.entityData.get(lastPeak) - (float) this.position().y());
    }

    public boolean doMaceSmash() {
        return this.getFallDistance() > 8 && this.getDeltaMovement().y < -0.3;
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.EMPTY;
    }

    @Override
    protected void hitBlockEnchantmentEffects(ServerLevel world, BlockHitResult blockHitResult, ItemStack weaponStack) {
        Vec3 vec3d = blockHitResult.getBlockPos().clampLocationWithin(blockHitResult.getLocation());
        EnchantmentHelper.onHitBlock(
                world,
                weaponStack,
                this.getOwner() instanceof LivingEntity livingEntity ? livingEntity : null,
                this,
                null,
                vec3d,
                world.getBlockState(blockHitResult.getBlockPos()),
                item -> this.kill(world)
        );
    }

    @Override
    public ItemStack getWeaponItem() {
        return this.getPickupItemStackOrigin();
    }

    @Override
    protected boolean tryPickup(Player player) {
        return super.tryPickup(player) || this.isNoPhysics() && this.ownedBy(player) && player.getInventory().add(this.getPickupItem());
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.MACE);
    }

    @Override
    public void playerTouch(Player player) {
        if (this.ownedBy(player) || this.getOwner() == null) {
            super.playerTouch(player);
        }
    }

    @Override
    public byte getPierceLevel() {
        return (byte)14;
    }

    public void setPeak() {
        setPeak((float) this.position().y());
    }

    public void setPeak(float val) {
        this.entityData.set(lastPeak, val);
    }

    public void ignite() {
        if (this.getFireAspect(this.getWeaponItem()) > 0) {this.igniteForSeconds(100);}
    }

    private byte getLoyalty(ItemStack stack) {
        return this.level() instanceof ServerLevel serverWorld
                ? (byte)Mth.clamp(EnchantmentHelper.getTridentReturnToOwnerAcceleration(serverWorld, stack, this), 0, 127)
                : 0;
    }

    private byte getWindBurstLevel(ItemStack stack) {
        return this.level() instanceof ServerLevel serverWorld
                ? (byte) EnchantmentHelper.getItemEnchantmentLevel(serverWorld.registryAccess().getOrThrow(Enchantments.WIND_BURST), stack)
                : 0;
    }

    public byte getWindBursts() {
        return this.entityData.get(ID_WIND_BURSTS);
    }

    public float getWindBurstRadius() {
        return (getWindBursts() > 0 ? (getWindBursts() * 0.45F) + 0.8F : 0) + (this.doMaceSmash() ? 0.8F : 0);
    }

    public float getWindBurstKnockback() {
        return (getWindBursts() > 0 ? (getWindBursts() * 0.35F) + 0.6F : 0)  + (this.doMaceSmash() ? 0.2F : 0);
    }

    public float getWindBurstBounceScaling() {
        return (getWindBursts() > 0 ? 0.85F + (getWindBursts() * 0.02F) : 0) + (this.doMaceSmash() ? 0.06F : 0);
    }

    public void bounceMace(Vec3 surfaceNormal, double velocityScaling) {
        bounceMace(surfaceNormal, velocityScaling, velocityScaling);
    }

    public void bounceMace(Vec3 surfaceNormal, double velocityScalingHorizontal, double velocityScalingVertical) {
        Vec3 v = this.getDeltaMovement();

        // R = V - 2 * (V ⋅ N) * N
        Vec3 r = v.subtract(surfaceNormal.scale(2 * v.dot(surfaceNormal)));
        this.setDeltaMovement(r.multiply(velocityScalingHorizontal, velocityScalingVertical, velocityScalingHorizontal));
    }

    public void createWindBurst() {
        this.level().explode(
                this,
                this.damageSources().windCharge(this, (LivingEntity) this.getOwner()),
                new SimpleExplosionDamageCalculator(
                        true,
                        false,
                        Optional.of(this.getWindBurstKnockback()),
                        BuiltInRegistries.BLOCK.get(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity())),
                this.position().x(),
                this.position().y(),
                this.position().z(),
                this.getWindBurstRadius(),
                false,
                Level.ExplosionInteraction.TRIGGER,
                ParticleTypes.GUST_EMITTER_SMALL,
                ParticleTypes.GUST_EMITTER_LARGE,
                WeightedList.of(),
                SoundEvents.WIND_CHARGE_BURST
        );

        byte decrementedWindBursts = (byte) (getWindBursts() - 1);
        this.entityData.set(ID_WIND_BURSTS, decrementedWindBursts);
    }

    public void createSmallWindBurst() {
        this.level().explode(
                this,
                this.damageSources().windCharge(this, (LivingEntity) this.getOwner()),
                new SimpleExplosionDamageCalculator(
                        true,
                        false,
                        Optional.of(this.getWindBurstKnockback()),
                        BuiltInRegistries.BLOCK.get(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity())),
                this.position().x(),
                this.position().y(),
                this.position().z(),
                this.getWindBurstRadius(),
                false,
                Level.ExplosionInteraction.TRIGGER,
                ParticleTypes.GUST_EMITTER_SMALL,
                ParticleTypes.GUST_EMITTER_SMALL,
                WeightedList.of(),
                SoundEvents.WIND_CHARGE_BURST
        );
        byte decrementedWindBursts = (byte) (getWindBursts() - 1);
        this.entityData.set(ID_WIND_BURSTS, decrementedWindBursts);
    }

    private byte getFireAspect(ItemStack stack) {
        return this.level() instanceof ServerLevel serverWorld
                ? (byte) EnchantmentHelper.getItemEnchantmentLevel(serverWorld.registryAccess().getOrThrow(Enchantments.FIRE_ASPECT), stack)
                : 0;
    }

    public float getFireBurstRadius() {
        return (float) ((this.entityData.get(ID_FIRE_ASPECT) * 0.9) + (this.doMaceSmash() ? (this.getFallDistance() - 8.0F) * 0.02F : 0));
    }

    public int getFireAspectTicks() {
        return (this.entityData.get(ID_FIRE_ASPECT) * 80);
    }

    public void createFireBurst(float knockback, float radius, boolean explode) {
        this.level().explode(
                this,
                null,
                new SimpleExplosionDamageCalculator(
                        true,
                        false,
                        Optional.of(knockback),
                        BuiltInRegistries.BLOCK.get(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity())),
                this.position().x(),
                this.position().y(),
                this.position().z(),
                radius,
                true,
                Level.ExplosionInteraction.NONE,
                ParticleTypes.ASH,
                ParticleTypes.ASH,
                WeightedList.of(),
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY)
        );
        if (explode) {
            this.level().explode(
                    this,
                    this.damageSources().explosion(this, this.getOwner()),
                    new SimpleExplosionDamageCalculator(
                            true,
                            false,
                            Optional.empty(),
                            BuiltInRegistries.BLOCK.get(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity())),
                    this.position().x(),
                    this.position().y(),
                    this.position().z(),
                    0.15F,
                    false,
                    Level.ExplosionInteraction.TNT,
                    ParticleTypes.EXPLOSION_EMITTER,
                    ParticleTypes.EXPLOSION_EMITTER,
                    WeightedList.of(),
                    SoundEvents.GENERIC_EXPLODE
            );
        }
    }

    public void tickDespawn() {
        int loyalty = (Byte)this.entityData.get(ID_LOYALTY);
        if (this.pickup != Pickup.ALLOWED || loyalty <= 0) {
            super.tickDespawn();
        }
    }

    @Override
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        return true;
    }
}
