package astrotweaks.world.NaturesPower;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStone;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.BlockWall;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ThreadLocalRandom;

import astrotweaks.ModVariables;
import astrotweaks.block.*;



public class BlockMossing {

	private static int MIN_DELAY_TICKS;
	private static int MAX_DELAY_TICKS;
	private static int MAX_OPER_PER_TICK;
	private static boolean BM_ON;

	public static void updVars() {
		MIN_DELAY_TICKS = ModVariables.BM_MIN_DELAY_TICK;
		MAX_DELAY_TICKS = ModVariables.BM_MAX_DELAY_TICK;
		MAX_OPER_PER_TICK = ModVariables.BM_MAX_OPER_PER_TICK;
		BM_ON = ModVariables.BM_ENABLED;

		if (MIN_DELAY_TICKS > MAX_DELAY_TICKS) MIN_DELAY_TICKS = MAX_DELAY_TICKS - 1;
		if (MIN_DELAY_TICKS < 1) MIN_DELAY_TICKS = 1;
		if (MAX_DELAY_TICKS < 1) MAX_DELAY_TICKS = 1;
	}

	// ==========================================================
	//  МАППИНГ ТРАНСФОРМАЦИЙ
	// ==========================================================

	private static final Map<IBlockState, IBlockState> BC_TRANSFORM = new HashMap<>();
	/** Блоки без "variant" -> результат */
	private static final Map<Block, IBlockState> BC_NOVAR = new HashMap<>();
	/** Блоки с "variant" -> (значение variant -> результат) */
	private static final Map<Block, Map<Comparable<?>, IBlockState>> BC_VAR = new HashMap<>();

