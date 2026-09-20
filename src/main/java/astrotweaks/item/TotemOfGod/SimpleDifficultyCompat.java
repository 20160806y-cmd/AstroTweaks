package astrotweaks.item.TotemOfGod;

import com.charles445.simpledifficulty.api.SDCapabilities;
import com.charles445.simpledifficulty.api.config.QuickConfig;
import com.charles445.simpledifficulty.api.temperature.ITemperatureCapability;
import com.charles445.simpledifficulty.api.temperature.TemperatureEnum;
import com.charles445.simpledifficulty.api.thirst.IThirstCapability;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.FMLLog;

/**
 * Опциональная интеграция с модом SimpleDifficulty.
 * <p>
 * Зависимость опциональна: все обращения к классам SimpleDifficulty находятся
 * только в методах этого класса и выполняются лишь после проверки
 * {@link Loader#isModLoaded(String)}. Если мод не установлен, эти методы
 * (а следовательно и классы SimpleDifficulty) не загружаются вообще.
 * </p>
 */
public final class SimpleDifficultyCompat {
	private static final String SD_MODID = "simpledifficulty";

	/** Максимальный уровень жажды (полоска). */
	private static final int MAX_THIRST = 20;
	/** Максимальное "насыщение водой" (выносливость жажды). */
	private static final float MAX_THIRST_SATURATION = 20.0f;

	private static boolean failureLogged = false;

	private SimpleDifficultyCompat() {}

	/**
	 * Обрабатывает механики SimpleDifficulty для HEAL-функции тотема бога.
	 * Безопасно вызывать всегда и на любой стороне:
	 * если мод не установлен — метод ничего не делает.
	 *
	 * @param player игрок, использующий тотем
	 */
	public static void applyHealEffects(EntityPlayer player) {
		if (!Loader.isModLoaded(SD_MODID)) {
			return;
		}
		try {
			if (QuickConfig.isThirstEnabled()) {
				restoreThirst(player);
			}
			if (QuickConfig.isTemperatureEnabled()) {
				resetTemperature(player);
			}
		} catch (Throwable t) {
			// Совместимость с другими версиями SimpleDifficulty: если какой-то метод
			// изменился или отсутствует, не ломаем HEAL, а просто пропускаем механики.
			if (!failureLogged) {
				failureLogged = true;
				FMLLog.log.error("SimpleDifficulty compat failed to apply totem of god effects", t);
			}
		}
	}

	/** Полностью восстанавливает жажду игрока. */
	private static void restoreThirst(EntityPlayer player) {
		IThirstCapability thirst = SDCapabilities.getThirstData(player);
		if (thirst == null) {
			return;
		}
		thirst.setThirstLevel(MAX_THIRST);
		thirst.setThirstSaturation(MAX_THIRST_SATURATION);
		thirst.setThirstExhaustion(0.0f);
		thirst.setThirstTickTimer(0);
		thirst.setThirstDamageCounter(0);
	}

	/** Сбрасывает температуру игрока до стандартного значения (середина NORMAL). */
	private static void resetTemperature(EntityPlayer player) {
		ITemperatureCapability temperature = SDCapabilities.getTemperatureData(player);
		if (temperature == null) {
			return;
		}
		temperature.setTemperatureLevel(TemperatureEnum.NORMAL.getMiddle());
		temperature.setTemperatureTickTimer(0);
		temperature.setTemperatureDamageCounter(0);
		temperature.clearTemporaryModifiers();
	}
}