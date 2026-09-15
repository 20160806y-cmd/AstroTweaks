package astrotweaks.procedure;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.init.Blocks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import astrotweaks.AstrotweaksMod;
import astrotweaks.world.DepthsDim;
import astrotweaks.Multiverse.LevelData;
import astrotweaks.Multiverse.LevelDimensionType;
import astrotweaks.Multiverse.LevelManager;
import astrotweaks.Multiverse.MessageMultiverse;
import astrotweaks.Multiverse.MultiverseDims;



public final class MineDimEnter {
    //private static final int OVERWORLD_ID = 0;
    private static final int CAVERN_DIM_ID = DepthsDim.DIMID;
    private static final int MAX_HEIGHT_OVERWORLD = 5;
    private static final int MIN_HEIGHT_CAVERN = 251;
    private static final int TELEPORT_HEIGHT_OVERWORLD = 5;
    private static final int TELEPORT_HEIGHT_CAVERN = 252;

    public MineDimEnter() {}

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {

        World world = event.getWorld();
        if (world.isRemote) return;
        int dim = world.provider.getDimension();

        // одна выборка на весь метод
        LevelData mvData = LevelManager.getInstance().getLevelByDimensionId(dim);
        LevelDimensionType mvType = (mvData != null) ? mvData.typeOf(dim) : null;

        // для «чужих» измерений — тот же фильтр, что был раньше
        if (dim != -6000 && dim != 0) {
            if (mvData == null) return;
            if (mvType != LevelDimensionType.OVERWORLD && mvType != LevelDimensionType.DEPTHS) return;
        }

        BlockPos pos = event.getPos();
        if (world.getBlockState(pos).getBlock() != Blocks.BEDROCK) return;

        EntityPlayer player = event.getEntityPlayer();
        if (!isHoldingPickaxe(player)) return;

        int targetDim;
        int targetY;

        //teleport conditions
	    if (dim == 0 && player.posY < MAX_HEIGHT_OVERWORLD) {
	        targetDim = CAVERN_DIM_ID;
	        targetY = TELEPORT_HEIGHT_CAVERN;
	    } else if (dim == CAVERN_DIM_ID && player.posY > MIN_HEIGHT_CAVERN) {
	        targetDim = 0;
	        targetY = TELEPORT_HEIGHT_OVERWORLD;
	    } else if (mvType == LevelDimensionType.OVERWORLD && player.posY < MAX_HEIGHT_OVERWORLD) {
	        targetDim = mvData.dimensionId(LevelDimensionType.DEPTHS);
	        targetY = TELEPORT_HEIGHT_CAVERN;
	    } else if (mvType == LevelDimensionType.DEPTHS && player.posY > MIN_HEIGHT_CAVERN) {
	        targetDim = mvData.dimensionId(LevelDimensionType.OVERWORLD);
	        targetY = TELEPORT_HEIGHT_OVERWORLD;
	    } else {
	        return;
	    }


        MinecraftServer server = world.getMinecraftServer();
        if (server == null) return;

        // MV levels: ensure the target world exists and the client knows the level's
        // dimension ids BEFORE the transfer packet is scheduled.
        if (mvData != null) {
            MultiverseDims.registerLevelDimensions(mvData.baseId);
            LevelDimensionType mvTargetType = mvData.typeOf(targetDim);
            LevelManager.getInstance().getOrCreateWorld(server, mvData, mvTargetType);
            if (player instanceof EntityPlayerMP) {
                AstrotweaksMod.PACKET_HANDLER.sendTo(new MessageMultiverse(mvData.baseId, mvData.seed), (EntityPlayerMP) player);
            }
        }

        P_SwitchDim.exect( player, true, Integer.toString(targetDim), player.getName(), Integer.toString(pos.getX()), Integer.toString(targetY), Integer.toString(pos.getZ()) );


        // clear target area
        World targetWorld = server.getWorld(targetDim);
        if (targetWorld != null) {
        	BlockPos targetPos = new BlockPos(pos.getX(), targetY, pos.getZ());
            targetWorld.destroyBlock(targetPos, 	true);
            targetWorld.destroyBlock(targetPos.up(),true);
	        //targetWorld.setBlockState(targetPos, Blocks.AIR.getDefaultState(), 2);
	        //targetWorld.setBlockState(targetPos.up(), Blocks.AIR.getDefaultState(), 2);
        }
        // debug
        //System.out.println("Switch dimension: Depths <-> Overworld");
        
    }
    private static boolean isHoldingPickaxe(EntityPlayer player) {
	    if (player == null)  return false;
        ItemStack held = player.getHeldItemMainhand(); // main hand
        if (held.isEmpty())  return false;
        Item item = held.getItem();
        if (item instanceof net.minecraft.item.ItemPickaxe) return true; // fast path
        return item.getToolClasses(held).contains("pickaxe");         // modded fallback
	}
}
