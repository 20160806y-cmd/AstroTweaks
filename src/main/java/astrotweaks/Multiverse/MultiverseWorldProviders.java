package astrotweaks.Multiverse;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.WorldProviderSurface;

/**
 * World providers for every multiverse dimension.
 *
 * <p>Instances are created reflectively by Forge's DimensionManager, so the
 * classes MUST be static and have an empty constructor. The dimension id of a
 * provider is inflicted afterwards (setDimension), so {@link #getDimensionType()}
 * returns the base DimensionType of the world it mimics. This matters for the
 * vanilla fire check in {@code BlockFire.onBlockAdded} which refuses to spawn a
 * nether portal when {@code provider.getDimensionType().getId() > 0}; returning
 * {@code DimensionType.getById(dim)} would yield a custom id (1000+) and make
 * portals un-spawnable in every multiverse overworld.</p>
 *
 * <p>Note: we intentionally do NOT override createWorld(...) - that hook does not
 * exist in 1.12.2. Worlds are constructed and registered manually by
 * {@link LevelManager}. The dimension id to save-folder layout is handled by
 * {@link LevelSaveHandler} (an AnvilSaveHandler) together with the instanceof
 * checks inside AnvilSaveHandler.getChunkLoader.</p>
 */
public class MultiverseWorldProviders {

    public static class MultiverseOverworld extends WorldProviderSurface {
        @Override
        public DimensionType getDimensionType() {
            return DimensionType.OVERWORLD;
        }
        @Override
        public int getRespawnDimension(EntityPlayerMP player) {
            return this.getDimension();
        }
    }

    public static class MultiverseHell extends WorldProviderHell {
        @Override
        public DimensionType getDimensionType() {
            return DimensionType.NETHER;
        }
        @Override
        public int getRespawnDimension(EntityPlayerMP player) {
            return LevelManager.getOwningOverworldDimension(this.getDimension());
        }
    }

    public static class MultiverseEnd extends WorldProviderEnd {
        @Override
        public DimensionType getDimensionType() {
            return DimensionType.THE_END;
        }
        @Override
        public int getRespawnDimension(EntityPlayerMP player) {
            return LevelManager.getOwningOverworldDimension(this.getDimension());
        }
    }

    /** The shared global dimension 9999: a regular overworld-like world over all saves. */
    public static class MultiverseGlobal extends WorldProviderSurface {
        @Override
        public DimensionType getDimensionType() {
            return DimensionType.OVERWORLD;
        }
        @Override
        public int getRespawnDimension(EntityPlayerMP player) {
            return this.getDimension();
        }
    }
}
