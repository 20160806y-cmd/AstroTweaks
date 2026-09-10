package astrotweaks.dimension;

import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.DimensionManager;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.World;
import net.minecraft.world.DimensionType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Biomes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;



public class VoidDimension {
	public static int DIMID = -1_000_000;
	public static DimensionType dtype;
	public VoidDimension() {}

    public static void preInit() {
        if (DimensionManager.isDimensionRegistered(DIMID)) {
            DIMID = DimensionManager.getNextFreeDimId();
            System.err.println("Dimension ID is already registered. Fallback to ID: " + DIMID);
        }
        dtype = DimensionType.register("uvoid","_uvoid",DIMID,WorldProviderMod.class,false);
        DimensionManager.registerDimension(DIMID, dtype);
    }

	public static class WorldProviderMod extends WorldProvider { // this's cavern
		@SubscribeEvent
		public void init() {
			this.biomeProvider = new BiomeProviderSingle(Biomes.VOID);
			this.nether = false; // false, because this isn't nether
			this.hasSkyLight = true; // naturally light (y/n)
		}
		@Override public void calculateInitialWeather() {}
		@Override public void updateWeather() {}
		@Override public boolean canDoLightning(net.minecraft.world.chunk.Chunk chunk) {return false;}
		@Override public boolean canDoRainSnowIce(net.minecraft.world.chunk.Chunk chunk) {return false;}
		@Override public DimensionType getDimensionType() { return dtype; }
		@SideOnly(Side.CLIENT)
		@Override public Vec3d getFogColor(float par1, float par2) { return new Vec3d(0.0f, 0.0f, 0.0f); }
		@SideOnly(Side.CLIENT)
		@Override public Vec3d getSkyColor(Entity cameraEntity, float partialTicks) { return new Vec3d(0.0D, 0.0D, 0.0D); /* sky */ }
		@SideOnly(Side.CLIENT)
		@Override public float calculateCelestialAngle(long worldTime, float partialTicks) { return 0.0F;/* fixed time/bg */ }
		@Override public IChunkGenerator createChunkGenerator() { return new ChunkProviderModded(this.world); }
		@Override public boolean isSurfaceWorld() { return false; }
		@Override public boolean canRespawnHere() { return false; }
		@Override public int getAverageGroundLevel() { return 256; }
		@Override public float getCloudHeight() { return -8192F; }
		@SideOnly(Side.CLIENT)
		@Override public boolean doesXZShowFog(int par1, int par2) {return true;}
		@Override public WorldSleepResult canSleepAt(EntityPlayer player, BlockPos pos) { return WorldSleepResult.DENY; }
		@Override public boolean doesWaterVaporize() {return false;}
		// No Nether/End portals
		@Override public boolean canCoordinateBeSpawn(int x, int z) { return false; }
		//@Override public BlockPos getSpawnPoint() { return new BlockPos(0, 64, 0); }
		@Override public BlockPos getSpawnCoordinate() { return new BlockPos(0, 64, 0); }
		@Override public boolean isSkyColored() { return true; }
		@Override public double getVoidFogYFactor() { return 512D; }
		@Nullable
		@Override
		@SideOnly(Side.CLIENT)
		public float[] calcSunriseSunsetColors(float celestialAngle, float partialTicks) {

			float f1 = MathHelper.cos(celestialAngle * ((float)Math.PI * 2F)) - 0.0F;

			float f3 = (f1 - -0.0F) / 0.4F * 0.5F + 0.5F;
			float f4 = 1.0F - (1.0F - MathHelper.sin(f3 * (float)Math.PI)) * 0.99F;
			f4 = f4 * f4;
			float[] colorsSunriseSunset = new float[4];
			colorsSunriseSunset[0] = 0.0F; // R
			colorsSunriseSunset[1] = 0.0F; // G
			colorsSunriseSunset[2] = 0.0F; // B
			colorsSunriseSunset[3] = f4;   // alpha — плавное появление/уход
			return colorsSunriseSunset;
    	}
	}


	public static class ChunkProviderModded implements IChunkGenerator { // this's cavern
		private final World world;
        public ChunkProviderModded(World world) {
            this.world = world;
            this.world.setSeaLevel(0);
        }
        @Override
        public Chunk generateChunk(int chunkX, int chunkZ) {
            ChunkPrimer primer = new ChunkPrimer();

            if (chunkX == 0 && chunkZ == 0) primer.setBlockState(0,63,0,Blocks.BEDROCK.getDefaultState());

            Chunk chunk = new Chunk(world, primer, chunkX, chunkZ);

            // Заполняем биомы чанка
            byte[] biomeArray = chunk.getBiomeArray();
            byte voidBiomeId = (byte) Biome.getIdForBiome(Biomes.VOID);
            Arrays.fill(biomeArray, voidBiomeId);

            chunk.generateSkylightMap();

            return chunk;
        }
        @Override
        public void populate(int chunkX, int chunkZ) {
            /*
            * Здесь ничего не должно быть.
            *
            * Не вызываем:
            * - biome.decorate(...)
            * - ForgeEventFactory.onChunkPopulate(...)
            */
        }
		@Override public List<Biome.SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) { return Collections.emptyList(); }
		@Override public void recreateStructures(Chunk chunkIn, int x, int z) {}
		@Override public boolean isInsideStructure(World worldIn, String structureName, BlockPos pos) {return false;}
		@Override public BlockPos getNearestStructurePos(World worldIn, String structureName, BlockPos position, boolean findUnexplored) {return null;}
		@Override public boolean generateStructures(Chunk chunkIn, int x, int z) {return false;}
	}
}
