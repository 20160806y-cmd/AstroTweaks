package astrotweaks.block.black_hole;

import net.minecraft.block.material.Material;

public final class BlackHoleUtils {

    private BlackHoleUtils() {}

    /**
     * Fake hardness used ONLY for the eat-ability check (accel >= hardness).
     * Mass gain always uses the real hardness.
     *
     * <ul>
     *   <li>Material.ROCK with real hardness in [1.5, 5.0] -&gt; 1.5 (stone level,
     *       so ores don't hang in the air of the crater);</li>
     *   <li>Material.WOOD -&gt; 0.6 unconditionally;</li>
     *   <li>everything else -&gt; real hardness (0 maps to 0.1 as before).</li>
     * </ul>
     * Hot path: reference equality on Material singletons + one float range
     * check, no allocations. JIT-inlinable.
     */
    public static final double FAKE_HARDNESS_ROCK = 1.5D;
    public static final double FAKE_HARDNESS_WOOD = 0.6D;

    public static double effectiveHardnessForCheck(Material mat, float realHardness) {
        if (mat == Material.ROCK) {
            // Most common case in the crater is stone-like rock: single range check.
            // Outside the window (e.g. obsidian 50) falls through to real hardness.
            // The else-if below is intentional: ROCK != WOOD, saves one comparison.
            if (realHardness >= 1.5F && realHardness <= 5.0F) return FAKE_HARDNESS_ROCK;
        } else if (mat == Material.WOOD) {
            return FAKE_HARDNESS_WOOD;
        }
        return realHardness == 0.0F ? 0.1D : (double) realHardness;
    }

    /** Real hardness mapped to mass gain (zero-hardness blocks give 0.1). */
    public static double massGainForHardness(float realHardness) {
        return realHardness == 0.0F ? 0.1D : (double) realHardness;
    }

    /** Game gravity constant tuned so mass=1000 => gravity range ~22 blocks at threshold 0.001 */
    public static final double G = 5.0e-4;
    /** Minimal displacement per tick to be applied */
    public static final double MIN_ACCEL = 0.001D;
    /** Hard cap for gravity scan box (per user req) */
    public static final double MAX_GRAVITY_RANGE = 128.0D;
    /** Block capture radius cap - same constant as gravity per user req (perf limited) */
    public static final double MAX_BLOCK_CAPTURE_RANGE = 96.0D;
    // проверки блоков в радиусе можно сделать более "умными", например, кешировать позицию где уже был съеден блок и проверять её только через несколько тиков 
    // также можно для блоков которые несколько чеков подряд являются воздухом снижать приоритет проверок, чтобы не тратить вычисления на пустоту
    // так же можно кешировать и блоки, которые сейчас невозможно съесть, и откладывать их проверку на несколько тиков (так как масса ЧД не меняется так быстро, чтобы проверка одного и того же блока так часто могла имать смысл)
    // 

    // Horizon: R_h = C * mass^E ; v3: -25% base (H_SCALE*m^H_EXP): 200->0.58 ; 1000->0.82 ; 5000->1.17
    public static final double H_SCALE = 0.08D;
    public static final double H_EXP = 0.28D;

    /** Halo thickness base formula: halo = 0.25 * horizon^0.602 (1->0.25, 10->1.0) */
    public static double getHaloThickness(double horizon) {
        if (horizon <= 0) return 0.25D;
        double h = 0.25D * Math.pow(horizon, 0.60206D);
        if (h < 0.12D) h = 0.12D;
        if (h > 1.8D) h = 1.8D;
        return h;
    }
    /** Legacy constant for compat - now computed */
    public static final double HALO_DELTA = 0.35D;

    /** Blocks per block-eat cycle (every 5 ticks). Configurable */
    //public static int BLOCKS_PER_TICK = 16;
    /** Entity blacklist for capture */
    public static final java.util.Set<Class<? extends net.minecraft.entity.Entity>> ENTITY_BLACKLIST = new java.util.HashSet<>();
    static {
        ENTITY_BLACKLIST.add(net.minecraft.entity.passive.EntitySquid.class);
    }

    /** Default mass for newly placed black hole */
    public static final double DEFAULT_MASS = 1000.0D;

    /** Mass delta per absorption */
    public static final double MASS_PER_ITEM = 1.0D;
    public static final double MASS_PER_ENTITY = 5.0D;
    public static final double MASS_PER_XP = 0.5D;
    public static final double MASS_PER_PLAYER = 20.0D;
    /** Mass gained per liquid block eaten. Cheap — liquids have no structural cost. */
    public static final double MASS_PER_LIQUID = 0.5D;

    public static double getGravityRange(double mass) {
        if (mass <= 0) return 0;
        double r = Math.sqrt(G * mass / MIN_ACCEL);
        if (r > MAX_GRAVITY_RANGE) r = MAX_GRAVITY_RANGE;
        return r;
    }

    /** Effective block capture radius - not just hardnessMin, but also capped by MAX range */
    public static double getBlockCaptureRadius(double mass) {
        return getGravityRange(mass); // per user: same constant as gravity
    }

    public static double getHorizonRadius(double mass) {
        if (mass <= 0) return 0.25D;
        double r = H_SCALE * Math.pow(mass, H_EXP);
        if (r < 0.3D) r = 0.3D;
        if (r > 20D) r = 20D; // Максимальный радиус ЧД
        return r;
    }

    /**
     * Visual-only horizon radius for rendering (TESR, bounding box).
     * Same formula as gameplay, but the minimum is 3x smaller (0.1 instead
     * of 0.3), so a fresh mass=1 hole renders tiny. Gameplay logic
     * (capture, absorption) keeps using {@link #getHorizonRadius}.
     */
    public static double getVisualHorizonRadius(double mass) {
        if (mass <= 0) return 0.1D;
        double r = H_SCALE * Math.pow(mass, H_EXP);
        if (r < 0.1D) r = 0.1D;
        if (r > 20D) r = 20D;
        return r;
    }

    /** Radius where accel >= hardness threshold (dynamic) */
    public static double getBlockEatRadiusByHardness(double mass, double hardness) {
        if (mass <= 0) return 0;
        if (hardness < 0.05) hardness = 0.1; // zero-hardness ->0.1 per req
        double r = Math.sqrt(G * mass / hardness);
        double h = getHorizonRadius(mass);
        if (r < h + 0.5D) r = h + 0.5D;
        // cap by global block capture limit
        if (r > MAX_BLOCK_CAPTURE_RANGE) r = MAX_BLOCK_CAPTURE_RANGE;
        if (r > MAX_GRAVITY_RANGE) r = MAX_GRAVITY_RANGE;
        return r;
    }

    /** Legacy alias */
    public static double getBlockEatRadius(double mass) {
        return getBlockEatRadiusByHardness(mass, 0.2D);
    }

    /** Acceleration per tick towards center at distance r */
    public static double getAcceleration(double mass, double dist) {
        if (dist < 0.1D) dist = 0.1D;
        return G * mass / (dist * dist);
    }

    /** Mass at which the horizon reaches radius r. Inverse of getHorizonRadius. */
    public static double massForHorizon(double r) {
        if (r <= H_SCALE) return 1.0;
        return Math.pow(r / H_SCALE, 1.0 / H_EXP);
    }

    /** Horizon radius that slowly expands with mass (alternative log formula)
     *  Exposed for debug, not used by default.
     */
    public static double getHorizonLog(double mass) {
        return 0.5D + Math.log10(1 + mass) * 1.2D;
    }
}
