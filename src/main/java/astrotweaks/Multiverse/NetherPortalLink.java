package astrotweaks.Multiverse;

import astrotweaks.AstrotweaksMod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side logic behind {@link BlockNetherPortal}.
 *
 * <ul>
 *   <li>defers the dimension hop to the next server tick ({@code addScheduledTask}),
 *       because {@code changeDimension} in the middle of {@code Entity.move()} is
 *       not safe; the portal cooldown ({@code timeUntilPortal}) prevents re-entry
 *       while the hop is pending and right after arrival</li>
 *   <li>a linked portal teleports the entity straight to the linked portal's center;
 *       an unlinked (or stale) link gets a fresh exit portal at the vanilla-scaled
 *       position, and both portals are linked to each other on every block</li>
 *   <li>nether-portal travel only exists for overworld &harr; nether of the same
 *       multiverse level</li>
 * </ul>
 */
public final class NetherPortalLink {
    private NetherPortalLink() {}

    private static final class Intent {
        final UUID entityId;
        final WorldServer origin;
        final int fromDim;
        final int targetDim;
        final LevelDimensionType targetType;
        final LevelData data;
        final int tx;
        final int ty;
        final int tz;
        final EnumFacing.Axis axis;
        final int linkX;
        final int linkY;
        final int linkZ;
        final boolean linkValid;
        final NetherPortalGeometry.Geometry sourceGeo;

        Intent(UUID entityId, WorldServer origin, int fromDim, int targetDim, LevelDimensionType targetType, LevelData data, int tx, int ty, int tz, EnumFacing.Axis axis,
               int linkX, int linkY, int linkZ, boolean linkValid, NetherPortalGeometry.Geometry sourceGeo) {
            this.entityId = entityId;
            this.origin = origin;
            this.fromDim = fromDim;
            this.targetDim = targetDim;
            this.targetType = targetType;
            this.data = data;
            this.tx = tx;
            this.ty = ty;
            this.tz = tz;
            this.axis = axis;
            this.linkX = linkX;
            this.linkY = linkY;
            this.linkZ = linkZ;
            this.linkValid = linkValid;
            this.sourceGeo = sourceGeo;
        }
    }

    private static final Map<UUID, Intent> PENDING = new ConcurrentHashMap<>();

    /** How many ticks an entity must keep standing inside the portal before travelling (vanilla = 80). */
    private static final int PORTAL_TICKS = 80;

    /** Per-entity ticks spent inside a portal block this session; decays every server tick while not in a portal. */
    private static final Map<UUID, Integer> PORTAL_COUNTERS = new ConcurrentHashMap<>();

    /** Entities that touched a portal block during the current server tick (they do not decay this tick). */
    private static final Set<UUID> PORTAL_SEEN = ConcurrentHashMap.newKeySet();

    /** Called from {@code BlockNetherPortal.onEntityCollidedWithBlock} on the server thread. */
    public static void handleCollision(World world, BlockPos pos, Entity entity) {
        if (world.isRemote) return;
        if (entity.isDead) return;

        UUID id = entity.getUniqueID();
        PORTAL_SEEN.add(id);

        if (PENDING.containsKey(id) || entity.timeUntilPortal > 0) {
            entity.timeUntilPortal = entity.getPortalCooldown();
            PORTAL_COUNTERS.remove(id);
            return;
        }

        NetherPortalGeometry.Geometry geo = NetherPortalGeometry.findInterior(world, pos);
        if (geo == null) return;

        int ticks = PORTAL_COUNTERS.getOrDefault(id, 0) + 1;
        if (ticks < PORTAL_TICKS) {
            PORTAL_COUNTERS.put(id, ticks);
            return;
        }
        PORTAL_COUNTERS.remove(id);

        int fromDim = world.provider.getDimension();
        LevelData data = LevelManager.getInstance().getLevelByDimensionId(fromDim);
        if (data == null) return;

        LevelDimensionType fromType = data.typeOf(fromDim);
        if (fromType == null) return;

        LevelDimensionType toType;
        if (fromType == LevelDimensionType.OVERWORLD) {
            toType = LevelDimensionType.NETHER;
        } else if (fromType == LevelDimensionType.NETHER) {
            toType = LevelDimensionType.OVERWORLD;
        } else { return; }

        int targetDim = data.dimensionId(toType);
        double scale = toType == LevelDimensionType.NETHER ? 1.0D / 8.0D : 8.0D;
        int tx = (int) Math.floor(entity.posX * scale);
        int ty = (int) Math.floor(entity.posY * scale);
        int tz = (int) Math.floor(entity.posZ * scale);

        boolean linkValid = false;
        int linkX = tx;
        int linkY = 0;
        int linkZ = tz;
        TileNetherPortal tile = getTile(world, geo.interiorMin);
        if (tile != null && tile.hasLink && tile.linkDim == targetDim) {
            linkValid = true;
            linkX = tile.linkX;
            linkY = tile.linkY;
            linkZ = tile.linkZ;
        }

        WorldServer server = world instanceof WorldServer ? (WorldServer) world : null;
        if (server == null) return;
        
        PENDING.put(entity.getUniqueID(), new Intent(entity.getUniqueID(), server, fromDim, targetDim, toType,
                data, tx, ty, tz, geo.axis, linkX, linkY, linkZ, linkValid, geo));
        server.addScheduledTask(() -> execute(entity));
    }

