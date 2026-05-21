package net.yyasso.hurl;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.yyasso.hurl.registry.HurlEntityType;
import net.yyasso.hurl.render.MaceEntityModel;
import net.yyasso.hurl.render.ThrownMaceRenderer;

public class HurlClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRenderers.register(HurlEntityType.MACE, ThrownMaceRenderer::new);
        ModelLayerRegistry.registerModelLayer(MaceEntityModel.MODEL_LAYER, MaceEntityModel::getTexturedModelData);
    }
}
