package mcjty.ariente.blocks.generators;

import mcjty.ariente.Ariente;
import mcjty.ariente.ai.IAlarmMode;
import mcjty.ariente.blocks.ModBlocks;
import mcjty.ariente.cables.CableColor;
import mcjty.ariente.gui.HoloGuiTools;
import mcjty.ariente.items.ModItems;
import mcjty.ariente.power.*;
import mcjty.hologui.api.IGuiComponent;
import mcjty.hologui.api.IGuiComponentRegistry;
import mcjty.hologui.api.IGuiTile;
import mcjty.hologui.api.IHoloGuiEntity;
import mcjty.lib.container.ContainerFactory;
import mcjty.lib.container.DefaultSidedInventory;
import mcjty.lib.container.InventoryHelper;
import mcjty.lib.gui.widgets.ImageChoiceLabel;
import mcjty.lib.tileentity.GenericTileEntity;
import mcjty.lib.typed.TypedMap;
import mcjty.lib.varia.RedstoneMode;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.api.TextStyleClass;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

import static mcjty.hologui.api.Icons.*;

public class NegariteGeneratorTile extends GenericTileEntity implements ITickable, DefaultSidedInventory, IGuiTile, IPowerBlob, IAlarmMode, IPowerSender {

    public static final String CMD_RSMODE = "negarite_gen.setRsMode";
    public static final PropertyBool WORKING = PropertyBool.create("working");

    public static final int POWERGEN = 1000;        // @todo configurable and based on tanks!

    public static final ContainerFactory CONTAINER_FACTORY = new ContainerFactory(new ResourceLocation(Ariente.MODID, "gui/negarite_generator.gui"));
    public static final int SLOT_NEGARITE_INPUT = 0;
    private InventoryHelper inventoryHelper = new InventoryHelper(this, CONTAINER_FACTORY, 1);

    private PowerSenderSupport powerBlobSupport = new PowerSenderSupport();

    // @todo, temporary: base on tanks later!
    private int dustCounter;        // Number of ticks before the current dust depletes

    @Override
    public boolean isHighAlert() {
        return false;
    }

    @Override
    protected boolean needsRedstoneMode() {
        return true;
    }

    @Override
    public void update() {
        if (!world.isRemote) {
            if (!isMachineEnabled()) {
                return;
            }
            if (dustCounter > 0) {
                dustCounter--;
                if (dustCounter == 0 && !canProceed()) {
                    markDirtyClient();
                } else {
                    markDirtyQuick();
                }
                sendPower();
            } else {
                if (canProceed()) {
                    inventoryHelper.decrStackSize(SLOT_NEGARITE_INPUT, 1);
                    dustCounter = 600;
                    markDirtyQuick();
                    sendPower();
                }
            }
        }
    }

    private boolean canProceed() {
        ItemStack stack = inventoryHelper.getStackInSlot(SLOT_NEGARITE_INPUT);
        return !stack.isEmpty() && stack.getItem() == ModItems.negariteDust;
    }

    private void sendPower() {
        int cnt = 0;
        BlockPos p = pos.up();
        while (world.getTileEntity(p) instanceof NegariteTankTile) {
            cnt++;
            p = p.up();
        }
        if (cnt > 0) {
            PowerSystem powerSystem = PowerSystem.getPowerSystem(world);
            powerSystem.addPower(powerBlobSupport.getCableId(), POWERGEN * cnt, PowerType.NEGARITE);
        }
    }

    @Override
    public boolean canSendPower() {
        return true;
    }

    @Override
    public CableColor getSupportedColor() {
        return CableColor.NEGARITE;
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        boolean working = isWorking();

        super.onDataPacket(net, packet);

        if (world.isRemote) {
            // If needed send a render update.
            boolean newWorking = isWorking();
            if (newWorking != working) {
                world.markBlockRangeForRenderUpdate(getPos(), getPos());
                BlockPos p = pos.up();
                IBlockState state = world.getBlockState(p);
                while (state.getBlock() == ModBlocks.negariteTankBlock) {
                    world.markBlockRangeForRenderUpdate(p, p);
                    p = p.up();
                    state = world.getBlockState(p);
                }
            }
        }
    }

    @Override
    public void setPowerInput(int powered) {
        boolean changed = powerLevel != powered;
        super.setPowerInput(powered);
        if (changed) {
            markDirtyClient();
        }
    }


