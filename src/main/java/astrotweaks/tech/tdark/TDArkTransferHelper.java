package astrotweaks.tech.tdark;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.entity.EntityLivingBase;

import net.minecraft.world.Teleporter;

import astrotweaks.tech.ATTechnologies;
import astrotweaks.tech.qts.SuppressorManager;
import astrotweaks.AstrotweaksMod;
import astrotweaks.Multiverse.LevelManager;
import astrotweaks.Multiverse.LevelData;
import astrotweaks.Multiverse.LevelDimensionType;
import astrotweaks.Multiverse.MessageMultiverse;
import astrotweaks.Multiverse.MultiverseDims;
import astrotweaks.Multiverse.MultiverseEvents;
import astrotweaks.Multiverse.MultiverseTeleporter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;




public class TDArkTransferHelper {
    // Область переноса TDARK: ±7 X/Z, ±5 Y от ядра (куб 15x11x15)
    public static final int AREA_RX = 7, AREA_RY = 5, AREA_RZ = 7;

    /**
     * Отправляет сообщение всем игрокам в области переноса.
     * Если corePos == null, сообщение отправляется только инициатору.
     */
    public static void broadcastToArea(World world, BlockPos corePos, EntityPlayerMP initiator, net.minecraft.util.text.ITextComponent message) {
        if (world.isRemote) return;
        if (corePos == null) {
            initiator.sendMessage(message);
            return;
        }
        AxisAlignedBB area = new AxisAlignedBB(corePos).grow(AREA_RX, AREA_RY, AREA_RZ);
        boolean initiatorReached = false;
        for (EntityPlayer p : world.getEntitiesWithinAABB(EntityPlayer.class, area)) {
            p.sendMessage(message);
            if (p == initiator) initiatorReached = true;
        }
        if (!initiatorReached) {
            initiator.sendMessage(message);
        }
    }

    // Helper class for saving blocks
    public static class BlockSave {
        public IBlockState state;
        public NBTTagCompound teNBT;
        public BlockSave(IBlockState state, NBTTagCompound teNBT) {
            this.state = state;
            this.teNBT = teNBT;
        }
    }
    // Custom Teleporter for entity relocation
    public static class CustomTeleporter extends Teleporter {
        private final double offsetX, offsetY, offsetZ;
        private final double targetCoreX, targetCoreY, targetCoreZ;

        public CustomTeleporter(WorldServer world, double targetCoreX, double targetCoreY, double targetCoreZ, double offsetX, double offsetY, double offsetZ) {
            super(world);
            this.targetCoreX = targetCoreX;
            this.targetCoreY = targetCoreY;
            this.targetCoreZ = targetCoreZ;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }

        @Override
        public void placeEntity(World world, Entity entity, float currentPortalY) {
		    double newX = targetCoreX + offsetX;
		    double newY = targetCoreY + offsetY;
		    double newZ = targetCoreZ + offsetZ;

            entity.setPositionAndUpdate(newX, newY, newZ);
            entity.motionX = 0.0D;
            entity.motionY = 0.0D;
            entity.motionZ = 0.0D;

            // Preserve rotation (already on the entity)
			if (entity instanceof EntityLivingBase) {
			    EntityLivingBase elb = (EntityLivingBase) entity;
			    elb.setRotationYawHead(elb.rotationYaw);
			    elb.prevRotationYawHead = elb.rotationYaw;
			    elb.setRenderYawOffset(elb.rotationYaw);
			}
        }

        @Override
        public boolean isVanilla() {
            return false; // Custom teleporter
        }
    }

