package astrotweaks.block.mirage;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;


/**
 * TileEntitySpecialRenderer для {@link MirageTileEntity}.
 * <p>
 * Рендерит модель <b>целевого блока</b> в позиции миража.
 * Сам блок миража имеет {@code EnumBlockRenderType.ENTITYBLOCK_ANIMATED},
 * поэтому стандартный чанк-рендер его полностью пропускает —
 * вся визуализация лежит на этом TESR.
 *
 * <p><b>Почему пайплайн {@code renderModel} + {@code setTranslation}:</b>
 * {@link net.minecraft.client.renderer.BlockModelRenderer#renderModel} использует
 * {@link BufferBuilder#putPosition(double,double,double)}, который прибавляет
 * {@code pos + xOffset} к каждой baked-вершине.  В чанк-рендерере это
 * компенсируется {@code setTranslation(-chunkOrigin)} — вершины остаются
 * локальными [0..1] внутри чанка.  ТESR делает то же самое: ставим
 * {@code setTranslation(-pos)}, и bake-координаты остаются в [0..1],
 * а {@code GlStateManager.translate(x,y,z)} корректно позиционирует
 * куб относительно камеры.
 *
 * @see MirageTEISR — аналогичная логика, но для предмета в руке
 */
public class MirageTESR extends TileEntitySpecialRenderer<MirageTileEntity> {

    @Override
    public void render(MirageTileEntity te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        World world = te.getWorld();
        IBlockState targetState = te.getTargetState();
        if (world == null || targetState == null) return;

        BlockPos pos = te.getPos();
        BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
        IBakedModel model = dispatcher.getBlockModelShapes().getModelForState(targetState);
        IBlockState extendedState = targetState.getBlock().getExtendedState(targetState, world, pos);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        buffer.begin(7, DefaultVertexFormats.BLOCK);
        buffer.setTranslation(-pos.getX(), -pos.getY(), -pos.getZ());

        dispatcher.getBlockModelRenderer().renderModel(
                world, model, extendedState, pos, buffer, false);

        tessellator.draw();

        buffer.setTranslation(0, 0, 0);
        GlStateManager.popMatrix();
    }
}
