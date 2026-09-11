package astrotweaks.Multiverse;

import astrotweaks.world.DepthsDim;

public final class MultiverseUtil {

	// Относительные ID измерения (введённые игроком в GUI ARK/TDARK)
	public static final int REL_OVERWORLD = 0;
	public static final int REL_NETHER = -1;
	public static final int REL_END = 1;
	public static final int REL_DEPTHS = DepthsDim.DIMID;

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
}