    public static void performTeleport(EntityPlayerMP player,World world,BlockPos termPos,BlockTDArk.TileEntityCustom teTDArk,int targetDim,int targetX,int targetY,int targetZ,
					boolean clearMode, boolean captureEntities, boolean captureItems) {
		//////////

	    BlockPos corePos = findCore(world, termPos);
	    if (corePos == null) {
	        player.sendMessage(new TextComponentTranslation("ark.err.structure"));
	        return;
	    }
	    if (!DimensionManager.isDimensionRegistered(targetDim)) {
	        broadcastToArea(world, corePos, player, new TextComponentTranslation("ark.err.dim", targetDim));
	        return;
	    }
	    
		// ########## Checking the suppressor in the source world
		if (SuppressorManager.isPositionBlocked(world, corePos)) {
		    broadcastToArea(world, corePos, player, new TextComponentTranslation("qts.no_tp"));
		    return;
		}

        // 3. Checking the boundaries of the world (taking into account Y+2 for the kernel)
        int coreTargetY = targetY + 2; // Y coord of the core in the new location
        int minY = coreTargetY - 5;
        int maxY = coreTargetY + 5;
        final int border = 29999990;
        if ((minY < 3 || maxY > 253) || (Math.abs(targetX) > border || Math.abs(targetZ) > border)) {
            broadcastToArea(world, corePos, player, new TextComponentTranslation("ark.err.aow"));
            return;
        }
        // Check X,Z boundaries (standard)

        // 4. Get target world (use getOrCreateWorld to rebuild if it was idle-unloaded
        //    during the delay, instead of vanilla getWorld which creates a phantom)
	    LevelManager lm = LevelManager.getInstance();
	    LevelData targetLevel = lm.getLevelByDimensionId(targetDim);
	    WorldServer targetWorld;
	    if (targetDim == MultiverseDims.GLOBAL_DIM) {
	        // The global void world (-1000000) lives in MULTIVERSE_GLOBAL, never in a
	        // save-local DIM-1000000. Load it through LevelManager so vanilla/Forge
	        // cannot create a phantom WorldServerMulti in the save root.
	        targetWorld = lm.getOrCreateGlobalWorld(player.getServer());
	    } else if (targetLevel != null) {
	        LevelDimensionType type = targetLevel.typeOf(targetDim);
	        if (type != null) {
	            targetWorld = lm.getOrCreateWorld(player.getServer(), targetLevel, type);
	        } else {
	            targetWorld = player.getServer().getWorld(targetDim);
	        }
	    } else {
	        targetWorld = player.getServer().getWorld(targetDim);
	    }
	    if (targetWorld == null) {
	        broadcastToArea(world, corePos, player, new TextComponentTranslation("ark.err.target_world"));
	        return;
	    }

	    // Send MessageMultiverse right before the teleport so the client's seed-stamp
	    // fires on the NEW WorldClient (deferred to next tick via addScheduledTask),
	    // not on the old one 100 ticks early.
	    if (targetDim == MultiverseDims.GLOBAL_DIM) {
	        AstrotweaksMod.PACKET_HANDLER.sendTo(MessageMultiverse.forGlobal(), player);
	    } else if (targetLevel != null) {
	        AstrotweaksMod.PACKET_HANDLER.sendTo(new MessageMultiverse(targetLevel.baseId, targetLevel.seed), player);
	    }

		// Загружаем чанки цели ДО проверок подавителя (>>4: чунки 16x16)
	    int minChunkX = targetX - 7 >> 4;
	    int maxChunkX = targetX + 7 >> 4;
	    int minChunkZ = targetZ - 7 >> 4;
	    int maxChunkZ = targetZ + 7 >> 4;
	    for (int cx = minChunkX; cx <= maxChunkX; cx++) {
	        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
	            targetWorld.getChunkProvider().provideChunk(cx, cz);
	        }
	    }

        // 5. Defining areas (куб 15x11x15 блоков)
	    BlockPos corePosTarget = new BlockPos(targetX, coreTargetY, targetZ);
	    BlockPos sourceMin = corePos.add(-7, -5, -7);
	    BlockPos sourceMax = corePos.add(7, 5, 7);
	    BlockPos targetMin = corePosTarget.add(-7, -5, -7);
	    BlockPos targetMax = corePosTarget.add(7, 5, 7);
        // Переносим куб 15x11x15 блоков
		
