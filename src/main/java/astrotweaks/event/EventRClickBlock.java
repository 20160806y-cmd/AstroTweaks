package astrotweaks.event;

import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.block.BlockStone;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import astrotweaks.item.ATItems;
import astrotweaks.block.*;;


@Mod.EventBusSubscriber(modid = "astrotweaks")
public class EventRClickBlock {
    public EventRClickBlock() {}

    private static final List<Rule> RULES = buildRules();
    private interface Condition { boolean matches(Context ctx); }
    private interface Action { void apply(Context ctx, double rand); }

    private static class Context {
        public final Entity entity;
        public final World world;
        public final int x, y, z;
        public final ItemStack mainHand, offHand;
        public final IBlockState blockState;
        public final net.minecraft.block.Block blockAt, blockAbove;

        public Context(Entity entity, World world, int x, int y, int z) {
            this.entity = entity;
            this.world = world;
            this.x = x; this.y = y; this.z = z;
            this.mainHand = (entity instanceof EntityLivingBase) ? ((EntityLivingBase) entity).getHeldItemMainhand() : ItemStack.EMPTY;
            this.offHand  = (entity instanceof EntityLivingBase) ? ((EntityLivingBase) entity).getHeldItemOffhand()  : ItemStack.EMPTY;
            BlockPos pos = new BlockPos(x, y, z);
            this.blockState = world.getBlockState(pos);
            this.blockAt    = this.blockState.getBlock();
            this.blockAbove = world.getBlockState(pos.up()).getBlock();
        }
        public boolean isPlayer() { return entity instanceof EntityPlayer; }
        public void giveToPlayer(ItemStack stack) {
            if (isPlayer()) ItemHandlerHelper.giveItemToPlayer((EntityPlayer) entity, stack);
        }
        public void removeOneMatching(net.minecraft.item.Item item) {
            if (isPlayer()) ((EntityPlayer) entity).inventory.clearMatchingItems(item, -1, 1, null);
        }
        public void spawnItemStack(ItemStack stack) {
            if (!world.isRemote) {
                EntityItem e = new EntityItem(world, x + 0.5, y + 1.5, z + 0.5, stack);
                e.setPickupDelay(10);
                world.spawnEntity(e);
            }
        }
    }

    private static class Rule {
        final Condition condition;
        final Action action;
        Rule(Condition c, Action a) { this.condition = c; this.action = a; }
    }

    // ---------------------------------------------------------------------
    // Таблицы трансформации "текущий блок" -> "целевой блок"
    // ---------------------------------------------------------------------
    private static final IBlockState[] CURRENT_BLOCKS = {
        Blocks.MOSSY_COBBLESTONE.getDefaultState(),
        Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.MOSSY),
		B_MossyCobblestoneStairs.block.getDefaultState(),
		B_MossyCobblestoneSlab.block.getDefaultState(),
		B_MossyStonebrickStairs.block.getDefaultState(),
		B_MossyStonebrickSlab.block.getDefaultState(),