    @Override
    public IBlockState getActualState(IBlockState state) {
        return state.withProperty(WORKING, isWorking());
    }

    public boolean isWorking() {
        return dustCounter > 0 && isMachineEnabled();
    }

    @Override
    public InventoryHelper getInventoryHelper() {
        return inventoryHelper;
    }

    @Override
    public int[] getSlotsForFace(EnumFacing side) {
        return new int[] { SLOT_NEGARITE_INPUT };
    }

    @Override
    public boolean canInsertItem(int index, ItemStack stack, EnumFacing direction) {
        return isItemValidForSlot(index, stack);
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        if (index == SLOT_NEGARITE_INPUT) {
            return stack.getItem() == ModItems.negariteDust;
        }
        return true;
    }

    @Override
    public boolean canExtractItem(int index, ItemStack stack, EnumFacing direction) {
        return false;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return canPlayerAccess(player);
    }

    @Override
    public void readFromNBT(NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);
        powerBlobSupport.setCableId(tagCompound.getInteger("cableId"));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
        tagCompound.setInteger("cableId", powerBlobSupport.getCableId());
        return super.writeToNBT(tagCompound);
    }

    @Override
    public void readRestorableFromNBT(NBTTagCompound tagCompound) {
        super.readRestorableFromNBT(tagCompound);

        readBufferFromNBT(tagCompound, inventoryHelper);
        dustCounter = tagCompound.getInteger("dust");
    }

    @Override
    public void writeRestorableToNBT(NBTTagCompound tagCompound) {
        super.writeRestorableToNBT(tagCompound);
        writeBufferToNBT(tagCompound, inventoryHelper);
        tagCompound.setInteger("dust", dustCounter);
    }

    @Override
    public boolean execute(EntityPlayerMP playerMP, String command, TypedMap params) {
        boolean rc = super.execute(playerMP, command, params);
        if (rc) {
            return true;
        }
        if (CMD_RSMODE.equals(command)) {
            setRSMode(RedstoneMode.values()[params.get(ImageChoiceLabel.PARAM_CHOICE_IDX)]);
            return true;
        }

        return false;
    }

    @Override
    @Optional.Method(modid = "theoneprobe")
    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world, IBlockState blockState, IProbeHitData data) {
        super.addProbeInfo(mode, probeInfo, player, world, blockState, data);
        probeInfo.text(TextStyleClass.LABEL + "Network: " + TextStyleClass.INFO + powerBlobSupport.getCableId());
        if (isWorking()) {
            probeInfo.text(TextStyleClass.LABEL + "Generating: " + TextStyleClass.INFO + POWERGEN + " flux");
        }
    }


    @SideOnly(Side.CLIENT)
    @Override
    @Optional.Method(modid = "waila")
    public void addWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        super.addWailaBody(itemStack, currenttip, accessor, config);
