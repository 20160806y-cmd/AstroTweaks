
package astrotweaks.block;

import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraft.world.IBlockAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.SoundType;
import net.minecraft.block.BlockFalling;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityDragonFireball;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntityEvokerFangs;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.entity.projectile.EntityLlamaSpit;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.entity.projectile.EntityShulkerBullet;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.entity.projectile.EntitySpectralArrow;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAreaEffectCloud;
import net.minecraft.entity.EntityLeashKnot;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.item.EntityEnderEye;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.item.EntityExpBottle;
import net.minecraft.entity.item.EntityFireworkRocket;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCaveSpider;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEndermite;
import net.minecraft.entity.monster.EntityIllusionIllager;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityVex;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.passive.EntityParrot;
import net.minecraft.entity.passive.EntityRabbit;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.world.World;



public class BlockHeavyMine {
	public static final Block block = new BlockCustom().setRegistryName("astrotweaks", "heavy_mine");

    // Список запрещённых классов сущностей
    private static final Set<Class<? extends Entity>> BLACKLIST = new HashSet<>(Arrays.asList(
        EntityChicken.class,
        EntityItem.class,
        EntityArrow.class,
        EntityTippedArrow.class,   // стрелы с эффектами
        EntitySpectralArrow.class,
		EntityPotion.class,
		EntityBat.class,
		EntityXPOrb.class,
		EntityVex.class,
		EntityWolf.class, EntityOcelot.class, EntitySquid.class, EntitySpider.class, EntityCaveSpider.class, EntitySnowball.class, EntitySmallFireball.class,
		EntityAreaEffectCloud.class, EntityExpBottle.class, EntityShulkerBullet.class, EntityRabbit.class, EntityFireball.class, EntityCreeper.class,
		EntityEnderCrystal.class, EntityDragonFireball.class, EntityEnderPearl.class, EntityEnderEye.class, EntityEndermite.class, EntitySilverfish.class,
		EntityEgg.class, EntityEvokerFangs.class, EntityFireworkRocket.class, EntityItemFrame.class, EntityPainting.class, EntityParrot.class,
		EntityFishHook.class, EntityLeashKnot.class, EntityIllusionIllager.class, EntityLargeFireball.class, EntityLightningBolt.class, EntityArmorStand.class,
		EntityLlamaSpit.class, EntityBlaze.class
    ));
    private static boolean isForbidden(Entity entity) {
        if (entity == null) return false;
        for (Class<? extends Entity> clazz : BLACKLIST) {
            if (clazz.isInstance(entity)) return true;
        }
        return false;
    }


	public static class BlockCustom extends BlockFalling {
		public BlockCustom() {
			super(Material.CLOTH);
			setUnlocalizedName("heavy_mine");
			setSoundType(SoundType.METAL);
			setHarvestLevel("shovel", 0);
			setHardness(1F);
			setResistance(5F);
			setLightOpacity(0);
			setCreativeTab(astrotweaks.creativetab.ATCreativeTabs.ASTRO_TWEAKS_CT);
		}
		@SideOnly(Side.CLIENT)
		@Override public BlockRenderLayer getBlockLayer() { return BlockRenderLayer.CUTOUT_MIPPED; }
		@Override public boolean isOpaqueCube(IBlockState state) { return false; }
		@Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
			return new AxisAlignedBB(0.1875, 0.0, 0.1875, 0.8125, 0.1875, 0.8125);
		}
		@Override public boolean isFullCube(IBlockState state) { return false; }
		@Override public MapColor getMapColor(IBlockState state, IBlockAccess blockAccess, BlockPos pos) { return MapColor.AIR; }
        @Override
        public void onEntityCollidedWithBlock(World world, BlockPos pos, IBlockState state, Entity entity) {
            super.onEntityCollidedWithBlock(world, pos, state, entity);
            if (!world.isRemote && !isForbidden(entity)) {
                initExp(world, pos, entity);
            }
        }
	    @Override public void onBlockClicked(World world, BlockPos pos, EntityPlayer entity) {
	        super.onBlockClicked(world, pos, entity);
	        if (!world.isRemote) initExp(world, pos, entity);
	    }
	    @Override
	    public boolean onBlockActivated(World world,BlockPos pos,IBlockState state, EntityPlayer entity,EnumHand hand,EnumFacing direction, float hitX, float hitY, float hitZ) {
	        super.onBlockActivated(world, pos, state, entity, hand, direction, hitX, hitY, hitZ);
	        if (!world.isRemote) initExp(world, pos, entity);
	        return true;
	    }
	    private static void initExp(World world, BlockPos pos, Entity entity) {
	        if (world.isRemote) return;
	        double x = pos.getX() + 0.5;
	        double y = pos.getY() + 0.75;
	        double z = pos.getZ() + 0.5;
	        // createExplosion(Entity exploder, double x, double y, double z, float strength, boolean createsFire)
	        world.setBlockToAir(pos);
	        world.createExplosion(null, x, y, z, 3.7F, true);
	    }
	}
}
