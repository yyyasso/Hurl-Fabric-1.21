package net.yyasso.hurl.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.state.EntityRenderState;

@Environment(EnvType.CLIENT)
public class MaceEntityRenderState extends EntityRenderState {
    public float pitch;
    public float yaw;
    public boolean enchanted;
}
