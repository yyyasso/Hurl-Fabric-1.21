package net.yyasso.hurl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.yyasso.hurl.Hurl;
import net.yyasso.hurl.mace.MaceEntity;

@Environment(EnvType.CLIENT)
public class MaceEntityRenderer extends EntityRenderer<MaceEntity, MaceEntityRenderState> {
    private final MaceEntityModel model;

    public MaceEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.model = new MaceEntityModel(context.getPart(MaceEntityModel.MODEL_LAYER));
    }

    public void render(MaceEntityRenderState maceEntityRenderState, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i) {
        matrixStack.push();
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(maceEntityRenderState.yaw - 90.0F));
        matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(maceEntityRenderState.pitch + 90.0F));
        VertexConsumer vertexConsumer = ItemRenderer.getItemGlintConsumer(
                vertexConsumerProvider, this.model.getLayer(MaceEntityModel.TEXTURE), false, maceEntityRenderState.enchanted
        );
        this.model.render(matrixStack, vertexConsumer, i, OverlayTexture.DEFAULT_UV);
        matrixStack.pop();
        super.render(maceEntityRenderState, matrixStack, vertexConsumerProvider, i);
    }

    public MaceEntityRenderState createRenderState() {
        return new MaceEntityRenderState();
    }

    public void updateRenderState(MaceEntity maceEntity, MaceEntityRenderState maceEntityRenderState, float f) {
        super.updateRenderState(maceEntity, maceEntityRenderState, f);
        maceEntityRenderState.yaw = maceEntity.getLerpedYaw(f);
        maceEntityRenderState.pitch = maceEntity.getLerpedPitch(f);
        maceEntityRenderState.enchanted = maceEntity.isEnchanted();
    }
}
