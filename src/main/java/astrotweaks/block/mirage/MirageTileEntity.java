package astrotweaks.block.mirage;

import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Тайл-энтити блока-миража.
 * <p>
 * Хранит registry name целевого блока с опциональными метаданными
 * ({@code "target"}, например {@code "minecraft:stone"} или
 * {@code "minecraft:stone:3"}) и сериализованные
 * свойства его IBlockState ({@code "state"}).  Клиент получает эти данные
 * через {@link #getUpdateTag()}/{@link #onDataPacket}, после чего
 * {@link MirageTESR} рендерит модель целевого блока в позиции миража.
 * <p>
 * Публичные методы:
 * <ul>
 *   <li>{@link #setTarget(ResourceLocation)} — задать цель (сервер, потом sync)</li>
 *   <li>{@link #getTargetName()} / {@link #getTargetState()} — чтение цели</li>
 *   <li>{@link #readCustomNBT(NBTTagCompound)} — загрузка только пользовательских полей
 *       (без pos/id).  Используется {@link MirageItemBlock} при размещении
 *       и {@link MirageBlock#getPickBlock} при middle-click.</li>
 *   <li>{@link #writeCustomNBT()} — запись только пользовательских полей.</li>
 * </ul>
 */
public class MirageTileEntity extends TileEntity {

    /**
     * Registry name целевого блока с опциональным суффиксом метаданных,
     * например {@code "minecraft:stone"} или {@code "minecraft:stone:3"}.
     * Метаданные могут быть любым целым (блоки JEID-style семейств,
     * не ограниченные диапазоном 0..15).
     */
    public static final String TARGET = "target";
    /** Сериализованные свойства IBlockState целевого блока. */
    public static final String STATE  = "state";

    // ───────── внутреннее состояние ─────────
    /** Registry name блока, под который мимикрируем. */
    private ResourceLocation targetName;
    /** Метаданные цели из суффикса {@code ":meta"} (например {@code "minecraft:stone:3"} → 3). -1 = не заданы. */
    private int targetMeta = -1;
    /** Кэш десериализованного IBlockState (сбрасывается при смене цели). */
    private IBlockState targetState;
    /** «Сырая» NBT со свойствами state, сохранённая до разрешения targetName → Block. */
    private NBTTagCompound serializedState = new NBTTagCompound();

    /** Кэш клиентской item/block-модели цели. Сбрасывается при смене target. */
    @SideOnly(Side.CLIENT) 
    @Nullable private transient IBakedModel cachedModel;



    /**
     * Безопасно для вызова с любой стороны: чистит кэш только на клиенте.
     * {@code world} может быть {@code null}, если TE ещё не установлен в мир
     * (например, фейковый TE в {@link MirageTEISR}) — тогда чистить нечего.
     */
    private void invalidateModelCache() {
        if (world != null && world.isRemote) {
            clearModelCache();
        }
    }
    @SideOnly(Side.CLIENT) private void clearModelCache() { this.cachedModel = null; }

    // ═══════════════════════════════════════════════════════════════
    //  Публичные геттеры / сеттеры
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    public ResourceLocation getTargetName() {
        return targetName;
    }

    /**
     * Клиентская модель для рендера. Кэшируется; сбрасывается при смене target
     * в {@link #readCustomNBT(NBTTagCompound)} и {@link #setTarget}.
     */
    @SideOnly(Side.CLIENT)
    @Nullable
    public IBakedModel getCachedModel() {
        IBlockState state = getTargetState();
        if (state == null)  return null;
        if (cachedModel == null) {
            cachedModel = Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state);
        }
        return cachedModel;
    }

    /**
     * Возвращает IBlockState целевого блока.  Результат кэшируется
     * в {@link #targetState} после первого успешного разрешения.
     *
     * @return IBlockState или {@code null}, если цель не задана / блок не найден
     */
    @Nullable
    public IBlockState getTargetState() {
        if (targetState != null)  return targetState;
        if (targetName == null)   return null;

        Block block = ForgeRegistries.BLOCKS.getValue(targetName);
        if (block == null)  return null;

        targetState = readState(block, targetMeta, serializedState);
        return targetState;
    }

    /**
     * Устанавливает registry name целевого блока и запускает sync на клиент.
     * Вызывать только на сервере.
     */
    public void setTarget(@Nullable ResourceLocation name) {
        this.targetName = name;
        this.targetMeta = -1;             // без метаданных
        this.targetState = null;          // сброс кэша — нужно перечитать state
        this.cachedModel = null;              // <-- NEW (внутри, не через хелпер,
        this.markDirty();
        if (world != null && !world.isRemote && pos != null) {
            world.markBlockRangeForRenderUpdate(pos, pos);
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }


    // ═══════════════════════════════════════════════════════════════
    //  Чтение / запись NBT (полный цикл — для world save)
    // ═══════════════════════════════════════════════════════════════

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);      // читает x, y, z, id
        readCustomNBT(compound);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);       // пишет x, y, z, id
        writeCustomFields(compound);
        return compound;
    }


    // ═══════════════════════════════════════════════════════════════
    //  «Голые» чтение/запись пользовательских полей (без pos/id)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Читает из {@code compound} только ключи {@code "target"} и {@code "state"},
     * игнорируя позицию/ID (super.readFromNBT не вызывается).
     * <p>
     * Используется:
     * <ul>
     *   <li>{@link MirageItemBlock#placeBlockAt} — копирует NBT предмета в TE при размещении</li>
     *   <li>{@link MirageBlock#getPickBlock} — (косвенно, через writeCustomNBT)</li>
     * </ul>
     */
    public void readCustomNBT(NBTTagCompound compound) {
        targetName      = null;
        targetState     = null;
        targetMeta      = -1;
        serializedState = new NBTTagCompound();

        if (compound.hasKey(TARGET, 8)) {
            String raw = compound.getString(TARGET);
            int metaFromSuffix = parseMetaSuffix(raw);

            if (metaFromSuffix >= 0) {
                targetMeta = metaFromSuffix;
            }
            try {
                targetName = new ResourceLocation(stripMetaSuffix(raw));
            } catch (Exception ignored) {
                targetName = null;
                targetMeta = -1;
            }
        }
        if (compound.hasKey(STATE, 10)) {
            serializedState = compound.getCompoundTag(STATE);
        }

        invalidateModelCache();
    }

    /**
     * Достаёт метаданные из суффикса {@code ":meta"} строки цели
     * ({@code "minecraft:stone:3"} → {@code 3}).  Возвращает {@code -1},
     * если суффикса нет.  Принимает любые целые значения — блоки
     * JEID-style семейств могут иметь meta вне диапазона 0..15.
     */
    private static int parseMetaSuffix(String raw) {
        int colon = raw.lastIndexOf(':');
        if (colon < 0 || colon == raw.length() - 1)  return -1;

        String suffix = raw.substring(colon + 1);
        if (!suffix.matches("-?[0-9]+"))  return -1;

        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /** Возвращает registry name без суффикса {@code ":meta"}. */
    private static String stripMetaSuffix(String raw) {
        int colon = raw.lastIndexOf(':');
        if (colon < 0)  return raw;

        String suffix = raw.substring(colon + 1);
        return suffix.matches("-?[0-9]+") ? raw.substring(0, colon) : raw;
    }

    /**
     * Записывает только {@code "target"} и {@code "state"} — без pos/id.
     * Используется {@link MirageBlock#getPickBlock} для создания ItemStack
     * с корректным NBT при middle-click в креативе.
     */
    public NBTTagCompound writeCustomNBT() {
        NBTTagCompound compound = new NBTTagCompound();
        writeCustomFields(compound);
        return compound;
    }

    /** Внутренний метод — записывает target + state в переданный compound. */
    private void writeCustomFields(NBTTagCompound compound) {
        if (targetName != null) {
            // Пишем цель вместе с метаданными — pick-block/копирование миража
            // не должны терять JEID-style meta.
            compound.setString(TARGET, targetMeta >= 0 ? targetName + ":" + targetMeta : targetName.toString());
        }
        IBlockState state = getTargetState();

        if (state != null) {
            // Цель разрешена — сериализуем актуальные свойства
            compound.setTag(STATE, writeState(state));
        } else if (serializedState != null && !serializedState.hasNoTags()) {
            // Цель не разрешена (мод не загружен?) — сохраняем как есть
            compound.setTag(STATE, serializedState.copy());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Сериализация / десериализация IBlockState ↔ NBTTagCompound
    // ═══════════════════════════════════════════════════════════════
    /** Каждое свойство блока → строковый ключ/значение в NBTTagCompound. */
    private NBTTagCompound writeState(IBlockState state) {
        NBTTagCompound result = new NBTTagCompound();

        for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
            result.setString(entry.getKey().getName(), getPropertyValue(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String getPropertyValue(IProperty<T> property, Comparable<?> value) {
        return property.getName((T) value);
    }

    /**
     * Восстанавливает IBlockState: база — {@link Block#getStateFromMeta(int)}
     * если заданы метаданные ({@code meta >= 0}), иначе default state;
     * поверх применяются свойства из NBTTagCompound.
     */
    private static IBlockState readState(Block block, int meta, NBTTagCompound properties) {
        IBlockState result;
        try {
            result = meta >= 0 ? block.getStateFromMeta(meta) : block.getDefaultState();
        } catch (Exception ignored) {
            result = block.getDefaultState();
        }

        for (String key : properties.getKeySet()) {
            IProperty<?> property = findProperty(block, key);

            if (property == null || !properties.hasKey(key, 8))  continue;

            result = applyProperty(result, property, properties.getString(key));
        }
        return result;
    }

    private static IProperty<?> findProperty(Block block, String name) {
        for (IProperty<?> property : block.getDefaultState().getPropertyKeys()) {
            if (property.getName().equals(name)) {
                return property;
            }
        }
        return null;
    }

    private static <T extends Comparable<T>> IBlockState applyProperty(IBlockState state, IProperty<T> property, String value) {
        com.google.common.base.Optional<T> parsed = property.parseValue(value);
        return parsed.isPresent() ? state.withProperty(property, parsed.get()) : state;
    }


    // ═══════════════════════════════════════════════════════════════
    //  Сетевая синхронизация (сервер → клиент)
    // ═══════════════════════════════════════════════════════════════

    /**
     * NBT, отправляемый клиенту при загрузке чанка или
     * {@code world.notifyBlockUpdate}.  Содержит все данные TE.
     */
    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    /** Пакет для chunk-level sync (вызывается при notifyBlockUpdate с флагом 2). */
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    /** Обработка входящего пакета на клиенте — читаем данные и перерисовываем. */
    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());

        if (world != null) {
            world.markBlockRangeForRenderUpdate(pos, pos);
        }
    }
}
