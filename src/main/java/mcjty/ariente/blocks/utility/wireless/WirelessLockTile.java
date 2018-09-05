package mcjty.ariente.blocks.utility.wireless;

import mcjty.ariente.blocks.utility.ILockable;
import mcjty.ariente.blocks.utility.door.DoorMarkerTile;
import mcjty.ariente.gui.HoloGuiHandler;
import mcjty.ariente.gui.IGuiComponent;
import mcjty.ariente.gui.IGuiTile;
import mcjty.ariente.gui.components.HoloButton;
import mcjty.ariente.gui.components.HoloNumber;
import mcjty.ariente.gui.components.HoloPanel;
import mcjty.ariente.gui.components.HoloText;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.api.TextStyleClass;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Optional;

import java.util.Map;

public class WirelessLockTile extends SignalChannelTileEntity implements ILockable, IGuiTile, ITickable {

    public static final PropertyBool LOCKED = PropertyBool.create("locked");

    private boolean locked = false;
    private int horizontalRange = 5;
    private int verticalRange = 3;

    public WirelessLockTile() {
    }

    @Override
    public void update() {
        if (!world.isRemote) {
            if (channel != -1) {
                RedstoneChannels channels = RedstoneChannels.getChannels(getWorld());
                RedstoneChannels.RedstoneChannel ch = channels.getChannel(channel);
                if (ch != null) {
                    setLocked(ch.getValue() <= 0);
                } else {
                    setLocked(true);
                }
            }
        }
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        boolean locked = isLocked();

        super.onDataPacket(net, packet);

        if (world.isRemote) {
            // If needed send a render update.
            boolean newLocked = isLocked();
            if (newLocked != locked) {
                world.markBlockRangeForRenderUpdate(pos, pos);
            }
        }
    }


