package net.yyasso.hurl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.yyasso.hurl.Hurl;

// Made with Blockbench 4.12.6
@Environment(EnvType.CLIENT)
public class MaceEntityModel extends Model<Unit> {
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Hurl.MOD_ID, "textures/entity/mace.png");
    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Hurl.MOD_ID, "mace"), "main");

    public MaceEntityModel(ModelPart root)  {
        super(root, RenderTypes::entitySolid);
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition modelPartData = modelData.getRoot();

        PartDefinition bb_main = modelPartData.addOrReplaceChild("bb_main", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
                .texOffs(0, 14).addBox(-1.0F, 8.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F))
                .texOffs(8, 16).addBox(-1.5F, 16.0F, -1.5F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.rotation(0.0F, 24.0F, 0.0F));

        bb_main.addOrReplaceChild("top_r1", CubeListBuilder.create().texOffs(24, 4).addBox(-2.0F, -2.0F, -2.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 1.0F, 0.0F, -0.7854F, 0.0F, 0.0F));
        return LayerDefinition.create(modelData, 32, 32);
    }
}
