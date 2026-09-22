package astrotweaks.Multiverse;

import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;

/**
 * RTG (rtgc) integration for Multiverse overworlds.
 *
 * <p>If dimension 0 uses generatorName "rtgc", every multiverse overworld
 * inherits it: its level.dat gets generatorName rtgc and its WorldProvider
 * delegates biome/chunk generation to RTG.</p>
 *
 * <p>All RTG-specific code is accessed via reflection / string checks so the
 * class loads and works even when RTGC is not installed. Missing-provider
 * guard: every reflective call is wrapped and returns null/false on any
 * failure, falling back to vanilla generation.</p>
 */
public final class RTGSupport {

    private static final String RTG_NAME = "rtgc";
    private static final String RTG_MODID = "rtgc";
    private static final String RTG_WORLD_TYPE_CLASS = "rtg.world.WorldTypeRTG";
    private static final String RTG_API_CLASS = "rtg.api.RTGAPI";

    private RTGSupport() {}

    /** True if RTGC mod jar is present (and WorldType class can be loaded). */
    public static boolean isRTGPresent() {
        if (!Loader.isModLoaded(RTG_MODID)) return false;
        try {
            Class.forName(RTG_WORLD_TYPE_CLASS, false, RTGSupport.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Returns WorldType for "rtgc" or null if not registered (RTG not loaded or not enabled). */
    @Nullable
    public static WorldType getRTGWorldType() {
        try {
            return WorldType.parseWorldType(RTG_NAME);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Checks if the given WorldInfo uses rtgc generator (string compare, no class needed). */
    public static boolean isRTGWorldInfo(WorldInfo info) {
        if (info == null) return false;
        try {
            WorldType type = info.getTerrainType();
            if (type != null && RTG_NAME.equalsIgnoreCase(type.getName())) return true;
            // Fallback: generatorName string via WorldInfo internal? WorldType name is enough.
            // Also check byName lookup in case type object is DEFAULT fallback but NBT says rtgc?
            // WorldInfo constructor falls back to DEFAULT when worldtype not found, so we need
            // to check raw generatorName via NBT? Instead we trust type check + isRTGPresent.
            // For already-loaded worlds where RTG was missing at load, type will be DEFAULT
            // so this returns false — which is correct fallback.
        } catch (Throwable ignored) {}
        return false;
    }

    /** Checks if base overworld (dim 0) currently uses RTG. No RTG class required. */
    public static boolean isBaseWorldRTG() {
        try {
            WorldServer base = DimensionManager.getWorld(0);
            if (base == null) return false;
            return isRTGWorldInfo(base.getWorldInfo());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Overload for server instance (used during world construction where DimensionManager may not yet have base). */
    public static boolean isBaseWorldRTG(net.minecraft.server.MinecraftServer server) {
        try {
            if (server == null) return isBaseWorldRTG();
            WorldServer base = server.getWorld(0);
            if (base == null) return isBaseWorldRTG();
            return isRTGWorldInfo(base.getWorldInfo());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Should the given MV overworld use RTG? True if base is RTG OR the mv world's own WorldInfo is already RTG. */
    public static boolean shouldUseRTG(World world) {
        if (world == null) return isBaseWorldRTG();
        try {
            if (isRTGWorldInfo(world.getWorldInfo())) return true;
        } catch (Throwable ignored) {}
        return isBaseWorldRTG();
    }

    /** Tries to register the MV overworld dimension type as allowed for RTG. No-op if RTG absent. */
    public static void ensureAllowedDimension(int dimId) {
        if (!isRTGPresent()) return;
        // Whitelist unconditionally when RTG is present — shouldUseRTG() still gates actual usage,
        // but whitelisting alone is harmless and needed for client-side rendering when server is RTG.
        // For server, isBaseWorldRTG() is checked in shouldUseRTG(), so adding extra check here
        // would only prevent client whitelist when client's base world is not yet RTG.
        // Keep permissive: if RTG present, allow MV overworld types.
        try {
            DimensionType type = DimensionType.getById(dimId);
            if (type == null) return;
            // reflectively call RTGAPI.addAllowedDimensionType
            Class<?> rtgApi = Class.forName(RTG_API_CLASS);
            java.lang.reflect.Method m = rtgApi.getMethod("addAllowedDimensionType", DimensionType.class);
            m.invoke(null, type);
        } catch (Throwable ignored) {
            // protection: provider not found or method missing -> fallback to vanilla
        }
    }

    /** Creates RTG BiomeProvider for the world via WorldTypeRTG, or null on failure. */
    @Nullable
    public static BiomeProvider createRTGBiomeProvider(World world) {
        if (world == null) return null;
        if (!isRTGPresent()) return null;
        try {
            // Ensure dimension is whitelisted before asking WorldTypeRTG
            try {
                ensureAllowedDimension(world.provider.getDimension());
            } catch (Throwable ignored) {}

            Class<?> wtClass = Class.forName(RTG_WORLD_TYPE_CLASS);
            java.lang.reflect.Method getInstance = wtClass.getMethod("getInstance");
            Object wtInstance = getInstance.invoke(null);
            java.lang.reflect.Method getBiome = wtClass.getMethod("getBiomeProvider", World.class);
            Object provider = getBiome.invoke(wtInstance, world);
            if (provider instanceof BiomeProvider) return (BiomeProvider) provider;
            return null;
        } catch (Throwable t) {
            // catch NoClassDefFoundError, InvocationTargetException etc.
            System.err.println("[MULTIVERSE][RTG] Failed to create RTG BiomeProvider: " + t);
            return null;
        }
    }

    /** Creates RTG ChunkGenerator for the world via WorldTypeRTG, or null on failure. */
    @Nullable
    public static IChunkGenerator createRTGChunkGenerator(World world) {
        if (world == null) return null;
        if (!isRTGPresent()) return null;
        try {
            try {
                ensureAllowedDimension(world.provider.getDimension());
            } catch (Throwable ignored) {}

            Class<?> wtClass = Class.forName(RTG_WORLD_TYPE_CLASS);
            java.lang.reflect.Method getInstance = wtClass.getMethod("getInstance");
            Object wtInstance = getInstance.invoke(null);
            // signature: getChunkGenerator(World, String)
            java.lang.reflect.Method getChunk = wtClass.getMethod("getChunkGenerator", World.class, String.class);
            String genOptions = world.getWorldInfo().getGeneratorOptions();
            if (genOptions == null) genOptions = "";
            Object gen = getChunk.invoke(wtInstance, world, genOptions);
            if (gen instanceof IChunkGenerator) return (IChunkGenerator) gen;
            return null;
        } catch (Throwable t) {
            System.err.println("[MULTIVERSE][RTG] Failed to create RTG ChunkGenerator: " + t);
            return null;
        }
    }

    /** Returns generatorOptions string from base world (for inheritance). */
    @Nullable
    public static String getBaseGeneratorOptions(net.minecraft.server.MinecraftServer server) {
        try {
            WorldServer base = server != null ? server.getWorld(0) : DimensionManager.getWorld(0);
            if (base == null) return "";
            String opts = base.getWorldInfo().getGeneratorOptions();
            return opts != null ? opts : "";
        } catch (Throwable t) {
            return "";
        }
    }

    /** Updates WorldInfo's generatorOptions via reflection (private field). */
    public static void setGeneratorOptions(WorldInfo info, String options) {
        if (info == null || options == null) return;
        try {
            // Try WorldSettings way: WorldInfo has private field_82576_c
            java.lang.reflect.Field f = null;
            // try deobfuscated name first, then srg
            try {
                f = WorldInfo.class.getDeclaredField("field_82576_c");
            } catch (NoSuchFieldException e) {
                // try alternative name (if mappings differ, search by type)
                for (java.lang.reflect.Field fld : WorldInfo.class.getDeclaredFields()) {
                    if (fld.getType() == String.class) {
                        // heuristic: check if field holds generatorOptions by reading current value
                        fld.setAccessible(true);
                        Object val = fld.get(info);
                        if (val != null && val.equals(info.getGeneratorOptions())) {
                            f = fld;
                            break;
                        }
                    }
                }
            }
            if (f != null) {
                f.setAccessible(true);
                f.set(info, options);
            }
        } catch (Throwable ignored) {}
    }
}
