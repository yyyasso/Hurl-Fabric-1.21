package net.yyasso.hurl;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.yyasso.hurl.registry.HurlEntityType;
import net.yyasso.hurl.render.MaceEntityModel;
import net.yyasso.hurl.render.MaceEntityRenderer;

public class HurlClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(HurlEntityType.MACE, MaceEntityRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(MaceEntityModel.MODEL_LAYER, MaceEntityModel::getTexturedModelData);
    }
}
