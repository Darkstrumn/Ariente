package mcjty.ariente.blocks.utility.autofield;

import mcjty.ariente.blocks.ModBlocks;
import mcjty.ariente.gui.HelpBuilder;
import mcjty.ariente.gui.HoloGuiTools;
import mcjty.ariente.power.IPowerReceiver;
import mcjty.hologui.api.IGuiComponent;
import mcjty.hologui.api.IGuiComponentRegistry;
import mcjty.hologui.api.IGuiTile;
import mcjty.lib.multipart.MultipartHelper;
import mcjty.lib.multipart.MultipartTE;
import mcjty.lib.multipart.PartSlot;
import mcjty.lib.tileentity.GenericTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import static mcjty.hologui.api.Icons.*;

public class AutoFieldTile extends GenericTileEntity implements IGuiTile, ITickable, IPowerReceiver {

    private int height = 1;
    private AxisAlignedBB fieldBox = null;
    private AxisAlignedBB renderBox = null;
    private Set<BlockPos> markers = null;

    @Override
    public void update() {

    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
        invalidateBox();
        markDirtyClient();
    }

    private void invalidateBox() {
        fieldBox = null;
        renderBox = null;
        markers = null;
    }

    private void changeHeight(int dy) {
        height += dy;
        if (height < 1) {
            height = 1;
        } else if (height > 256) {
            height = 256;
        }
        invalidateBox();
        markDirtyClient();
    }

    public AxisAlignedBB getFieldBox() {
        if (fieldBox == null) {
            calculateMarkers();
        }
        return fieldBox;
    }

    public AxisAlignedBB getRenderBox() {
        if (renderBox == null) {
            calculateMarkers();
        }
        return renderBox;
    }

    private Set<BlockPos> getMarkers() {
        if (markers == null) {
            calculateMarkers();
        }
        return markers;
    }

    private void calculateMarkers() {
        fieldBox = null;
        markers = new HashSet<>();
        Queue<BlockPos> todo = new ArrayDeque<>();
        todo.add(getPos());
        while (!todo.isEmpty()) {
            BlockPos pos = todo.poll();
            for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                BlockPos p = pos.offset(facing);
                if (!markers.contains(p)) {
                    TileEntity te = world.getTileEntity(p);
                    if (te instanceof MultipartTE) {
                        IBlockState state = MultipartHelper.getBlockState(world, p, PartSlot.DOWN);
                        if (state != null && state.getBlock() == ModBlocks.fieldMarker) {
                            if (fieldBox == null) {
                                fieldBox = new AxisAlignedBB(p, p.add(0, height, 0));
                            } else {
                                fieldBox = fieldBox.union(new AxisAlignedBB(p));
                            }
                            markers.add(p);
                            todo.add(p);
                        }
                    }
                }
            }
        }

        renderBox = new AxisAlignedBB(getPos());
        if (fieldBox != null) {
            renderBox = renderBox.union(fieldBox);
        }
    }

    @Override
    public void readRestorableFromNBT(NBTTagCompound tagCompound) {
        super.readRestorableFromNBT(tagCompound);
        height = tagCompound.getInteger("height");
    }

    @Override
    public void writeRestorableToNBT(NBTTagCompound tagCompound) {
        super.writeRestorableToNBT(tagCompound);
        tagCompound.setInteger("height", height);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return getRenderBox();
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 1;
    }


    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        int oldheight = height;

        super.onDataPacket(net, packet);

        if (getWorld().isRemote) {
            // If needed send a render update.
            if (oldheight != height) {
                getWorld().markBlockRangeForRenderUpdate(getPos(), getPos());
                invalidateBox();
            }
        }
    }


    @Override
    public IGuiComponent<?> createGui(String tag, IGuiComponentRegistry registry) {
        if (TAG_HELP.equals(tag)) {
            return HoloGuiTools.createHelpGui(registry,
                    HelpBuilder.create()
                            .line("This block sets up an automation")
                            .line("field. Setup adjacent field markers")
                            .line("in a rectangle to mark where the field")
                            .line("is active. Use nodes in the field to")
                            .line("transfer items, energy, ...")
            );
        } else {
            return createMainGui(registry);
        }
    }

    private IGuiComponent<?> createMainGui(IGuiComponentRegistry registry) {
        return HoloGuiTools.createPanelWithHelp(registry)
                .add(registry.text(0, 2, 1, 1).text("Height").color(0xaaccff))
                .add(registry.number(3, 4, 1, 1).color(0xffffff).getter((p,h) -> getHeight()))

                .add(registry.iconButton(1, 4, 1, 1).icon(registry.image(GRAY_DOUBLE_ARROW_LEFT)).hover(registry.image(WHITE_DOUBLE_ARROW_LEFT))
                        .hitEvent((component, player, entity1, x, y) -> changeHeight(-8)))
                .add(registry.iconButton(2, 4, 1, 1).icon(registry.image(GRAY_ARROW_LEFT)).hover(registry.image(WHITE_ARROW_LEFT))
                        .hitEvent((component, player, entity1, x, y) -> changeHeight(-1)))
                .add(registry.iconButton(5, 4, 1, 1).icon(registry.image(GRAY_ARROW_RIGHT)).hover(registry.image(WHITE_ARROW_RIGHT))
                        .hitEvent((component, player, entity1, x, y) -> changeHeight(1)))
                .add(registry.iconButton(6, 4, 1, 1).icon(registry.image(GRAY_DOUBLE_ARROW_RIGHT)).hover(registry.image(WHITE_DOUBLE_ARROW_RIGHT))
                        .hitEvent((component, player, entity1, x, y) -> changeHeight(8)))
                ;
    }

    @Override
    public void syncToClient() {

    }
}