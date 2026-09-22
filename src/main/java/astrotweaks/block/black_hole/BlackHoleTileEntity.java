package astrotweaks.block.black_hole;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class BlackHoleTileEntity extends TileEntity implements ITickable {

    public static final String TAG_MASS = "mass";

    private double mass = BlackHoleUtils.DEFAULT_MASS;
    private int syncCooldown = 0;

    public double getMass() { return mass; }

    public void setMass(double m) {
        if (m < 1) m = 1;
        this.mass = m;
        markDirty();
        // force sync soon
        syncCooldown = 0;
    }

    public void addMass(double delta) {
        setMass(this.mass + delta);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey(TAG_MASS)) {
            this.mass = nbt.getDouble(TAG_MASS);
            if (nbt.hasKey(TAG_MASS, 3) || nbt.hasKey(TAG_MASS, 4)) {
                // also handle int/long legacy
                // already read as double, fine
            }
        }
        // legacy int tag
        if (nbt.hasKey("Mass")) {
            this.mass = nbt.getDouble("Mass");
        }
        if (this.mass < 1) this.mass = BlackHoleUtils.DEFAULT_MASS;
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
    public AxisAlignedBB getRenderBoundingBox() {
        double h = BlackHoleUtils.getHorizonRadius(mass);
        double t = BlackHoleUtils.getHaloThickness(h);
        double r = h + t * 2 + 1.0D;
        double rad = Math.max(r, 2.0D);
        return new AxisAlignedBB(pos).grow(rad + 1, rad + 1, rad + 1);
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 0 || pass == 1;
    }

    // =================================================================
    // Server tick - gravity & absorption
    // =================================================================
    @Override
    public void update() {
        if (world == null) return;
        if (world.isRemote) {
            // client could do particle / lens animation ticks, not needed
            return;
        }
        // Only server gravity
        double horizon = BlackHoleUtils.getHorizonRadius(mass);
        double gravRange = BlackHoleUtils.getGravityRange(mass);
        if (gravRange < 0.5) return;

        // center of block
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 0.5D;
        double cz = pos.getZ() + 0.5D;

        AxisAlignedBB aabb = new AxisAlignedBB(
                cx - gravRange, cy - gravRange, cz - gravRange,
                cx + gravRange, cy + gravRange, cz + gravRange);

        List<Entity> entities = world.getEntitiesWithinAABB(Entity.class, aabb, e -> e != null && !e.isDead && !(e instanceof net.minecraft.entity.player.EntityPlayerMP && ((EntityPlayer)e).isSpectator()));

        boolean massChanged = false;

        for (Entity e : entities) {
            // blacklist (e.g. squid)
            boolean blacklisted = false;
            for (Class<? extends Entity> cls : BlackHoleUtils.ENTITY_BLACKLIST) {
                if (cls.isInstance(e)) { blacklisted = true; break; }
            }
            if (blacklisted) continue;

            // players in creative/spectator immune
            if (e instanceof EntityPlayer) {
                EntityPlayer p = (EntityPlayer) e;
                if (p.isCreative() || p.isSpectator()) continue;
            }

            // skip dead / invalid already filtered by predicate but double-check
            if (e.isDead) continue;

            double ey = e.posY + e.height * 0.5D;
            if (e instanceof EntityItem) ey = e.posY + 0.25D;
            if (e instanceof EntityXPOrb) ey = e.posY + 0.25D;

            double dx = cx - e.posX;
            double dy = cy - ey;
            double dz = cz - e.posZ;

            double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (dist < 0.05) dist = 0.05;

            // Event horizon absorption
            if (dist <= horizon) {
                try {
                    if (e instanceof EntityItem) {
                        EntityItem ei = (EntityItem) e;
                        int count = ei.getItem().getCount();
                        if (count <= 0) count = 1;
                        mass += count * BlackHoleUtils.MASS_PER_ITEM;
                        e.setDead();
                        massChanged = true;
                    } else if (e instanceof EntityXPOrb) {
                        mass += BlackHoleUtils.MASS_PER_XP;
                        e.setDead();
                        massChanged = true;
                    } else if (e instanceof EntityPlayer) {
                        // don't force setDead on players - let vanilla death handling run
                        // prevents ghost entity where isDead=true but client still controls player -> gravity stops + flicker after failed absorb
                        e.attackEntityFrom(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
                        if (e.isDead) {
                            mass += BlackHoleUtils.MASS_PER_ENTITY;
                            massChanged = true;
                        } else {
                            // survived (totem / invuln ticks) - still count? No mass until actually dead
                            // keep entity alive, will be retried next tick inside horizon
                        }
                    } else {
                        // living / other - try damage first, fallback to setDead
                        boolean damaged = false;
                        try { damaged = e.attackEntityFrom(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE); } catch (Exception ignored) {}
                        if (!e.isDead) e.setDead();
                        mass += BlackHoleUtils.MASS_PER_ENTITY;
                        massChanged = true;
                    }
                } catch (Exception ex) {
                    // prevent whole tick abort -> flicker
                    ex.printStackTrace();
                }
                continue;
            }

            // Gravity pull
            if (dist > gravRange) continue;

            double accel = BlackHoleUtils.getAcceleration(mass, dist);
            if (accel < BlackHoleUtils.MIN_ACCEL) continue;

            // suffocation effect if accel > 0.5 (as if underwater)
            if (accel > 0.5D && e instanceof net.minecraft.entity.EntityLivingBase) {
                net.minecraft.entity.EntityLivingBase living = (net.minecraft.entity.EntityLivingBase) e;
                // drain air like underwater entity: 300 -> suffocate
                int air = living.getAir();
                air -= 6; // faster than vanilla (4 per tick under water)
                if (air < -20) {
                    air = 0;
                    living.attackEntityFrom(DamageSource.DROWN, 1.0F);
                }
                living.setAir(air);
            }

            double maxAccel = Math.min(accel, dist * 0.45D);
            if (maxAccel > 1.5D) maxAccel = 1.5D;

            double nx = dx / dist;
            double ny = dy / dist;
            double nz = dz / dist;

            e.motionX += nx * maxAccel * 0.35D;
            e.motionY += ny * maxAccel * 0.35D;
            e.motionZ += nz * maxAccel * 0.35D;

            double speed = Math.sqrt(e.motionX*e.motionX + e.motionY*e.motionY + e.motionZ*e.motionZ);
            double maxSpeed = 2.0D;
            if (speed > maxSpeed) {
                double s = maxSpeed / speed;
                e.motionX *= s;
                e.motionY *= s;
                e.motionZ *= s;
            }

            e.fallDistance = 0;
            e.velocityChanged = true;
            // for players, ensure velocity sync (client prediction otherwise causes jitter / no pull)
            if (e instanceof net.minecraft.entity.player.EntityPlayerMP) {
                net.minecraft.entity.player.EntityPlayerMP mp = (net.minecraft.entity.player.EntityPlayerMP) e;
                try {
                    mp.connection.sendPacket(new net.minecraft.network.play.server.SPacketEntityVelocity(e));
                } catch (Exception ignored) {}
            }
        }

        // ---- Block eating: random from nearest layer + next ----
        // Every 5 ticks, pick up to BLOCKS_PER_TICK blocks from (minLayer and minLayer+1)
        if ((world.getTotalWorldTime() + pos.hashCode()) % 5 == 0) {
            double captureR = BlackHoleUtils.getBlockCaptureRadius(mass);
            double scanR = Math.min(captureR, BlackHoleUtils.MAX_BLOCK_CAPTURE_RANGE);
            if (scanR < horizon + 0.5D) scanR = horizon + 0.5D;
            int r = (int) Math.ceil(scanR);
            if (r >= 1) {
                java.util.Map<Integer, java.util.ArrayList<BlockPos>> byLayer = new java.util.HashMap<>();
                int minLayer = Integer.MAX_VALUE;
                // scan cube
                for (int bx = -r; bx <= r; bx++) {
                    for (int by = -r; by <= r; by++) {
                        for (int bz = -r; bz <= r; bz++) {
                            if (bx == 0 && by == 0 && bz == 0) continue;
                            double bdist = Math.sqrt((double)bx*bx + (double)by*by + (double)bz*bz);
                            if (bdist > scanR + 0.1D) continue;
                            BlockPos bp = pos.add(bx, by, bz);
                            IBlockState st = world.getBlockState(bp);
                            Block blk = st.getBlock();
                            if (blk == Blocks.AIR) continue;
                            if (blk instanceof BlackHoleBlock) continue;
                            float hardness;
                            try { hardness = blk.getBlockHardness(st, world, bp); } catch (Exception ex) { continue; }
                            if (hardness < 0) continue;
                            boolean isLiquid = false;
                            try { isLiquid = st.getMaterial().isLiquid(); } catch (Exception ignored) {}
                            double effective = isLiquid ? 0.5D : (hardness == 0.0F ? 0.1D : hardness);
                            boolean insideHorizon = bdist <= horizon + 0.5D;
                            double accelAt = BlackHoleUtils.getAcceleration(mass, bdist);
                            if (!(insideHorizon || accelAt >= effective)) continue;
                            int layer = (int) Math.floor(bdist); // 1-block thick shell
                            if (layer < minLayer) minLayer = layer;
                            byLayer.computeIfAbsent(layer, k -> new java.util.ArrayList<>()).add(bp);
                        }
                    }
                }
                if (!byLayer.isEmpty()) {
                    java.util.ArrayList<BlockPos> pool = new java.util.ArrayList<>();
                    java.util.ArrayList<BlockPos> l0 = byLayer.get(minLayer);
                    if (l0 != null) pool.addAll(l0);
                    java.util.ArrayList<BlockPos> l1 = byLayer.get(minLayer + 1);
                    if (l1 != null) pool.addAll(l1);
                    if (!pool.isEmpty()) {
                        java.util.Collections.shuffle(pool, world.rand);
                        int toEat = Math.min(BlackHoleUtils.BLOCKS_PER_TICK, pool.size());
                        for (int i = 0; i < toEat; i++) {
                            BlockPos bp = pool.get(i);
                            IBlockState st = world.getBlockState(bp);
                            Block blk = st.getBlock();
                            if (blk == Blocks.AIR || blk instanceof BlackHoleBlock) continue;
                            float h;
                            try { h = blk.getBlockHardness(st, world, bp); } catch (Exception ex) { continue; }
                            if (h < 0) continue;
                            boolean isLiquid = false;
                            try { isLiquid = st.getMaterial().isLiquid(); } catch (Exception ignored) {}
                            double effective = isLiquid ? 0.5D : (h == 0.0F ? 0.1D : h);
                            // Use flag 2 + no neighbor notify for liquids to reduce flow blocking (see comment below)
                            // world.setBlockState(bp, Blocks.AIR.getDefaultState(), 2);
                            // For now use setBlockToAir (flag 3) for non-liquids, flag 2 for liquids
                            if (isLiquid) {
                                world.setBlockState(bp, Blocks.AIR.getDefaultState(), 2);
                                // also suppress pending fluid ticks in this pos by removing scheduled updates if any (best-effort)
                                // world.getPendingBlockUpdates() is not accessible in 1.12 without AT, so we just rely on flag 2
                            } else {
                                world.setBlockToAir(bp);
                            }
                            mass += effective;
                            massChanged = true;
                        }
                    }
                }
            }
        }

        if (massChanged) {
            markDirty();
            // sync to client occasionally
            syncCooldown = 0;
        }

        // periodic sync every 20 ticks if mass changed or just to keep clients updated
        syncCooldown--;
        if (syncCooldown <= 0) {
            syncCooldown = 20;
            if (!world.isRemote) {
                // notify block update for TESR horizon size update
                world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
                // Also mark for render update already done via notify
            }
        }
    }
}
