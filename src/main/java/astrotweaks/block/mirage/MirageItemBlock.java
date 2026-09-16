package astrotweaks.block.mirage;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;


/**
 * Кастомный ItemBlock для {@link MirageBlock}.
 * <p>
 * Стандартный {@link ItemBlock} при размещении <b>не</b> копирует NBT
 * из предмета в TileEntity.  Этот класс исправляет это:
 * после {@code super.placeBlockAt(…)} берём созданный
 * {@link MirageTileEntity} и загружаем в него пользовательские ключи
 * из {@code stack.getTagCompound()} через
 * {@link MirageTileEntity#readCustomNBT}.
 * <p>
 * Это позволяет делать:
 * <pre>
 *   /give @p astrotweaks:mirage_a 1 0 {target:"minecraft:stone"}
 * </pre>
 * и при размещении блок сразу мимикрирует указанный блок.
 *
 * @see MirageTileEntity#readCustomNBT
 */
public class MirageItemBlock extends ItemBlock {

    public MirageItemBlock(Block block) {
        super(block);
    }

    /**
     * После стандартного размещения блока (setBlockState + createTileEntity)
     * копирует {@code "target"} и {@code "state"} из NBT предмета в TE.
     *
     * @return {@code true} если блок успешно размещён
     */
    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world,
                                BlockPos pos, EnumFacing facing,
                                float hitX, float hitY, float hitZ,
                                IBlockState newState) {
        if (!super.placeBlockAt(stack, player, world, pos, facing, hitX, hitY, hitZ, newState)) {
            return false;
        }

        // Блок размещён — TE создан через createTileEntity.
        // Копируем пользовательские данные из предмета.
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof MirageTileEntity && stack.hasTagCompound()) {
            MirageTileEntity mirage = (MirageTileEntity) te;
            mirage.readCustomNBT(stack.getTagCompound());
            te.markDirty();

            // ВАЖНО: без notifyBlockUpdate клиент не получит update packet
            // с target/state → getTargetState() на клиенте вернёт null →
            // TESR не отрендерит блок (он станет невидимым).
            IBlockState blockState = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, blockState, blockState, 3);
            world.markBlockRangeForRenderUpdate(pos, pos);

            // Гарантированная доставка TE-данных на клиент (Dedicated Server / survival):
            // notifyBlockUpdate не всегда влечёт SPacketUpdateTileEntity,
            // поэтому шлём его явно всем игрокам поблизости.
            if (!world.isRemote && world.getMinecraftServer() != null) {
                SPacketUpdateTileEntity packet = mirage.getUpdatePacket();
                world.getMinecraftServer().getPlayerList().sendToAllNearExcept(
                        null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        64.0D, world.provider.getDimension(), packet);
            }
        }

        return true;
    }
}
