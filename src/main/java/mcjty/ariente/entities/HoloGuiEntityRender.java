package mcjty.ariente.entities;

import mcjty.ariente.Ariente;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;

public class HoloGuiEntityRender extends Render<HoloGuiEntity> {

    private static final ResourceLocation guiBackground = new ResourceLocation(Ariente.MODID, "textures/gui/hologui.png");

    public HoloGuiEntityRender(RenderManager renderManager) {
        super(renderManager);
    }

    @Override
    public void doRender(HoloGuiEntity entity, double x, double y, double z, float entityYaw, float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        GlStateManager.rotate(180.0F - entityYaw, 0.0F, 1.0F, 0.0F);

//        GlStateManager.rotate(-90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 0.5f);

        Minecraft mc = Minecraft.getMinecraft();

        mc.renderEngine.bindTexture(guiBackground);

        Tessellator t = Tessellator.getInstance();
        BufferBuilder builder = t.getBuffer();
        builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        double min = -.5;
        double max = .5;
        builder.pos(min, min, 0).tex(0, 0).endVertex(); //1
        builder.pos(max, min, 0).tex(1, 0).endVertex();
        builder.pos(max, max, 0).tex(1, 1).endVertex();
        builder.pos(min, max, 0).tex(0, 1).endVertex();
        builder.pos(min, max, 0).tex(0, 1).endVertex(); //2
        builder.pos(max, max, 0).tex(1, 1).endVertex();
        builder.pos(max, min, 0).tex(1, 0).endVertex();
        builder.pos(min, min, 0).tex(0, 0).endVertex();

        t.draw();

        GlStateManager.disableDepth();
        GlStateManager.scale(0.01, 0.01, 0.01);
        GlStateManager.rotate(180, 0, 1, 0);
        GlStateManager.rotate(180, 0, 0, 1);
        mc.fontRenderer.drawString("Machine Status", -40, -40, 0xaaccff);
        mc.fontRenderer.drawString("OK", -30, -30, 0xffcccc);

        GlStateManager.popMatrix();

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
    }

    protected float interpolateRotation(float prevYawOffset, float yawOffset, float partialTicks) {
        float f;

        for (f = yawOffset - prevYawOffset; f < -180.0F; f += 360.0F) {
            ;
        }

        while (f >= 180.0F) {
            f -= 360.0F;
        }

        return prevYawOffset + partialTicks * f;
    }


    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(HoloGuiEntity entity) {
        return null;
    }


    public static class Factory implements IRenderFactory<HoloGuiEntity> {

        @Override
        public Render<? super HoloGuiEntity> createRenderFor(RenderManager manager) {
            return new HoloGuiEntityRender(manager);
        }

    }
}
