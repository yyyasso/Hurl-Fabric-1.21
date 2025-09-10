package net.yyasso.hurl.mace;

import com.mojang.serialization.Codec;
import net.minecraft.block.*;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.explosion.AdvancedExplosionBehavior;
import net.yyasso.hurl.registry.HurlDamageTypes;
import net.yyasso.hurl.registry.HurlEntityType;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class MaceEntity extends PersistentProjectileEntity {
    public static final float MACE_HEIGHT = 0.55F;
    public static final float MACE_WIDTH = 0.55F;

    private static final TrackedData<Byte> LOYALTY = DataTracker.registerData(MaceEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Byte> FIRE_ASPECT = DataTracker.registerData(MaceEntity.class, TrackedDataHandlerRegistry.BYTE);
    private static final TrackedData<Boolean> ENCHANTED = DataTracker.registerData(MaceEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Byte> WIND_BURSTS = DataTracker.registerData(MaceEntity.class, TrackedDataHandlerRegistry.BYTE);
    private final Supplier<Byte> defaultWindBurstsSupplier = () -> (byte) 0;
    public boolean dealtDamage = false;
    public double lastPeak;
    public int returnTimer;

    public MaceEntity(EntityType<? extends MaceEntity> entityType, World world) {
        super(entityType, world);
    }

    public MaceEntity(World world, LivingEntity owner, ItemStack stack) {
        super(HurlEntityType.MACE, owner, world, stack, null);
        this.dataTracker.set(LOYALTY, this.getLoyalty(stack));
        this.dataTracker.set(FIRE_ASPECT, this.getFireAspect(stack));
        this.dataTracker.set(ENCHANTED, stack.hasGlint());
        this.dataTracker.set(WIND_BURSTS, this.getWindBurstLevel(stack));
        this.setPeak();
        this.ignite();
    }

    public MaceEntity(World world, double x, double y, double z, ItemStack stack) {
        super(HurlEntityType.MACE, x, y, z, world, stack, stack);
        this.dataTracker.set(LOYALTY, this.getLoyalty(stack));
        this.dataTracker.set(FIRE_ASPECT, this.getFireAspect(stack));
        this.dataTracker.set(ENCHANTED, stack.hasGlint());
        this.dataTracker.set(WIND_BURSTS, this.getWindBurstLevel(stack));
        this.setPeak();
        this.ignite();
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(LOYALTY, (byte)0);
        builder.add(FIRE_ASPECT, (byte)0);
        builder.add(ENCHANTED, false);
        builder.add(WIND_BURSTS, (byte)0);
    }

    @Override
    protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        this.dealtDamage = view.getBoolean("DealtDamage", false);
        this.dataTracker.set(WIND_BURSTS, view.read("WindBursts", Codec.BYTE).orElseGet(defaultWindBurstsSupplier));
        this.dataTracker.set(LOYALTY, this.getLoyalty(this.getItemStack()));
        this.dataTracker.set(FIRE_ASPECT, this.getFireAspect(this.getItemStack()));
    }

    @Override
    protected void writeCustomData(WriteView view) {
        super.writeCustomData(view);
        view.putByte("WindBursts", this.getWindBursts());
        view.putBoolean("DealtDamage", this.dealtDamage);
    }

    @Override
    public void tick() {
        if (this.isInGround() || this.isNoClip()) {
            this.setPeak();
        } else {
            if (this.getVelocity().y > 0 || (this.getVelocity().y > -0.5 && this.isTouchingWater())) {
                this.setPeak();
            }
        }

        if (this.inGroundTime > 4) {
            this.dealtDamage = true;
        }

        Entity entity = this.getOwner();
        int i = this.dataTracker.get(LOYALTY);
        if (i > 0 && (this.dealtDamage || this.isNoClip()) && entity != null) {
            if (!this.isOwnerAlive()) {
                if (this.getWorld() instanceof ServerWorld serverWorld && this.pickupType == PersistentProjectileEntity.PickupPermission.ALLOWED) {
                    this.dropStack(serverWorld, this.asItemStack(), 0.1F);
                }

                this.discard();
            } else {
                if (!(entity instanceof PlayerEntity) && this.getPos().distanceTo(entity.getEyePos()) < entity.getWidth() + 1.0) {
                    this.discard();
                    return;
                }

                this.setNoClip(true);
                Vec3d vec3d = entity.getEyePos().subtract(this.getPos());
                this.setPos(this.getX(), this.getY() + vec3d.y * 0.015 * i, this.getZ());
                double d = 0.05 * i;
                this.setVelocity(this.getVelocity().multiply(0.95).add(vec3d.normalize().multiply(d)));
                if (this.returnTimer == 0) {
                    this.playSound(SoundEvents.ITEM_TRIDENT_RETURN, 10.0F, 1.0F);
                }

                this.returnTimer++;
            }
        }

        if (this.getVelocity().getY() < 0.0 && this.getVelocity().getY() > -0.7 && getWindBursts() > 0) {
            tryWindBurstSkip();
        }

        super.tick();
        if (this.isTouchingWater()) { this.setVelocity(this.getVelocity().multiply(1, 1.15F, 1)); }
    }

    private boolean isOwnerAlive() {
        Entity entity = this.getOwner();
        if (entity != null && entity.isAlive()) {
            return !(entity instanceof ServerPlayerEntity) || !entity.isSpectator();
        } else {
            return false;
        }
    }

    public boolean isEnchanted() {
        return this.dataTracker.get(ENCHANTED);
    }

    @Nullable
    @Override
    protected EntityHitResult getEntityCollision(Vec3d currentPosition, Vec3d nextPosition) {
        return super.getEntityCollision(currentPosition, nextPosition);
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        Entity entity = entityHitResult.getEntity();
        if (entity == this.getOwner() && this.age < 20) { return; }
        float f = 5.0F;
        Entity entity2 = this.getOwner();
        DamageSource damageSource;
        if (this.doMaceSmash()) {
            damageSource = this.getDamageSources().create(HurlDamageTypes.FALLING_MACE_DAMAGE, this, entity2 == null ? this : entity2);
        } else {
            damageSource = this.getDamageSources().maceSmash(entity2 == null ? this : entity2);
        }

        if (this.getFallDistance() > 5.0 && this.getVelocity().getY() < -0.2) {
            double fDis = this.getFallDistance();

            double g;
            if (fDis <= 15.0) {
                g = f + 2.0 * (fDis - 5);
            } else {
                g = 25.0 + fDis - 15;
            }

            if (this.getWorld() instanceof ServerWorld serverWorld) {
                f = (float)(g + EnchantmentHelper.getSmashDamagePerFallenBlock(serverWorld, this.getWeaponStack(), entity, damageSource, 0.0F) * fDis * 0.4);
                f = EnchantmentHelper.getDamage(serverWorld, this.getWeaponStack(), entity, damageSource, f);
            }
        }

        this.dealtDamage = true;
        // TutorialMod.LOGGER.info("Damage {}", String.valueOf(f));
        if (entity.sidedDamage(damageSource, f)) {
            if (entity.getType() == EntityType.ENDERMAN) {
                return;
            }

            if (this.getWorld() instanceof ServerWorld serverWorld) {
                EnchantmentHelper.onTargetDamaged(serverWorld, entity, damageSource, this.getWeaponStack(), item -> this.kill(serverWorld));
                if (serverWorld.isThundering() && serverWorld.isSkyVisible(this.getBlockPos()) && this.doMaceSmash()) {
                    HurlMaceItem.trySpawnChannelingLightningBolt(this.getWeaponStack(), this.getBlockPos(), this);
                }
            }

            if (this.dataTracker.get(FIRE_ASPECT) > 0) {
                this.onFireAspectEntityHit(entityHitResult);
            }

            if (getWindBursts() > 1) {
                this.onWindBurstEntityHit(entityHitResult);
                this.createWindBurst();
            }

            if (entity instanceof LivingEntity livingEntity) {
                this.knockback(livingEntity, damageSource);
                if (getWindBursts() == 1) { this.createWindBurst(); }
                this.onHit(livingEntity);
            }
        }

        this.setVelocity(this.getVelocity().multiply(0.75, 0.85, 0.75));
        this.playSound(SoundEvents.ITEM_MACE_SMASH_AIR, 1.0F, 1.0F);
    }

    @Override
    protected void onBlockHit(BlockHitResult blockHitResult) {
        World world = this.getWorld();
        if (doMaceSmash()) {
            HurlMaceItem.knockbackNearbyEntitiesThrown(world, this);
            this.updateSupportingBlockPos(this.isOnGround(), this.getMovement());

            if (!this.dealtDamage) {
                HurlMaceItem.trySpawnChannelingLightningBolt(this.getWeaponStack(), this.getBlockPos(), this);
            }
        }
        this.playSound(this.getBlockHitSound(), 2.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));

        if (this.isInFluid()) { this.dataTracker.set(WIND_BURSTS, (byte) 0); }
        if (getWindBursts() > 1 || (getWindBursts() == 1 && blockHitResult.getSide() != Direction.UP)) {
            this.onWindBurstBlockHit(blockHitResult);
            this.createWindBurst();
            return;
        }

        if (blockHitResult.getSide() != Direction.UP) {
            this.onNonTopBlockHit(blockHitResult);
        } else {
            if (this.dataTracker.get(FIRE_ASPECT) > 0 && !this.isInFluid()) {
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
            super.onBlockHit(blockHitResult);
            this.setSound(SoundEvents.INTENTIONALLY_EMPTY);
            if (getWindBursts() == 1) { this.createWindBurst(); }
            if (world.getBlockState(blockHitResult.getBlockPos()).getBlock() instanceof AirBlock) { this.setInGround(false); }
        }
    }

    protected void onNonTopBlockHit(BlockHitResult blockHitResult) {
        BlockState blockState = this.getWorld().getBlockState(blockHitResult.getBlockPos());
        blockState.onProjectileHit(this.getWorld(), blockState, blockHitResult, this);

        ItemStack itemStack = this.getWeaponStack();
        if (this.getWorld() instanceof ServerWorld serverWorld && itemStack != null) {
            this.onBlockHitEnchantmentEffects(serverWorld, blockHitResult, itemStack);
        }

        Direction hitSide = blockHitResult.getSide();
        Vec3d v = this.getVelocity();
        Vec3d offset = new Vec3d(Math.signum(v.x), Math.signum(v.y), Math.signum(v.z));
        this.setPosition(this.getPos().subtract(offset.multiply(0.05F)));

        this.bounceMace(hitSide.getDoubleVector().normalize(), 0.6, 0.6);
        this.playSound(SoundEvents.ITEM_MACE_SMASH_AIR, 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
    }

    protected void onWindBurstBlockHit(BlockHitResult blockHitResult) {
        BlockState blockState = this.getWorld().getBlockState(blockHitResult.getBlockPos());
        blockState.onProjectileHit(this.getWorld(), blockState, blockHitResult, this);

        ItemStack itemStack = this.getWeaponStack();
        if (this.getWorld() instanceof ServerWorld serverWorld && itemStack != null) {
            this.onBlockHitEnchantmentEffects(serverWorld, blockHitResult, itemStack);
        }

        Direction hitSide = blockHitResult.getSide();
        Vec3d v = this.getVelocity();
        Vec3d offset = new Vec3d(Math.signum(v.x), Math.signum(v.y), Math.signum(v.z));
        this.setPosition(this.getPos().subtract(offset.multiply(0.2F)));

        this.bounceMace(hitSide.getDoubleVector().normalize(), 0.85, this.getWindBurstBounceScaling());

        this.playSound(this.getSound(), 1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
        this.shake = 3 * getWindBursts();
    }

    protected void tryWindBurstSkip() {
        World world = this.getWorld();
        Vec3d footPos = this.getPos();
        Vec3d headPos = this.getPos().add(0, MACE_HEIGHT, 0);

        if (world.getFluidState(BlockPos.ofFloored(footPos)).getFluid() != Fluids.EMPTY
                && world.getFluidState(BlockPos.ofFloored(headPos)).getFluid() == Fluids.EMPTY) {
            this.bounceMace(Direction.UP.getDoubleVector(), 1.01, 0.99);
            this.createSmallWindBurst();
        }
    }

    protected void onWindBurstEntityHit(EntityHitResult entityHitResult) {
        Entity entity = entityHitResult.getEntity();
        Vec3d center = entity.getBoundingBox().getCenter();

        this.bounceMace(this.getPos().subtract(center).normalize(), 0.85, this.getWindBurstBounceScaling());

        this.shake = 3 * getWindBursts();
    }

    protected void onFireAspectBlockHit(BlockHitResult blockHitResult) {
        if ( !this.doMaceSmash()) {this.createFireBurst(-2, 0.5F, false); }
        else { this.createFireBurst(-2, this.getFireBurstRadius(), true); }
    }

    protected void onFireAspectEntityHit(EntityHitResult entityHitResult) {
        Entity entity = entityHitResult.getEntity();
        entity.setFireTicks(this.getFireAspectTicks());
    }

    public double getFallDistance() {
        return (this.lastPeak - this.getPos().getY());
    }

    public boolean doMaceSmash() {
        return this.getFallDistance() > 8 && this.getVelocity().y < -0.3;
    }

    protected SoundEvent getBlockHitSound() {
        if (doMaceSmash()) { return SoundEvents.ITEM_MACE_SMASH_GROUND; }
        else { return SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY; }
    }

    @Override
    protected SoundEvent getHitSound() {
        return SoundEvents.INTENTIONALLY_EMPTY;
    }

    @Override
    protected void onBlockHitEnchantmentEffects(ServerWorld world, BlockHitResult blockHitResult, ItemStack weaponStack) {
        Vec3d vec3d = blockHitResult.getBlockPos().clampToWithin(blockHitResult.getPos());
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
    public ItemStack getWeaponStack() {
        return this.getItemStack();
    }

    @Override
    protected boolean tryPickup(PlayerEntity player) {
        return super.tryPickup(player) || this.isNoClip() && this.isOwner(player) && player.getInventory().insertStack(this.asItemStack());
    }

    @Override
    protected ItemStack getDefaultItemStack() {
        return new ItemStack(Items.MACE);
    }

    @Override
    public void onPlayerCollision(PlayerEntity player) {
        if (this.isOwner(player) || this.getOwner() == null) {
            super.onPlayerCollision(player);
        }
    }

    @Override
    public byte getPierceLevel() {
        return (byte)12;
    }

    public void setPeak() {
        this.lastPeak = this.getPos().getY();
    }

    public void ignite() {
        if (this.getFireAspect(this.getWeaponStack()) > 0) {this.setOnFireFor(100);}
    }

    private byte getLoyalty(ItemStack stack) {
        return this.getWorld() instanceof ServerWorld serverWorld
                ? (byte)MathHelper.clamp(EnchantmentHelper.getTridentReturnAcceleration(serverWorld, stack, this), 0, 127)
                : 0;
    }

    private byte getWindBurstLevel(ItemStack stack) {
        return this.getWorld() instanceof ServerWorld serverWorld
                ? (byte) EnchantmentHelper.getLevel(serverWorld.getRegistryManager().getEntryOrThrow(Enchantments.WIND_BURST), stack)
                : 0;
    }

    public byte getWindBursts() {
        return this.dataTracker.get(WIND_BURSTS);
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

    public void bounceMace(Vec3d surfaceNormal, double velocityScaling) {
        bounceMace(surfaceNormal, velocityScaling, velocityScaling);
    }

    public void bounceMace(Vec3d surfaceNormal, double velocityScalingHorizontal, double velocityScalingVertical) {
        Vec3d v = this.getVelocity();

        // R = V - 2 * (V ⋅ N) * N
        Vec3d r = v.subtract(surfaceNormal.multiply(2 * v.dotProduct(surfaceNormal)));
        this.setVelocity(r.multiply(velocityScalingHorizontal, velocityScalingVertical, velocityScalingHorizontal));
    }

    public void createWindBurst() {
        this.getWorld().createExplosion(
                this,
                this.getDamageSources().windCharge(this, (LivingEntity) this.getOwner()),
                new AdvancedExplosionBehavior(
                        true,
                        false,
                        Optional.of(this.getWindBurstKnockback()),
                        Registries.BLOCK.getOptional(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity())),
                this.getPos().getX(),
                this.getPos().getY(),
                this.getPos().getZ(),
                this.getWindBurstRadius(),
                false,
                World.ExplosionSourceType.TRIGGER,
                ParticleTypes.GUST_EMITTER_SMALL,
                ParticleTypes.GUST_EMITTER_LARGE,
                SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST
        );
        byte decrementedWindBursts = (byte) (getWindBursts() - 1);
        this.dataTracker.set(WIND_BURSTS, decrementedWindBursts);
    }

    public void createSmallWindBurst() {
        this.getWorld().createExplosion(
                this,
                this.getDamageSources().windCharge(this, (LivingEntity) this.getOwner()),
                new AdvancedExplosionBehavior(
                        true,
                        false,
                        Optional.of(this.getWindBurstKnockback()),
                        Registries.BLOCK.getOptional(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity())),
                this.getPos().getX(),
                this.getPos().getY(),
                this.getPos().getZ(),
                this.getWindBurstRadius(),
                false,
                World.ExplosionSourceType.TRIGGER,
                ParticleTypes.GUST_EMITTER_SMALL,
                ParticleTypes.GUST_EMITTER_SMALL,
                SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST
        );
        byte decrementedWindBursts = (byte) (getWindBursts() - 1);
        this.dataTracker.set(WIND_BURSTS, decrementedWindBursts);
    }

    private byte getFireAspect(ItemStack stack) {
        return this.getWorld() instanceof ServerWorld serverWorld
                ? (byte) EnchantmentHelper.getLevel(serverWorld.getRegistryManager().getEntryOrThrow(Enchantments.FIRE_ASPECT), stack)
                : 0;
    }

    public float getFireBurstRadius() {
        return (float) ((this.dataTracker.get(FIRE_ASPECT)) + (this.doMaceSmash() ? (this.getFallDistance() - 8.0F) * 0.02F : 0));
    }

    public int getFireAspectTicks() {
        return (this.dataTracker.get(FIRE_ASPECT) * 80);
    }

    public void createFireBurst(float knockback, float radius, boolean explode) {
        this.getWorld().createExplosion(
                this,
                null,
                new AdvancedExplosionBehavior(
                        true,
                        false,
                        Optional.of(knockback),
                        Registries.BLOCK.getOptional(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity())),
                this.getPos().getX(),
                this.getPos().getY(),
                this.getPos().getZ(),
                radius,
                true,
                World.ExplosionSourceType.NONE,
                ParticleTypes.FLASH,
                ParticleTypes.FLASH,
                Registries.SOUND_EVENT.getEntry(SoundEvents.INTENTIONALLY_EMPTY)
        );
        if (explode) {
            this.getWorld().createExplosion(
                    this,
                    this.getDamageSources().explosion(this, this.getOwner()),
                    new AdvancedExplosionBehavior(
                            true,
                            false,
                            Optional.empty(),
                            Registries.BLOCK.getOptional(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity())),
                    this.getPos().getX(),
                    this.getPos().getY(),
                    this.getPos().getZ(),
                    0.15F,
                    false,
                    World.ExplosionSourceType.TNT,
                    ParticleTypes.FLASH,
                    ParticleTypes.FLASH,
                    SoundEvents.ENTITY_GENERIC_EXPLODE
            );
        }
    }

    @Override
    public void age() {
        int i = this.dataTracker.get(LOYALTY);
        if (this.pickupType != PersistentProjectileEntity.PickupPermission.ALLOWED || i <= 0) {
            super.age();
        }
    }

    @Override
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        return true;
    }
}
