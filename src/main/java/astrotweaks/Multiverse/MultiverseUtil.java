package astrotweaks.Multiverse;

import astrotweaks.world.DepthsDim;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class MultiverseUtil {

	// Относительные ID измерения (введённые игроком в GUI ARK/TDARK)
	public static final int REL_OVERWORLD = 0;
	public static final int REL_NETHER = -1;
	public static final int REL_END = 1;
	public static final int REL_DEPTHS = DepthsDim.DIMID;

	/** Sea-level stand height used for ocean spawns (above the water). */
	private static final int OCEAN_SPAWN_Y = 64;

	private static final ThreadLocal<BlockPos.MutableBlockPos> TL_MPOS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

	private static BlockPos.MutableBlockPos mpos() {
		return TL_MPOS.get();
	}

	private MultiverseUtil() {}

	/**
	 * Переводит относительный ID измерения из GUI в абсолютный.
	 *
	 * Для якоря B (baseId мультивселенной) используется единое сопоставление:
	 *   0      -> B      (overworld)
	 *   -1     -> B + 1  (nether)
	 *   1      -> B + 2  (end)
	 *   -6000  -> B + 3  (depths)
	 *
	 * Для ванильного мира (anchor = 0) сопоставление совпадает со стандартным:
	 *   -1 -> -1, 1 -> 1, -6000 -> DepthsDim.DIMID
	 *
	 * Для глобального измерения (-1000000) относительные ID не переводятся,
	 * чтобы нельзя было "проникнуть" в чужую вселенную (его ID вернётся как есть).
	 */
	public static int resolveRelativeDim(int anchorBase, int relative) {
		if (anchorBase == MultiverseDims.GLOBAL_DIM) return relative;
		switch (relative) {
			case REL_OVERWORLD: return anchorBase == 0 ? 0 : anchorBase;
			case REL_NETHER: return anchorBase == 0 ? -1 : anchorBase + 1;
			case REL_END: return anchorBase == 0 ? 1 : anchorBase + 2;
			case REL_DEPTHS: return anchorBase == 0 ? DepthsDim.DIMID : anchorBase + 3;
			default: return relative;
		}
	}

	/**
	 * Якорь (baseId) вселенной, которой принадлежит измерение.
	 * Для ванильных измерений и Depths возвращает 0, для глобального - '-1000000'.
	 */
	public static int anchorBaseOf(int dimensionId) {
		if (dimensionId == MultiverseDims.GLOBAL_DIM) return MultiverseDims.GLOBAL_DIM;
		LevelData data = LevelManager.getInstance().getLevelByDimensionId(dimensionId);
		return data != null ? data.baseId : 0;
	}

	/**
	 * Одна ли вселенная у двух измерений.
	 * Глобальное (-1000000) считается отдельной вселенной без доступа к дополнительным измерениям.
	 * Два MV-измерения в одной вселенной iff принадлежат одному LevelData.
	 * Если хотя бы одно измерение из MV, а второе ванильное - разные вселенные.
	 */
	public static boolean isSameUniverse(int a, int b) {
		if (a == b) return true;
		if (a == MultiverseDims.GLOBAL_DIM || b == MultiverseDims.GLOBAL_DIM) return false;
		LevelManager lm = LevelManager.getInstance();
		LevelData da = lm.getLevelByDimensionId(a);
		LevelData db = lm.getLevelByDimensionId(b);
		if (da != null || db != null) {
			return da != null && da == db;
		}
		return true;
	}

	// ------------------------------------------------------------------ safe world spawn

	/**
	 * True when a player can actually stand at {@code pos}: the cell below is a liquid
	 * or has a collision surface (standable ground), while the feet and head cells are
	 * neither liquid nor collidable — i.e. the position is never inside a block/liquid.
	 * Ocean fix: feet at {@link #OCEAN_SPAWN_Y}=64 over water at y=62 has an air gap at 63,
	 * so we also accept liquid/solid 2 blocks below when feet is exactly 64.
	 */
	public static boolean isColumnFree(World world, BlockPos pos) {
		IBlockState below = world.getBlockState(pos.down());
		boolean standable = isStandableGround(below, world, pos.down());
		if (!standable && pos.getY() == OCEAN_SPAWN_Y) {
			BlockPos below2Pos = pos.down(2);
			IBlockState below2 = world.getBlockState(below2Pos);
			if (isStandableGround(below2, world, below2Pos)) {
				standable = true;
			}
		}
		if (!standable) return false;
		for (int dy = 0; dy <= 1; dy++) {
			IBlockState s = world.getBlockState(pos.up(dy));
			if (!isPassable(s, world, pos.up(dy))) return false;
		}
		return true;
	}

	/** True when an entity can occupy the cell (AIR или replaceable, не жидкость, без коллизии — нельзя задохнуться). */
	private static boolean isPassable(IBlockState state, World world, BlockPos pos) {
		if (state.getMaterial().isLiquid()) return false;
		// быстрый отсев твёрдых кубов без аллокации AABB
		if (state.getMaterial().isSolid() && state.isFullCube()) return false;
		// replaceable (трава, цветы) уже без коллизии, но проверяем AABB для гарантии
		return state.getCollisionBoundingBox(world, pos) == null;
	}

	/** True when an entity can stand on the cell (liquid surface или твёрдая поверхность). */
	private static boolean isStandableGround(IBlockState state, World world, BlockPos pos) {
		if (state.getMaterial().isLiquid()) return true;
		if (state.getMaterial().isSolid() && state.isFullCube()) return true;
		return state.getCollisionBoundingBox(world, pos) != null;
	}

	/**
	 * Computes the feet-Y of a safe world spawn at {@code (x, z)} of a surface world:
	 * <ul>
	 *   <li>the column's topmost solid-or-liquid block is a solid  &rarr; stand right above it;</li>
	 *   <li>it is a liquid (ocean) &rarr; stand at {@link #OCEAN_SPAWN_Y} (64), above the water;</li>
	 *   <li>the column is empty (void) &rarr; float at 64.</li>
	 * </ul>
	 * The result is then nudged upward while the feet/head cells are still inside a block
	 * so the spawn is guaranteed to be open air.
	 */
	public static int findSafeSpawnFeetY(World world, int x, int z) {
		int ground = findGroundBelow(world, x, z, world.getHeight() - 1);
		if (ground < 0) {
			return OCEAN_SPAWN_Y;
		}
		IBlockState surface = world.getBlockState(new BlockPos(x, ground, z));
		int feet = surface.getMaterial().isLiquid() ? Math.max(ground + 1, OCEAN_SPAWN_Y) : ground + 1;
		return clampToOpenColumn(world, x, z, feet);
	}

	/**
	 * Same as {@link #findSafeSpawnFeetY} but for nether-like surfaces: scans only
	 * {@code startY} and below (below the bedrock ceiling) and stands right above the
	 * first solid-or-liquid block found, so a lava floor never swallows the spawn.
	 */
	public static int findStandableFeetY(World world, int x, int z, int startY) {
		int ground = findGroundBelow(world, x, z, startY);
		if (ground < 0) {
			return OCEAN_SPAWN_Y;
		}
		return clampToOpenColumn(world, x, z, ground + 1);
	}

	/**
	 * Highest solid-or-liquid block at or below {@code startY} in the column, or -1
	 * when the whole column is open (void). Оптимизировано (#1): если чанк загружен — читаем напрямую из Chunk без hash-lookup World.
	 */
	public static int findGroundBelow(World world, int x, int z, int startY) {
		int top = Math.min(startY, world.getHeight() - 1);
		// пробуем взять уже загруженный чанк — без генерации
		net.minecraft.world.chunk.Chunk chunk = null;
		try {
			chunk = world.getChunkProvider().getLoadedChunk(x >> 4, z >> 4);
		} catch (Throwable ignored) {}
		if (chunk != null) {
			for (int y = top; y >= 0; y--) {
				IBlockState s = chunk.getBlockState(x & 15, y, z & 15);
				if (s.getMaterial().isSolid() || s.getMaterial().isLiquid()) return y;
			}
			return -1;
		}
		BlockPos.MutableBlockPos mpos = mpos();
		for (int y = top; y >= 0; y--) {
			IBlockState s = world.getBlockState(mpos.setPos(x, y, z));
			if (s.getMaterial().isSolid() || s.getMaterial().isLiquid()) return y;
		}
		return -1;
	}

	/** Walks {@code feet} upward until the feet/head cells are open air (гарантированно не в блоке). Переиспользует ThreadLocal mpos (#9). */
	private static int clampToOpenColumn(World world, int x, int z, int feet) {
		int max = Math.max(1, world.getHeight() - 2);
		BlockPos.MutableBlockPos mpos = mpos();
		int y = Math.min(Math.max(feet, 1), max);
		while (y < max) {
			if (isColumnFree(world, mpos.setPos(x, y, z))) return y;
			y++;
		}
		return max;
	}

	/**
	 * Performs the {@link #findSafeSpawnFeetY}/{@link #findStandableFeetY} search and
	 * returns the full feet position. When {@code netherSurfaceScan} is true the scan is
	 * limited to below the nether ceiling (starts at Y=100).
	 */
	public static BlockPos safeWorldSpawn(World world, int x, int z, boolean netherSurfaceScan) {
		int feetY = netherSurfaceScan
				? findStandableFeetY(world, x, z, 100)
				: findSafeSpawnFeetY(world, x, z);
		return new BlockPos(x, feetY, z);
	}
}
