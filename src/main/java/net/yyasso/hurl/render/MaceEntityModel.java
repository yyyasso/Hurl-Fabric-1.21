package net.yyasso.hurl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.*;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import net.yyasso.hurl.Hurl;

// Made with Blockbench 4.12.6
@Environment(EnvType.CLIENT)
public class MaceEntityModel extends Model<Unit> {
    public static final Identifier TEXTURE = Identifier.of(Hurl.MOD_ID, "textures/entity/mace.png");
    public static final EntityModelLayer MODEL_LAYER = new EntityModelLayer(Identifier.of(Hurl.MOD_ID, "mace"), "main");
    public MaceEntityModel(ModelPart root) {
        super(root, RenderLayer::getEntitySolid);
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData modelPartData = modelData.getRoot();

        ModelPartData bb_main = modelPartData.addChild("bb_main", ModelPartBuilder.create()
                .uv(0, 0).cuboid(-4.0F, 0.0F, -4.0F, 8.0F, 8.0F, 8.0F, new Dilation(0.0F))
                .uv(0, 14).cuboid(-1.0F, 8.0F, -1.0F, 2.0F, 8.0F, 2.0F, new Dilation(0.0F))
                .uv(8, 16).cuboid(-1.5F, 16.0F, -1.5F, 3.0F, 2.0F, 3.0F, new Dilation(0.0F)), ModelTransform.rotation(0.0F, 24.0F, 0.0F));

        bb_main.addChild("top_r1", ModelPartBuilder.create().uv(24, 4).cuboid(-2.0F, -2.0F, -2.0F, 2.0F, 2.0F, 2.0F, new Dilation(0.0F)), ModelTransform.of(1.0F, 1.0F, 0.0F, -0.7854F, 0.0F, 0.0F));
        return TexturedModelData.of(modelData, 32, 32);
    }
}