    /**
     * Server tick decay for the standing counters: entities that did not touch a
     * portal block this tick lose progress (vanilla {@code portalCounter--}); the
     * seen-set itself is reset every tick.
     */
    public static void onEndTick() {
        if (!PORTAL_COUNTERS.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> it = PORTAL_COUNTERS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> entry = it.next();
                if (PORTAL_SEEN.contains(entry.getKey())) {
                    continue;
                }
                int value = entry.getValue() - 1;
                if (value <= 0) {
                    it.remove();
                } else {
                    entry.setValue(value);
                }
            }
        }
        PORTAL_SEEN.clear();
    }

    private static void execute(Entity entity) {
        if (entity == null || entity.isDead) return;
        
        Intent intent = PENDING.remove(entity.getUniqueID());
        if (intent == null) return;
        
        if (entity.getEntityWorld() != intent.origin) return;

        MinecraftServer server = intent.origin.getMinecraftServer();
        if (server == null) return;

        WorldServer target = ensureTargetWorld(server, intent.data, intent.targetType);
        if (target == null) return;

        BlockPos stand;
        if (intent.linkValid && target.getBlockState(new BlockPos(intent.linkX, intent.linkY, intent.linkZ)).getBlock() == BlockNetherPortal.BLOCK) {
            stand = new BlockPos(intent.linkX, intent.linkY, intent.linkZ);
        } else {
            // Vanilla behavior: before building a fresh portal, link to an existing one
            // found within a 128-block radius of the expected arrival position.
            NetherPortalGeometry.Geometry exitGeo = NetherPortalGeometry.findNearbyPortal(target, intent.tx, intent.ty, intent.tz);
            if (exitGeo == null) {
                exitGeo = NetherPortalGeometry.placeExitPortal(target, intent.tx, intent.ty, intent.tz, intent.axis);
            }
            if (exitGeo == null) {
                return;
            }
            writeLinks(intent.origin, intent.sourceGeo, intent.fromDim, target, exitGeo, intent.targetDim);
            stand = exitGeo.interiorMin;
        }

        if (entity instanceof EntityPlayerMP) {
            AstrotweaksMod.PACKET_HANDLER.sendTo(new MessageMultiverse(intent.data != null ? intent.data.baseId : 0), (EntityPlayerMP) entity);
        }

        Entity moved;
        if (entity instanceof EntityPlayerMP) {
            moved = MultiverseEvents.teleportIgnoringPortalRemap((EntityPlayerMP) entity, intent.targetDim, new MultiverseTeleporter(stand));
        } else {
            moved = MultiverseEvents.teleportIgnoringPortalRemap(entity, intent.targetDim, new MultiverseTeleporter(stand));
        }
        if (moved != null) {
            moved.timeUntilPortal = moved.getPortalCooldown();
            moved.fallDistance = 0.0F;
        }
    }

    private static WorldServer ensureTargetWorld(MinecraftServer server, LevelData data, LevelDimensionType type) {
        if (data != null) {
            MultiverseDims.registerLevelDimensions(data.baseId);
            return LevelManager.getInstance().getOrCreateWorld(server, data, type);
        }
        return server.getWorld(type == LevelDimensionType.NETHER ? -1 : 0);
    }
    /** Writes the beacon of the opposite portal onto every block of both portals. */
    private static void writeLinks(WorldServer fromWorld, NetherPortalGeometry.Geometry fromGeo, int fromDim,
                                   WorldServer toWorld, NetherPortalGeometry.Geometry toGeo, int toDim) {
        writeLink(fromWorld, fromGeo, toDim, toGeo.interiorMin.getX(), toGeo.interiorMin.getY(), toGeo.interiorMin.getZ(), axisToInt(toGeo.axis));
        writeLink(toWorld, toGeo, fromDim, fromGeo.interiorMin.getX(), fromGeo.interiorMin.getY(), fromGeo.interiorMin.getZ(), axisToInt(fromGeo.axis));
    }
    private static void writeLink(World world, NetherPortalGeometry.Geometry geo, int dim, int x, int y, int z, int axis) {
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        int bx = geo.interiorMin.getX();
        int by = geo.interiorMin.getY();
        int bz = geo.interiorMin.getZ();
        EnumFacing rightDir = geo.rightDir();
        int rx = NetherPortalGeometry.stepX(rightDir);
        int rz = NetherPortalGeometry.stepZ(rightDir);
        for (int i = 0; i < geo.width; i++) {
            for (int j = 0; j < geo.height; j++) {
                cell.setPos(bx + rx * i, by + j, bz + rz * i);
                TileNetherPortal tile = getTile(world, cell);
                if (tile != null) {
                    tile.setLink(dim, x, y, z, axis);
                }
            }
        }
    }
    private static int axisToInt(EnumFacing.Axis axis) {
        return axis == EnumFacing.Axis.Z ? 1 : 0;
    }
    private static TileNetherPortal getTile(World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TileNetherPortal ? (TileNetherPortal) tile : null;
    }
}
