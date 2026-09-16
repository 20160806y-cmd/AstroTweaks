package astrotweaks.block.mirage;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;


/**
 * TileEntityItemStackRenderer для предмета {@link MirageBlock}.
 * <p>
 * Рендерит модель <b>целевого блока</b> вместо стандартной модели миража.
 * Это даёт визуальную обратную связь: в инвентаре и в руке предмет выглядит
 * как тот блок, который он мимикрирует.
 *
 * <h3>Как это работает</h3>
 * <ol>
 *   <li>Создаём фейковый {@link MirageTileEntity} и загружаем в него
 *       {@code "target"} / {@code "state"} из NBT предмета через
 *       {@link MirageTileEntity#readCustomNBT}</li>
 *   <li>Берём <b>item-модель</b> цели ({@link RenderItem#getItemModelWithOverrides})
 *       — ту же, что рисует {@link RenderItem} для обычного предмета</li>
 *   <li>Рисуем её квады в {@code DefaultVertexFormats.ITEM} через
 *       {@link RenderItem#renderQuads} — ровно тот же путь, что использует
 *       {@code RenderItem.renderModel} для обычных блоков</li>
 * </ol>
 *
 * <p>Камерные трансформации ({@code display} в mirage_a.json) и биндинг
 * атласа блоков выполняет {@link RenderItem} ДО вызова этого рендера
 * ({@code handleCameraTransforms} → {@code renderItem} → {@code renderByItem}).
 *
 * @see MirageTESR — аналогичная логика, но для блока в мире
 * @see MirageItemBlock — копирует NBT при размещении
 */
public class MirageTEISR extends TileEntityItemStackRenderer {

    /**
     * Рендер предмета. Вызывается из {@link RenderItem}
     * только когда модель предмета — {@code builtin/entity} (isBuiltInRenderer = true).
     * <p>
     * Для любых предметов, кроме миража, делегируем в суперкласс —
     * иначе сломаем рендер ванильных сундуков/знамён/голов, которые используют
     * общий {@link TileEntityItemStackRenderer#instance}.
     */
    @Override
    public void renderByItem(ItemStack stack) {
        if (!(stack.getItem() instanceof MirageItemBlock)) {
            super.renderByItem(stack);
            return;
        }

        MirageTileEntity fakeTE = new MirageTileEntity();
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        fakeTE.readCustomNBT(tag);

        IBlockState targetState = fakeTE.getTargetState();
        if (targetState == null) return;

        RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
        ItemStack targetStack = new ItemStack(targetState.getBlock(), 1,
                                              targetState.getBlock().getMetaFromState(targetState));

        IBakedModel targetModel = renderItem.getItemModelWithOverrides(targetStack, null, null);

        // builtin/entity цели (сундуки/знамёна) рисуют сами себя через TEISR —
        // не запускаем вложенный TEISR, рисуем простой куб её стейта.
        if (targetModel.isBuiltInRenderer()) {
            targetModel = Minecraft.getMinecraft().getBlockRendererDispatcher()
                    .getBlockModelShapes().getModelForState(targetState);
        }

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.ITEM);

        for (EnumFacing f : EnumFacing.values()) {
            renderItem.renderQuads(buffer, targetModel.getQuads(null, f, 0L), -1, targetStack);
        }
        renderItem.renderQuads(buffer, targetModel.getQuads(null, null, 0L), -1, targetStack);

        tessellator.draw();
    }
}