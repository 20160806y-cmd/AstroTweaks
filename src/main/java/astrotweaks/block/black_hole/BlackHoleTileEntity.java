package astrotweaks.block.black_hole;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.List;



public class BlackHoleTileEntity extends TileEntity implements ITickable {

    public static final String TAG_MASS = "mass";

    /** Глобальный реестр активных BH — O(число BH) вместо O(все TE) в ивентах. Strong set, чтобы BH не пропадала при выгрузке чанка (WeakHashMap собиралась GC). */
    private static final java.util.Set<BlackHoleTileEntity> ACTIVE_HOLES =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public static java.util.Set<BlackHoleTileEntity> getActiveHoles() { return ACTIVE_HOLES; }

    private double mass = BlackHoleUtils.DEFAULT_MASS;
    private int syncCooldown = 0;

    private final BlackHoleRegionManager regionManager = new BlackHoleRegionManager(this);

    public BlackHoleRegionManager getRegionManager() { return regionManager; }

    public double getMass() { return mass; }

    public void setMass(double m) {
        this.mass = BlackHoleUtils.clampMass(m);
        markDirty();
        // force sync soon
        syncCooldown = 0;
    }

    public void addMass(double delta) {
        setMass(this.mass + delta);
    }

    /**
     * Continuous per-tick delta (evaporation, BH-vs-BH tug). Clamped like
     * setMass, but does NOT force an immediate client sync — the periodic
     * 20-tick sync covers it, otherwise every BH would spam update packets
     * every tick.
     */
    public void addMassPassive(double delta) {
        if (delta == 0.0D) return;
        this.mass = BlackHoleUtils.clampMass(this.mass + delta);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey(TAG_MASS)) {
            this.mass = nbt.getDouble(TAG_MASS);
        }
        // legacy int tag
        if (nbt.hasKey("Mass")) {
            this.mass = nbt.getDouble("Mass");
        }
        // Clamp, don't reset: huge NBT values (past int range, incl. wrapped
        // negatives from external editors) saturate at the configured limits
        // instead of silently falling back to default.
        this.mass = BlackHoleUtils.clampMass(this.mass);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setDouble(TAG_MASS, mass);
        return nbt;
    }
    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }
    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }
    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        readFromNBT(tag);
    }

    @Override
    public void validate() {
        super.validate();
        ACTIVE_HOLES.add(this);
    }

    @Override
    public void invalidate() {
        ACTIVE_HOLES.remove(this);
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        // Не удаляем из ACTIVE_HOLES — TE остаётся валидной, вернётся при загрузке чанка.
        super.onChunkUnload();
    }

    // Кэш для getRenderBoundingBox — избегаем 2 pow + аллокацию AABB каждый кадр
    private double cachedBBMass = Double.NaN;
    private AxisAlignedBB cachedBB;
    // Кэш для tickEntities AABB
    private double cachedGravRange = Double.NaN;
    private AxisAlignedBB cachedAABB;

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        double m = mass;
        if (m != cachedBBMass || cachedBB == null) {
            cachedBBMass = m;
            double h = BlackHoleUtils.getVisualHorizonRadius(m);
            double t = BlackHoleUtils.getHaloThickness(h);
            double rad = Math.max(h + t * 2 + 1.0D, 2.0D) + 1.0D;
            cachedBB = new AxisAlignedBB(pos).grow(rad, rad, rad);
        }
        return cachedBB;
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 0 || pass == 1;
    }

    @Override
    public double getMaxRenderDistanceSquared() {
        return super.getMaxRenderDistanceSquared();
    }

    /**
     * Уничтожает ядро чёрной дыры (блок + TE). Безопасен при повторном вызове.
     * Вызывать только на сервере.
     */
    public void destroyBlackHole() {
        if (world == null || world.isRemote || isInvalid()) return;
        // setBlockToAir -> BlackHoleBlock.breakBlock -> world.removeTileEntity(pos) -> invalidate()
        world.setBlockToAir(pos);
    }

    // =================================================================
    // Server tick - gravity & block eating (region-driven)
    // =================================================================
    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        // Смерть от испарения/поглощения: если масса дошла до пола — сносим ядро.
        // mass==0 невозможен из-за clampMass->MIN_MASS, поэтому проверяем <= MIN_MASS.
        if (mass <= BlackHoleUtils.MIN_MASS) {
            destroyBlackHole();
            return;
        }

        // Evaporation: slow mass bleed, weaker for heavier holes.
        double m = mass;
        if (m > BlackHoleUtils.MIN_MASS) {
            double evap = BlackHoleUtils.getEvaporationPerTick(m);
            if (evap > 0.0D) {
                addMassPassive(-evap);
                if (mass <= BlackHoleUtils.MIN_MASS) {
                    destroyBlackHole();
                    return;
                }
            }
        }

        // Entities: split across 2 ticks by entity-id parity.
        tickEntities();

        // Blocks: budgeted, region-driven.
        regionManager.tick();

        // periodic sync every N ticks (mass change forces syncCooldown=0)
        syncCooldown--;
        if (syncCooldown <= 0) {
            syncCooldown = 4;
            net.minecraft.block.state.IBlockState st = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, st, st, 3);
        }
    }

    private void tickEntities() {
        int parity2 = (int)(world.getTotalWorldTime() & 1);
        int parity4 = (int)(world.getTotalWorldTime() & 3);

        double horizon   = BlackHoleUtils.getHorizonRadius(mass);
        double gravRange = BlackHoleUtils.getGravityRange(mass);
        if (gravRange < 0.5) return;

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        AxisAlignedBB aabb;
        if (gravRange != cachedGravRange || cachedAABB == null) {
            cachedGravRange = gravRange;
            cachedAABB = new AxisAlignedBB(
                    cx - gravRange, cy - gravRange, cz - gravRange,
                    cx + gravRange, cy + gravRange, cz + gravRange);
        } else {
            // Центр не меняется, но если TE переместился (не должен) — пересоздать
            // Быстрая проверка: AABB центр vs текущая позиция
            double ax = (cachedAABB.minX + cachedAABB.maxX) * 0.5;
            if (ax != cx) {
                cachedAABB = new AxisAlignedBB(
                        cx - gravRange, cy - gravRange, cz - gravRange,
                        cx + gravRange, cy + gravRange, cz + gravRange);
            }
        }
        aabb = cachedAABB;

        List<Entity> entities = world.getEntitiesWithinAABB(Entity.class, aabb);

        boolean massChanged = false;
        for (Entity e : entities) {
            if (e == null || e.isDead) continue;
            if (e instanceof EntityPlayer && ((EntityPlayer)e).isSpectator()) continue;
            boolean isPlayer = e instanceof EntityPlayerMP;
            // Stride by type: items/XP across 4 ticks, others across 2 ticks
            boolean isItemOrXp = e instanceof EntityItem || e instanceof EntityXPOrb;
            if (!isPlayer) {
                if (isItemOrXp) {
                    if ((e.getEntityId() & 3) != parity4) continue;
                } else {
                    if ((e.getEntityId() & 1) != parity2) continue;
                }
            }

            // Blacklist
            boolean blacklisted = false;
            for (Class<? extends Entity> cls : BlackHoleUtils.ENTITY_BLACKLIST)
                if (cls.isInstance(e)) { blacklisted = true; break; }
            if (blacklisted) continue;

            if (e instanceof EntityPlayer) {
                EntityPlayer p = (EntityPlayer) e;
                if (p.isCreative() || p.isSpectator()) continue;
            }
            if (e.isDead) continue;

            double ey = e.posY + e.height * 0.5;
            if (e instanceof EntityItem || e instanceof EntityXPOrb) ey = e.posY + 0.25;

            // Compensate stride: items/XP run every 4 ticks (x4), others every 2 ticks (x2)
            double stride = isItemOrXp ? 4.0 : 2.0;

            double dx = cx - e.posX;
            double dy = cy - ey;
            double dz = cz - e.posZ;
            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (dist < 0.05) dist = 0.05;

            double accel = BlackHoleUtils.getAcceleration(mass, dist);
            boolean insideHorizon = dist <= horizon;

            // --- Suffocation (applied BEFORE horizon block, so guaranteed inside) ---
            // Threshold lowered 0.5 -> 0.4 per request.
            // Inside horizon -> always true for any living entity. Creative /
            // spectator players were filtered out above, so gm 0/2 are covered.
            if (e instanceof EntityLivingBase && (insideHorizon || accel > 0.4)) {
                EntityLivingBase living = (EntityLivingBase) e;
                // ~1.5 air units per real tick, scaled by stride so faster/slower
                // tick rates give the same effective drain rate.
                int airDelta = (int) Math.max(1, Math.round(1.5 * stride));
                int air = living.getAir() - airDelta;
                if (air < -20) { air = 0; living.attackEntityFrom(DamageSource.DROWN, 1.0F); }
                living.setAir(air);
                if (e.isDead) continue;
            }

            // --- Horizon absorption ---
            if (insideHorizon) {
                try {
                    if (e instanceof EntityItem) {
                        int count = Math.max(1, ((EntityItem) e).getItem().getCount());
                        mass += count * BlackHoleUtils.MASS_PER_ITEM;
                        e.setDead();
                        massChanged = true;
                    } else if (e instanceof EntityXPOrb) {
                        mass += BlackHoleUtils.MASS_PER_XP;
                        e.setDead();
                        massChanged = true;
                    } else if (e instanceof EntityPlayer) {
                        e.attackEntityFrom(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
                        if (e.isDead) { mass += BlackHoleUtils.MASS_PER_PLAYER; massChanged = true; }
                    } else {
                        try { e.attackEntityFrom(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE); }
                        catch (Exception ignored) {}
                        if (!e.isDead) e.setDead();
                        mass += BlackHoleUtils.MASS_PER_ENTITY;
                        massChanged = true;
                    }
                } catch (Exception ex) { ex.printStackTrace(); }
                continue;
            }

            if (dist > gravRange) continue;
            if (accel < BlackHoleUtils.MIN_ACCEL) continue;

            // --- Motion ---
            double maxAccel = Math.min(accel, dist * 0.45) * stride;
            if (maxAccel > 3.0) maxAccel = 3.0;

            double nx = dx / dist, ny = dy / dist, nz = dz / dist;
            e.motionX += nx * maxAccel * 0.35;
            e.motionY += ny * maxAccel * 0.35;
            e.motionZ += nz * maxAccel * 0.35;

            double speed = Math.sqrt(e.motionX*e.motionX + e.motionY*e.motionY + e.motionZ*e.motionZ);
            double maxSpeed = 2.5;
            if (speed > maxSpeed) {
                double s = maxSpeed / speed;
                e.motionX *= s; e.motionY *= s; e.motionZ *= s;
            }
            e.fallDistance = 0;
            e.velocityChanged = true;

            if (e instanceof EntityPlayerMP) {
                // Players aren't covered by vanilla entity velocity tracking, and
                // the local client doesn't interpolate server-set motion the way
                // it does for remote entities. Push every processed tick.
                // Threshold removed so tiny accumulations aren't silently dropped.
                try {
                    ((EntityPlayerMP) e).connection.sendPacket(new net.minecraft.network.play.server.SPacketEntityVelocity(e));
                } catch (Exception ignored) {}
            }
        }
        if (massChanged) { markDirty(); syncCooldown = 0; }
    }
}