		if (isSuppressorInArea(targetWorld, targetMin, targetMax)) {
		    broadcastToArea(world, corePos, player, new TextComponentTranslation("qts.tp_interrupted")); // Целевая область защищена подавителем
		    return;
		}

		// ########## Checking the suppressor in the target world (after receiving targetWorld and corePosTarget)
		if (SuppressorManager.isPositionBlocked(targetWorld, corePosTarget)) {
		    broadcastToArea(world, corePos, player, new TextComponentTranslation("qts.no_tp_target"));
		    return;
		}

        // 6. Check for unbreakable blocks in the target area
        for (BlockPos p : BlockPos.getAllInBoxMutable(targetMin, targetMax)) {
            IBlockState state = targetWorld.getBlockState(p);
            if (state.getBlockHardness(targetWorld, p) < 0) { // hardness < 0 => unbreakable
            	System.out.println("[TDArk] ERROR: Unbreakable block at " + p);
                broadcastToArea(world, corePos, player, new TextComponentTranslation("ark.err.unbreakable_target",  state.getBlock().getLocalizedName()));
                return;
            }
        }
        // Target area clear of unbreakable blocks

        // 6.5 Потребляем 1 алмаз из инвентаря машины (слот с наименьшим номером).
        if (!teTDArk.consumeDiamond()) {
            broadcastToArea(world, corePos, player, new TextComponentTranslation("tdark.err.item"));
            return;
        }

        // 7. Save blocks and TileEntity from the source
	    List<BlockSave> blocksToMove = new ArrayList<>();
	    for (BlockPos p : BlockPos.getAllInBoxMutable(sourceMin, sourceMax)) {
	        IBlockState state = world.getBlockState(p);
	        TileEntity te = world.getTileEntity(p);
	        NBTTagCompound teNBT = null;
	        if (te != null) {
	            teNBT = te.serializeNBT();
	            teNBT.removeTag("x");
	            teNBT.removeTag("y");
	            teNBT.removeTag("z");
	        }
	        blocksToMove.add(new BlockSave(state, teNBT));
	    }

