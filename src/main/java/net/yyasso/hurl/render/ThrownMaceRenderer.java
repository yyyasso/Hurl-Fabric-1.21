package net.yyasso.hurl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Unit;
import net.yyasso.hurl.mace.ThrownMace;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jetbrains.annotations.NotNull;

@Environment(EnvType.CLIENT)
public class ThrownMaceRenderer extends EntityRenderer<@NotNull ThrownMace, @NotNull ThrownMaceRenderState> {
    private final MaceEntityModel model;

    public ThrownMaceRenderer(final EntityRendererProvider.Context context) {
        super(context);
        this.model = new MaceEntityModel(context.bakeLayer(MaceEntityModel.MODEL_LAYER));
    }

    public void submit(final ThrownMaceRenderState state, final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector, final @NotNull CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yRot - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.xRot + 90.0F));
        submitNodeCollector.order(0).submitModel(this.model, Unit.INSTANCE, poseStack, MaceEntityModel.TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, (ModelFeatureRenderer.CrumblingOverlay)null);
        if (state.isFoil) {
            submitNodeCollector.order(1).submitModel(this.model, Unit.INSTANCE, poseStack, ItemFeatureRenderer.getFoilRenderType(this.model.renderType(MaceEntityModel.TEXTURE), false), state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, (ModelFeatureRenderer.CrumblingOverlay)null);
        }

        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
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
