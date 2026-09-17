package astrotweaks.world.NaturesPower;

import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockDoublePlant.EnumPlantType;
import net.minecraft.block.material.Material;
import net.minecraft.block.BlockDoublePlant.EnumBlockHalf;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ThreadLocalRandom;

import astrotweaks.block.BlockGiantGrass;
import astrotweaks.ModVariables;



public class GrassGrowth {
	private static int GRASS_DENSITY; // def 18
	private static int TALL_GRASS_DENSITY; // def 9
	private static int GIANT_GRASS_DENSITY; // def 4
    private static int MIN_DELAY_TICKS;
    private static int MAX_DELAY_TICKS;
    private static int MAX_OPER_PER_TICK;
    private static boolean GG_ON;

	// Порог перехода на след. уровень высоты
	private static int GRASS_THRESHOLD;
	private static int TALL_GRASS_THRESHOLD;
	//private static int GIANT_GRASS_THRESHOLD;

	public static void updVars() {
		GRASS_DENSITY = ModVariables.GG_Density; // def 18
		TALL_GRASS_DENSITY = ModVariables.GG_Tall_Density; // def 9
		GIANT_GRASS_DENSITY = ModVariables.GG_Giant_Density; // def 4
		MIN_DELAY_TICKS = ModVariables.GG_MIN_DELAY_TICK;
		MAX_DELAY_TICKS = ModVariables.GG_MAX_DELAY_TICK;
		MAX_OPER_PER_TICK = ModVariables.GG_MAX_OPER_PER_TICK;
		GG_ON = ModVariables.GG_ENABLED;

		// Порог перехода на след. уровень высоты
		GRASS_THRESHOLD = GRASS_DENSITY - 1;
		TALL_GRASS_THRESHOLD = TALL_GRASS_DENSITY - 1;
		//GIANT_GRASS_THRESHOLD = GIANT_GRASS_DENSITY - 1;

        if (MIN_DELAY_TICKS > MAX_DELAY_TICKS) MIN_DELAY_TICKS = MAX_DELAY_TICKS - 1;
		if (MIN_DELAY_TICKS < 1) MIN_DELAY_TICKS = 1;
		if (MAX_DELAY_TICKS < 1) MAX_DELAY_TICKS = 1;

	}



    // Per-dimension priority queues
	private static final Map<Integer, ConcurrentSkipListSet<ScheduledChunk>> queues = new ConcurrentHashMap<>();
	private static final Map<Integer, Set<Long>>  loadedChunks   = new ConcurrentHashMap<>();
	private static final Map<Integer, Map<Long,Long>> scheduledTimes = new ConcurrentHashMap<>();
	//private static final Object STATE_LOCK = new Object();



    // Helper class for the priority queue
    private static class ScheduledChunk implements Comparable<ScheduledChunk> {
        final long chunkKey;
        final long scheduledTime;

        ScheduledChunk(long key, long time) { chunkKey = key; scheduledTime = time; }

		@Override
		public int compareTo(ScheduledChunk o) {
			int c = Long.compare(scheduledTime, o.scheduledTime);
			return c != 0 ? c : Long.compare(chunkKey, o.chunkKey);
		}
		// equals/hashCode можно оставить, но для skip-list важнее compareTo
		@Override public boolean equals(Object o) {
			return o instanceof ScheduledChunk && ((ScheduledChunk)o).chunkKey == chunkKey
				&& ((ScheduledChunk)o).scheduledTime == scheduledTime;
		}
		@Override public int hashCode() { return Long.hashCode(chunkKey * 31 + scheduledTime); }
    }

    // Helper methods to get per-dimension structures
	private static ConcurrentSkipListSet<ScheduledChunk> getQueue(int dim) {
		return queues.computeIfAbsent(dim, k -> new ConcurrentSkipListSet<>());
	}
	private static Set<Long> getLoadedSet(int dim) {
		return loadedChunks.computeIfAbsent(dim, k -> ConcurrentHashMap.newKeySet());
	}
	private static Map<Long,Long> getScheduledMap(int dim) {
		return scheduledTimes.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
	}
	private static boolean isTurfBlock(IBlockState state) {
		if (state.getBlock() == Blocks.GRASS) {
			return true;
		}
		if (state.getBlock() == Blocks.DIRT) {
			return state.getValue(BlockDirt.VARIANT) == BlockDirt.DirtType.PODZOL;
		}
		return false;
	}
	private static boolean isReplaceableGrassAbove(IBlockState state) {
		return (state.getBlock() == Blocks.AIR || (state.getBlock() == Blocks.TALLGRASS) && (state.getValue(BlockTallGrass.TYPE) == BlockTallGrass.EnumType.GRASS));
	}
	private static boolean isFoliage(IBlockState state) {
		return state.getMaterial() == Material.LEAVES;
	}