    @Override
    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        if (locked == this.locked) {
            return;
        }
        this.locked = locked;
        doLock(locked);
        markDirtyClient();
    }


    private void doLock(boolean l) {
        if (!world.isRemote) {
            for (int dx = -horizontalRange ; dx <= horizontalRange ; dx++) {
                for (int dy = -verticalRange ; dy <= verticalRange ; dy++) {
                    for (int dz = -horizontalRange ; dz <= horizontalRange ; dz++) {
                        BlockPos p = pos.add(dx, dy, dz);
                        TileEntity te = world.getTileEntity(p);
                        if (te instanceof DoorMarkerTile) { // @todo generalize!
                            ((DoorMarkerTile) te).setLocked(l);
                        }
                    }
                }
            }
        }
    }

    public int getHorizontalRange() {
        return horizontalRange;
    }

    public void setHorizontalRange(int horizontalRange) {
        doLock(false);
        this.horizontalRange = horizontalRange;
        markDirtyQuick();
        doLock(true);
    }

    public int getVerticalRange() {
        return verticalRange;
    }

    public void setVerticalRange(int verticalRange) {
        doLock(false);
        this.verticalRange = verticalRange;
        markDirtyQuick();
        doLock(true);
    }

    @Override
    public IBlockState getActualState(IBlockState state) {
        return state.withProperty(LOCKED, locked);
    }

    @Override
    public void onBlockBreak(World workd, BlockPos pos, IBlockState state) {
        doLock(false);
        super.onBlockBreak(workd, pos, state);
    }

    @Override
    public Map<String, Object> save() {
        Map<String, Object> data = super.save();
        data.put("vertical", verticalRange);
        data.put("horizontal", horizontalRange);
        return data;
    }

    @Override
    public void load(Map<String, Object> data) {
        super.load(data);
        if (data.get("horizontal") instanceof Integer) {
            setHorizontalRange((Integer) data.get("horizontal"));
        }
        if (data.get("vertical") instanceof Integer) {
            setVerticalRange((Integer) data.get("vertical"));
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);
        locked = tagCompound.getBoolean("locked");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
        tagCompound.setBoolean("locked", locked);
        return super.writeToNBT(tagCompound);
    }

    @Override
    public void readRestorableFromNBT(NBTTagCompound tagCompound) {
        super.readRestorableFromNBT(tagCompound);
        if (tagCompound.hasKey("vertical")) {
            verticalRange = tagCompound.getInteger("vertical");
        }
        if (tagCompound.hasKey("horizontal")) {
            horizontalRange = tagCompound.getInteger("horizontal");
        }
    }

    @Override
    public void writeRestorableToNBT(NBTTagCompound tagCompound) {
        super.writeRestorableToNBT(tagCompound);
        tagCompound.setInteger("vertical", verticalRange);
        tagCompound.setInteger("horizontal", horizontalRange);
    }

    private void changeHorizontalRange(int dy) {
        int h = horizontalRange + dy;
        if (h < 1) {
            h = 1;
        } else if (h > 20) {
            h = 20;
        }
        setHorizontalRange(h);
    }


    private void changeVerticalRange(int dy) {
        int h = verticalRange + dy;
        if (h < 1) {
            h = 1;
        } else if (h > 10) {
            h = 10;
        }
        setVerticalRange(h);
    }

    @Override
    @Optional.Method(modid = "theoneprobe")
    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world, IBlockState blockState, IProbeHitData data) {
        super.addProbeInfo(mode, probeInfo, player, world, blockState, data);
        if (isLocked()) {
            probeInfo.text(TextStyleClass.WARNING + "Locked!");
        }
    }

    @Override
    public IGuiComponent createGui(String tag) {
        if (isLocked()) {
            return HoloGuiHandler.createNoAccessPanel();
        }
        return new HoloPanel(0, 0, 8, 8)
                .add(new HoloText(0, 1, 1, 1, "Horizontal", 0xaaccff))
                .add(new HoloNumber(3, 2, 1, 1, 0xffffff, (p,h) -> getHorizontalRange()))

                .add(new HoloButton(1, 2, 1, 1).image(128 + 32, 128 + 16).hover(128 + 32 + 16, 128 + 16)
                        .hitEvent((component, player, entity1, x, y) -> changeHorizontalRange(-8)))
                .add(new HoloButton(2, 2, 1, 1).image(128 + 32, 128).hover(128 + 32 + 16, 128)
                        .hitEvent((component, player, entity1, x, y) -> changeHorizontalRange(-1)))
                .add(new HoloButton(5, 2, 1, 1).image(128, 128).hover(128 + 16, 128)
                        .hitEvent((component, player, entity1, x, y) -> changeHorizontalRange(1)))
                .add(new HoloButton(6, 2, 1, 1).image(128, 128 + 16).hover(128 + 16, 128 + 16)
                        .hitEvent((component, player, entity1, x, y) -> changeHorizontalRange(8)))


                .add(new HoloText(0, 4, 1, 1, "Vertical", 0xaaccff))
                .add(new HoloNumber(3, 5, 1, 1, 0xffffff, (p,h) -> getVerticalRange()))

                .add(new HoloButton(1, 5, 1, 1).image(128 + 32, 128 + 16).hover(128 + 32 + 16, 128 + 16)
                        .hitEvent((component, player, entity1, x, y) -> changeVerticalRange(-8)))
                .add(new HoloButton(2, 5, 1, 1).image(128 + 32, 128).hover(128 + 32 + 16, 128)
                        .hitEvent((component, player, entity1, x, y) -> changeVerticalRange(-1)))
                .add(new HoloButton(5, 5, 1, 1).image(128, 128).hover(128 + 16, 128)
                        .hitEvent((component, player, entity1, x, y) -> changeVerticalRange(1)))
                .add(new HoloButton(6, 5, 1, 1).image(128, 128 + 16).hover(128 + 16, 128 + 16)
                        .hitEvent((component, player, entity1, x, y) -> changeVerticalRange(8)))
                ;
    }

    @Override
    public void syncToClient() {

    }

}