        // 8. Collect entities in the area (except for players)
	    List<Entity> entitiesInArea = world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(sourceMin, sourceMax.add(1, 1, 1)));
	    List<Entity> entitiesToTransfer = new ArrayList<>();
	    List<EntityPlayer> playersInArea = new ArrayList<>();
	    for (Entity entity : entitiesInArea) {
		    boolean isItem = entity instanceof EntityItem;
		    if (isItem && !captureItems) continue;
		    if (!(entity instanceof EntityPlayer) && !isItem && !captureEntities) continue;

	        if (entity instanceof EntityPlayer) {
	            playersInArea.add((EntityPlayer) entity);
	        } else {
	            entitiesToTransfer.add(entity);
	        }
	    }
	    // Ensure the initiator always receives notification messages
	    if (!playersInArea.contains(player)) {
	        playersInArea.add(player);
	    }

		// Loading chunks of the target area (done earlier, before the suppressor checks).
        // 9. Clear the target area (destroy all blocks)
        if (clearMode) {
            // destroy mode
            for (BlockPos p : BlockPos.getAllInBoxMutable(targetMin, targetMax)) {
                targetWorld.destroyBlock(p, true);
            }
        } else {
            // replace mode
            for (BlockPos p : BlockPos.getAllInBoxMutable(targetMin, targetMax)) {
                targetWorld.destroyBlock(p, false);
            }
        }

        // 10. Place blocks in the target area
	    int index = 0;
	    for (BlockPos p : BlockPos.getAllInBoxMutable(targetMin, targetMax)) {
	        BlockSave save = blocksToMove.get(index++);
	        IBlockState state = save.state;

		    // 1) place block
		    targetWorld.setBlockState(p, state, 3);

		    // 2) If there is NBT, restore TE correctly:
		    if (save.teNBT != null) {
		        // Insert coordinates into NBT (VERY IMPORTANT)
		        save.teNBT.setInteger("x", p.getX());
		        save.teNBT.setInteger("y", p.getY());
		        save.teNBT.setInteger("z", p.getZ());

		        // Delete the existing TE (if any)
		        targetWorld.removeTileEntity(p);

		        // Create a new TE from NBT
	            TileEntity newTe = TileEntity.create(targetWorld, save.teNBT);
	            if (newTe != null) {
	                targetWorld.setTileEntity(p, newTe);
	                newTe.validate();
	                newTe.markDirty();
	            }
		    }
			// Explicitly synchronize all TileEntities with client	
		    // Notify the world a bit more strongly (instead of just markBlockRangeForRenderUpdate)
	        targetWorld.notifyBlockUpdate(p, state, state, 3);
	        if (targetWorld instanceof WorldServer) {
	            ((WorldServer) targetWorld).getPlayerChunkMap().markBlockForUpdate(p);
	        }
		}

		// 12. Teleport players
		int sourceDim = world.provider.getDimension();
		for (EntityPlayer playerX : playersInArea) {
		    if (playerX instanceof EntityPlayerMP) {
		        EntityPlayerMP mp = (EntityPlayerMP) playerX;

		        double newX = corePosTarget.getX() + (playerX.posX - corePos.getX());
		        double newY = corePosTarget.getY() + (playerX.posY - corePos.getY() + 0.1);
		        double newZ = corePosTarget.getZ() + (playerX.posZ - corePos.getZ());

		        if (sourceDim == targetDim) {
		            // Same dimension - just move it
		            mp.setPositionAndUpdate(newX, newY, newZ);

		            // Synchronize head rotation (like entities)
		            mp.setRotationYawHead(mp.rotationYaw);
		            mp.prevRotationYawHead = mp.rotationYaw;
		            mp.setRenderYawOffset(mp.rotationYaw);
		        } else if (targetDim == MultiverseDims.GLOBAL_DIM) {
		            // The global void world (-1000000) is managed by LevelManager and lives
		            // in MULTIVERSE_GLOBAL. Go through the MV teleporter so vanilla/Forge
		            // cannot spin up a save-local DIM-1000000 phantom WorldServerMulti.
		            Entity moved = MultiverseEvents.teleportIgnoringPortalRemap(mp, targetDim,
		                    new MultiverseTeleporter(new BlockPos((int) newX, (int) newY, (int) newZ)));
		            EntityPlayerMP mm = moved instanceof EntityPlayerMP ? (EntityPlayerMP) moved : mp;
		            mm.connection.setPlayerLocation(newX, newY, newZ, mm.rotationYaw, mm.rotationPitch);
		            mm.setRotationYawHead(mm.rotationYaw);
		            mm.prevRotationYawHead = mm.rotationYaw;
		            mm.setRenderYawOffset(mm.rotationYaw);
		        } else {
		            // Different dimensions - use the standard method
		            MinecraftServer server = player.getServer();
		            server.getPlayerList().transferPlayerToDimension(mp, targetDim, 
		                new TeleporterDirectWrapper(targetDim, server, newX, newY, newZ));
		        }
		    }
		}

        // 11. Transfer entities with dimension check
        //int sourceDim = world.provider.getDimension();
        for (Entity entity : entitiesToTransfer) {
            double dx = entity.posX - corePos.getX();
            double dy = entity.posY - corePos.getY();
            double dz = entity.posZ - corePos.getZ();
            double newX = corePosTarget.getX() + dx;
            double newY = corePosTarget.getY() + dy;
            double newZ = corePosTarget.getZ() + dz;

            boolean success = false;
            if (sourceDim == targetDim) {
                // Same dimension: direct reposition (no changeDimension to avoid UUID conflict)
		        entity.setPositionAndUpdate(newX, newY, newZ);
		        entity.motionX = 0.0D;
		        entity.motionY = 0.0D;
		        entity.motionZ = 0.0D;

		        if (entity instanceof EntityLivingBase) {
		            EntityLivingBase living = (EntityLivingBase) entity;
		            living.setRotationYawHead(living.rotationYaw);
		            living.prevRotationYawHead = living.rotationYaw;
		            living.setRenderYawOffset(living.rotationYaw);
		        }
                if (targetWorld instanceof WorldServer) {
                    ((WorldServer) targetWorld).getPlayerChunkMap().markBlockForUpdate(new BlockPos(newX, newY, newZ)); // Update chunk
                }
		        success = true;
		        System.out.println("[TDArk] Repositioned entity in same dim: " + entity);
		    } else if (targetDim == MultiverseDims.GLOBAL_DIM) {
                // Global void world: use the MV teleporter so vanilla/Forge cannot
                // create a save-local DIM-1000000 phantom WorldServerMulti.
				Entity newEntity = MultiverseEvents.teleportIgnoringPortalRemap(entity, targetDim,
				        new MultiverseTeleporter(new BlockPos((int) newX, (int) newY, (int) newZ)));
				if (newEntity != null && !newEntity.isDead) {
				    newEntity.setPositionAndUpdate(newX, newY, newZ);
				    newEntity.motionX = 0.0D;
				    newEntity.motionY = 0.0D;
				    newEntity.motionZ = 0.0D;
				    if (newEntity instanceof EntityLivingBase) {
				        EntityLivingBase living = (EntityLivingBase) newEntity;
				        living.setRotationYawHead(living.rotationYaw);
				        living.prevRotationYawHead = living.rotationYaw;
				        living.setRenderYawOffset(living.rotationYaw);
		            }
		            success = true;
                } else {
                    System.out.println("[TDArk] Failed to transfer entity to global dim: " + entity);
                }
            } else {
                // Different dimension: use changeDimension with default teleporter, then adjust pos
                // Temporarily set pos in entity before transfer (teleporter will override if custom, but we use default)
                //entity.setPositionAndUpdate(newX, newY, newZ); // Pre-set for safety
				CustomTeleporter teleporter = new CustomTeleporter(targetWorld, corePosTarget.getX(), corePosTarget.getY(), corePosTarget.getZ(), dx, dy, dz);
				Entity newEntity = entity.changeDimension(targetDim, teleporter);
				if (newEntity != null) {
				    newEntity.motionX = 0.0D;
				    newEntity.motionY = 0.0D;
				    newEntity.motionZ = 0.0D;
				    if (newEntity instanceof EntityLivingBase) {
				        EntityLivingBase living = (EntityLivingBase) newEntity;
				        living.setRotationYawHead(living.rotationYaw);
				        living.prevRotationYawHead = living.rotationYaw;
				        living.setRenderYawOffset(living.rotationYaw);
		            }
                } else {
                    System.out.println("[TDArk] Failed to transfer entity to different dim: " + entity);
                }
            }
            if (!success) {
                System.out.println("[TDArk] Entity transfer failed: " + entity);
            }
        }

        // 13. Remove blocks in the source (replace with air)
        for (BlockPos p : BlockPos.getAllInBoxMutable(sourceMin, sourceMax)) {
		    if (world.getTileEntity(p) != null) {
		        world.removeTileEntity(p);
		    }
            world.setBlockState(p, Blocks.AIR.getDefaultState(), 3);
            world.getChunkFromBlockCoords(p).markDirty();
        }
        // Notify the players in the transfer area
        TextComponentTranslation successMsg = new TextComponentTranslation("tdark.success");
        for (EntityPlayer areaPlayer : playersInArea) {
            areaPlayer.sendMessage(successMsg);
        }

    }

	/**
	 * Saves the 15x11x15 transfer area (blocks + tile entities) together with all
	 * entities/players inside it AND their offsets from the core position. Used by the
	 * synchronous proxy flow that runs BEFORE a universe is recycled, so the captured
	 * data survives the source world being deleted.
	 */
	public static TeleportData saveTeleportData(World world, BlockPos corePos, boolean captureEntities, boolean captureItems) {
	    BlockPos sourceMin = corePos.add(-7, -5, -7);
	    BlockPos sourceMax = corePos.add(7, 5, 7);

	    List<BlockSave> blocksToMove = new ArrayList<>();
	    for (BlockPos p : BlockPos.getAllInBoxMutable(sourceMin, sourceMax)) {
	        IBlockState state = world.getBlockState(p);
	        TileEntity te = world.getTileEntity(p);
	        NBTTagCompound teNBT = null;
	        if (te != null) {
	            teNBT = te.serializeNBT();
	            teNBT.removeTag("x");
	            teNBT.removeTag("y");
	            teNBT.removeTag("z");
	        }
	        blocksToMove.add(new BlockSave(state, teNBT));
	    }

	    List<Entity> entitiesInArea = world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(sourceMin, sourceMax.add(1, 1, 1)));
	    List<Parked> parked = new ArrayList<>();
	    for (Entity entity : entitiesInArea) {
	        boolean isItem = entity instanceof EntityItem;
	        if (isItem && !captureItems) continue;
	        if (!(entity instanceof EntityPlayer) && !isItem && !captureEntities) continue;
	        parked.add(new Parked(entity.getUniqueID(), entity instanceof EntityPlayer, 
	                entity.posX - corePos.getX(), entity.posY - corePos.getY(), entity.posZ - corePos.getZ()));
	    }

	    return new TeleportData(blocksToMove, parked, corePos);
	}

	/**
	 * Places a previously saved transfer area into the freshly (re)created universe and
	 * teleports every parked player/entity from the proxy dimension to the exact spot
	 * (target core + their original offset from the source core). Must be called after
	 * the new target world is built. Returns true when the block transfer succeeded.
	 */
	public static boolean placeRecycledTeleportData(MinecraftServer server, LevelData targetLevel, WorldServer targetWorld,
	        int targetDim, BlockPos corePosTarget, TeleportData data, boolean clearMode) {
	    BlockPos targetMin = corePosTarget.add(-7, -5, -7);
	    BlockPos targetMax = corePosTarget.add(7, 5, 7);

	    int minChunkX = targetMin.getX() >> 4;
	    int maxChunkX = targetMax.getX() >> 4;
	    int minChunkZ = targetMin.getZ() >> 4;
	    int maxChunkZ = targetMax.getZ() >> 4;
	    for (int cx = minChunkX; cx <= maxChunkX; cx++) {
	        for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
	            targetWorld.getChunkProvider().provideChunk(cx, cz);
	        }
	    }

	    if (isSuppressorInArea(targetWorld, targetMin, targetMax)) {
	        return false;
	    }
	    if (SuppressorManager.isPositionBlocked(targetWorld, corePosTarget)) {
	        return false;
	    }
	    for (BlockPos p : BlockPos.getAllInBoxMutable(targetMin, targetMax)) {
	        IBlockState state = targetWorld.getBlockState(p);
	        if (state.getBlockHardness(targetWorld, p) < 0) {
	            return false;
	        }
	    }

	    if (clearMode) {
	        for (BlockPos p : BlockPos.getAllInBoxMutable(targetMin, targetMax)) {
	            targetWorld.destroyBlock(p, true);
	        }
	    } else {
	        for (BlockPos p : BlockPos.getAllInBoxMutable(targetMin, targetMax)) {
	            targetWorld.destroyBlock(p, false);
	        }
	    }

	    int index = 0;
	    for (BlockPos p : BlockPos.getAllInBoxMutable(targetMin, targetMax)) {
	        BlockSave save = data.blocks.get(index++);
	        IBlockState state = save.state;
	        targetWorld.setBlockState(p, state, 3);
	        if (save.teNBT != null) {
	            save.teNBT.setInteger("x", p.getX());
	            save.teNBT.setInteger("y", p.getY());
	            save.teNBT.setInteger("z", p.getZ());
	            targetWorld.removeTileEntity(p);
	            TileEntity newTe = TileEntity.create(targetWorld, save.teNBT);
	            if (newTe != null) {
	                targetWorld.setTileEntity(p, newTe);
	                newTe.validate();
	                newTe.markDirty();
	            }
	        }
	        targetWorld.notifyBlockUpdate(p, state, state, 3);
	        if (targetWorld instanceof WorldServer) {
	            ((WorldServer) targetWorld).getPlayerChunkMap().markBlockForUpdate(p);
	        }
	    }