	private static IBlockState above_state;
	private static IBlockState state;
	private static BlockPos findGrassSurface(World world, int x, int z) {
		int chunkX = x >> 4;
		int chunkZ = z >> 4;
		Chunk chunk = world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
		int localX = x & 15;
		int localZ = z & 15;

		/*
		* getHeightValue() возвращает Y сразу над верхним блоком согласно heightmap чанка.
		*/
		int y = chunk.getHeightValue(localX, localZ) - 1;
		if (y < 1) return null;

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);

		/*
		* Пропускаем неполные блоки над поверхностью:
		* снег, растения и прочие блоки с нулевой прозрачностью для heightmap.
		*/
		while (y >= 1) {
			pos.setY(y);
			state = world.getBlockState(pos);

			if (isTurfBlock(state)) {
				BlockPos candidate = pos.toImmutable();
				/*
				* Проверяем именно блок над дёрном.
				* Для открытой поверхности там должен быть SkyLight 15.
				*/
				BlockPos above = candidate.up();
				above_state = world.getBlockState(above);
				if (isReplaceableGrassAbove(above_state) && world.getLightFor(EnumSkyBlock.SKY, above) > 0 && !( above_state.getMaterial().isLiquid() )) {
					return candidate;
				}
				return null;
			}

			/*
			* Листву можно пропускать и продолжать сканирование вниз.
			*/
			if (isFoliage(state)) {
				y--;
				continue;
			}
			/*
			* Если верхний блок является полноценным непрозрачным блоком,
			* значит доступной поверхности дёрна в этом столбце нет.
			*/
			if (state.isFullCube() || state.getLightOpacity(world, pos) > 0) {
				return null;
			}
			y--;
		}
		return null;
	}



    // -------- Chunk load/unload events --------
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
    	if (!GG_ON)  return;
        World world = event.getWorld();
		if (/*world == null || */world.isRemote)  return;
        if (world.provider.getDimensionType() != DimensionType.OVERWORLD) return;

        Chunk chunk = event.getChunk();
        long key = ChunkPos.asLong(chunk.x, chunk.z);
        int dim = world.provider.getDimension();

        // Schedule first check with random delay
		long currentTick = world.getTotalWorldTime();
		long delay = MIN_DELAY_TICKS + ThreadLocalRandom.current().nextInt(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1);

		long scheduled = currentTick + delay;
		getScheduledMap(dim).put(key, scheduled);
		getLoadedSet(dim).add(key);
		getQueue(dim).add(new ScheduledChunk(key, scheduled)); // последним!
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
    	if (!GG_ON)  return;
        World world = event.getWorld();
		if (/*world == null || */world.isRemote)  return;
        if (world.provider.getDimensionType() != DimensionType.OVERWORLD) return;

        Chunk chunk = event.getChunk();

		long key = ChunkPos.asLong(chunk.x, chunk.z);
		int dim = world.provider.getDimension();
		getLoadedSet(dim).remove(key);          // 1) снимаем флаг загрузки
		Long old = getScheduledMap(dim).remove(key); // 2) убираем расписание
		if (old != null) {
			getQueue(dim).remove(new ScheduledChunk(key, old)); // 3) убираем из очереди
		}
        // The queue entry will be ignored during processing if not found in loaded set
    }

    // -------- World tick processing --------
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
    	if (!GG_ON)  return;
        if (event.phase != TickEvent.Phase.END)  return;
        World world = event.world;
        if (/*world == null || */world.isRemote)  return; // server only
        int dim = world.provider.getDimension();
        if (world.provider.getDimensionType() != DimensionType.OVERWORLD) return;

		long currentTick = world.getTotalWorldTime();
		int processed = 0;

		ConcurrentSkipListSet<ScheduledChunk> queue = getQueue(dim);
		Map<Long,Long> times = getScheduledMap(dim);
		Set<Long> loaded = getLoadedSet(dim);

		while (processed < MAX_OPER_PER_TICK) {
			ScheduledChunk first = queue.first();               // атомарно
			if (first == null || first.scheduledTime > currentTick) break;

			// remove(first) вернёт false, если кто-то другой уже это сделал
			if (!queue.remove(first)) continue;

			Long actualTime = times.get(first.chunkKey);
			if (actualTime == null || actualTime.longValue() != first.scheduledTime) continue;
			if (!loaded.contains(first.chunkKey)) continue;

			int cx = (int)(first.chunkKey & 0xFFFFFFFFL);
			int cz = (int)((first.chunkKey >>> 32) & 0xFFFFFFFFL);

			Chunk chunk = world.getChunkProvider().getLoadedChunk(cx, cz); // НЕ загружаем насильно
			if (chunk == null || !chunk.isLoaded()) continue;

			performGrowth(world, chunk);   // строго серверный поток

			long delay = MIN_DELAY_TICKS + ThreadLocalRandom.current().nextInt(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1);
			long newScheduled = currentTick + delay;
			// Чанк мог выгрузиться, пока выполнялся performGrowth()
			if (!loaded.contains(first.chunkKey)) {
				times.remove(first.chunkKey);
				continue;
			}
			times.put(first.chunkKey, newScheduled);
			queue.add(new ScheduledChunk(first.chunkKey, newScheduled));
			processed++;
		}
    }


	private static final IBlockState TALL_GRASS = Blocks.TALLGRASS.getDefaultState()
			.withProperty(BlockTallGrass.TYPE, BlockTallGrass.EnumType.GRASS);
	private static final IBlockState DOUBLE_GRASS_LOWER = Blocks.DOUBLE_PLANT.getDefaultState()
			.withProperty(BlockDoublePlant.VARIANT, EnumPlantType.GRASS)
			.withProperty(BlockDoublePlant.HALF, EnumBlockHalf.LOWER);
	private static final IBlockState DOUBLE_GRASS_UPPER = Blocks.DOUBLE_PLANT.getDefaultState()
			.withProperty(BlockDoublePlant.VARIANT, EnumPlantType.GRASS)
			.withProperty(BlockDoublePlant.HALF, EnumBlockHalf.UPPER);


	private static final int[] OFFSET_X = {4, 4, -4, -4};
	private static final int[] OFFSET_Z = {4, -4, 4, -4};



    // -------- Main growth algorithm --------
    private static void performGrowth(World world, Chunk chunk) {
	    int baseX = chunk.x * 16;
	    int baseZ = chunk.z * 16;
	    ThreadLocalRandom rnd = ThreadLocalRandom.current();


	    int x = baseX + rnd.nextInt(16);
	    int z = baseZ + rnd.nextInt(16);

	    //int[] ox = {4, 4, -4, -4};
	    //int[] oz = {4, -4, 4, -4};
	    BlockPos.MutableBlockPos mcheck = new BlockPos.MutableBlockPos();
	    for (int i = 0; i < 4; i++) {
	        mcheck.setPos(x + OFFSET_X[i], 64, z + OFFSET_Z[i]);
	        if (!world.isBlockLoaded(mcheck)) return;
	    }

	    //IBlockState state;

		BlockPos grassPos = findGrassSurface(world, x, z);
	    if (grassPos == null) return; // это важно, так как grassPos бывает null 

		// Check biome before any block scanning (must be allowed)
		Biome biome = world.getBiome(grassPos);
		if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.SNOWY)) {
			return;
		}


		Block block;

		// ---- Подсчёт травы в области 5x3x5 ----
		int turfCount = 0;
		int tallCount = 0;
		int doubleCount = 0;
		int tripleCount = 0;
		BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					check.setPos(grassPos.getX() + dx, grassPos.getY() + dy, grassPos.getZ() + dz);

					state = world.getBlockState(check);
					block = state.getBlock();

					if (isTurfBlock(state)) {
						turfCount++;
					}
					// Обычная высокая трава, но не папоротник и не другой вариант
					if (block == Blocks.TALLGRASS && state.getValue(BlockTallGrass.TYPE) == BlockTallGrass.EnumType.GRASS) {
						tallCount++;
					}
					// Нижняя часть ванильной травы высотой в два блока
					else if (block == Blocks.DOUBLE_PLANT && state.getValue(BlockDoublePlant.VARIANT) == EnumPlantType.GRASS && state.getValue(BlockDoublePlant.HALF) == EnumBlockHalf.LOWER) {
						doubleCount++;
					}
					// Одна гигантская трава считается только по нижней части
					else if (block == BlockGiantGrass.block && state.getValue(BlockGiantGrass.BlockCustom.PART) == BlockGiantGrass.BlockCustom.Part.LOWER) {
						tripleCount++;
					}
				}
			}
		}
		if (turfCount == 0) return;
		if (tallCount == 0 && doubleCount == 0 && tripleCount == 0) return;
	    // Установить лимит каждого вида травы
		if (tallCount >= 6 && doubleCount >= 16 && tripleCount >= 3) return;
		int Sum = tallCount + doubleCount + tripleCount;
		// Чтобы трава могла расти на уже заросшей территории до нужного лимита каждого вида

		// x1 = 24%		-> 6
		// x2 = 64%		-> 16
		// x3 = 12%		-> 3

		// ---- Попытка поставить обычную траву ----
		if ((tallCount > 0 || doubleCount > 0 || tripleCount > 0) && tallCount < GRASS_DENSITY && Sum < 25) {
			BlockPos above = grassPos.up();
			if (world.isAirBlock(above)) {
				world.setBlockState(above, TALL_GRASS, 2);
				return;
			}
		}

		// ---- Попытка поставить высокую траву ----
		boolean doubleGrassQuotaAvailable = doubleCount < TALL_GRASS_DENSITY && (int) (doubleCount * 1.75) < tallCount;
		if ((tallCount >= GRASS_THRESHOLD  || doubleCount >= 4 || tallCount == GRASS_DENSITY) && doubleGrassQuotaAvailable) {
			BlockPos above1 = grassPos.up();
			BlockPos above2 = grassPos.up(2);
			if ((world.isAirBlock(above1) || world.getBlockState(above1).getBlock() == Blocks.TALLGRASS) && world.isAirBlock(above2)) {
				IBlockState lowerState = DOUBLE_GRASS_LOWER;
				IBlockState upperState = DOUBLE_GRASS_UPPER;
				world.setBlockState(above1, lowerState, 2);
				world.setBlockState(above2, upperState, 2);
				return; // успешно поставили - выходим
			}
		}

		// Попытка поставить Гигантскую траву
		if ((doubleCount >= TALL_GRASS_THRESHOLD || doubleCount == TALL_GRASS_DENSITY) && tripleCount < GIANT_GRASS_DENSITY) {
			BlockPos lowerPos = grassPos.up();

			BlockGiantGrass.BlockCustom giantGrass = (BlockGiantGrass.BlockCustom) BlockGiantGrass.block;
			if (giantGrass.placeGiantGrass(world, lowerPos)) { return; }
		}
	}

    // -------- Biome whitelist check (uses your ModVariables.GGAllowed) --------
	//private static boolean isBiomeInBlacklist(Biome biome) {
	//    int id = Biome.REGISTRY.getIDForObject(biome);
	//    return id >= 0 && BIOME_BLACKLIST.get(id);
	//}
    // -------- Cleanup on world load (avoid stale data across sessions) --------
	@SubscribeEvent
	public void onWorldLoad(WorldEvent.Load event) {
		if (!GG_ON)  return;
		World world = event.getWorld();
		if (world.isRemote)  return;
		if (world.provider.getDimensionType() != DimensionType.OVERWORLD)  return;

		int dim = world.provider.getDimension();

		ConcurrentSkipListSet<ScheduledChunk> queue = queues.get(dim);
		if (queue != null) queue.clear();

		Set<Long> loaded = loadedChunks.get(dim);
		if (loaded != null) loaded.clear();

		Map<Long, Long> times = scheduledTimes.get(dim);
		if (times != null) times.clear();
	}
}
