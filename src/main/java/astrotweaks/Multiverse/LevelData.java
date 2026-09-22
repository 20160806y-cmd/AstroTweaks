package astrotweaks.Multiverse;

import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.WorldInfo;

import java.io.File;

/** Immutable description of one multiverse level (one "other universe"). */
public class LevelData {

    public final String name;
    public final int number;     // номер вселенной (1..MULTIVERSE_MAX_UNIVERSES)
    public final String uid;     // 8-hex-хеш вселенной
    public final int baseId;
    public final long seed;
    public final File folder;
    public LevelData(String name, int number, String uid, int baseId, long seed, File folder) {
        this.name = name;
        this.number = number;
        this.uid = uid;
        this.baseId = baseId;
        this.seed = seed;
        this.folder = folder;
    }

    /** Global minecraft dimension id for the requested type (baseId .. baseId+3). */
    public int dimensionId(LevelDimensionType type) {
        switch (type) {
            case OVERWORLD: return baseId;
            case NETHER:    return baseId + 1;
            case END:       return baseId + 2;
            case DEPTHS:    return baseId + 3;
            default:
                throw new IllegalStateException("Unknown level dimension type: " + type);
                // return baseId;
        }
    }

    /** Which type the given global dimension id maps to, or null if it is not part of this level. */
    public LevelDimensionType typeOf(int dimensionId) {
        if (dimensionId == baseId) 
            return LevelDimensionType.OVERWORLD;
        if (dimensionId == baseId + 1) 
            return LevelDimensionType.NETHER;
        if (dimensionId == baseId + 2) 
            return LevelDimensionType.END;
        if (dimensionId == baseId + 3) 
            return LevelDimensionType.DEPTHS;

        return null;
    }

    /**
     * Reads level.dat from the level folder, or creates a fresh WorldInfo when the
     * level is brand new (seed comes from the command then).
     * For overworlds, inherits rtgc generatorName from base world (dim 0) if enabled.
     */
    public WorldInfo loadOrCreateWorldInfo() {
        WorldInfo info = new LevelSaveHandler(folder).loadWorldInfo();
        if (info == null) {
            // Only overworld data is stored via this path (shared level.dat), but we
            // conservatively check if base is RTG — if so, new MV overworld should be RTG.
            if (RTGSupport.isBaseWorldRTG()) {
                WorldType rtgType = RTGSupport.getRTGWorldType();
                String baseOpts = RTGSupport.getBaseGeneratorOptions(null);
                if (rtgType != null) {
                    WorldSettings settings = new WorldSettings(seed, GameType.SURVIVAL, true, false, rtgType);
                    if (baseOpts != null && !baseOpts.isEmpty()) settings.setGeneratorOptions(baseOpts);
                    info = new WorldInfo(settings, name);
                    return info;
                }
            }
            info = new WorldInfo( new WorldSettings(seed, GameType.SURVIVAL, true, false, WorldType.DEFAULT), name );
        }
        return info;
    }
}
