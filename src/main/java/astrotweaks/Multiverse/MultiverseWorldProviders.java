package astrotweaks.Multiverse;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.init.Biomes;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import astrotweaks.dimension.VoidDimension;
import astrotweaks.world.DepthsDim;

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

    /**
     * Per-level "depths"/cavern world (baseId+3): the same dark all-fill cavern
     * generator and biome provider as the vanilla DepthsDim world, but rooted in
     * the level folder and with a unique id per level. Fire cannot light portals
     * here because the registered dimension id is &gt; 0 (the custom portal is
     * overworld/nether only anyway); entry/exit is handled by MineDimEnter.
     */
    public static class MultiverseDepths extends WorldProvider {
        @Override
        protected void init() {
            this.nether = false;
            this.hasSkyLight = false;
            this.biomeProvider = new DepthsDim.BiomeProviderCustom(this.world.getSeed());
        }
        @Override
        public IChunkGenerator createChunkGenerator() {
            return new DepthsDim.ChunkProviderModded(this.world, this.world.getSeed() - this.getDimension());
        }
        @Override
        public DimensionType getDimensionType() {
            return DimensionType.getById(this.getDimension());
        }
        @Override
        public int getRespawnDimension(EntityPlayerMP player) {
            return LevelManager.getOwningOverworldDimension(this.getDimension());
        }
        @Override public void calculateInitialWeather() {}
        @Override public void updateWeather() {}
        @Override public boolean canDoLightning(Chunk chunk) { return false; }
        @Override public boolean canDoRainSnowIce(Chunk chunk) { return false; }
        @Override public boolean isSurfaceWorld() { return false; }
        @Override public boolean canRespawnHere() { return false; }
        @Override public boolean doesWaterVaporize() { return false; }
        @Override public WorldSleepResult canSleepAt(EntityPlayer player, BlockPos pos) { return WorldSleepResult.DENY; }
        @Override public boolean canCoordinateBeSpawn(int x, int z) { return false; }
        @SideOnly(Side.CLIENT)
        @Override public Vec3d getFogColor(float par1, float par2) { return new Vec3d(0.0, 0.0, 0.0); }
        @SideOnly(Side.CLIENT)
        @Override public Vec3d getSkyColor(net.minecraft.entity.Entity cameraEntity, float partialTicks) { return new Vec3d(0.0, 0.0, 0.0); }
        @SideOnly(Side.CLIENT)
        @Override public float calculateCelestialAngle(long worldTime, float partialTicks) { return 0.5F; }
        @SideOnly(Side.CLIENT)
        @Override public boolean doesXZShowFog(int x, int z) { return true; }
    }


    /**
     * The shared global dimension -1000000: a void world over all saves.
     * Registered only when {@code ModVariables.Enable_uVOID} is on — the gate lives
     * in {@link MultiverseDims#registerGlobalDimension()}, and LevelManager/commands
     * never touch its world/folder while the config flag is off.
     */
    public static class MultiverseGlobal extends WorldProvider {
        @Override
        protected void init() {
            this.biomeProvider = new BiomeProviderSingle(Biomes.VOID);
            this.nether = false;
            this.hasSkyLight = true;
        }
        @Override public DimensionType getDimensionType() { return DimensionType.getById(this.getDimension()); }
        @Override public IChunkGenerator createChunkGenerator() { return new VoidDimension.ChunkProviderModded(this.world); }
        @Override public int getRespawnDimension(EntityPlayerMP player) { return 0; }
        @Override public void calculateInitialWeather() {}
        @Override public void updateWeather() {}
        @Override public boolean canDoLightning(Chunk chunk) { return false; }
        @Override public boolean canDoRainSnowIce(Chunk chunk) { return false; }
        @Override public boolean isSurfaceWorld() { return false; }
        @Override public boolean canRespawnHere() { return false; }
        @Override public boolean canCoordinateBeSpawn(int x, int z) { return false; }
    }
}
