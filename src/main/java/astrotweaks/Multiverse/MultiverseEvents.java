package astrotweaks.Multiverse;

import astrotweaks.AstrotweaksMod;

import net.minecraft.command.CommandGameRule;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Glues the multiverse into the game:
 * <ul>
 *   <li>world bind: on overworld load, (re)attach this save's MULTIVERSE folder</li>
 *   <li>re-login: return players to their last multiverse dimension</li>
 *   <li>portals: nether / end portals inside a level stay inside that level</li>
 *   <li>persistence: remember the position of players inside a level</li>
 *   <li>unloading: drop worlds only after they stay player-free long enough</li>
 * </ul>
 */
public class MultiverseEvents {

    private static final Set<UUID> SKIP_PORTAL_REMAP = new HashSet<>();
    /** dimId &rarr; System.currentTimeMillis() when the world was first found empty. Server-thread only. */
    private static final Map<Integer, Long> PENDING_UNLOAD = new HashMap<>();

    private int tickCounter;
    private MinecraftServer server;
    private static volatile MinecraftServer cachedServer;

    private static final int GAMERULE_SYNC_INTERVAL = 40; // Тиков.  20t = 1s

    /**
     * Pending gamerule edits typed inside a multiverse world, keyed by rule name.
     * Vanilla writes (and validates) them on that world's own GameRules; we confirm
     * the write at the end of the same tick (see {@link #propagateGameRuleUpdates})
     * and mirror the value onto dim 0 so it becomes the new global default instead of
     * being silently reverted by {@link #syncGameRulesFromOverworld}.
     */

    private static final Map<String, String> PENDING_GAMERULE = new HashMap<>();
    private static volatile boolean hasPendingRules = false;
    private static volatile boolean hasActiveMultiverseWorlds = false;

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

    /** Общий путь: либо сохраняем позицию в MV-измерении, либо чистим запись. */
    private static void persistPlayerLocation(EntityPlayerMP player) {
        LevelManager lm = LevelManager.getInstance();
        if (lm.isMultiverseDimension(player.dimension)) {
            lm.recordPlayer(player);
        } else {
            lm.clearPlayer(player.getUniqueID());
        }
    }

    private static boolean scanForActiveMultiverseWorlds(MinecraftServer srv) {
        if (srv == null) return false;
        LevelManager lm = LevelManager.getInstance();
        for (WorldServer w : srv.worlds) {
            if (w != null && lm.isMultiverseDimension(w.provider.getDimension())) return true;
        }
        return false;
    }

    /** Возвращает живой сервер, попутно обновляя кэш. */
    private static MinecraftServer serverRef() {
        MinecraftServer s = cachedServer;
        if (s != null && s.getWorld(0) != null) return s;
        s = FMLCommonHandler.instance().getMinecraftServerInstance();
        cachedServer = s;
        return s;
    }

    /**
     * Mirrors the ORIGINAL world's (dim 0) gamerules onto every multiverse world, so
     * that keepInventory, doDaylightCycle, doWeatherCycle, spawnRadius etc. behave in
     * each universe exactly like they do in the base world. Every MV level keeps its
     * own WorldInfo/GameRules in its level.dat, so a freshly created universe starts
     * with vanilla defaults and a /gamerule typed in dim 0 never reaches it otherwise.
     * Runs every server tick (cheap: only set actual differences) and after world
     * construction, so a world can never serve a stale rule. Combined with
     * {@link #onGameRuleCommand} this makes dim 0 the single authoritative source:
     * /gamerule typed INSIDE a multiverse world is lifted onto dim 0, then fanned out
     * here, instead of being silently reverted.
     */
    public static void syncGameRulesFromOverworld(MinecraftServer srv) {
        if (srv == null) return;
        WorldServer base = srv.getWorld(0);
        if (base == null) return;
        GameRules src = base.getGameRules();
        if (src == null) return;

        LevelManager lm = LevelManager.getInstance();

        // Сначала убеждаемся, что есть хотя бы один MV-мир — иначе снапшот зря.
        boolean any = false;
        for (WorldServer w : srv.worlds) {
            if (w != null && w != base && lm.isMultiverseDimension(w.provider.getDimension())) {
                any = true; break;
            }
        }
        if (!any) return;

        String[] ruleKeys = src.getRules();
        Map<String, String> snapshot = new HashMap<>(ruleKeys.length * 2);
        for (String k : ruleKeys) snapshot.put(k, src.getString(k));

        for (WorldServer world : srv.worlds) {
            if (world == null || world == base) continue;
            if (!lm.isMultiverseDimension(world.provider.getDimension())) continue;
            GameRules dst = world.getGameRules();
            if (dst == null || dst == src) continue;
            for (Map.Entry<String, String> e : snapshot.entrySet()) {
                if (!e.getValue().equals(dst.getString(e.getKey()))) {
                    dst.setOrCreateGameRule(e.getKey(), e.getValue());
                }
            }
        }
    }

