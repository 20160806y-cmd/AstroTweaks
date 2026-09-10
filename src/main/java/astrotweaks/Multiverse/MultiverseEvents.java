package astrotweaks.Multiverse;

import astrotweaks.AstrotweaksMod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Glues the multiverse into the game:
 * <ul>
 *   <li>world bind: on overworld load, (re)attach this save's MULTIVERSE folder</li>
 *   <li>re-login: return players to their last multiverse dimension</li>
 *   <li>portals: nether / end portals inside a level stay inside that level</li>
 *   <li>persistence: remember the position of players inside a level</li>
 *   <li>unloading: drop worlds once their last player leaves</li>
 * </ul>
 */
public class MultiverseEvents {

    private static final Set<UUID> SKIP_PORTAL_REMAP = new HashSet<>();

    private int tickCounter;


    private MinecraftServer server;



    /**
     * Runs an entity dimension change without the portal re-mapping (used by
     * /mv join 0, where a nether portal in the level would otherwise hijack
     * the trip to the vanilla overworld). Returns the entity actually placed in the
     * target world (which for players is a fresh EntityPlayerMP).
     */
    public static Entity teleportIgnoringPortalRemap(EntityPlayerMP player, int dimension, ITeleporter teleporter) {
        SKIP_PORTAL_REMAP.add(player.getUniqueID());
        try {
            return player.changeDimension(dimension, teleporter);
        } finally {
            SKIP_PORTAL_REMAP.remove(player.getUniqueID());
        }
    }

    /** Non-player variant of {@link #teleportIgnoringPortalRemap}. */
    public static Entity teleportIgnoringPortalRemap(Entity entity, int dimension, ITeleporter teleporter) {
        SKIP_PORTAL_REMAP.add(entity.getUniqueID());
        try {
            return entity.changeDimension(dimension, teleporter);
        } finally {
            SKIP_PORTAL_REMAP.remove(entity.getUniqueID());
        }
    }

    // ------------------------------------------------------------------ world bind

    @SubscribeEvent
    public void onWorldLoaded(WorldEvent.Load event) {
        if (event.getWorld() == null || event.getWorld().isRemote) {
            return;
        }
        if (event.getWorld().provider == null || event.getWorld().provider.getDimension() != 0) {
            return;
        }
        server = event.getWorld().getMinecraftServer();
        if (server != null) {
            LevelManager.getInstance().onWorldLoaded(server);
        }
    }

