package astrotweaks.block.mirage;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * TileEntitySpecialRenderer для {@link MirageTileEntity}.
 * <p>
 * Рендерит модель <b>целевого блока</b> в позиции миража.
 * Сам блок миража имеет {@code EnumBlockRenderType.ENTITYBLOCK_ANIMATED},
 * поэтому стандартный чанк-рендер его полностью пропускает —
 * вся визуализация лежит на этом TESR.
 *
 * <p>Алгоритм повторяет ванильный {@code TileEntityPistonRenderer} —
 * единственный штатный TESR, рисующий полноценную блок-модель:
 * <ul>
 *   <li>{@link BufferBuilder#setTranslation(double,double,double)} =
 *       {@code (x - pos)} — а {@link net.minecraft.client.renderer.BlockModelRenderer#renderModel}
 *       прибавляет {@code pos + xOffset}; в сумме вершины уходят в
 *       координаты TESR, а сдвиг GL-матрицы не нужен</li>
 *   <li>{@link TextureMap}-биндинг — иначе куб белый (нет атласа)</li>
 *   <li>{@code disableCull} — иначе грани видны не со всех ракурсов</li>
 *   <li>{@code RenderHelper.disableStandardItemLighting()} — иначе
 *       GL_LIGHTING слоем поверх baked lightmap затемняет модель</li>
 * </ul>
 *
 * @see MirageTEISR — аналогичная логика, но для предмета в руке
 */
public final class MirageTESR extends TileEntitySpecialRenderer<MirageTileEntity> {

    @Override
    public void render(MirageTileEntity te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        World world = te.getWorld();
        IBlockState targetState = te.getTargetState();
        if (world == null || targetState == null)  return;

        BlockPos pos = te.getPos();
        BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
        IBakedModel model = te.getCachedModel();
        if (model == null)  return;              // цель не разрешена — нечего рисовать
        IBlockState extendedState = targetState.getBlock().getExtendedState(targetState, world, pos);

        this.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableCull();

        if (Minecraft.isAmbientOcclusionEnabled()) {
            GlStateManager.shadeModel(7425);
        } else {
            GlStateManager.shadeModel(7424);
        }

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.BLOCK);
        buffer.setTranslation(x - pos.getX(), y - pos.getY(), z - pos.getZ());

        // ▼ Ключевое: мираж-осведомлённый IBlockAccess + checkSides=true
        IBlockAccess access = new MirageAwareBlockAccess(world);
        dispatcher.getBlockModelRenderer().renderModel(access, model, extendedState, pos, buffer, /* checkSides = */ true);

        buffer.setTranslation(0, 0, 0);
        tessellator.draw();

        GlStateManager.enableCull();
        RenderHelper.enableStandardItemLighting();
    }

    /**
     * Обёртка IBlockAccess, которая «прозрачно» подменяет соседние MirageBlock'и
     * их target-состояниями. Благодаря этому ванильный face-culling внутри
     * BlockModelRenderer.renderModel(..., checkSides=true) видит именно ту модель,
     * что реально отрисуется в соседней клетке.
     */
    @SideOnly(Side.CLIENT)
    private static final class MirageAwareBlockAccess implements IBlockAccess {

        private final IBlockAccess delegate;
        /** Кэш на 6 прямых соседей + запросы target-блока (walls/fences могут спрашивать чуть больше). */
        private final Map<BlockPos, IBlockState> cache = new HashMap<>(12);

        MirageAwareBlockAccess(IBlockAccess delegate) { this.delegate = delegate; }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            IBlockState cached = cache.get(pos);
            if (cached != null) return cached;

            IBlockState state = delegate.getBlockState(pos);
            if (state.getBlock() == MirageBlock.block) {
                TileEntity te = delegate.getTileEntity(pos);
                if (te instanceof MirageTileEntity) {
                    IBlockState target = ((MirageTileEntity) te).getTargetState();
                    if (target != null) {
                        cache.put(pos.toImmutable(), target);
                        return target;
                    }
                }
            }
            cache.put(pos.toImmutable(), state);
            return state;
        }

        @Override public TileEntity getTileEntity(BlockPos pos)                            { return delegate.getTileEntity(pos); }
        @Override public int        getCombinedLight(BlockPos pos, int lightValue)         { return delegate.getCombinedLight(pos, lightValue); }
        @Override public boolean    isAirBlock(BlockPos pos)                               { return delegate.isAirBlock(pos); }
        @Override public Biome      getBiome(BlockPos pos)                                 { return delegate.getBiome(pos); }
        @Override public int        getStrongPower(BlockPos pos, EnumFacing direction)     { return delegate.getStrongPower(pos, direction); }
        @Override public WorldType  getWorldType()                                         { return delegate.getWorldType(); }
        @Override public boolean    isSideSolid(BlockPos pos, EnumFacing side, boolean dv) { return delegate.isSideSolid(pos, side, dv); }
    }
}