    /**
     * Makes {@code /gamerule} behave globally from ANY multiverse world: the original
     * world (dim 0) stays the single source of truth, so a rule typed inside a universe
     * is recorded here and later lifted to dim 0 once the vanilla command confirms it
     * (validation happens in {@link CommandGameRule} before it actually writes). The
     * vanilla command itself is NOT cancelled &mdash; it still writes to the sender's
     * world (querying {@code /gamerule <rule>} already reports the mirrored dim 0 value).
     */
    @SubscribeEvent
    public void onGameRuleCommand(CommandEvent event) {
        if (!(event.getCommand() instanceof CommandGameRule)) return;
        ICommandSender sender = event.getSender();
        if (sender == null || sender.getEntityWorld() == null) return; // console: already acts on dim 0
        World w = sender.getEntityWorld();
        if (w.isRemote) return;
        int dim = w.provider.getDimension();
        if (dim == 0) return;
        LevelManager lm = LevelManager.getInstance();
        if (!lm.isMultiverseDimension(dim)) return;

        MinecraftServer srv = w.getMinecraftServer();
        if (srv == null || srv.getWorld(0) == null) return;
        String[] params = event.getParameters();
        if (params == null || params.length < 2) return; // /gamerule <rule> (query) already shows the mirrored value
        GameRules src = srv.getWorld(0).getGameRules();
        if (!src.hasRule(params[0])) return; // let vanilla throw the "no such rule" error, don't touch dim 0
        synchronized (PENDING_GAMERULE) {
            PENDING_GAMERULE.put(params[0], params[1]);
            hasPendingRules = true;
        }
    }

    /**
     * Copies every {@code /gamerule} change that vanilla has actually applied to a
     * multiverse world in this tick onto dim 0, then {@link #syncGameRulesFromOverworld}
     * fans it out to all worlds. Only applied when some MV world currently holds the
     * exact pending value, so invalid input (rejected by {@code CommandGameRule})
     * never reaches dim 0.
     */
    private static boolean propagateGameRuleUpdates() {
        if (!hasPendingRules)  return false;

        MinecraftServer srv = serverRef();
        if (srv == null) return false;
        WorldServer base = srv.getWorld(0);
        if (base == null) return false;
        GameRules src = base.getGameRules();
        LevelManager lm = LevelManager.getInstance();

        boolean changed = false;
        for (Map.Entry<String, String> e : PENDING_GAMERULE.entrySet()) {
            String key = e.getKey(), value = e.getValue();
            for (WorldServer world : srv.worlds) {
                if (world == null || world == base) continue;
                if (!lm.isMultiverseDimension(world.provider.getDimension())) continue;
                if (value.equals(world.getGameRules().getString(key))) {
                    if (!value.equals(src.getString(key))) {
                        src.setOrCreateGameRule(key, value);
                        changed = true;
                    }
                    break;
                }
            }
        }
        PENDING_GAMERULE.clear();
        hasPendingRules = false;
        return changed;
    }

    // ------------------------------------------------------------------ world bind

    @SubscribeEvent
    public void onWorldLoaded(WorldEvent.Load event) {
        if (event.getWorld() == null || event.getWorld().isRemote)  return;
        if (event.getWorld().provider == null || event.getWorld().provider.getDimension() != 0)  return;

        MinecraftServer srv = event.getWorld().getMinecraftServer();
        if (srv != null) {
            cachedServer = srv;
            LevelManager.getInstance().onWorldLoaded(srv);
            // Pending-unload timestamps belong to the previous server/save session.
            PENDING_UNLOAD.clear();
        }
    }

