package astrotweaks.block.mirage;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.util.NonNullList;


/**
 * Блок-мираж: невидимый «пустой» блок, который через {@link MirageTileEntity}
 * хранит registry name + IBlockState другого блока и рендерит его модель
 * через {@link MirageTESR} (в мире) / {@link MirageTEISR} (в руке).
 *
 * <h3>Ключевые особенности</h3>
 * <ul>
 *   <li>{@code Material.AIR} + {@code NULL_AABB} → нет коллизий, пропускает свет</li>
 *   <li>{@link EnumBlockRenderType#ENTITYBLOCK_ANIMATED} → стандартный чанк-рендер
 *       пропускает блок; визуал целиком на TESR</li>
 *   <li>Блок неразрушим (hardness -1), но survival-игрок убирает его ЛКМ/ПКМ
 *       через {@link MirageRemovalQueue} (flood-fill по соседним миражам)</li>
 *   <li>В креативе middle-click возвращает ItemStack с NBT цели
 *       (для повторного использования через /give)</li>
 * </ul>
 *
 * @see MirageTileEntity
 * @see MirageTESR
 * @see MirageTEISR
 * @see MirageItemBlock
 * @see MirageRemovalQueue
 */
public class MirageBlock extends Block {

    /**
     * Статический экземпляр блока.
     * Регистрируется в {@link astrotweaks.block.ATBlocks#BLOCKS_TO_REGISTER}.
     */
    public static final Block block = new MirageBlock()
            .setRegistryName("astrotweaks", "mirage_a")
            .setUnlocalizedName("astrotweaks" + ".mirage_a");

    public MirageBlock() {
        super(Material.STRUCTURE_VOID);

        setHardness(-1.0F);               // неразрушим
        setResistance(1000000.0F);         // взрывоустойчив
        setLightOpacity(0);                // полностью пропускает свет
        setLightLevel(0.0F);               // не излучает свет
        setBlockUnbreakable();             // hardness = -1 (повтор вызова — ок)
        setCreativeTab(
                astrotweaks.creativetab.ATCreativeTabs.ASTRO_TWEAKS_CT
        );
    }


    // ═══════════════════════════════════════════════════════════════
    //  Визуальные свойства блока
    // ═══════════════════════════════════════════════════════════════

    @Override
    public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
        return MapColor.AIR;
    }

    /** Прозрачный — не кэширует освещение. */
    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    /** Полностью прозрачный — солнечный свет проходит сквозь. */
    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    /** Не считается «нормальным» — не проводит redstone, не привязывает забор/стекло. */
    @Override
    public boolean isNormalCube(IBlockState state, IBlockAccess world, BlockPos pos) {
        return false;
    }

    /** Нельзя заменить другим блоком (как воздух). */
    @Override
    public boolean isReplaceable(IBlockAccess world, BlockPos pos) {
        return false;
    }

    /**
     * ENTIREBLOCK_ANIMATED — чанк-рендер НЕ создаёт вершинный буфер для этого блока.
     * Визуал целиком обеспечивается {@link MirageTESR}.
     */
    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    /**
     * TRANSLUCENT-слой: корректное отображение в клиентском рендере
     * (альфа-тест, смешивание).
     */
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    @Override
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.TRANSLUCENT;
    }


    // ═══════════════════════════════════════════════════════════════
    //  Коллизии / физика
    // ═══════════════════════════════════════════════════════════════

    /** Нет хитбокса — сущности проходят сквозь. */
    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return NULL_AABB;
    }

    /** Сущности не могут «сломать» мираж (взрыв, End Crystal, и т.д.). */
    @Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, Entity entity) {
        return false;
    }


    // ═══════════════════════════════════════════════════════════════
    //  TileEntity
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new MirageTileEntity();
    }


    // ═══════════════════════════════════════════════════════════════
    //  Дроп / Pick-block
    // ═══════════════════════════════════════════════════════════════

    /** Мираж не дропает предметы при удалении. */
    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        // пусто — блок исчезает без следа
    }

    /**
     * Middle-click в креативе: возвращаем ItemStack с NBT цели
     * ({@code {target:"minecraft:...", state:{...}}}).
     * Позволяет использовать {@code /give} с этим NBT для копирования миража.
     */
    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        ItemStack stack = new ItemStack(this);
        TileEntity te = world.getTileEntity(pos);

        if (te instanceof MirageTileEntity) {
            NBTTagCompound tag = ((MirageTileEntity) te).writeCustomNBT();
            if (!tag.hasNoTags()) {
                stack.setTagCompound(tag);
            }
        }
        return stack;
    }


    // ═══════════════════════════════════════════════════════════════
    //  Взаимодействие / разрушение
    // ═══════════════════════════════════════════════════════════════

    /**
     * ЛКМ (left click): survival-игрок →.enqueue удаление через
     * {@link MirageRemovalQueue}.  В креативе — ничего не делаем,
     * чтобы не мешать обычномуBreaking.
     */
    @Override
    public void onBlockClicked(World world, BlockPos pos, EntityPlayer player) {
        if (world.isRemote)  return;
        if (player.isCreative()) {
            world.setBlockToAir(pos);
            return;
        }

        MirageRemovalQueue.enqueue(world, pos);
    }

    /**
     * ПКМ (right click): survival-игрок → enqueue удаление.
     * Возвращает {@code false}, чтобы не блокировать использование предмета
     * в руке (размещение другого блока, и т.д.).
     */
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && !player.isCreative()) {
            MirageRemovalQueue.enqueue(world, pos);
        }
        return false;
    }

    /** Взрыв тоже запускает удаление (вместо стандартного drop). */
    @Override
    public void onBlockExploded(World world, BlockPos pos, Explosion explosion) {
        if (world.isRemote)  return;

        MirageRemovalQueue.enqueue(world, pos);
    }
}