    // ------------------------------------------------------------------ re-login

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player == null || event.player.world.isRemote) {
            return;
        }
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }
        final EntityPlayerMP player = (EntityPlayerMP) event.player;
        final MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        
        // PlayerLoggedInEvent fires before the target WorldServer is guaranteed to be
        // attached and before the client knows the DimensionType. Defer the restore to
        // the next server tick so the login sequence completes first.
        server.addScheduledTask(() -> {
            if (player.isDead) {
                return;
            }
            LevelManager lm = LevelManager.getInstance();
            LevelManager.PlayerEntry entry = lm.getPlayerEntry(player.getUniqueID());
            if (entry == null) {
                return;
            }
            if (!lm.restorePlayer(player)) {
                System.err.println("[MULTIVERSE] Failed to restore " + player.getName() + " to dimension " + entry.dimension);
            }
        });
    }

    // ------------------------------------------------------------------ portal remap

    @SubscribeEvent
    public void onTravelToDimension(EntityTravelToDimensionEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || entity.getEntityWorld() == null || entity.getEntityWorld().isRemote) return;
        

        EntityPlayerMP player = entity instanceof EntityPlayerMP ? (EntityPlayerMP) entity : null;
        // Skip both players AND non-players that are being moved by our own code:
        // the custom nether portal and /mv already computed exactly where the entity
        // should land, which is never the vanilla nether/end mapping.
        if (SKIP_PORTAL_REMAP.remove(entity.getUniqueID())) return;
        

        int from = entity.dimension;
        int to = event.getDimension();

        // The global dimension (-1000000) is a sealed overworld-only world: it lives in
        // MULTIVERSE_GLOBAL and must never let a player portal out of it into the
        // vanilla nether/end or into any multiverse level.
        if (from == MultiverseDims.GLOBAL_DIM) {
            event.setCanceled(true);
            return;
        }

        LevelManager lm = LevelManager.getInstance();
        LevelData data = lm.getLevelByDimensionId(from);
        if (data == null) return;

        LevelDimensionType targetType = resolvePortalTarget(data.typeOf(from), to);
        if (targetType == null) return;

        server = entity.getServer();
        if (server == null) return;

        event.setCanceled(true);
        System.out.println("[MULTIVERSE] Portal remap: dim " + from + " -> " + to
                + " remapped to " + targetType + " in level '" + data.name + "'");

        WorldServer targetWorld = lm.getOrCreateWorld(server, data, targetType);
        if (targetWorld == null) return;
        

        int corrected = data.dimensionId(targetType);

        BlockPos targetPos;
        if (targetType == LevelDimensionType.END) {
            targetPos = endSpawn(targetWorld);
        } else if (targetType == LevelDimensionType.OVERWORLD && data.typeOf(from) == LevelDimensionType.END) {
            targetPos = targetWorld.getSpawnPoint();
        } else {
            // Overworld <-> nether: apply the vanilla movement-factor scaling so a
            // player does not end up buried. x/z are divided by 8 going to the nether
            // and multiplied by 8 coming back to the overworld.
            double scale = targetType == LevelDimensionType.NETHER ? 1.0 / 8.0D : 8.0D;
            targetPos = new BlockPos(
                    (int) (entity.posX * scale),
                    (int) entity.posY,
                    (int) (entity.posZ * scale));
        }

        boolean portalPair = targetType == LevelDimensionType.NETHER
                || (targetType == LevelDimensionType.OVERWORLD && data.typeOf(from) == LevelDimensionType.NETHER);
        ITeleporter teleporter = new MultiverseTeleporter(targetPos, portalPair);

        if (player != null) {
            AstrotweaksMod.PACKET_HANDLER.sendTo(new MessageMultiverse(data.baseId), player);
            player.changeDimension(corrected, teleporter);
        } else {
            entity.changeDimension(corrected, teleporter);
        }
    }

    private static BlockPos endSpawn(WorldServer world) {
        BlockPos coordinate = world.getSpawnCoordinate();
        if (coordinate == null || coordinate.equals(BlockPos.ORIGIN)) {
            return world.getSpawnPoint();
        }
        return coordinate;
    }

    /**
     * Where a portal in the level should lead. Everything else is left untouched
     * (vanilla behavior for real-lane travel, and no-op for already-corrected targets).
     */
    private static LevelDimensionType resolvePortalTarget(LevelDimensionType from, int to) {
        if (from == null) {
            return null;
        }
        switch (from) {
            case OVERWORLD:
                if (to == -1) {
                    return LevelDimensionType.NETHER;
                }
                if (to == 1) {
                    return LevelDimensionType.END;
                }
                return null;
            case NETHER:
                if (to == -1 || to == 0) {
                    return LevelDimensionType.OVERWORLD;
                }
                if (to == 1) {
                    return LevelDimensionType.END;
                }
                return null;
            case END:
                if (to == -1) {
                    return LevelDimensionType.NETHER;
                }
                if (to == 0 || to == 1) {
                    return LevelDimensionType.OVERWORLD;
                }
                return null;
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------ portal creation safety net

    /**
     * Portal creation safety net:
     * <ul>
     *   <li>the global dimension (-1000000) is a sealed world: no nether/end portal may
     *       ever be created inside it (the vanilla fire check would allow it now that
     *       MultiverseGlobal reports the overworld DimensionType);</li>
     *   <li>inside a multiverse level overworld/nether the vanilla frame lighting is
     *       cancelled and re-built with our custom {@link BlockNetherPortal} so every
     *       portal block gets a two-way link (portals in MV end/depths stay forbidden:
     *       fire cannot even light there because their DimensionType id &gt; 0).</li>
     * </ul>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        if (event.getWorld() == null || event.getWorld().isRemote) return;
        if (event.getWorld().provider == null) return;
        int dim = event.getWorld().provider.getDimension();

        if (dim == MultiverseDims.GLOBAL_DIM) {
            event.setCanceled(true);
            return;
        }

        LevelData data = LevelManager.getInstance().getLevelByDimensionId(dim);
        if (data == null) {
            // Real-lane (-1/0/1) nether/end portals keep vanilla behavior.
            return;
        }
        event.setCanceled(true);
        LevelDimensionType type = data.typeOf(dim);
        if (type != LevelDimensionType.OVERWORLD && type != LevelDimensionType.NETHER) {
            return;
        }
        NetherPortalGeometry.Geometry geometry = NetherPortalGeometry.findFrame(event.getWorld(), event.getPos());
        if (geometry != null) {
            final net.minecraft.world.World world = event.getWorld();
            final NetherPortalGeometry.Geometry geo = geometry;
            if (world instanceof WorldServer) {
                ((WorldServer) world).addScheduledTask(() -> NetherPortalGeometry.placePortal(world, geo));
            } else {
                NetherPortalGeometry.placePortal(world, geo);
            }
        }
    }

    // ------------------------------------------------------------------ persistence

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player == null || event.player.world.isRemote) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        LevelManager lm = LevelManager.getInstance();
        int dim = player.dimension;
        if (lm.isMultiverseDimension(dim)) {
            lm.recordPlayer(player);
        } else {
            lm.clearPlayer(player.getUniqueID());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player == null || event.player.world.isRemote) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        LevelManager lm = LevelManager.getInstance();
        int dim = player.dimension;
        if (lm.isMultiverseDimension(dim)) {
            lm.recordPlayer(player);
        } else {
            lm.clearPlayer(player.getUniqueID());
        }
        server = player.world.getMinecraftServer();
        if (server != null) {
            lm.unloadEmptyDimensions(server, player.getUniqueID());
        }
    }

    // ------------------------------------------------------------------ unloading / save

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        NetherPortalLink.onEndTick();

        if (++tickCounter % 100 != 0) {
            return;
        }
        server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.getWorld(0) == null) {
            return;
        }
        LevelManager.getInstance().unloadEmptyDimensions(server);
    }
}