//        if (isWorking()) {
//            currenttip.add(TextFormatting.GREEN + "Producing " + getRfPerTick() + " RF/t");
//        }
    }

    @Override
    public int getCableId() {
        return powerBlobSupport.getCableId();
    }

    @Override
    public void fillCableId(int id) {
        powerBlobSupport.fillCableId(world, pos, id, getCableColor());
    }

    @Override
    public CableColor getCableColor() {
        return CableColor.NEGARITE;
    }

    @Override
    public IGuiComponent<?> createGui(String tag, IGuiComponentRegistry registry) {
        return registry.panel(0, 0, 8, 8)
                .add(registry.text(0, 0, 8, 1).text("Negarite").color(0xaaccff))
                .add(registry.stackIcon(0, 3, 1, 1).itemStack(new ItemStack(ModItems.negariteDust)))

                .add(registry.icon(1, 3, 1, 1).icon(registry.image(WHITE_PLAYER)))
                .add(registry.number(2, 3, 1, 1).color(0xffffff).getter((p,h) -> HoloGuiTools.countItem(p, ModItems.negariteDust)))

                .add(registry.iconButton(2, 4, 1, 1).icon(registry.image(GRAY_DOUBLE_ARROW_LEFT)).hover(registry.image(WHITE_DOUBLE_ARROW_LEFT))
                    .hitEvent((component, player, e, x, y) -> toPlayer(player, 64)))
                .add(registry.iconButton(3, 4, 1, 1).icon(registry.image(GRAY_ARROW_LEFT)).hover(registry.image(WHITE_ARROW_LEFT))
                    .hitEvent((component, player, e, x, y) -> toPlayer(player, 1)))
                .add(registry.iconButton(5, 4, 1, 1).icon(registry.image(GRAY_ARROW_RIGHT)).hover(registry.image(WHITE_ARROW_RIGHT))
                    .hitEvent((component, player, e, x, y) -> toMachine(player, 1)))
                .add(registry.iconButton(6, 4, 1, 1).icon(registry.image(GRAY_DOUBLE_ARROW_RIGHT)).hover(registry.image(WHITE_DOUBLE_ARROW_RIGHT))
                    .hitEvent((component, player, e, x, y) -> toMachine(player, 64)))

                .add(registry.stackIcon(5, 3, 1, 1).itemStack(new ItemStack(ModBlocks.negariteGeneratorBlock)))
                .add(registry.number(6, 3, 1, 1).color(0xffffff).getter(this::countNegariteGenerator))

                .add(registry.iconChoice(7, 6, 1, 1)
                        .getter((player) -> getRSModeInt())
                        .addImage(registry.image(REDSTONE_DUST))
                        .addImage(registry.image(REDSTONE_OFF))
                        .addImage(registry.image(REDSTONE_ON))
                        .hitEvent((component, player, entity1, x, y) -> changeMode()))
                ;
    }

    private void changeMode() {
        int current = rsMode.ordinal() + 1;
        if (current >= RedstoneMode.values().length) {
            current = 0;
        }
        setRSMode(RedstoneMode.values()[current]);
        markDirtyClient();
    }

    private void toPlayer(EntityPlayer player, int amount) {
        ItemStack stack = inventoryHelper.decrStackSize(SLOT_NEGARITE_INPUT, amount);
        if ((!stack.isEmpty()) && player.inventory.addItemStackToInventory(stack)) {
            markDirtyClient();
        } else {
            ItemStack stillThere = inventoryHelper.getStackInSlot(SLOT_NEGARITE_INPUT);
            if (stillThere.isEmpty()) {
                stillThere = stack;
            } else {
                stillThere.grow(stack.getCount());
            }
            inventoryHelper.setStackInSlot(SLOT_NEGARITE_INPUT, stillThere);
        }
    }

    private void toMachine(EntityPlayer player, int amount) {
        ItemStack toTransfer = ItemStack.EMPTY;
        ItemStack stackInSlot = inventoryHelper.getStackInSlot(SLOT_NEGARITE_INPUT);
        if (!stackInSlot.isEmpty()) {
            amount = Math.min(amount, 64 - stackInSlot.getCount());    // @todo item specific max stacksize
        }

        for (int i = 0 ; i < player.inventory.getSizeInventory() ; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.getItem() == ModItems.negariteDust) {
                ItemStack splitted = stack.splitStack(amount);
                if ((!splitted.isEmpty())) {
                    if (toTransfer.isEmpty()) {
                        toTransfer = splitted;
                    } else {
                        toTransfer.grow(amount);
                    }
                    amount -= splitted.getCount();
                    if (amount <= 0) {
                        break;
                    }
                }
            }
        }

        if (!toTransfer.isEmpty()) {
            if (!stackInSlot.isEmpty()) {
                toTransfer.grow(stackInSlot.getCount());
            }
            inventoryHelper.setStackInSlot(SLOT_NEGARITE_INPUT, toTransfer);
            markDirtyClient();
        }

    }

    @Override
    public void syncToClient() {
        // @todo more efficient
        markDirtyClient();
    }

    public Integer countNegariteGenerator(EntityPlayer player, IHoloGuiEntity holo) {
        int size = inventoryHelper.getCount();
        int cnt = 0;
        for (int i = 0 ; i < size ; i++) {
            ItemStack stack = inventoryHelper.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == ModItems.negariteDust) {
                cnt += stack.getCount();
            }
        }
        return cnt;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (!world.isRemote) {
            PowerSenderSupport.fixNetworks(world, pos);
        }
    }

    @Override
    public void onBlockBreak(World world, BlockPos pos, IBlockState state) {
        super.onBlockBreak(world, pos, state);
        if (!this.world.isRemote) {
            PowerSenderSupport.fixNetworks(this.world, pos);
        }
    }
}
