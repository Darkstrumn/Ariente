package mcjty.ariente.gui.components;

import mcjty.ariente.Ariente;
import mcjty.ariente.gui.HoloGuiEntity;
import mcjty.ariente.gui.HoloGuiRenderTools;
import mcjty.ariente.sounds.ModSounds;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;

import java.util.function.Function;

public class HoloItemStackToggle extends AbstractHoloComponent {

    private final ItemStack stack;
    private Function<EntityPlayer, Boolean> currentValue;
    private IEvent hitEvent;

    private static final ResourceLocation DARKEN = new ResourceLocation(Ariente.MODID, "textures/gui/darken.png");
    private static final ResourceLocation INVALID = new ResourceLocation(Ariente.MODID, "textures/gui/darken_red.png");

    public HoloItemStackToggle(double x, double y, double w, double h, ItemStack stack, Function<EntityPlayer, Boolean> getter) {
        super(x, y, w, h);
        this.stack = stack;
        this.currentValue = getter;
    }

    @Override
    public void render(EntityPlayer player, HoloGuiEntity holo, double cursorX, double cursorY) {
        Boolean state = currentValue.apply(player);
        ResourceLocation lightmap = null;
        boolean border = false;
        if (state == null) {
            lightmap = DARKEN;
        } else if (!state) {
        } else {
            border = true;
        }
        HoloGuiRenderTools.renderItem(x * 1.05, y * 0.85 + .45, stack, lightmap, border);
    }

    @Override
    public void renderTooltip(EntityPlayer player, HoloGuiEntity holo, double cursorX, double cursorY) {
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.01, 0.01, 0.01);
        GlStateManager.rotate(180, 0, 1, 0);
        GlStateManager.rotate(180, 0, 0, 1);
        GlStateManager.translate(0, 0, -10);
        GlStateManager.scale(0.4, 0.4, 0.0);
        HoloGuiRenderTools.renderToolTip(stack, (int) (x * 30 - 120), (int) (y * 30 - 120));
        GlStateManager.popMatrix();
    }

    public HoloItemStackToggle hitEvent(IEvent event) {
        this.hitEvent = event;
        return this;
    }

    @Override
    public void hit(EntityPlayer player, HoloGuiEntity entity, double cursorX, double cursorY) {
        if (hitEvent != null) {
            hitEvent.hit(this, player, entity, cursorX, cursorY);
        }
    }

    @Override
    public void hitClient(EntityPlayer player, HoloGuiEntity entity, double cursorX, double cursorY) {
        player.world.playSound(entity.posX, entity.posY, entity.posZ, ModSounds.guiclick, SoundCategory.PLAYERS, 1.0f, 1.0f, true);
    }


}