    // ------------------------------------------------------------------ re-login

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player == null || event.player.world.isRemote)  return;
        if (!(event.player instanceof EntityPlayerMP))  return;


        final EntityPlayerMP player = (EntityPlayerMP) event.player;
        final MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;

        // PlayerLoggedInEvent fires before the target WorldServer is guaranteed to be
        // attached and before the client knows the DimensionType. Defer the restore to
        // the next server tick so the login sequence completes first.
        server.addScheduledTask(() -> {
            if (player.isDead)  return;

            LevelManager lm = LevelManager.getInstance();
            LevelManager.PlayerEntry entry = lm.getPlayerEntry(player.getUniqueID());
            if (entry == null)  return;

            if (!lm.restorePlayer(player)) 
                System.err.println("[MULTIVERSE] Failed to restore " + player.getName() + " to dimension " + entry.dimension);

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
        // vanilla nether/end or into any multiverse level. The proxy dimension
        // (1_111_111) is sealed likewise: players are moved in/out of it synchronously
        // by our own code, never through a portal.
        if (from == MultiverseDims.GLOBAL_DIM || from == MultiverseDims.PROXY_DIM) {
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
        System.out.println("[MULTIVERSE] Portal remap: dim " + from + " -> " + to + " remapped to " + targetType + " in level '" + data.name + "'");

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
            targetPos = new BlockPos((int) (entity.posX * scale), (int) entity.posY, (int) (entity.posZ * scale));
        }

        boolean portalPair = targetType == LevelDimensionType.NETHER || (targetType == LevelDimensionType.OVERWORLD && data.typeOf(from) == LevelDimensionType.NETHER);
        ITeleporter teleporter = new MultiverseTeleporter(targetPos, portalPair);

        if (player != null) {
            AstrotweaksMod.PACKET_HANDLER.sendTo(new MessageMultiverse(data.baseId, data.seed), player);
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
                if (to == -1) return LevelDimensionType.NETHER;
                if (to == 1)  return LevelDimensionType.END;
                return null;
            case NETHER:
                if (to == -1 || to == 0)return LevelDimensionType.OVERWORLD;
                if (to == 1)            return LevelDimensionType.END;
                return null;
            case END:
                if (to == -1)           return LevelDimensionType.NETHER;
                if (to == 0 || to == 1) return LevelDimensionType.OVERWORLD;
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

        if (dim == MultiverseDims.GLOBAL_DIM || dim == MultiverseDims.PROXY_DIM) {
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
        if (type != LevelDimensionType.OVERWORLD && type != LevelDimensionType.NETHER)  return;
        
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
        persistPlayerLocation((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player == null || event.player.world.isRemote) return;
        if (!(event.player instanceof EntityPlayerMP)) return;

        EntityPlayerMP player = (EntityPlayerMP) event.player;
        persistPlayerLocation((EntityPlayerMP) event.player);

        MultiverseClientSoundHandler.reset();

        MinecraftServer srv = player.world.getMinecraftServer();
        if (srv != null) {
            LevelManager lm = LevelManager.getInstance();
            lm.processDeferredUnloading(srv, PENDING_UNLOAD, lm.getUnloadDelayMs(), player.getUniqueID());
            lm.flushRegistryIfDirty(srv);
        }
    }

    // ------------------------------------------------------------------ unloading / save

    /**
     * Keeps player positions fresh across the vanilla autosave so a crash never loses
     * a player's last multiverse location. {@link LevelManager#recordPlayer} only
     * writes the player file when the stored position actually changed, so this stays
     * cheap even though every loaded world fires the event.
     */
    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event) {
        if (event.getWorld() == null || event.getWorld().isRemote) return;
        if (!(event.getWorld() instanceof WorldServer)) return;
        WorldServer ws = (WorldServer) event.getWorld();
        if (ws.playerEntities.isEmpty()) return;

        LevelManager lm = LevelManager.getInstance();
        for (net.minecraft.entity.player.EntityPlayer p : ws.playerEntities) {
            if (p instanceof EntityPlayerMP && lm.isMultiverseDimension(p.dimension)) {
                lm.recordPlayer((EntityPlayerMP) p);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)  return;

        MinecraftServer srv = serverRef();
        if (srv == null || srv.getWorld(0) == null) return;

        // Пересчёт флага раз в секунду — O(worlds), но редко.
        if (tickCounter % 20 == 0) {
            hasActiveMultiverseWorlds = scanForActiveMultiverseWorlds(srv);
        }
        if (!hasActiveMultiverseWorlds) {
            // Ни MV-миров, ни MV-порталов, ни геймрул для синхронизации.
            // PENDING/PORTAL_COUNTERS гарантированно пусты: попасть в них можно
            // только из MV-мира, а его нет.
            return;
        }

        ++tickCounter;
        NetherPortalLink.onEndTick();

        boolean rulesChanged = propagateGameRuleUpdates();
        if (rulesChanged || tickCounter % GAMERULE_SYNC_INTERVAL == 0) {
            syncGameRulesFromOverworld(srv); // overload с srv, см. п.3
        }

        if (tickCounter % 160 == 0) {
            LevelManager lm = LevelManager.getInstance();
            lm.processDeferredUnloading(srv, PENDING_UNLOAD, lm.getUnloadDelayMs());
            lm.flushRegistryIfDirty(srv);
            tickCounter = 0;
        }
    }
}
