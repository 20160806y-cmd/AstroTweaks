package astrotweaks.block;


import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumHand;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.SoundType;
import net.minecraft.block.Block;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.List;



public class BlockUKillerBlock {
	public static final Block block = new BlockCustom().setRegistryName("astrotweaks", "u_killer_block");
	public static class BlockCustom extends Block {
		public BlockCustom() {
			super(Material.IRON, MapColor.IRON);
			setUnlocalizedName("u_killer_block");
			setSoundType(SoundType.METAL);
			setHardness(1000F);
			setResistance(1000F);
			setCreativeTab(astrotweaks.creativetab.ATCreativeTabs.ASTRO_TWEAKS_CT);
			setBlockUnbreakable();
		}
		@Override
		public void neighborChanged(IBlockState state, World world, BlockPos pos, Block neighborBlock, BlockPos fromPos) {
			super.neighborChanged(state, world, pos, neighborBlock, fromPos);
			if (!world.isRemote && world.isBlockIndirectlyGettingPowered(pos) > 0) {
				KillXArea(world, pos);
			}
		}
		@Override
		public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer entity, EnumHand hand, EnumFacing direction,
				float hitX, float hitY, float hitZ) {
			if (!world.isRemote) {
				KillXArea(world, pos);
			}
			return true;
		}
		private static void KillXArea(World world, BlockPos pos) {
			if (world == null || pos == null || world.isRemote) return;
			// Define 21x21x21 cube centered on block: from -10 to +10 inclusive
			AxisAlignedBB box = new AxisAlignedBB(
					pos.getX() - 10, pos.getY() - 10, pos.getZ() - 10,
					pos.getX() + 11, pos.getY() + 11, pos.getZ() + 11); // +11 because AABB is exclusive on max side

			List<Entity> entities = world.getEntitiesWithinAABB(Entity.class, box, e -> !(e instanceof EntityPlayer));
			for (Entity e : entities) {
				if (e.isDead) continue;
				
				if (e instanceof EntityDragon) {
					killDragon((EntityDragon) e);   // настоящая смерть + портал + яйцо + снятие боссбара
				} else {
					e.setDead();                    // как было: мгновенно, без дропа, без анимации
				}
					}
			// remove the block itself
			world.setBlockToAir(pos);
		}
	}
	private static void killDragon(EntityDragon dragon) {
		dragon.onKillCommand();
	}
}
