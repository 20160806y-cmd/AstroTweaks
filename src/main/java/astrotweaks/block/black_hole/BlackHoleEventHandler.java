package astrotweaks.block.black_hole;

import astrotweaks.AstrotweaksMod;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = AstrotweaksMod.MODID)
public class BlackHoleEventHandler {

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.PlaceEvent event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) return;
        BlockPos pos = event.getPos();
        for (TileEntity te : world.loadedTileEntityList) {
            if (te instanceof BlackHoleTileEntity) {
                ((BlackHoleTileEntity) te).getRegionManager().onBlockPlaced(pos);
            }
        }
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) return;

        // Fire for liquids and vegetation (tallgrass, flowers, bushes, vine etc.).
        // Covers: bucket place (setBlockState → notify), water spreading, and vegetation growth.
        IBlockState state = world.getBlockState(event.getPos());
        if (!state.getMaterial().isLiquid() && !BlackHoleRegionManager.isVegetation(state)) return;

        BlockPos pos = event.getPos();
        for (TileEntity te : world.loadedTileEntityList) {
            if (te instanceof BlackHoleTileEntity) {
                ((BlackHoleTileEntity) te).getRegionManager().onBlockPlaced(pos);
            }
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) return;
        Chunk chunk = event.getChunk();
        for (TileEntity te : world.loadedTileEntityList) {
            if (te instanceof BlackHoleTileEntity) {
                ((BlackHoleTileEntity) te).getRegionManager().onChunkLoaded(chunk);
            }
        }
    }
}