	static {
		// ---- Cobblestone -> Mossy Cobblestone ----
		register(Blocks.COBBLESTONE.getDefaultState(), Blocks.MOSSY_COBBLESTONE.getDefaultState());
		register(Blocks.STONE.getDefaultState() .withProperty(BlockStone.VARIANT, BlockStone.EnumType.STONE), ATBlocks.MOSSY_STONE.getDefaultState());

		// ---- Stone Bricks (все немаховые варианты) -> Mossy ----
		IBlockState mossyBrick = Blocks.STONEBRICK.getDefaultState() .withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.MOSSY);
		register(Blocks.STONEBRICK.getDefaultState() .withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.DEFAULT), mossyBrick);
		register(Blocks.STONEBRICK.getDefaultState() .withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CRACKED), ATBlocks.MOSSY_CRACKED_STONEBRICK.getDefaultState());
		register(Blocks.STONEBRICK.getDefaultState() .withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CHISELED), ATBlocks.MOSSY_CARVED_STONEBRICK.getDefaultState());

		// ---- Cobblestone Wall -> Mossy Cobblestone Wall ----
		IBlockState mossyWall = Blocks.COBBLESTONE_WALL.getDefaultState() .withProperty(BlockWall.VARIANT, BlockWall.EnumType.MOSSY);
		register(Blocks.COBBLESTONE_WALL.getDefaultState() .withProperty(BlockWall.VARIANT, BlockWall.EnumType.NORMAL), mossyWall);

		// others

		register(Blocks.DIRT.getDefaultState(), Blocks.GRASS.getDefaultState()); // трава из земли. Почему нет?
		register(Blocks.GRASS_PATH.getDefaultState(), Blocks.DIRT.getDefaultState()); // зарастание тропинок

		register(Blocks.STONE_SLAB.getStateFromMeta(3), B_MossyCobblestoneSlab.block.getDefaultState());
		register(Blocks.STONE_STAIRS.getDefaultState(), B_MossyCobblestoneStairs.block.getDefaultState());

		register(Blocks.STONE_SLAB.getStateFromMeta(5), B_MossyStonebrickSlab.block.getDefaultState());
		register(Blocks.STONE_BRICK_STAIRS.getDefaultState(), B_MossyStonebrickStairs.block.getDefaultState());




	}

	private static void register(IBlockState from, IBlockState to) {
		BC_TRANSFORM.put(from, to);
		Block b = from.getBlock();
		IProperty<?> var = findVariantProperty(b);
		if (var == null) {
			BC_NOVAR.put(b, to);
		} else {
			BC_VAR.computeIfAbsent(b, k -> new HashMap<>())
			      .put((Comparable<?>) from.getValue(var), to);
		}
	}

	private static IProperty<?> findVariantProperty(Block b) {
		for (IProperty<?> p : b.getDefaultState().getPropertyKeys()) {
			if ("variant".equals(p.getName())) return p;
		}
		return null;
	}

	private static IBlockState getTransformResult(IBlockState source) {
		Block b = source.getBlock();
		Map<Comparable<?>, IBlockState> byVar = BC_VAR.get(b);
		if (byVar != null) {
			IProperty<?> var = findVariantProperty(b);
			if (var != null) {
				return byVar.get(source.getValue(var));
			}
		}
		return BC_NOVAR.get(b);
	}

	/** Переносит совместимые свойства источника в результат (напр., NORTH/UP у стен). */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static IBlockState applyTransform(IBlockState source, IBlockState result) {
		for (IProperty<?> prop : source.getPropertyKeys()) {
			if (result.getPropertyKeys().contains(prop)) {
				try {
					result = result.withProperty((IProperty) prop, (Comparable) source.getValue(prop));
				} catch (Exception ignored) { /* несовместимое значение — пропускаем */ }
			}
		}
		return result;
	}

	// ==========================================================
	//  КАТАЛИЗАТОРЫ
	// ==========================================================

	private static boolean isCatalyst(IBlockState state) {
		Material m = state.getMaterial();
		if (m == Material.WATER)  return true;
		if (m == Material.GRASS)  return true;
		if (m == Material.PLANTS) return true;
		//if (m == Material.LEAVES) return true;
		if (m == Material.VINE) return true;
		return false;
	}

	// ==========================================================
	//  ОЧЕРЕДИ ПЛАНИРОВЩИКА (по образцу GrassGrowth)
	// ==========================================================

	private static final Map<Integer, ConcurrentSkipListSet<ScheduledChunk>> queues = new ConcurrentHashMap<>();
	private static final Map<Integer, java.util.Set<Long>> loadedChunks = new ConcurrentHashMap<>();
	private static final Map<Integer, Map<Long,Long>> scheduledTimes = new ConcurrentHashMap<>();

	private static class ScheduledChunk implements Comparable<ScheduledChunk> {
		final long chunkKey;
		final long scheduledTime;

		ScheduledChunk(long key, long time) { chunkKey = key; scheduledTime = time; }

		@Override
		public int compareTo(ScheduledChunk o) {
			int c = Long.compare(scheduledTime, o.scheduledTime);
			return c != 0 ? c : Long.compare(chunkKey, o.chunkKey);
		}
		@Override public boolean equals(Object o) {
			return o instanceof ScheduledChunk && ((ScheduledChunk)o).chunkKey == chunkKey && ((ScheduledChunk)o).scheduledTime == scheduledTime;
		}
		@Override public int hashCode() { return Long.hashCode(chunkKey * 31 + scheduledTime); }
	}

	private static final ConcurrentSkipListSet<ScheduledChunk> getQueue(int dim) {
		return queues.computeIfAbsent(dim, k -> new ConcurrentSkipListSet<>());
	}
	private static final java.util.Set<Long> getLoadedSet(int dim) {
		return loadedChunks.computeIfAbsent(dim, k -> ConcurrentHashMap.newKeySet());
	}
	private static final Map<Long,Long> getScheduledMap(int dim) {
		return scheduledTimes.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
	}

	// ==========================================================
	//  СОБЫТИЯ ЧАНКОВ
	// ==========================================================

	@SubscribeEvent
	public void onChunkLoad(ChunkEvent.Load event) {
		if (!BM_ON)  return;
		World world = event.getWorld();
		if (world.isRemote)  return;
		if (world.provider.getDimensionType() != DimensionType.OVERWORLD)  return;

		Chunk chunk = event.getChunk();
		long key = ChunkPos.asLong(chunk.x, chunk.z);
		int dim = world.provider.getDimension();

		long currentTick = world.getTotalWorldTime();
		long delay = MIN_DELAY_TICKS + ThreadLocalRandom.current().nextInt(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1);
		long scheduled = currentTick + delay;

		getScheduledMap(dim).put(key, scheduled);
		getLoadedSet(dim).add(key);
		getQueue(dim).add(new ScheduledChunk(key, scheduled));
	}

	@SubscribeEvent
	public void onChunkUnload(ChunkEvent.Unload event) {
		if (!BM_ON)  return;
		World world = event.getWorld();
		if (world.isRemote) return;
		if (world.provider.getDimensionType() != DimensionType.OVERWORLD) return;

		Chunk chunk = event.getChunk();
		long key = ChunkPos.asLong(chunk.x, chunk.z);
		int dim = world.provider.getDimension();

		getLoadedSet(dim).remove(key);
		Long old = getScheduledMap(dim).remove(key);
		if (old != null) {
			getQueue(dim).remove(new ScheduledChunk(key, old));
		}
	}

	@SubscribeEvent
	public void onWorldLoad(WorldEvent.Load event) {
		if (!BM_ON) return;
		World world = event.getWorld();
		if (world.isRemote) return;
		if (world.provider.getDimensionType() != DimensionType.OVERWORLD) return;

		int dim = world.provider.getDimension();

		ConcurrentSkipListSet<ScheduledChunk> queue = queues.get(dim);
		if (queue != null) queue.clear();
		java.util.Set<Long> loaded = loadedChunks.get(dim);
		if (loaded != null) loaded.clear();
		Map<Long, Long> times = scheduledTimes.get(dim);
		if (times != null) times.clear();
	}

	// ==========================================================
	//  ТИК МИРА
	// ==========================================================

	@SubscribeEvent
	public void onWorldTick(TickEvent.WorldTickEvent event) {
		if (!BM_ON) return;
		if (event.phase != TickEvent.Phase.END) return;
		World world = event.world;
		if (world.isRemote) return;
		if (world.provider.getDimensionType() != DimensionType.OVERWORLD) return;

		int dim = world.provider.getDimension();
		long currentTick = world.getTotalWorldTime();
		int processed = 0;

		ConcurrentSkipListSet<ScheduledChunk> queue = getQueue(dim);
		Map<Long,Long> times = getScheduledMap(dim);
		java.util.Set<Long> loaded = getLoadedSet(dim);

		while (processed < MAX_OPER_PER_TICK) {
			if (queue.isEmpty()) break;

			ScheduledChunk first = queue.first();
			if (first == null || first.scheduledTime > currentTick) break;

			if (!queue.remove(first)) continue;

			Long actualTime = times.get(first.chunkKey);
			if (actualTime == null || actualTime.longValue() != first.scheduledTime) continue;
			if (!loaded.contains(first.chunkKey)) continue;

			int cx = (int)(first.chunkKey & 0xFFFFFFFFL);
			int cz = (int)((first.chunkKey >>> 32) & 0xFFFFFFFFL);

			Chunk chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
			if (chunk == null || !chunk.isLoaded()) continue;

			performTransform(world, chunk);

			long delay = MIN_DELAY_TICKS + ThreadLocalRandom.current().nextInt(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1);
			long newScheduled = currentTick + delay;

			if (!loaded.contains(first.chunkKey)) {
				times.remove(first.chunkKey);
				continue;
			}
			times.put(first.chunkKey, newScheduled);
			queue.add(new ScheduledChunk(first.chunkKey, newScheduled));
			processed++;
		}
	}

	// ==========================================================
	//  ОСНОВНОЙ АЛГОРИТМ
	// ==========================================================

	private static final int[] OFFSET_X = {4, 4, -4, -4};
	private static final int[] OFFSET_Z = {4, -4, 4, -4};

	private static void performTransform(World world, Chunk chunk) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int baseX = chunk.x * 16;
		int baseZ = chunk.z * 16;

		int x = baseX + rnd.nextInt(16);
		int z = baseZ + rnd.nextInt(16);
		int y = 2 + rnd.nextInt(253); // 2..254

		// ---- Проверяем, что угловые точки чанка на этой высоте загружены ----
		BlockPos.MutableBlockPos mcheck = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 4; i++) {
			mcheck.setPos(x + OFFSET_X[i], y, z + OFFSET_Z[i]);
			if (!world.isBlockLoaded(mcheck)) return;
		}

		BlockPos pos = new BlockPos(x, y, z);

		// ---- Проверка целевого блока ----
		IBlockState source = world.getBlockState(pos);
		IBlockState target = getTransformResult(source);
		if (target == null) return;

		// ---- Биом-фильтр: SANDY и SNOWY исключаем ----
		Biome biome = world.getBiome(pos);
		if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.SNOWY)) return;
		if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.SANDY)) return;

		// ---- Проверка: хотя бы с одной стороны AIR или катализатор ----
		boolean hasOpenAdjacent = false;
		for (EnumFacing f : EnumFacing.VALUES) {
			BlockPos np = pos.offset(f);
			if (np.getY() < 0 || np.getY() > 255) continue;
			if (!world.isBlockLoaded(np)) {
				// Незагруженный сосед считаем закрытым, чтобы не вызывать генерацию чанка
				// (вряд ли такое произойдёт)
				hasOpenAdjacent = false;
				break;
			}
			IBlockState n = world.getBlockState(np);
			if (n.getBlock() == Blocks.AIR || isCatalyst(n)) {
				hasOpenAdjacent = true;
				break;
			}
		}
		if (!hasOpenAdjacent) return;

		// ---- Скан куба 5x5x5 (125 блоков) на наличие катализатора ----
		boolean hasCatalyst = false;
		outer:
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) continue;
					int yy = y + dy;
					if (yy < 0 || yy > 255) continue;
					mcheck.setPos(x + dx, yy, z + dz);
					if (!world.isBlockLoaded(mcheck)) continue;
					if (isCatalyst(world.getBlockState(mcheck))) {
						hasCatalyst = true;
						break outer;
					}
				}
			}
		}
		if (!hasCatalyst) return;

		// ---- Трансформация ----
		IBlockState result = applyTransform(source, target);
		world.setBlockState(pos, result, 2);
	}
}
