package mcjty.ariente.blocks.utility.autofield;

import mcjty.ariente.Ariente;
import mcjty.lib.client.RenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.Random;

@SideOnly(Side.CLIENT)
public class AutoFieldRenderer extends TileEntitySpecialRenderer<AutoFieldTile> {

    private static ResourceLocation beams[] = new ResourceLocation[3];
    static {
        beams[0] = new ResourceLocation(Ariente.MODID, "textures/effects/autobeam1.png");
        beams[1] = new ResourceLocation(Ariente.MODID, "textures/effects/autobeam2.png");
        beams[2] = new ResourceLocation(Ariente.MODID, "textures/effects/autobeam3.png");
    }

    private Random random = new Random();

    public AutoFieldRenderer() {
    }

    @Override
    public void render(AutoFieldTile te, double x, double y, double z, float time, int breakTime, float alpha) {
        AxisAlignedBB box = te.getFieldBox();
        if (box == null) {
            return;
        }

//        if (te.isWorking()) {
        Tessellator tessellator = Tessellator.getInstance();
        GlStateManager.pushMatrix();
//            GlStateManager.translate(x, y, z);

        GlStateManager.enableBlend();
        GlStateManager.depthMask(false);
//        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
//        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.003921569F);
        GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
//        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.003921569F);
        GlStateManager.disableCull();
        GlStateManager.enableDepth();

        ResourceLocation beamIcon = beams[random.nextInt(3)];
        bindTexture(beamIcon);

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.player;
        double doubleX = p.lastTickPosX + (p.posX - p.lastTickPosX) * time;
        double doubleY = p.lastTickPosY + (p.posY - p.lastTickPosY) * time;
        double doubleZ = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * time;

        GlStateManager.translate(-doubleX, -doubleY, -doubleZ);

        RenderHelper.Vector player = new RenderHelper.Vector((float) doubleX, (float) doubleY + p.getEyeHeight(), (float) doubleZ);

        long tt = System.currentTimeMillis() / 100;

        GlStateManager.color(1, 1, 1, 1);

        BufferBuilder renderer = tessellator.getBuffer();
        renderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_LMAP_COLOR);

        float mx = (float) box.minX;
        float my = (float) box.minY;
        float mz = (float) box.minZ;
        float px = (float) (box.maxX + 0.0f);
        float py = (float) (box.maxY + 0.0f);
        float pz = (float) (box.maxZ + 0.0f);
        RenderHelper.drawBeam(new RenderHelper.Vector(mx, my, mz), new RenderHelper.Vector(px, my, mz), player, 0.1f);
        RenderHelper.drawBeam(new RenderHelper.Vector(mx, my, mz), new RenderHelper.Vector(mx, py, mz), player, 0.1f);
        RenderHelper.drawBeam(new RenderHelper.Vector(mx, my, mz), new RenderHelper.Vector(mx, my, pz), player, 0.1f);

        RenderHelper.drawBeam(new RenderHelper.Vector(px, py, pz), new RenderHelper.Vector(mx, py, pz), player, 0.1f);
        RenderHelper.drawBeam(new RenderHelper.Vector(px, py, pz), new RenderHelper.Vector(px, my, pz), player, 0.1f);
        RenderHelper.drawBeam(new RenderHelper.Vector(px, py, pz), new RenderHelper.Vector(px, py, mz), player, 0.1f);

        RenderHelper.drawBeam(new RenderHelper.Vector(px, my, mz), new RenderHelper.Vector(px, py, mz), player, 0.1f);
        RenderHelper.drawBeam(new RenderHelper.Vector(px, my, mz), new RenderHelper.Vector(px, my, pz), player, 0.1f);
        RenderHelper.drawBeam(new RenderHelper.Vector(mx, py, mz), new RenderHelper.Vector(px, py, mz), player, 0.1f);
        RenderHelper.drawBeam(new RenderHelper.Vector(mx, py, mz), new RenderHelper.Vector(mx, py, pz), player, 0.1f);
        RenderHelper.drawBeam(new RenderHelper.Vector(mx, my, pz), new RenderHelper.Vector(px, my, pz), player, 0.1f);
        RenderHelper.drawBeam(new RenderHelper.Vector(mx, my, pz), new RenderHelper.Vector(mx, py, pz), player, 0.1f);

//        net.minecraft.util.math.Vec3d cameraPos = net.minecraft.client.renderer.ActiveRenderInfo.getCameraPosition();
//        tessellator.getBuffer().sortVertexData((float) (player.x + doubleX), (float) (player.y + doubleY), (float) (player.z + doubleZ));
//        tessellator.getBuffer().sortVertexData((float)(cameraPos.x+doubleX), (float)(cameraPos.y+doubleY), (float)(cameraPos.z+doubleZ));
        tessellator.draw();

        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);

        GlStateManager.popMatrix();
//        }

    }

    @Override
    public boolean isGlobalRenderer(AutoFieldTile te) {
        return true;
    }

    public static void register() {
        ClientRegistry.bindTileEntitySpecialRenderer(AutoFieldTile.class, new AutoFieldRenderer());
    }
}
