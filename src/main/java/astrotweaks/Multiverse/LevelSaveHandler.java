package astrotweaks.Multiverse;

import net.minecraft.util.datafix.DataFixer;
import net.minecraft.util.datafix.DataFixesManager;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.chunk.storage.AnvilSaveHandler;

import java.io.File;

/**
 * Anvil save handler rooted at the level folder (MULTIVERSE/&lt;name&gt;).
 *
 * <p>Like every other save in the game, chunks are laid out as:</p>
 * <ul>
 *     <li>overworld &rarr; &lt;name&gt;/region</li>
 *     <li>nether &rarr; &lt;name&gt;/DIM-1/region</li>
 *     <li>end &rarr; &lt;name&gt;/DIM1/region</li>
 * </ul>
 * and level.dat / data / playerdata live at the folder root.
 *
 * <p>Session lock: {@link net.minecraft.world.storage.SaveHandler} writes a
 * timestamp into session.lock at construction and {@link #checkSessionLock()}
 * compares it on every chunk save. All three dimensions of a level share the
 * same folder (region / DIM-1 / DIM1 subfolders), so constructing the overworld
 * while the nether is still loaded rewrites the shared session.lock and would
 * make the already-loaded nether world throw
 * "The save is being accessed from another location" on its very next save. The
 * lock is only a cross-access guard for vanilla single-folder saves, so we skip
 * the comparison (the per-level folders already keep worlds separate).</p>
 */
public class LevelSaveHandler extends AnvilSaveHandler {

    // DataFixer immutable после построения; lazy-таблица в DataFixerUpper
    // инициализируется потокобезопасно (volatile). Один инстанс на JVM достаточно.
    private static final DataFixer SHARED_FIXER = DataFixesManager.createFixer();

    public LevelSaveHandler(File levelFolder) {
        super(levelFolder.getParentFile(), levelFolder.getName(), true, SHARED_FIXER);
    }

    @Override public void checkSessionLock() throws MinecraftException {
        // No-op: see class javadoc. Sibling dimensions of the same level rewrite
        // the shared session.lock timestamp; the timestamp check is meaningless
        // for roots shared across the level's three dimensions.
    }
}
