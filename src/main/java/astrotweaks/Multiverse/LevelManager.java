package astrotweaks.Multiverse;

import astrotweaks.AstrotweaksMod;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.ServerWorldEventHandler;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Manages multiverse levels: the name&rarr;iteration registry persisted under the
 * current save's MULTIVERSE/registry.dat, dimension registration and WorldServer
 * creation, player locate persistence and dimension unloading.
 *
 * <p>Every save folder (the overworld that is currently loaded) owns its own
 * MULTIVERSE folder + registry + per-dimension level folders. The registry is
 * additionally bound to the save by a marker file (universe.dat). The shared
 * global dimension (-1000000) lives in MULTIVERSE_GLOBAL at the server root, outside
 * any single save.</p>
 *
 * <p>A level occupies 4 consecutive dimension ids (base .. base+3) and is saved
 * into its own folder. Worlds are constructed manually and then registered with
 * {@link DimensionManager#setWorld(int, WorldServer, MinecraftServer)} because Forge's
 * initDimension only knows how to build a WorldServerMulti sharing the main overworld's
 * save handler.</p>
 *
 * <p>Invariants:
 * <ol>
 *   <li>Every LevelData has unique (name, number, uid, baseId).</li>
 *   <li>All 4 dimension ids (baseId..baseId+3) belong to exactly one LevelData.</li>
 *   <li>levels.size() == universeByNumber.size() == number of registered universes.</li>
 *   <li>playerEntries.size() &le; number of saved players who visited MV dimensions.</li>
 *   <li>Every level folder name starts with "MV_" (e.g. "MV_5" or "MV_myWorld"),
 *       and `number` (the 1..MAX slot backing baseId/uid) is always unique.</li>
 * </ol>
 */
public class LevelManager {

    private static final int BASE_START = 10_000;
    private static final int STEP = 100;
    //private static final int BASE_MAX = 20_000;
    private static final int DIMS_PER_LEVEL = 4;
    private static final String LEVEL_PREFIX = "MV_";
    private static final String ERRORED_FOLDER_NAME = "MULTIVERSE_ERRORED";

    private static final Random RNG = new Random();

    private static volatile LevelManager INSTANCE;

    private final Map<String, LevelData> levels = new HashMap<>();
    private final Map<Integer, LevelData> universeByNumber = new HashMap<>();
    private final Map<Integer, LevelData> dimensionToLevel = new HashMap<>();
    private final Map<UUID, PlayerEntry> playerEntries = new HashMap<>();
    private final Map<String, Integer> uidToNumber = new HashMap<>();

    private MinecraftServer cachedServer;
    private File multiverseFolder;
    private File globalFolder;
    private File proxyFolder;
    private boolean registryLoaded;
    private long worldSeed;

    private int cachedMaxUniverses;

    private LevelManager() {}

    public static LevelManager getInstance() {
        LevelManager i = INSTANCE;
        if (i == null) {
            synchronized (LevelManager.class) {
                i = INSTANCE;
                if (i == null) {
                    INSTANCE = i = new LevelManager();
                }
            }
        }
        return i;
    }

    // ------------------------------------------------------------------ config

    private int maxUniverses() {
        return cachedMaxUniverses;
    }

    public int getMaxUniverses() {
        return cachedMaxUniverses;
    }

    private void loadConfig() {
        int m = astrotweaks.ModVariables.MULTIVERSE_MAX_UNIVERSES;
        cachedMaxUniverses = (m < 1) ? 1 : m;
    }

    // ------------------------------------------------------------------ lifecycle

    private boolean registryDirty;
    private void markRegistryDirty() { registryDirty = true; }
    public void flushRegistryIfDirty(MinecraftServer server) {
        if (registryDirty) {
            saveRegistry(server);
            registryDirty = false;
        }
    }

    private static void forEachDim(LevelData data, java.util.function.IntConsumer action) {
        for (int k = 0; k < DIMS_PER_LEVEL; k++) action.accept(data.baseId + k);
    }

    /**
     * (Re)initializes the manager when the save's overworld (dim 0) finishes loading.
     * Called from {@code WorldEvent.Load}.
     */
    public void onWorldLoaded(MinecraftServer server) {
        WorldServer world0 = server.getWorld(0);
        if (world0 == null) return;
        
        File mvFolder = new File(world0.getSaveHandler().getWorldDirectory(), "MULTIVERSE");
        if (isSameFolder(multiverseFolder, mvFolder)) {
            registerGlobalDimensionIfMissing();
            return;
        }
        if (multiverseFolder != null) {
            // Unload every old multiverse world from DimensionManager so the
            // next save does not inherit stale WorldServer instances or create
            // folders for dimensions that belong to the previous save.
            MinecraftServer oldServer = cachedServer != null ? cachedServer : server;
            for (LevelData data : levels.values()) {
                forEachDim(data, dim -> unloadWorldNow(oldServer, dim, true));
                // Unregister so DimensionManager.initDimensions() does not
                // recreate them as phantom worlds in the new save.
                unregisterLevelDimensions(oldServer, data);
            }
            if (MultiverseDims.isGlobalDimensionEnabled()) {
                unloadWorldNow(oldServer, MultiverseDims.GLOBAL_DIM, true);
                if (DimensionManager.isDimensionRegistered(MultiverseDims.GLOBAL_DIM)) {
                    DimensionManager.unregisterDimension(MultiverseDims.GLOBAL_DIM);
                }
            }
            saveAll(oldServer);
        }
        cachedServer = server;
        multiverseFolder = mvFolder;
        globalFolder = new File(resolveGameRoot(), "MULTIVERSE_GLOBAL");
        levels.clear();
        universeByNumber.clear();
        dimensionToLevel.clear();
        playerEntries.clear();
        uidToNumber.clear();
        registryLoaded = false;

        loadConfig();
        migrateLegacyRootFolder(server);

        if (!multiverseFolder.exists() && !multiverseFolder.mkdirs()) {
            throw new IllegalStateException("Cannot create MULTIVERSE folder: " + multiverseFolder);
        }
        if (MultiverseDims.isGlobalDimensionEnabled()) {
            if (!globalFolder.exists() && !globalFolder.mkdirs()) {
                throw new IllegalStateException("Cannot create MULTIVERSE_GLOBAL folder: " + globalFolder);
            }
            //System.out.println("[MULTIVERSE] Shared global dimension folder: " + globalFolder);
        }

        proxyFolder = new File(multiverseFolder, "MV_PROXY");
        if (!proxyFolder.exists() && !proxyFolder.mkdirs()) {
            System.err.println("[MULTIVERSE] Cannot create MV_PROXY folder: " + proxyFolder);
        }

        worldSeed = readWorldSeed(world0);
        ensureRegistry(server);
        writeBindingMarker(world0);
        registerGlobalDimensionIfMissing();
        registerProxyDimensionIfMissing();
        reconcileWithDisk(server);
        loadPlayerData();

        // Pre-load worlds that saved players are in.  Without this, vanilla's
        // login sequence creates a phantom WorldServerMulti (writing a junk
        // DIM<id>/ folder into the save root) BEFORE our scheduled restorePlayer
        // gets a chance to load the correct world.
        preloadPlayerDimensions(server);

        // Remove any leftover phantom DIM<id>/ folders in the save root that
        // previous sessions or the preload above may have left behind.
        cleanupPhantomDimFolders(world0);
    }

    private void ensureActive(MinecraftServer server) {
        if (multiverseFolder == null) {
            onWorldLoaded(server);
        }
    }

    private void registerGlobalDimensionIfMissing() {
        if (!MultiverseDims.isGlobalDimensionEnabled()) return;
        if (!DimensionManager.isDimensionRegistered(MultiverseDims.GLOBAL_DIM)) {
            MultiverseDims.registerGlobalDimension();
        }
    }

    private void registerProxyDimensionIfMissing() {
        if (!DimensionManager.isDimensionRegistered(MultiverseDims.PROXY_DIM)) {
            MultiverseDims.registerProxyDimension();
        }
    }

    /**
     * Loads (and registers) the exact dimension that each saved player occupies so that
     * vanilla's {@code PlayerList.initializeConnectionToPlayer} finds an already-loaded
     * WorldServer instead of creating a phantom one.
     */
    private void preloadPlayerDimensions(MinecraftServer server) {
        for (PlayerEntry entry : playerEntries.values()) {
            LevelData data = dimensionToLevel.get(entry.dimension);
            if (data == null) continue;
            LevelDimensionType type = data.typeOf(entry.dimension);
            if (type == null) continue;
            MultiverseDims.registerLevelDimensions(data.baseId);
            getOrCreateWorld(server, data, type);
        }
    }

    /**
     * Deletes phantom {@code DIM<id>/} folders that Forge may have created in the
     * save root when it auto-loaded one of our custom dimensions through the
     * default {@code WorldServerMulti} path.  Only removes folders whose id
     * belongs to a registered multiverse level or the global dimension.
     */
    private void cleanupPhantomDimFolders(WorldServer world0) {
        File saveRoot = world0.getSaveHandler().getWorldDirectory();
        if (saveRoot == null || !saveRoot.isDirectory()) return;

        java.util.Set<Integer> ownedIds = new java.util.HashSet<>();
        for (LevelData data : levels.values()) {
            forEachDim(data, ownedIds::add);
        }
        if (MultiverseDims.isGlobalDimensionEnabled()) {
            ownedIds.add(MultiverseDims.GLOBAL_DIM);
        }

        for (int dimId : ownedIds) {
            File dimFolder = new File(saveRoot, "DIM" + dimId);
            if (dimFolder.isDirectory()) {
                System.out.println("[MULTIVERSE] Cleaning up phantom DIM folder: " + dimFolder);
                deleteRecursively(dimFolder);
            }
        }
    }

    /**
     * Old builds kept MULTIVERSE at the server root; if the current save has never
     * seen one but a legacy root folder with a registry exists, move it in.
     */
    private void migrateLegacyRootFolder(MinecraftServer server) {
        File legacy = server.getFile("MULTIVERSE");
        if (!multiverseFolder.exists() && legacy.isDirectory() && new File(legacy, "registry.dat").isFile()) {
            if (legacy.renameTo(multiverseFolder)) {
                System.out.println("[MULTIVERSE] Migrated legacy MULTIVERSE folder to " + multiverseFolder);
            } else {
                System.err.println("[MULTIVERSE] Could not migrate legacy MULTIVERSE folder " + legacy);
            }
        }
    }

    private void writeBindingMarker(WorldServer world0) {
        NBTTagCompound root = new NBTTagCompound();
        root.setString("worldName", world0.getSaveHandler().getWorldDirectory().getName());
        root.setLong("worldSeed", world0.getWorldInfo().getSeed());
        writeNbt(new File(multiverseFolder, "universe.dat"), root);
    }

    /** worldSeed стабильно для всего сейва (из universe.dat, иначе из мира 0). */
    private long readWorldSeed(WorldServer world0) {
        NBTTagCompound root = readNbt(new File(multiverseFolder, "universe.dat"));
        if (root != null && root.hasKey("worldSeed")) {
            return root.getLong("worldSeed");
        }
        return world0.getWorldInfo().getSeed();
    }
    private static boolean isSameFolder(File a, File b) {
        if (a == null || b == null) {
            return false;
        }
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (IOException e) {
            return a.equals(b);
        }
    }

    /**
     * Resolves the folder that holds the shared MULTIVERSE_GLOBAL world. On the client
     * this is Minecraft.mcDataDir (the .minecraft folder); on a dedicated server it
     * falls back to the parent of the config folder (the server root). Never the save.
     */
    private static File resolveGameRoot() {
        if (FMLCommonHandler.instance().getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT
                && net.minecraft.client.Minecraft.getMinecraft() != null) {
            File mcDataDir = net.minecraft.client.Minecraft.getMinecraft().mcDataDir;
            if (mcDataDir != null) {
                return mcDataDir;
            }
        }
        File configDir = Loader.instance().getConfigDir();
        return configDir == null ? new File(".") : configDir.getParentFile();
    }

    /**
     * True for worlds saved through a {@link LevelSaveHandler} (our per-level folders
     * and the global world). Worlds owned by any other save handler - above all the
     * for-save WorldServerMulti Forge spins up via DimensionManager.initDimension that
     * reuses the OVERWORLD's session.lock and region folder - must never be saved:
     * doing so writes into the overworld and throws the session-lock MinecraftException.
     */
    private static boolean isOwnedWorld(WorldServer world) {
        return world.getSaveHandler() instanceof LevelSaveHandler;
    }

    // ------------------------------------------------------------------ registry

    private void ensureRegistry(MinecraftServer server) {
        if (registryLoaded) return;
        registryLoaded = true;

        File reg = new File(multiverseFolder, "registry.dat");
        if (!reg.isFile()) return;

        NBTTagCompound root = readNbt(reg);
        if (root == null) {
            System.err.println("[MULTIVERSE] registry.dat is corrupted or unreadable — starting with an empty registry. File: " + reg);
            return;
        }

        int max = maxUniverses();
        NBTTagList list = root.getTagList("levels", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            String name = entry.getString("name");
            int number = entry.hasKey("number") ? entry.getInteger("number") : numberForBase(entry.getInteger("baseId"));
            long seed = entry.getLong("seed");
            String uid = entry.getString("uid");

            // Числовые имена (MV_N) авторитетнее сохранённого number: слот всегда
            // определяется именем, даже если registry.dat повреждён.
            int nameNumber = parseNumber(name);
            if (nameNumber > 0) {
                number = nameNumber;
            }

            if (number < 1 || number > max) {
                System.err.println("[MULTIVERSE] Skipping registry entry '" + name + "': number " + number + " is outside 1.." + max);
                continue;
            }
            // baseId однозначно выводится из слота; хранимый baseId не расходится.
            int baseId = baseForNumber(number);

            if (uid.isEmpty()) {
                uid = uidForNumber(number);
            }

            File folder = new File(multiverseFolder, name);

            LevelData data = new LevelData(name, number, uid, baseId, seed, folder);
            levels.put(name, data);
            universeByNumber.put(number, data);
            uidToNumber.put(uid, number);
            linkDimensions(data);
        }
    }

    private void saveRegistry(MinecraftServer server) {
        if (multiverseFolder == null) return;

        NBTTagCompound root = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (LevelData data : levels.values()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("name", data.name);
            entry.setInteger("baseId", data.baseId);
            entry.setLong("seed", data.seed);
            entry.setString("uid", data.uid);
            // Числовые имена жёстко привязаны к слоту (MV_5 <-> number 5) и сохраняют
            // его явно. Кастомные имена слота не получают: их number выводится из baseId
            // на загрузке, а адресация идёт по имени (regName = bare-имя без префикса MV_).
            int parsed = parseNumber(data.name);
            if (parsed > 0) {
                entry.setInteger("number", data.number);
            } else {
                entry.setString("regName", data.name.substring(LEVEL_PREFIX.length()));
            }
            list.appendTag(entry);
        }
        root.setTag("levels", list);
        writeNbt(new File(multiverseFolder, "registry.dat"), root);
    }
    private void linkDimensions(LevelData data) {
        forEachDim(data, dim -> dimensionToLevel.put(dim, data));
    }

    // ------------------------------------------------------------------ levels
    /**
     * Единая точка создания/получения вселенной по пользовательскому имени.
     *
     * <p>Имя нормализуется к {@code MV_<bare>}. Числовой {@code bare} (например "5")
     * жёстко занимает слот 5. Строковый {@code bare} (кастомное имя) получает любой
     * свободный слот 1..max-1; если все слоты заняты — перезаписывается числовая
     * вселенная с максимальным слотом. Кастомные вселенные не трогаются.</p>
     */
    public LevelData getOrCreateLevel(MinecraftServer server, String rawName, long seed) {
        ensureActive(server);

        String name = normalizeName(rawName);
        if (name == null) {
            System.err.println("[MULTIVERSE] Invalid level name: " + rawName);
            return null;
        }

        // 1) Уже в реестре — возвращаем как есть.
        LevelData existing = levels.get(name);
        if (existing != null) return existing;

        // 2) Определяем фиксированный слот для числового имени.
        String bare = name.substring(LEVEL_PREFIX.length());
        Integer fixedNumber = null;
        try {
            int n = Integer.parseInt(bare);
            if (n >= 1 && n <= maxUniverses()) {
                fixedNumber = n;
            } else {
                System.err.println("[MULTIVERSE] Numeric level name " + name + " is outside 1.." + maxUniverses());
                return null;
            }
        } catch (NumberFormatException ignored) {
            // кастомное имя
        }

        // 3) Проверяем конфликт по слоту для числового имени.
        if (fixedNumber != null && universeByNumber.containsKey(fixedNumber)) {
            LevelData other = universeByNumber.get(fixedNumber);
            System.err.println("[MULTIVERSE] Slot " + fixedNumber + " is occupied by '" + other.name + "'; refusing to create " + name);
            return null;
        }

        // 4) Выбираем номер слота.
        int number;
        LevelData victim = null;
        if (fixedNumber != null) {
            number = fixedNumber;
        } else {
            number = -1;
            for (int n = 1; n < maxUniverses(); n++) {
                if (!universeByNumber.containsKey(n)) {
                    number = n;
                    break;
                }
            }
            if (number < 0) {
                // Все слоты 1..max-1 заняты. Проверяем, есть ли ещё слот max.
                if (!universeByNumber.containsKey(maxUniverses()) && countUniverses() < maxUniverses()) {
                    number = maxUniverses();
                } else {
                    victim = findRecyclableLevel();
                    if (victim == null) {
                        System.err.println("[MULTIVERSE] Cannot create '" + name + "': universe limit reached and no recyclable numeric level");
                        return null;
                    }
                    number = victim.number;
                }
            }
        }

        // 5) Перезапись жертвы (только числовая, никогда кастомная).
        if (victim != null) {
            System.out.println("[MULTIVERSE] Recycling numeric universe '" + victim.name + "' (slot " + victim.number + ") to free slot for " + name);
            recycleLevel(server, victim);
        }

        // 6) Создаём или подхватываем папку.
        File folder = new File(multiverseFolder, name);
        if (folder.isDirectory() && isLevelDataPresent(folder)) {
            long usedSeed = resolveSeedForExisting(server, folder, seed, number, "getOrCreateLevel('" + name + "')");
            LevelData data = registerNewLevel(server, name, number, usedSeed, folder);
            if (data != null) markRegistryDirty();
            return data;
        }
        if (!folder.exists() && !folder.mkdirs()) {
            System.err.println("[MULTIVERSE] Cannot create level folder: " + folder);
            return null;
        }
        LevelData data = registerNewLevel(server, name, number, seed, folder);
        if (data != null) markRegistryDirty();
        return data;
    }

    /**
     * Returns the level for a universe slot (1..max), creating it when the slot is free.
     * If the slot already has a universe, that universe is returned and the seed is ignored
     * (travelling to an existing universe). Used by TD/ARK portal transfers. Rejects slots
     * outside 1..max instead of silently clamping.
     */
    public LevelData getOrCreateLevel(MinecraftServer server, int number, long seed) {
        ensureActive(server);
        int max = maxUniverses();
        if (number < 1 || number > max) {
            System.err.println("[MULTIVERSE] Universe number " + number + " is outside 1.." + max);
            return null;
        }

        LevelData data = universeByNumber.get(number);
        if (data != null) {
            System.out.println("[MULTIVERSE] getOrCreateLevel#" + number + ": EXISTING data.name=" + data.name + " data.seed=" + data.seed + " (ignoring requested seed=" + seed + ")");
            return data;
        }
        String name = LEVEL_PREFIX + number;
        File folder = new File(multiverseFolder, name);
        if (folder.isDirectory() && isLevelDataPresent(folder)) {
            long usedSeed = resolveSeedForExisting(server, folder, seed, number, "getOrCreateLevel#" + number);
            System.out.println("[MULTIVERSE] getOrCreateLevel#" + number + ": FOLDER_EXISTS, requestedSeed=" + seed + " usedSeed=" + usedSeed);
            data = registerNewLevel(server, name, number, usedSeed, folder);
        } else if ((folder.exists() || folder.mkdirs()) && folder.isDirectory()) {
            System.out.println("[MULTIVERSE] getOrCreateLevel#" + number + ": NEW_FOLDER, using requestedSeed=" + seed);
            data = registerNewLevel(server, name, number, seed, folder);
        }
        if (data != null) {
            markRegistryDirty();
        }
        return data;
    }

    /**
     * Всегда создаёт вселенную в свободном/перезаписываемом слоте. Сначала ищет
     * свободный слот 1..max-1, затем slot max, и только как крайнюю меру
     * перезаписывает числовую вселенную с максимальным слотом.
     */
    public LevelData getRandomLevelOrCreate(MinecraftServer server, long seed) {
        ensureActive(server);
        for (int n = 1; n < maxUniverses(); n++) {
            if (!universeByNumber.containsKey(n)) {
                LevelData result = getOrCreateLevel(server, n, seed);
                System.out.println("[MULTIVERSE] getRandomLevelOrCreate: seed=" + seed + " slot=" + n + " result=" + (result != null ? result.name : "null"));
                return result;
            }
        }
        if (!universeByNumber.containsKey(maxUniverses()) && countUniverses() < maxUniverses()) {
            LevelData result = getOrCreateLevel(server, maxUniverses(), seed);
            System.out.println("[MULTIVERSE] getRandomLevelOrCreate: seed=" + seed + " slot=" + maxUniverses() + " result=" + (result != null ? result.name : "null"));
            return result;
        }
        LevelData victim = findRecyclableLevel();
        if (victim == null) {
            System.err.println("[MULTIVERSE] getRandomLevelOrCreate: no slot available");
            return null;
        }
        int slot = victim.number;
        System.out.println("[MULTIVERSE] getRandomLevelOrCreate: recycling " + victim.name + " (slot " + slot + ")");
        recycleLevel(server, victim);
        LevelData result = getOrCreateLevel(server, slot, seed);
        System.out.println("[MULTIVERSE] getRandomLevelOrCreate: seed=" + seed + " slot=" + slot + " result=" + (result != null ? result.name : "null"));
        return result;
    }

    /**
     * Пересоздаёт вселенную в максимальном слоте: выгружает старую, удаляет папку,
     * создаёт новую с указанным сидом. Используется командой /mv join при номере > MAX
     * и TDArk при пустом UID и достигнутом максимуме.
     */
    public LevelData recreateMaxLevel(MinecraftServer server, long seed) {
        ensureActive(server);
        int max = maxUniverses();
        LevelData existing = universeByNumber.get(max);
        if (existing != null) {
            System.out.println("[MULTIVERSE] recreateMaxLevel: recycling existing #" + max + " (" + existing.name + ")");
            recycleLevel(server, existing);
        }
        LevelData result = getOrCreateLevel(server, max, seed);
        System.out.println("[MULTIVERSE] recreateMaxLevel: created #" + max + " seed=" + seed + " result=" + (result != null ? result.name : "null"));
        return result;
    }

    private boolean isLevelDataPresent(File folder) {
        return new File(folder, "level.dat").isFile();
    }
    private LevelData registerNewLevel(MinecraftServer server, String name, int number, long seed, File folder) {
        if (!folder.exists() && !folder.mkdirs()) {
            System.err.println("[MULTIVERSE] Cannot create level folder: " + folder);
            return null;
        }
        int baseId = baseForNumber(number);
        String uid = uidForNumber(number);
        LevelData data = new LevelData(name, number, uid, baseId, seed, folder);
        levels.put(name, data);
        universeByNumber.put(number, data);
        uidToNumber.put(uid, number);
        linkDimensions(data);
        return data;
    }

    /**
     * Unloads all 4 dimensions of the universe (without saving), deletes its world files
     * and drops it from the registry. Slot number/UID/baseId are then reusable.
     */
    private void recycleLevel(MinecraftServer server, LevelData old) {
        System.out.println("[MULTIVERSE] Recycling universe #" + old.number + " (" + old.name + ")");
        forEachDim(old, dim -> unloadWorldNow(server, dim, false));
        unregisterLevelDimensions(server, old);
        levels.remove(old.name);
        universeByNumber.remove(old.number);
        uidToNumber.remove(old.uid);
        releaseRegionFileHandles();
        deleteRecursively(old.folder);
        if (old.folder.exists()) {
            System.err.println("[MULTIVERSE] WARNING: universe " + old.name + " folder was NOT fully deleted (files locked on disk?): " + old.folder);
        }
        System.out.println("[MULTIVERSE] Recycled universe #" + old.number);
    }

    /**
     * Resolves the effective seed for an existing level folder: when {@code requestedSeed}
     * is 0 the stored seed is read back from level.dat; when it differs from the stored
     * seed the stale world contents are cleared so the world regenerates with the requested
     * seed. {@code slot} is the universe slot the folder will be assigned to: its dimension
     * ids are unloaded defensively before the folder is wiped.
     */
    private long resolveSeedForExisting(MinecraftServer server, File folder, long requestedSeed, int slot, String context) {
        if (requestedSeed == 0) {
            WorldInfo info = new LevelSaveHandler(folder).loadWorldInfo();
            return info != null ? info.getSeed() : 0;
        }
        WorldInfo existingInfo = new LevelSaveHandler(folder).loadWorldInfo();
        if (existingInfo != null && existingInfo.getSeed() != requestedSeed) {
            System.out.println("[MULTIVERSE] " + context + ": seed mismatch — existing=" + existingInfo.getSeed() + " requested=" + requestedSeed + " — cleaning stale world data");
            for (int k = 0; k < DIMS_PER_LEVEL; k++) {
                unloadWorldNow(server, baseForNumber(slot) + k, false);
            }
            releaseRegionFileHandles();
            deleteWorldContents(folder);
        }
        return requestedSeed;
    }

    /**
     * The static {@link RegionFileCache} keeps open file handles for every region
     * (.mca/.mcr) ever touched by the loaded chunk loaders. On Windows those handles
     * block deletion, so they must be released before world folders are wiped.
     */
    private static void releaseRegionFileHandles() {
        try {
            RegionFileCache.clearRegionFileReferences();
        } catch (Exception e) {
            System.err.println("[MULTIVERSE] RegionFileCache.clearRegionFileReferences failed: " + e);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        deleteTree(file);
        if (file.exists()) {
            releaseRegionFileHandles();
            deleteTree(file);
        }
        if (file.exists()) {
            System.err.println("[MULTIVERSE] WARNING: could not fully delete " + file + " (files locked on disk?)");
        }
    }

    private static void deleteTree(File file) {
        try {
            Path path = file.toPath();
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path p, BasicFileAttributes attrs) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        System.err.println("[MULTIVERSE] Cannot delete file " + p + ": " + e);
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path p, IOException exc) {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        System.err.println("[MULTIVERSE] Cannot delete directory " + p + ": " + e);
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFileFailed(Path p, IOException exc) {
                    System.err.println("[MULTIVERSE] Cannot access " + p + ": " + exc);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.err.println("[MULTIVERSE] Cannot delete " + file + ": " + e);
        }
    }

    /**
     * Deletes everything inside {@code folder} (level.dat, region/, DIM-1/, etc.)
     * but keeps the folder itself so {@link #registerNewLevel} can populate it fresh.
     */
    private static void deleteWorldContents(File folder) {
        if (folder == null || !folder.isDirectory()) return;
        File[] children = folder.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        System.out.println("[MULTIVERSE] Cleared world contents of " + folder);
    }

    /** Lowest free slot 1..max-1; when all are busy returns the recycle slot (max). */
    private int allocateNumber() {
        int max = maxUniverses();
        for (int n = 1; n < max; n++) {
            if (!universeByNumber.containsKey(n)) {
                return n;
            }
        }
        return max;
    }

    private int parseNumber(String name) {
        if (name != null && name.startsWith(LEVEL_PREFIX)) {
            try {
                int n = Integer.parseInt(name.substring(LEVEL_PREFIX.length()));
                if (n >= 1 && n <= maxUniverses()) {
                    return n;
                }
            } catch (NumberFormatException ignore) {}
        }
        return -1;
    }

    private static int baseForNumber(int number) {
        return BASE_START + (number - 1) * STEP;
    }
    private static int numberForBase(int baseId) {
        return (baseId - BASE_START) / STEP + 1;
    }

    /**
     * Приводит пользовательский ввод к каноническому имени папки "MV_&lt;bare&gt;".
     * Отбрасывает запрещённые символы и пустые значения. Возвращает {@code null}
     * при отказе. Имя, уже начинающееся с {@code MV_}, не получает второй префикс.
     */
    private static String normalizeName(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (s.contains("/") || s.contains("\\") || s.contains("..")) return null;
        if (!s.startsWith(LEVEL_PREFIX)) s = LEVEL_PREFIX + s;
        if (s.length() == LEVEL_PREFIX.length()) return null;
        if (s.indexOf(' ') >= 0 || s.indexOf('\t') >= 0) return null;
        return s;
    }

    /**
     * Возвращает числовую вселенную с максимальным слотом для перезаписи, либо
     * {@code null}, если числовых вселенных нет (кастомные не трогаем).
     */
    private LevelData findRecyclableLevel() {
        LevelData best = null;
        int bestNumber = -1;
        for (LevelData d : levels.values()) {
            int n = parseNumber(d.name);
            if (n > 0 && n > bestNumber) {
                bestNumber = n;
                best = d;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ disk reconciliation

    /**
     * Синхронизирует реестр с содержимым MULTIVERSE/:
     * <ul>
     *   <li>Папки без префикса {@code MV_}, содержащие валидный {@code level.dat},
     *       переименовываются в {@code MV_<имя>}. Невалидные папки (нет level.dat
     *       или он повреждён) переносятся в MULTIVERSE_ERRORED (соседняя папка
     *       рядом с MULTIVERSE).</li>
     *   <li>Папки {@code MV_*}, которых нет в реестре, добавляются в реестр с
     *       ближайшим свободным слотом (числовые имена сохраняют свой слот; если
     *       он занят — папка переносится в MULTIVERSE_ERRORED).</li>
     *   <li>Записи реестра, для которых нет папки, удаляются из реестра.</li>
     * </ul>
     * Помечает реестр «грязным», если что-то поменялось.
     */
    private void reconcileWithDisk(MinecraftServer server) {
        if (multiverseFolder == null || !multiverseFolder.isDirectory()) return;
        File[] children = multiverseFolder.listFiles();
        if (children == null) return;

        File errored = new File(multiverseFolder.getParentFile(), ERRORED_FOLDER_NAME);
        java.util.Set<String> onDisk = new java.util.HashSet<>();
        boolean changed = false;

        for (File child : children) {
            if (!child.isDirectory()) continue;
            String name = child.getName();

            if (!name.startsWith(LEVEL_PREFIX)) {
                if (isValidMinecraftWorld(child)) {
                    File target = new File(multiverseFolder, LEVEL_PREFIX + name);
                    if (target.exists()) {
                        System.err.println("[MULTIVERSE] Cannot migrate '" + name + "' -> '" + target.getName() + "': target exists. Moving to ERRORED.");
                        moveToErrored(child, errored);
                    } else if (child.renameTo(target)) {
                        System.out.println("[MULTIVERSE] Migrated folder '" + name + "' -> '" + target.getName() + "'");
                        onDisk.add(target.getName());
                    } else {
                        System.err.println("[MULTIVERSE] Rename failed for '" + name + "'. Moving to ERRORED.");
                        moveToErrored(child, errored);
                    }
                } else {
                    System.out.println("[MULTIVERSE] Folder '" + name + "' is not a valid Minecraft world. Moving to ERRORED.");
                    moveToErrored(child, errored);
                }
                changed = true;
                continue;
            }

            if (!isValidMinecraftWorld(child)) {
                System.err.println("[MULTIVERSE] Folder '" + name + "' has no valid level.dat. Moving to ERRORED.");
                moveToErrored(child, errored);
                changed = true;
                continue;
            }
            onDisk.add(name);
        }

        // 1) Папки на диске, но не в реестре — регистрируем.
        for (String name : onDisk) {
            if (!levels.containsKey(name)) {
                if (registerFolderIntoRegistry(name)) changed = true;
            }
        }

        // 2) Записи в реестре без папки — выкидываем из реестра.
        java.util.Iterator<java.util.Map.Entry<String, LevelData>> it = levels.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String, LevelData> e = it.next();
            if (!onDisk.contains(e.getKey())) {
                System.err.println("[MULTIVERSE] Registry has '" + e.getKey() + "' but folder is missing. Removing from registry.");
                LevelData d = e.getValue();
                universeByNumber.remove(d.number);
                uidToNumber.remove(d.uid);
                forEachDim(d, dimensionToLevel::remove);
                it.remove();
                changed = true;
            }
        }

        if (changed) markRegistryDirty();
    }

    /** Папка содержит level.dat и он читается как WorldInfo. */
    private static boolean isValidMinecraftWorld(File folder) {
        File levelDat = new File(folder, "level.dat");
        if (!levelDat.isFile()) return false;
        try {
            WorldInfo info = new LevelSaveHandler(folder).loadWorldInfo();
            return info != null;
        } catch (Exception e) {
            System.err.println("[MULTIVERSE] Failed to read level.dat of '" + folder + "': " + e);
            return false;
        }
    }

    /**
     * Добавляет существующую папку {@code MV_*} в реестр, если слот доступен.
     * seed/uid/baseId берутся из сохранённого level.dat / из слота.
     */
    private boolean registerFolderIntoRegistry(String name) {
        File folder = new File(multiverseFolder, name);
        int fixedNumber = parseNumber(name);

        int number;
        if (fixedNumber > 0) {
            if (universeByNumber.containsKey(fixedNumber)) {
                System.err.println("[MULTIVERSE] Folder '" + name + "' wants slot " + fixedNumber + " but it's occupied by '" + universeByNumber.get(fixedNumber).name + "'. Moving folder to ERRORED.");
                moveToErrored(folder, new File(multiverseFolder.getParentFile(), ERRORED_FOLDER_NAME));
                return true;
            }
            number = fixedNumber;
        } else {
            number = -1;
            for (int n = 1; n < maxUniverses(); n++) {
                if (!universeByNumber.containsKey(n)) {
                    number = n;
                    break;
                }
            }
            if (number < 0) {
                if (!universeByNumber.containsKey(maxUniverses())
                        && countUniverses() < maxUniverses()) {
                    number = maxUniverses();
                } else {
                    System.err.println("[MULTIVERSE] Cannot register '" + name + "': no free slot. Moving folder to ERRORED.");
                    moveToErrored(folder, new File(multiverseFolder.getParentFile(), ERRORED_FOLDER_NAME));
                    return true;
                }
            }
        }

        WorldInfo info = new LevelSaveHandler(folder).loadWorldInfo();
        long seed = info != null ? info.getSeed() : 0L;
        String uid = uidForNumber(number);
        int baseId = baseForNumber(number);

        LevelData data = new LevelData(name, number, uid, baseId, seed, folder);
        levels.put(name, data);
        universeByNumber.put(number, data);
        uidToNumber.put(uid, number);
        linkDimensions(data);
        System.out.println("[MULTIVERSE] Registered existing folder '" + name + "' as slot " + number + " seed=" + seed);
        return true;
    }

    /** Переносит папку в MULTIVERSE_ERRORED (с суффиксом timestamp при конфликте имён). */
    private static void moveToErrored(File src, File erroredDir) {
        if (!erroredDir.exists() && !erroredDir.mkdirs()) {
            System.err.println("[MULTIVERSE] Cannot create ERRORED folder: " + erroredDir);
            return;
        }
        File dst = new File(erroredDir, src.getName());
        if (dst.exists()) {
            dst = new File(erroredDir, src.getName() + "_" + System.currentTimeMillis());
        }
        if (src.renameTo(dst)) {
            System.out.println("[MULTIVERSE] Moved '" + src.getName()
                    + "' -> " + dst.getAbsolutePath());
        } else {
            System.err.println("[MULTIVERSE] Failed to move '" + src + "' -> " + dst);
        }
    }

    // ------------------------------------------------------------------ uid hash

    /** Deterministic 8-hex hash of a universe slot for this world's seed. Bijective for 1..max. */
    public static String uidForNumber(long seed, int number) {
        long h = number * 0x9E3779B9L + seed;
        return String.format("%08x", h & 0xFFFFFFFFL);
    }
    public String uidForNumber(int number) {
        return uidForNumber(worldSeed, number);
    }
    /** Reverse of {@link #uidForNumber(int)} for slots 1..max, or -1 when the uid matches no slot. O(1) via the reverse index. */
    public int numberForUid(String uid) {
        if (uid == null || uid.length() != 8) return -1;
        String u = uid.toLowerCase(Locale.ROOT);
        Integer n = uidToNumber.get(u);
        return n != null ? n : -1;
    }
    public LevelData getLevelByUid(String uid) {
        if (uid == null) return null;
        String u = uid.trim().toLowerCase(Locale.ROOT);
        if (u.isEmpty()) return null;
        for (LevelData d : levels.values()) {
            if (d.uid.equals(u)) return d;
        }
        return null;
    }

    public long getWorldSeed() {
        return worldSeed;
    }
    public LevelData getLevelByName(String name) {
        if (name == null) return null;
        LevelData d = levels.get(name);
        if (d != null) return d;
        // Try with MV_ prefix if caller gave a bare name (e.g. "myWorld").
        if (!name.startsWith(LEVEL_PREFIX)) {
            d = levels.get(LEVEL_PREFIX + name);
            if (d != null) return d;
        }
        return null;
    }
    public LevelData getLevelByNumber(int number) {
        return universeByNumber.get(number);
    }

    /**
     * True when the given numeric universe is the max-1 slot AND all numeric slots are
     * occupied, i.e. the random pick falls back to recycling this very universe. Used
     * by TDArk to detect that its own source universe will be destroyed by the transfer.
     */
    public boolean isRecycleVictim(LevelData level) {
        if (level == null || universeByNumber.get(level.number) != level) return false;
        if (level.number != maxUniverses()) return false;
        for (int n = 1; n <= maxUniverses(); n++) {
            if (!universeByNumber.containsKey(n)) return false;
        }
        return true;
    }

    /** Число зарегистрированных вселенных. Корректно после {@link #reconcileWithDisk} — совпадает с числом валидных папок MV_* в MULTIVERSE/. */
    public int countUniverses() {
        return universeByNumber.size();
    }
    public LevelData getLevelByDimensionId(int id) {
        return dimensionToLevel.get(id);
    }
    public boolean isMultiverseDimension(int id) {
        return id == MultiverseDims.GLOBAL_DIM || id == MultiverseDims.PROXY_DIM || dimensionToLevel.containsKey(id);
    }

    /**
     * Returns the overworld dimension id that owns the given dimension.
     * For overworld dimensions, returns the dim itself. For nether/end dims,
     * returns the overworld dim of the same level. Used by WorldProviders
     * so that death in nether/end respawns the player in the correct overworld.
     */
    public static int getOwningOverworldDimension(int dimId) {
        LevelManager lm = getInstance();
        LevelData data = lm.dimensionToLevel.get(dimId);
        if (data != null) {
            return data.baseId;
        }
        return dimId;
    }

    // ------------------------------------------------------------------ worlds
    /**
     * Loads (or creates) the WorldServer backing the given dimension of the level.
     * This is the 1.12.2 equivalent of a per-save dimension root.
     */
    public WorldServer getOrCreateWorld(MinecraftServer server, LevelData data, LevelDimensionType type) {
        int dimId = data.dimensionId(type);

        WorldServer existing = DimensionManager.getWorld(dimId);
        if (existing != null) {
            // If the existing world points at the wrong save root (e.g. Forge/the game
            // auto-created DIM1000 in the save folder instead of MULTIVERSE/<name>),
            // unload it so we can rebuild it rooted at the level folder.
            File existingDir = existing.getSaveHandler().getWorldDirectory();
            if (isSameFolder(existingDir, data.folder)) {
                return existing;
            }
            //System.out.println("[MULTIVERSE] Unloading world for dim " + dimId + " pointing at wrong folder: " + existingDir + " (expected " + data.folder + ")");
            unloadWorldNow(server, dimId, true);
        }

        // Dimension must be registered before constructing the WorldServer.
        MultiverseDims.registerLevelDimensions(data.baseId);

        return constructWorld(server, data.folder, dimId, data.name, data.seed, type == LevelDimensionType.END);
    }

    /** Loads (or creates) the shared global dimension world. Returns null when Enable_uVOID is off (folder never touched). */
    public WorldServer getOrCreateGlobalWorld(MinecraftServer server) {
        if (!MultiverseDims.isGlobalDimensionEnabled()) return null;
        ensureActive(server);
        WorldServer existing = DimensionManager.getWorld(MultiverseDims.GLOBAL_DIM);
        if (existing != null) {
            // Same duplicate-guard as getOrCreateWorld: the game might have loaded the
            // global dim rooted at the wrong folder.
            File existingDir = existing.getSaveHandler().getWorldDirectory();
            if (isSameFolder(existingDir, globalFolder)) {
                return existing;
            }
            //System.out.println("[MULTIVERSE] Unloading global world pointing at wrong folder: " + existingDir + " (expected " + globalFolder + ")");
            unloadWorldNow(server, MultiverseDims.GLOBAL_DIM, true);
        }
        MultiverseDims.registerGlobalDimension();
        return constructWorld(server, globalFolder, MultiverseDims.GLOBAL_DIM, "__global", RNG.nextLong(), false);
    }

    /** Loads (or creates) the proxy dimension world used as temporary holding during universe recreation. */
    public WorldServer getOrCreateProxyWorld(MinecraftServer server) {
        ensureActive(server);
        WorldServer existing = DimensionManager.getWorld(MultiverseDims.PROXY_DIM);
        if (existing != null) {
            File existingDir = existing.getSaveHandler().getWorldDirectory();
            if (isSameFolder(existingDir, proxyFolder)) {
                return existing;
            }
            unloadWorldNow(server, MultiverseDims.PROXY_DIM, true);
        }
        MultiverseDims.registerProxyDimension();
        return constructWorld(server, proxyFolder, MultiverseDims.PROXY_DIM, "__proxy", RNG.nextLong(), false);
    }

    private WorldServer constructWorld(MinecraftServer server, File folder, int dimId, String saveName, long seed, boolean buildEndPortal) {
        LevelSaveHandler saveHandler = new LevelSaveHandler(folder);
        WorldInfo info = saveHandler.loadWorldInfo();
        boolean fresh = info == null;
        if (fresh) {
            info = new WorldInfo(
                    new WorldSettings(seed, GameType.SURVIVAL, true, false, WorldType.DEFAULT),
                    saveName
            );
        } else if (seed != 0 && info.getSeed() != seed) {
            info.populateFromWorldSettings(new WorldSettings(seed, GameType.SURVIVAL, true, false, WorldType.DEFAULT));
        }
        //System.out.println("[MULTIVERSE] constructWorld dim=" + dimId + " fresh=" + fresh + " requestedSeed=" + seed + " finalSeed=" + info.getSeed());

        WorldServer world = new WorldServer(server, saveHandler, info, dimId, server.profiler);
        world.init();
        world.addEventListener(new ServerWorldEventHandler(server, world));

        // Register explicitly. DimensionManager.setWorld idempotently puts the world
        // into the loaded-world map AND rebuilds the server's tick list from it, so
        // any stale world (e.g. a Forge WorldServerMulti hotspot for this id) is
        // atomically replaced instead of ticking alongside us.
        DimensionManager.setWorld(dimId, world, server);
        //System.out.println("[MULTIVERSE] Constructed world dim=" + dimId + " folder=" + folder + " world@" + System.identityHashCode(world) + " handler@" + System.identityHashCode(saveHandler));

        // Pull spawn chunks first so findSpawn() has loaded terrain to scan.
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                world.getChunkFromChunkCoords(cx, cz);
            }
        }

        if (fresh) {
            BlockPos spawn = findSpawn(world);
            world.getWorldInfo().setSpawn(spawn);
            world.getWorldInfo().setServerInitialized(true);
            world.setSpawnPoint(spawn);
            if (buildEndPortal) {
                buildEndExitPortal(world);
            }
        }

        MinecraftForge.EVENT_BUS.post(new WorldEvent.Load(world));
        return world;
    }

    /** Builds the vanilla end spawn platform (5x5 obsidian) at (100,49,0) so a fresh MV end is never a dead end. */
    private void buildEndExitPortal(WorldServer world) {
        //BlockPos base = new BlockPos(100, 49, 0);
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                // Обсидиан
                mpos.setPos(100 + dx, 49, dz);
                world.setBlockState(mpos, Blocks.OBSIDIAN.getDefaultState(), 2);

                // Воздух над платформой
                for (int dy = 1; dy <= 3; dy++) {
                    mpos.setPos(100 + dx, 49 + dy, dz);
                    world.setBlockState(mpos, Blocks.AIR.getDefaultState(), 2);
                }
            }
        }
    }

    /**
     * Chooses a standable spawn for a brand-new level. For nether-like worlds this is
     * the highest solid block at/below Y 64 (i.e. the FLOOR), never the bedrock ceiling;
     * for depths worlds the fixed dark-cavern pocket at (8,252,8) used by MineDimEnter
     * and /mv join; for overworld-like worlds the top solid block found via heightmap,
     * avoiding liquids (ocean floors, lava) by only counting non-liquid solid blocks.
     */
    private static BlockPos findSpawn(WorldServer world) {
        int y = 64;
        if (world.provider.getDimensionType() == net.minecraft.world.DimensionType.NETHER) {
            y = Math.max(scanForTopSolidBelow(world, 8, 8, 64), 4);
        } else if (world.provider.getDimension() == MultiverseDims.GLOBAL_DIM) {
            return new BlockPos(0, 64, 0);
        } else if (world.provider instanceof MultiverseWorldProviders.MultiverseDepths) {
            world.setBlockToAir(new BlockPos(8, 252, 8));
            world.setBlockToAir(new BlockPos(8, 253, 8));
            y = 252;
        } else {
            y = findStandableY(world, 8, 8);
        }
        return new BlockPos(8, y, 8);
    }

    /**
     * Scans downward from the heightmap at (x, z) to find the highest solid,
     * non-liquid block and returns Y+1 (the standable position above it).
     * Falls back to 70 if nothing is found.
     */
    static int findStandableY(WorldServer world, int x, int z) {
        int heightmapY = world.getHeight(new BlockPos(x, 64, z)).getY();
        for (int yy = Math.min(heightmapY, world.getHeight() - 1); yy > 0; yy--) {
            IBlockState state = world.getBlockState(new BlockPos(x, yy, z));
            if (state.isFullCube() && !state.getMaterial().isLiquid()) {
                return yy + 1;
            }
        }
        return Math.max(heightmapY, 70);
    }

    private static int scanForTopSolidBelow(WorldServer world, int x, int z, int startY) {
        for (int yy = Math.min(startY, world.getHeight() - 1); yy >= 0; yy--) {
            if (world.getBlockState(new BlockPos(x, yy, z)).isFullCube()) {
                return yy;
            }
        }
        return startY;
    }

    // ------------------------------------------------------------------ player persistence

    private File playerDataFile() {
        return new File(multiverseFolder, "mv_playerdata.dat");
    }
    public void recordPlayer(EntityPlayerMP player) {
        PlayerEntry old = playerEntries.put(player.getUniqueID(), new PlayerEntry(player.dimension, player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch));
        // Only write when the entry actually changed (or is new) so autosave hooks that
        // re-record the same position are cheap.
        if (old == null || old.dimension != player.dimension
                || old.x != player.posX || old.y != player.posY || old.z != player.posZ
                || old.yaw != player.rotationYaw || old.pitch != player.rotationPitch) {
            savePlayerData();
        }
    }
    public void clearPlayer(UUID uuid) {
        if (playerEntries.remove(uuid) != null) {
            savePlayerData();
        }
    }
    public PlayerEntry getPlayerEntry(UUID uuid) {
        return playerEntries.get(uuid);
    }

    /**
     * Teleports a freshly logged-in player back into their multiverse dimension.
     *
     * <p>The transfer goes through the portal/re-map bypass so the intermediate
     * {@code PlayerChangedDimensionEvent} (which would otherwise see dim 0 and clear
     * the entry mid-restore) cannot drop the player's saved location. The entity
     * actually placed in the target world is used for the post-transfer position so we
     * never write to a stale reference.</p>
     */
    public boolean restorePlayer(EntityPlayerMP player) {
        PlayerEntry entry = playerEntries.get(player.getUniqueID());
        if (entry == null) return false;

        MinecraftServer server = player.world.getMinecraftServer();
        if (server == null) {
            server = FMLCommonHandler.instance().getMinecraftServerInstance();
        }
        if (server == null) return false;

        ensureActive(server);

        // The global void dimension may be disabled in the config while a player
        // entry from an earlier session still points at it: drop the entry instead
        // of re-creating the MULTIVERSE_GLOBAL folder / phantom dimension.
        if (entry.dimension == MultiverseDims.GLOBAL_DIM && !MultiverseDims.isGlobalDimensionEnabled()) {
            System.err.println("[MULTIVERSE] Dropping restore for " + player.getName() + ": global void dimension is disabled");
            clearPlayer(player.getUniqueID());
            return false;
        }

        LevelData data = dimensionToLevel.get(entry.dimension);
        if (data == null && entry.dimension != MultiverseDims.GLOBAL_DIM) {
            System.err.println("[MULTIVERSE] Dropping unknown restore dimension " + entry.dimension + " for " + player.getName());
            clearPlayer(player.getUniqueID());
            return false;
        }

        WorldServer target;
        if (data != null) {
            LevelDimensionType type = data.typeOf(entry.dimension);
            if (type == null) {
                clearPlayer(player.getUniqueID());
                return false;
            }
            target = getOrCreateWorld(server, data, type);
            // Send dimension registration BEFORE teleport so the client has the
            // DimensionType registered before the respawn packet is processed.
            AstrotweaksMod.PACKET_HANDLER.sendTo(new MessageMultiverse(data.baseId), player);
        } else {
            MultiverseDims.registerGlobalDimension();
            target = getOrCreateGlobalWorld(server);
            AstrotweaksMod.PACKET_HANDLER.sendTo(MessageMultiverse.forGlobal(), player);
        }

        if (target == null) {
            clearPlayer(player.getUniqueID());
            return false;
        }

        // Teleport through the dedicated MV teleporter (which skips the vanilla nether
        // portal math) and apply position/rotation to the RETURNED entity.
        Entity restored = MultiverseEvents.teleportIgnoringPortalRemap(
                player, entry.dimension,
                new MultiverseTeleporter(new BlockPos((int) entry.x, (int) entry.y, (int) entry.z)));
        EntityPlayerMP moved = restored instanceof EntityPlayerMP ? (EntityPlayerMP) restored : player;
        moved.fallDistance = 0.0F;
        moved.connection.setPlayerLocation(entry.x, entry.y/* + 1.0D */, entry.z, entry.yaw, entry.pitch);

        // Send seed AFTER teleport so the client's WorldClient exists when the
        // scheduled seed-update task runs.
        if (data != null) {
            AstrotweaksMod.PACKET_HANDLER.sendTo(new MessageMultiverse(data.baseId, data.seed), moved);
        }

        // Bind the player's spawn point to THIS universe's overworld/level spawn so a
        // respawn after death lands in the correct world, not the vanila dim 0.
        BlockPos spawn = target.getSpawnPoint();
        if (spawn != null) {
            moved.setSpawnPoint(spawn, true);
        }
        return true;
    }

    private void savePlayerData() {
        if (multiverseFolder == null) return;

        NBTTagCompound root = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, PlayerEntry> e : playerEntries.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("uuid", e.getKey().toString());
            tag.setInteger("dim", e.getValue().dimension);
            tag.setDouble("x", e.getValue().x);
            tag.setDouble("y", e.getValue().y);
            tag.setDouble("z", e.getValue().z);
            tag.setFloat("yaw", e.getValue().yaw);
            tag.setFloat("pitch", e.getValue().pitch);
            list.appendTag(tag);
        }
        root.setTag("players", list);
        writeNbt(playerDataFile(), root);
    }

    private void loadPlayerData() {
        playerEntries.clear();
        File file = playerDataFile();
        if (!file.isFile())  return;

        NBTTagCompound root = readNbt(file);
        if (root == null)  return;

        NBTTagList list = root.getTagList("players", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            try {
                UUID uuid = UUID.fromString(tag.getString("uuid"));
                PlayerEntry entry = new PlayerEntry(
                    tag.getInteger("dim"),
                    tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"),
                    tag.getFloat("yaw"), tag.getFloat("pitch"));
                playerEntries.put(uuid, entry);
            } catch (IllegalArgumentException e) {
                System.err.println("[MULTIVERSE] Skipping bad player entry: " + e);
            }
        }
    }

    // ------------------------------------------------------------------ unloading

    /**
     * Immediately drops the world for the given dimension from the loaded-world map and
     * the server tick list, optionally saving it first. Unlike
     * {@code DimensionManager.unloadWorld} (which only queues and may silently abort),
     * this guarantees a stale WorldServer can never keep ticking next to a rebuilt one
     * that shares its folder and session.lock.
     */
    private static void unloadWorldNow(MinecraftServer server, int dim, boolean saveIfOwned) {
        WorldServer world = DimensionManager.getWorld(dim);
        if (world == null) return;
        if (saveIfOwned && isOwnedWorld(world)) {
            try {
                world.saveAllChunks(true, null);
            } catch (Exception e) {
                System.err.println("[MULTIVERSE] Failed to save world " + dim + " before unload: " + e);
            }
        }
        MinecraftForge.EVENT_BUS.post(new WorldEvent.Unload(world));
        DimensionManager.setWorld(dim, null, server);
        //System.out.println("[MULTIVERSE] Unloaded dimension " + dim + " (world@" + System.identityHashCode(world) + " dropped from tick list)");
    }

    // ------------------------------------------------------------------ unregister

    /**
     * Unregisters all 4 dimension ids of a level from DimensionManager so that
     * DimensionManager.initDimensions() on the next server start does not
     * recreate them as phantom worlds.
     */
    private static void unregisterLevelDimensions(MinecraftServer server, LevelData data) {
        forEachDim(data, dimId -> {
            if (DimensionManager.isDimensionRegistered(dimId)) {
                WorldServer world = DimensionManager.getWorld(dimId);
                if (world != null) {
                    DimensionManager.setWorld(dimId, null, server);
                }
                DimensionManager.unregisterDimension(dimId);
            }
        });
    }

    /**
     * Saves all multiverse worlds, unloads them from the tick list, unregisters
     * every dimension id from DimensionManager (except the global void -1000000)
     * and clears internal maps. Call on {@code FMLServerStoppingEvent} so that
     * no stale registrations survive into the next server / save session.
     */
    public void unloadAndUnregisterAll(MinecraftServer server) {
        if (multiverseFolder == null)  return;

        // Record all online players in multiverse dimensions BEFORE saving.
        // FMLServerStoppingEvent fires before players are disconnected in
        // MinecraftServer.stopServer(), so onPlayerLoggedOut may not have run yet.
        for (EntityPlayerMP online : server.getPlayerList().getPlayers()) {
            if (isMultiverseDimension(online.dimension)) {
                recordPlayer(online);
            }
        }

        saveAll(server);

        for (LevelData data : levels.values()) {
            forEachDim(data, dim -> unloadWorldNow(server, dim, false));
            unregisterLevelDimensions(server, data);
        }
        if (MultiverseDims.isGlobalDimensionEnabled()) {
            unloadWorldNow(server, MultiverseDims.GLOBAL_DIM, false);
            if (DimensionManager.isDimensionRegistered(MultiverseDims.GLOBAL_DIM)) {
                DimensionManager.unregisterDimension(MultiverseDims.GLOBAL_DIM);
            }
        }
        if (DimensionManager.isDimensionRegistered(MultiverseDims.PROXY_DIM)) {
            unloadWorldNow(server, MultiverseDims.PROXY_DIM, false);
            DimensionManager.unregisterDimension(MultiverseDims.PROXY_DIM);
        }

        levels.clear();
        universeByNumber.clear();
        dimensionToLevel.clear();
        playerEntries.clear();
        uidToNumber.clear();
        multiverseFolder = null;
        registryLoaded = false;
        System.out.println("[MULTIVERSE] All dimensions unloaded and unregistered.");
    }

    /** Unloads level and global worlds that no longer contain any (non-exempt) player. */
    public void unloadEmptyDimensions(MinecraftServer server, UUID... ignore) {
        if (multiverseFolder == null) return;
        for (LevelData data : levels.values()) {
            forEachDim(data, dim -> unloadIfEmpty(server, dim, ignore));
        }
        if (MultiverseDims.isGlobalDimensionEnabled()) {
            unloadIfEmpty(server, MultiverseDims.GLOBAL_DIM, ignore);
        }
        if (DimensionManager.isDimensionRegistered(MultiverseDims.PROXY_DIM)) {
            unloadIfEmpty(server, MultiverseDims.PROXY_DIM, ignore);
        }
    }

    private void unloadIfEmpty(MinecraftServer server, int dim, UUID... ignore) {
        WorldServer world = DimensionManager.getWorld(dim);
        if (world == null) return;
        if (world.playerEntities.isEmpty() || onlyIgnoredPlayers(world.playerEntities, ignore)) {
            if (!isOwnedWorld(world)) {
                // Phantom world created by Forge reusing the overworld's save handler:
                // dump it without saving (saving would touch the overworld's region files
                // or throw MinecraftException from the mismatched session lock).
                unloadWorldNow(server, dim, false);
                return;
            }
            unloadWorldNow(server, dim, true);
        }
    }

    private static boolean onlyIgnoredPlayers(List<EntityPlayer> players, UUID... ignore) {
        for (EntityPlayer player : players) {
            boolean matches = false;
            for (UUID uuid : ignore) {
                if (player.getUniqueID().equals(uuid)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ saving
    /** Saves the registry, player data and all loaded multiverse worlds (server stop). */
    public void saveAll(MinecraftServer server) {
        if (multiverseFolder == null) return;
        saveRegistry(server);
        registryDirty = false;
        savePlayerData();
        for (LevelData data : levels.values()) {
            forEachDim(data, dim -> saveWorldIfLoaded(dim));
        }
        if (MultiverseDims.isGlobalDimensionEnabled()) {
            saveWorldIfLoaded(MultiverseDims.GLOBAL_DIM);
        }
    }

    private void saveWorldIfLoaded(int dim) {
        WorldServer world = DimensionManager.getWorld(dim);
        if (world == null) return;
        if (!isOwnedWorld(world))  return;
        try {
            world.saveAllChunks(true, null);
        } catch (Exception e) {
            System.err.println("[MULTIVERSE] Failed to save world " + dim + ": " + e);
        }
    }

    // ------------------------------------------------------------------ nbt helpers

    private static void writeNbt(File file, NBTTagCompound tag) {
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        try (FileOutputStream fout = new FileOutputStream(file)) {
            CompressedStreamTools.writeCompressed(tag, fout);
        } catch (IOException e) {
            System.err.println("[MULTIVERSE] Failed to write " + file + ": " + e);
        }
    }
    private static NBTTagCompound readNbt(File file) {
        try (FileInputStream fin = new FileInputStream(file)) {
            return CompressedStreamTools.readCompressed(fin);
        } catch (IOException e) {
            System.err.println("[MULTIVERSE] Failed to read " + file + ": " + e);
            return null;
        }
    }
    /** Where a player stands inside a multiverse dimension when they leave/download. */
    public static class PlayerEntry {
        public final int dimension;
        public final double x, y, z;
        public final float yaw, pitch;
        PlayerEntry(int dimension, double x, double y, double z, float yaw, float pitch) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
