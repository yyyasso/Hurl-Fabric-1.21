package net.yyasso.hurl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Unit;
import net.minecraft.util.math.RotationAxis;
import net.yyasso.hurl.mace.MaceEntity;

import java.util.List;

@Environment(EnvType.CLIENT)
public class MaceEntityRenderer extends EntityRenderer<MaceEntity, MaceEntityRenderState> {
    private final MaceEntityModel model;

    public MaceEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.model = new MaceEntityModel(context.getPart(MaceEntityModel.MODEL_LAYER));
    }

    public void render(MaceEntityRenderState tridentEntityRenderState, MatrixStack matrixStack, OrderedRenderCommandQueue orderedRenderCommandQueue, CameraRenderState cameraRenderState) {
        matrixStack.push();
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(tridentEntityRenderState.yaw - 90.0F));
        matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(tridentEntityRenderState.pitch + 90.0F));
        List<RenderLayer> list = ItemRenderer.getGlintRenderLayers(this.model.getLayer(MaceEntityModel.TEXTURE), false, tridentEntityRenderState.enchanted);

        for(int i = 0; i < list.size(); ++i) {
            orderedRenderCommandQueue.getBatchingQueue(i).submitModel(this.model, Unit.INSTANCE, matrixStack, list.get(i), tridentEntityRenderState.light, OverlayTexture.DEFAULT_UV, -1, null, tridentEntityRenderState.outlineColor, null);
        }

        matrixStack.pop();
        super.render(tridentEntityRenderState, matrixStack, orderedRenderCommandQueue, cameraRenderState);
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
