package net.yyasso.hurl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ThrownTridentRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.AABB;
import net.yyasso.hurl.Hurl;
import net.yyasso.hurl.mace.ThrownMace;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4fc;
import org.jspecify.annotations.NonNull;

@Environment(EnvType.CLIENT)
public class ThrownMaceRenderer extends EntityRenderer<@NotNull ThrownMace, @NotNull ThrownMaceRenderState> {
    public static final Identifier MACE_TEXTURE = Identifier.fromNamespaceAndPath(Hurl.MOD_ID, "textures/entity/mace.png");
    private final MaceEntityModel model;

    public ThrownMaceRenderer(final EntityRendererProvider.Context context) {
        super(context);
        this.model = new MaceEntityModel(context.bakeLayer(MaceEntityModel.MODEL_LAYER));
    }

    public void submit(final ThrownMaceRenderState state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, state.yRot - 90.0F);
        poseStack.rotateDegrees(Axis.ZP, state.xRot + 90.0F);
        if (state.isFoil) {
            submitNodeCollector.submitModel(this.model, Unit.INSTANCE, poseStack, RenderTypes.entitySolidGlint(MACE_TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        } else {
            submitNodeCollector.submitModel(this.model, Unit.INSTANCE, poseStack, MACE_TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }

        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }

    protected AABB getBoundingBoxForCulling(final ThrownMace entity, final float partialTicks) {
        return super.getBoundingBoxForCulling(entity, partialTicks).inflate((double)1.5F);
    }

    public ThrownMaceRenderState createRenderState() {
        return new ThrownMaceRenderState();
    }

    public void extractRenderState(final ThrownMace entity, final ThrownMaceRenderState state, final float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        state.xRot = entity.getXRot(partialTicks);
        state.isFoil = entity.isFoil();
    }
}
