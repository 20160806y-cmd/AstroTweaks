package astrotweaks.Multiverse;

import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.DimensionManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Registers the dimension ids used by the multiverse (three per level plus the
 * shared global dimension -1000000) and their DimensionManager mappings. Run on BOTH
 * sides:
 * <ul>
 *   <li>server &rarr; before creating the WorldServer (WorldServer ctor internally calls
 *       DimensionManager.createProviderFor)</li>
 *   <li>client &rarr; before the respawn packet is handled (WorldClient reads the
 *       provider class straight from DimensionType.getById(dim))</li>
 * </ul>
 *
 * <p>Dimensions are registered with keepLoaded=false so DimensionManager is allowed
 * to unload the backing world once the last player leaves (isMultiverse worlds are
 * unloaded by {@link LevelManager}).</p>
 *
 * <p>Registration is guarded by a single lock. {@link DimensionType#register} goes
 * through {@code EnumHelper.addEnum}, which mutates the internal Java enum array and
 * is not thread-safe; the whole check&rarr;register sequence must be atomic (netty
 * threads can deliver {@link MessageMultiverse} concurrently).</p>
 *
 * <p>The enum constants of {@code DimensionType} survive for the whole JVM (an
 * integrated server keeps running between world sessions), while
 * {@code DimensionManager}'s registrations are dropped on server stop. So the same
 * constant must be REUSED (never re-created via EnumHelper.addEnum, that duplicates
 * the enum entry) but the dimension RE-REGISTERED with DimensionManager whenever it
 * is currently missing. {@link #REGISTERED_DIMENSIONS} only caches the enum constant,
 * it must never gate the DimensionManager registration.</p>
 */
public final class MultiverseDims {

    /** Shared "global" dimension: one overworld-like world over all saves. */
    public static final int GLOBAL_DIM = -1_000_000;

    /** Proxy dimension used as a temporary holding area during universe recreation. */
    public static final int PROXY_DIM = 1_111_111;

    private static final Object DIMENSION_REGISTRATION_LOCK = new Object();
    private static final Map<Integer, DimensionType> REGISTERED_DIMENSIONS = new HashMap<>();

    private MultiverseDims() {}

    /** Registers overworld (base), nether (base+1), end (base+2) and depths (base+3) of the level. Idempotent + thread-safe. */
    public static void registerLevelDimensions(int baseId) {
        if (DimensionManager.isDimensionRegistered(baseId)
            && DimensionManager.isDimensionRegistered(baseId + 1)
            && DimensionManager.isDimensionRegistered(baseId + 2)
            && DimensionManager.isDimensionRegistered(baseId + 3)) {
            return;
        }
        synchronized (DIMENSION_REGISTRATION_LOCK) {
            registerOneLocked(baseId, MultiverseWorldProviders.MultiverseOverworld.class);
            registerOneLocked(baseId + 1, MultiverseWorldProviders.MultiverseHell.class);
            registerOneLocked(baseId + 2, MultiverseWorldProviders.MultiverseEnd.class);
            registerOneLocked(baseId + 3, MultiverseWorldProviders.MultiverseDepths.class);
        }
    }

    /**
     * Whether the shared global void dimension (-1000000) is enabled in the mod
     * config (ModVariables.Enable_uVOID). When disabled the dimension is never
     * registered, never loaded and its MULTIVERSE_GLOBAL folder is never created.
     */
    public static boolean isGlobalDimensionEnabled() {
        return astrotweaks.ModVariables.Enable_uVOID;
    }

    /** Registers the shared global dimension (-1000000). Idempotent + thread-safe. No-op when Enable_uVOID is off. */
    public static void registerGlobalDimension() {
        if (!isGlobalDimensionEnabled()) return;
        synchronized (DIMENSION_REGISTRATION_LOCK) {
            registerOneLocked(GLOBAL_DIM, MultiverseWorldProviders.MultiverseGlobal.class);
        }
    }

    /**
     * Client-side registration of the global dimension, bypassing {@link #isGlobalDimensionEnabled()}.
     * The server is authoritative here: it only sends {@code MessageMultiverse.forGlobal()} when it is
     * about to move the player into the void, and registering a DimensionType on the client has no
     * save-folder side effects. This keeps a client whose local Enable_uVOID differs from the server's
     * from crashing when the respawn packet arrives for an unregistered dimension.
     */
    public static void registerGlobalDimensionForClient() {
        synchronized (DIMENSION_REGISTRATION_LOCK) {
            registerOneLocked(GLOBAL_DIM, MultiverseWorldProviders.MultiverseGlobal.class);
        }
    }
    /** Registers the proxy dimension (1_111_111). Idempotent + thread-safe. Used as temporary holding during universe recreation. */
    public static void registerProxyDimension() {
        synchronized (DIMENSION_REGISTRATION_LOCK) {
            registerOneLocked(PROXY_DIM, MultiverseWorldProviders.MultiverseGlobal.class);
        }
    }
    /** Client-side registration of the proxy dimension. */
    public static void registerProxyDimensionForClient() {
        synchronized (DIMENSION_REGISTRATION_LOCK) {
            registerOneLocked(PROXY_DIM, MultiverseWorldProviders.MultiverseGlobal.class);
        }
    }

    /** Must be called with {@link #DIMENSION_REGISTRATION_LOCK} held. */
    private static void registerOneLocked(int dimId, Class<? extends WorldProvider> providerClass) {
        DimensionType type = REGISTERED_DIMENSIONS.get(dimId);
        if (type == null) {
            // A previous server session in this JVM (integrated quit-to-title) may
            // have already appended the constant to the DimensionType enum. Re-running
            // EnumHelper.addEnum with the same name would add a duplicate enum entry,
            // so reuse the existing constant instead.
            try { type = DimensionType.getById(dimId);
            } catch (IllegalArgumentException ignored) { /* getById rejects negative / out-of-range ids; treat as not found. */ }

            if (type == null) {
                // EnumHelper.addEnum appends a real constant, Forge's DimensionType.getById
                // will then find the id on both sides. The enum constant name must be unique
                // per id and valid as a Java identifier.
                type = DimensionType.register("MV_DIM_" + dimId, "_mv", dimId, providerClass, false);
            }
            REGISTERED_DIMENSIONS.put(dimId, type);
        }
        // DimensionManager registrations are scoped to one server session and are
        // unregistered on stop, so always re-register when the id is currently missing.
        if (!DimensionManager.isDimensionRegistered(dimId)) {
            DimensionManager.registerDimension(dimId, type);
        }
    }
}