		ATBlocks.MOSSY_STONE.getDefaultState(),
		ATBlocks.MOSSY_CARVED_STONEBRICK.getDefaultState(),
		ATBlocks.MOSSY_CRACKED_STONEBRICK.getDefaultState(),




    };
    private static final IBlockState[] TARGET_BLOCKS = {
        Blocks.COBBLESTONE.getDefaultState(),
        Blocks.STONEBRICK.getDefaultState().withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.DEFAULT),
		Blocks.STONE_STAIRS.getDefaultState(),
		Blocks.STONE_SLAB.getStateFromMeta(3),
		Blocks.STONE_BRICK_STAIRS.getDefaultState(),
		Blocks.STONE_SLAB.getStateFromMeta(5),

		Blocks.STONE.getStateFromMeta(0),
		Blocks.STONEBRICK.getStateFromMeta(3),
		Blocks.STONEBRICK.getStateFromMeta(2),




    };
    private static final Class<?>[] ALLOWED_TOOL_CLASSES = {
        ItemPickaxe.class, ItemAxe.class, ItemSpade.class, ItemHoe.class, ItemSword.class, ItemTool.class
    };

    /**
     * Проверяет, что предмет в стаке — инструмент одного из разрешённых классов.
     * Пустой стак / null -> false.
     */
    private static boolean isAllowedTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        for (int i = 0; i < ALLOWED_TOOL_CLASSES.length; i++) {
            if (ALLOWED_TOOL_CLASSES[i].isInstance(item)) return true;
        }
        return false;
    }

    /**
     * Индекс совпадающего состояния в CURRENT_BLOCKS, либо -1.
     * Массив маленький, линейный поиск оптимален.
     */
    private static int findCurrentBlockIndex(IBlockState state) {
        for (int i = 0; i < CURRENT_BLOCKS.length; i++) {
            if (CURRENT_BLOCKS[i].equals(state)) return i;
        }
        return -1;
    }


    private static List<Rule> buildRules() {
        List<Rule> rules = new ArrayList<>();

        // Vine -> fiber: if holding vine (main or off) and block is magma/fire/lava above or dimension == -1
        rules.add(new Rule(ctx -> {
            boolean holdingVine = ctx.mainHand.getItem() == ATItems.CORDAGE_VINE || ctx.offHand.getItem() == ATItems.CORDAGE_VINE;
            boolean hotBelow = ctx.blockAt == Blocks.MAGMA || ctx.blockAt == Blocks.FIRE;
            boolean hotAbove = ctx.blockAbove == Blocks.FLOWING_LAVA || ctx.blockAbove == Blocks.LAVA;
            boolean inNether = ctx.entity.dimension == -1;
            return holdingVine && (hotBelow || hotAbove || inNether);
        }, (ctx, r) -> {
            ctx.removeOneMatching(ATItems.CORDAGE_VINE);
            ctx.giveToPlayer(new ItemStack(ATItems.CORDAGE_FIBER, 1));
        }));

        // Rock -> flat rock (stone/cobble/various stone metas/obsidian)
        rules.add(new Rule(ctx -> {
            boolean holdingRock = ctx.mainHand.getItem() == ATItems.ROCK;
            boolean blockOk = ctx.blockAt == Blocks.STONE || ctx.blockAt == Blocks.COBBLESTONE || ctx.blockAt == Blocks.OBSIDIAN;
            return holdingRock && blockOk;
        }, (ctx, r) -> {
            if (r > 0.75) {
                ctx.removeOneMatching(ATItems.ROCK);
                if (r > 0.8) ctx.spawnItemStack(new ItemStack(ATItems.ROCK_FLAT, 1));
                if (r > 0.95) ctx.spawnItemStack(new ItemStack(ATItems.ROCK_FLAT, 1));
                if (r < 0.04) ctx.spawnItemStack(new ItemStack(ATItems.FLINT_SHARD, 1));
            }
        }));

        // Bone -> bone shards on stone-like blocks (same block check as above)
        rules.add(new Rule(ctx -> {
            ItemStack main = ctx.mainHand;
            boolean holdingBone = main.getItem() == Items.BONE;
            boolean blockOk = ctx.blockAt == Blocks.STONE || ctx.blockAt == Blocks.COBBLESTONE || ctx.blockAt == Blocks.OBSIDIAN;
            return holdingBone && blockOk;
        }, (ctx, r) -> {
            if (r > 0.9) {
                ctx.removeOneMatching(Items.BONE);
                ctx.spawnItemStack(new ItemStack(ATItems.BONE_SHARD, 1));
                if (r < 0.95) ctx.spawnItemStack(new ItemStack(ATItems.BONE_SHARD, 1));
                if (r < 0.35) ctx.spawnItemStack(new ItemStack(ATItems.BONE_SHARD, 1));
            }
        }));

        // Flint -> flint shards on stone-like blocks
        rules.add(new Rule(ctx -> {
            ItemStack main = ctx.mainHand;
            boolean holdingFlint = main.getItem() == Items.FLINT;
            boolean blockOk = ctx.blockAt == Blocks.STONE || ctx.blockAt == Blocks.COBBLESTONE || ctx.blockAt == Blocks.OBSIDIAN;
            return holdingFlint && blockOk;
        }, (ctx, r) -> {
            if (r > 0.9) {
                ctx.removeOneMatching(Items.FLINT);
                ctx.spawnItemStack(new ItemStack(ATItems.FLINT_SHARD, 1));
                if (r < 0.75) ctx.spawnItemStack(new ItemStack(ATItems.FLINT_SHARD, 1));
                if (r < 0.33) ctx.spawnItemStack(new ItemStack(ATItems.FLINT_SHARD, 1));
            }
        }));


        // Очищение блока инструментом: MOSSY_COBBLESTONE -> COBBLESTONE,
        // MOSSY_STONEBRICK -> STONEBRICK (обычный вариант).
        // Сработает при клике ПКМ любым инструментом из ALLOWED_TOOL_CLASSES (в любой руке).
        rules.add(new Rule(ctx -> {
            if (!isAllowedTool(ctx.mainHand) && !isAllowedTool(ctx.offHand)) return false;
            return findCurrentBlockIndex(ctx.blockState) >= 0;
        }, (ctx, r) -> {
            int idx = findCurrentBlockIndex(ctx.blockState);
            if (idx < 0) return;
			ctx.mainHand.damageItem(1, (EntityLivingBase) ctx.entity);
            ctx.world.setBlockState(new BlockPos(ctx.x, ctx.y, ctx.z), TARGET_BLOCKS[idx], 3);
        }));

	
        return rules;
    }

    public static void exect(Entity entity, World world, int x, int y, int z) {
        if (world.isRemote)  return;
        if (!(entity instanceof EntityPlayer))  return;
        Context ctx = new Context(entity, world, x, y, z);
        for (Rule r : RULES) {
            if (r.condition.matches(ctx)) {
                double rand = ThreadLocalRandom.current().nextDouble();
                r.action.apply(ctx, rand);
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer entity = event.getEntityPlayer();
        int x = event.getPos().getX();
        int y = event.getPos().getY();
        int z = event.getPos().getZ();
        World world = event.getWorld();

        exect(entity, world, x, y, z);
    }
}
