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
    public static final double MAX_GRAVITY_RANGE = 150.0D;
    /** Block capture radius cap - same constant as gravity per user req (perf limited) */
    public static final double MAX_BLOCK_CAPTURE_RANGE = 128.0D;


    // Horizon: R_h = C * mass^E ; v3: -25% base (H_SCALE*m^H_EXP): 200->0.58 ; 1000->0.82 ; 5000->1.17
    public static final double H_SCALE = 0.05D;
    public static final double H_EXP = 0.3D;

    /** Halo thickness base formula: halo = 0.25 * horizon^0.602 (1->0.25, 10->1.0) */
    public static double getHaloThickness(double horizon) {
        if (horizon <= 0) return 0.2D;
        double h = 0.2D * Math.pow(horizon, 0.60206D);
        if (h < 0.12D) h = 0.12D;
        if (h > 1.8D) h = 1.8D;
        return h;
    }
    /** Legacy constant for compat - now computed */
    //public static final double HALO_DELTA = 0.35D;

    /** Blocks per block-eat cycle (every 5 ticks). Configurable */
    //public static int BLOCKS_PER_TICK = 16;
    /** Entity blacklist for capture */
    public static final java.util.Set<Class<? extends net.minecraft.entity.Entity>> ENTITY_BLACKLIST = new java.util.HashSet<>();
    static {
        ENTITY_BLACKLIST.add(net.minecraft.entity.passive.EntitySquid.class);
        ENTITY_BLACKLIST.add(net.minecraft.entity.boss.EntityDragon.class);
    }

    /** Default mass for newly placed black hole */
    public static final double DEFAULT_MASS = 5000.0D;

    /** Hard floor for mass (evaporation and drains never go below this). */
    public static final double MIN_MASS = 1.0D;
    /**
     * Hard cap for mass. Clamp, not reset: values above (e.g. huge NBT)
     * saturate here, values below MIN_MASS saturate at the floor.
     * Gravity range caps at 128 anyway (~32768 mass); horizon keeps growing
     * up to ~3.6e8 mass, so this cap only bounds runaway growth.
     */
    public static final double MAX_MASS = 1.0E12D;

    /**
     * Evaporation: mass lost per tick, inversely proportional to mass.
     * Halves every decade: 1e4 -&gt; 32, 1e5 -&gt; 16, 1e6 -&gt; 8, ...
     * loss(m) = EVAP_BASE / m^EVAP_EXP, EVAP_EXP = log10(2).
     */
    public static final double EVAP_BASE = 512.0D;
    public static final double EVAP_EXP = 0.30103D / 2;

    public static double getEvaporationPerTick(double mass) {
        if (mass < MIN_MASS)  return 0.0D; // floor or NaN: nothing to evaporate
        double loss = EVAP_BASE / Math.pow(mass, EVAP_EXP);
        double maxLoss = mass - MIN_MASS;
        return loss < maxLoss ? loss : maxLoss;
    }

    /**
     * BH-vs-BH mass tug rate. A hole of mass M drains
     * TUG_RATE * M * (1 + grav) per tick from every other hole inside its
     * gravity range, where grav is its own acceleration at that distance.
     * The drained amount is credited to the drainer (conserved transfer).
     */
    public static final double TUG_RATE = 0.0001D;

    /** Clamp any mass value into [MIN_MASS, MAX_MASS] (NaN-safe: NaN -> floor). */
    public static double clampMass(double m) {
        if (!(m >= MIN_MASS)) return MIN_MASS;
        if (m > MAX_MASS) return MAX_MASS;
        return m;
    }

    /** Mass delta per absorption */
    public static final double MASS_PER_ITEM = 1.0D;
    public static final double MASS_PER_ENTITY = 10.0D;
    public static final double MASS_PER_XP = 0.5D;
    public static final double MASS_PER_PLAYER = 25.0D;
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
        if (mass <= 0) return 0.04D;
        double r = H_SCALE * Math.pow(mass, H_EXP);
        if (r < 0.04D) r = 0.04D;
        if (r > 50D) r = 50D; // Максимальный радиус ЧД
        return r;
    }

    /**
     * Visual-only horizon radius for rendering (TESR, bounding box).
     * Same formula as gameplay, but the minimum is 3x smaller (0.1 instead
     * of 0.3), so a fresh mass=1 hole renders tiny. Gameplay logic
     * (capture, absorption) keeps using {@link #getHorizonRadius}.
     */
    public static double getVisualHorizonRadius(double mass) {
        if (mass <= 0) return 0.04D;
        double r = H_SCALE * Math.pow(mass, H_EXP);
        if (r < 0.04D) r = 0.04D;
        if (r > 50D) r = 50D;
        return r;
    }

    /** Radius where accel >= hardness threshold (dynamic) */
    public static double getBlockEatRadiusByHardness(double mass, double hardness) {
        if (mass <= 0)  return 0;
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
        if (r <= H_SCALE) return H_SCALE;
        return Math.pow(r / H_SCALE, 1.0 / H_EXP);
    }

    /** Horizon radius that slowly expands with mass (alternative log formula)
     *  Exposed for debug, not used by default.
     */
    public static double getHorizonLog(double mass) {
        return 0.5D + Math.log10(1 + mass) * 1.2D;
    }
}
