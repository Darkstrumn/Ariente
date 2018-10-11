package mcjty.ariente.blocks.utility;

import mcjty.hologui.api.IGuiComponent;
import mcjty.hologui.api.IGuiComponentRegistry;
import mcjty.hologui.api.IGuiTile;
import mcjty.lib.tileentity.GenericTileEntity;

public class LevelMarkerTile extends GenericTileEntity implements IGuiTile {

    @Override
    public IGuiComponent<?> createGui(String tag, IGuiComponentRegistry registry) {
        // @todo implement this
        return registry.panel(0, 0, 8, 8)
                .add(registry.text(0, 2, 1, 1).text("Floor name (WIP)").color(0xaaccff));
    }

    @Override
    public void syncToClient() {

    }
}
