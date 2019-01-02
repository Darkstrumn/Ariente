package mcjty.ariente.facade;

import mcjty.ariente.Ariente;
import mcjty.ariente.cables.NetCableBlock;
import mcjty.lib.McJtyLib;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class FacadeBlock extends NetCableBlock {

    public static final String FACADE = "facade";

    public static final ModelResourceLocation MODEL_RESOURCE_LOCATION = new ModelResourceLocation(new ResourceLocation(Ariente.MODID, "facade"), "inventory");

    public FacadeBlock() {
        super(Material.IRON, FACADE);
        initTileEntity();
        setHardness(0.8f);
    }

    @Override
    protected ItemBlock createItemBlock() {
        return new FacadeItemBlock(this);
    }

    @Override
    public void getSubBlocks(CreativeTabs tab, NonNullList<ItemStack> items) {
        items.add(new ItemStack(this));
    }

    @Override
    protected void initTileEntity() {
    }

    @Override
    public void initModel() {
        // To make sure that our ISBM model is chosen for all states we use this custom state mapper:
        McJtyLib.proxy.initStateMapper(this, MODEL_RESOURCE_LOCATION);
    }


    @Override
    public void initItemModel() {
        // For our item model we want to use a normal json model. This has to be called in
        // ClientProxy.init (not preInit) so that's why it is a separate method.
        Item itemBlock = ForgeRegistries.ITEMS.getValue(new ResourceLocation(Ariente.MODID, FACADE));
        ModelResourceLocation itemModelResourceLocation = new ModelResourceLocation(getRegistryName(), "inventory");
        final int DEFAULT_ITEM_SUBTYPE = 0;
        McJtyLib.proxy.initItemModelMesher(itemBlock, DEFAULT_ITEM_SUBTYPE, itemModelResourceLocation);
    }
}