// Teleport parked players/entities from the proxy into the fresh universe.
	    // Deferred one tick: the park-in-proxy already performed a changeDimension in this
	    // tick, and a second one immediately after is unsafe in Forge 1.12.2.
	    server.addScheduledTask(() -> {
	        WorldServer proxyWorld = DimensionManager.getWorld(MultiverseDims.PROXY_DIM);
	        for (Parked parked : data.parked) {
	            if (proxyWorld == null) break;
	            Entity found = parked.isPlayer ? proxyWorld.getPlayerEntityByUUID(parked.uuid) : proxyWorld.getEntityFromUuid(parked.uuid);
	            if (found == null || found.isDead) continue;

	            double newX = corePosTarget.getX() + parked.dx;
	            double newY = corePosTarget.getY() + parked.dy;
	            double newZ = corePosTarget.getZ() + parked.dz;

	            if (parked.isPlayer) {
	                EntityPlayerMP mp = (EntityPlayerMP) found;
	                AstrotweaksMod.PACKET_HANDLER.sendTo(new MessageMultiverse(targetLevel.baseId, targetLevel.seed), mp);
	                Entity moved = MultiverseEvents.teleportIgnoringPortalRemap(mp, targetDim,
	                        new MultiverseTeleporter(new BlockPos((int) newX, (int) newY, (int) newZ)));
	                if (moved instanceof EntityPlayerMP) {
	                    EntityPlayerMP mm = (EntityPlayerMP) moved;
	                    mm.connection.setPlayerLocation(newX, newY, newZ, mm.rotationYaw, mm.rotationPitch);
	                    mm.setRotationYawHead(mm.rotationYaw);
	                    mm.prevRotationYawHead = mm.rotationYaw;
	                    mm.setRenderYawOffset(mm.rotationYaw);
	                    mm.fallDistance = 0.0F;
	                }
	            } else {
	                CustomTeleporter teleporter = new CustomTeleporter(targetWorld,
	                        corePosTarget.getX(), corePosTarget.getY(), corePosTarget.getZ(),
	                        parked.dx, parked.dy, parked.dz);
	                Entity newEntity = MultiverseEvents.teleportIgnoringPortalRemap(found, targetDim, teleporter);
	                if (newEntity != null) {
	                    newEntity.motionX = 0.0D;
	                    newEntity.motionY = 0.0D;
	                    newEntity.motionZ = 0.0D;
	                    if (newEntity instanceof EntityLivingBase) {
	                        EntityLivingBase living = (EntityLivingBase) newEntity;
	                        living.setRotationYawHead(living.rotationYaw);
	                        living.prevRotationYawHead = living.rotationYaw;
	                        living.setRenderYawOffset(living.rotationYaw);
	                    }
	                }
	            }
	        }
	    });
	    return true;
	}

	/** Captured 15x11x15 area (blocks + parked players/entities with core-relative offsets). */
	public static class TeleportData {
	    public final List<BlockSave> blocks;
	    public final List<Parked> parked;
	    public final BlockPos corePos;
	    public TeleportData(List<BlockSave> blocks, List<Parked> parked, BlockPos corePos) {
	        this.blocks = blocks;
	        this.parked = parked;
	        this.corePos = corePos;
	    }
	}

	/** A player or entity that must be moved through the proxy, with its offset from the source core. */
	public static class Parked {
	    public final UUID uuid;
	    public final boolean isPlayer;
	    public final double dx, dy, dz;
	    public Parked(UUID uuid, boolean isPlayer, double dx, double dy, double dz) {
	        this.uuid = uuid;
	        this.isPlayer = isPlayer;
	        this.dx = dx;
	        this.dy = dy;
	        this.dz = dz;
	    }
	}

	/**
	 * Moves every player/entity recorded in {@code data} from the source world into the
	 * proxy dimension, so the source universe can be recycled without stranding them.
	 * {@link #placeRecycledTeleportData} later brings them into the fresh universe.
	 */
	public static void parkInProxy(World world, TeleportData data) {
	    WorldServer proxy = LevelManager.getInstance().getOrCreateProxyWorld(world.getMinecraftServer());
	    if (proxy == null) return;
	    WorldServer sourceWorld = (WorldServer) world;
	    BlockPos parkSpot = new BlockPos(0, 64, 0);
	    for (Parked parked : data.parked) {
	        Entity source = parked.isPlayer
	                ? sourceWorld.getPlayerEntityByUUID(parked.uuid)
	                : sourceWorld.getEntityFromUuid(parked.uuid);
	        if (source == null || source.isDead) continue;
	        if (parked.isPlayer) {
	            AstrotweaksMod.PACKET_HANDLER.sendTo(MessageMultiverse.forProxy(), (EntityPlayerMP) source);
	        }
	        MultiverseEvents.teleportIgnoringPortalRemap(source, MultiverseDims.PROXY_DIM,
	                new MultiverseTeleporter(parkSpot));
	    }
	}

	// Search for core structure (QM block and resonator) 
	public static BlockPos findCore(World world, BlockPos termPos) {
	    BlockPos up1 = termPos.up();       // +1
	    BlockPos up2 = termPos.up(2);      // +2
	    BlockPos down1 = termPos.down();  // -1
	    BlockPos down2 = termPos.down(2);  // -2

	    IBlockState stateUp1 = world.getBlockState(up1);
	    IBlockState stateUp2 = world.getBlockState(up2);
	    IBlockState stateDown1 = world.getBlockState(down1);
	    IBlockState stateDown2 = world.getBlockState(down2);
	    Block qmBlock = Block.getBlockFromName("astrotweaks:qm_block");
	    Block resBlock = Block.getBlockFromName("astrotweaks:ark_resonator");
	    if (stateUp1.getBlock() == qmBlock && stateUp2.getBlock() == resBlock) {
	        return up1;
	    }
	    if (stateDown1.getBlock() == qmBlock && stateDown2.getBlock() == resBlock) {
	        return down1;
	    }
	    return null;
	}

    private static class TeleporterDirectWrapper extends Teleporter {
        private final double wx, wy, wz;
        public TeleporterDirectWrapper(int dimension, MinecraftServer server, double wx, double wy, double wz) {
            super(server.getWorld(dimension));
            this.wx = wx;
            this.wy = wy;
            this.wz = wz;
        }
        @Override public void placeInPortal(Entity entity,float yawRotation) { entity.setPosition(wx, wy, wz); }
        @Override public boolean placeInExistingPortal(Entity entity,float yawRotation) { return true; }
        @Override public boolean makePortal(Entity entity) { return true; }
    }
	private static boolean isSuppressorInArea(World world, BlockPos min, BlockPos max) {
	    for (BlockPos p : BlockPos.getAllInBoxMutable(min, max)) {
	        if (world.getBlockState(p).getBlock() == ATTechnologies.QTP_SUPRESSOR) {
	            return true;
	        }
	    }
	    return false;
	}
}
