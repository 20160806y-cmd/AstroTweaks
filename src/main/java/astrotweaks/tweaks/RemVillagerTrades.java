package astrotweaks.tweaks;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerCareer;
import net.minecraftforge.fml.common.registry.VillagerRegistry.VillagerProfession;
import net.minecraftforge.registries.IForgeRegistry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.List;



public final class RemVillagerTrades {

    private static final Logger LOGGER = LogManager.getLogger("AstroTweaks");


    public static void onLoadComplete() {
        //if (!ModVariables.Remove_METS_engineer) return;
        if (!Loader.isModLoaded("mets")) return;

        try {
            ResourceLocation professionName = new ResourceLocation("mets", "engineer");

            IForgeRegistry<VillagerProfession> registry = GameRegistry.findRegistry(VillagerProfession.class);
            if (registry == null) {
                LOGGER.warn("VillagerProfession registry not found");
                return;
            }
            VillagerProfession profession = registry.getValue(professionName);
            if (profession == null) {
                LOGGER.info("Profession {} not found", professionName);
                return;
            }
            // careers (List<VillagerCareer>)
            Field careersField = findFirstListField(VillagerProfession.class, profession, VillagerCareer.class);
            if (careersField == null) {
                LOGGER.info("No careers list found for {}", professionName);
                return;
            }
            @SuppressWarnings("unchecked")
            List<VillagerCareer> careers = (List<VillagerCareer>) careersField.get(profession);
            if (careers == null || careers.isEmpty()) {
                LOGGER.info("No careers for {}", professionName);
                return;
            }
            //  trades (List<List<ITradeList>>)
            Field tradesField = findFirstListField(VillagerCareer.class, null, null);
            if (tradesField == null) {
                LOGGER.info("No trades field found in VillagerCareer");
                return;
            }
            for (VillagerCareer career : careers) {
                Object tradesObj = tradesField.get(career);
                if (tradesObj instanceof List) {
                    ((List<?>) tradesObj).clear();
                    LOGGER.info("Cleared trades for career: {}", career.getName());
                }
            }
            LOGGER.info("Successfully removed all trades for {}", professionName);
        } catch (Exception e) {
            LOGGER.error("Failed to remove villager trades:\n", e);
        }
    }
    private static Field findFirstListField(Class<?> clazz, Object instance, Class<?> expectedElementType) {
        for (Field f : clazz.getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                if (instance != null && expectedElementType != null) {
                    try {
                        List<?> list = (List<?>) f.get(instance);
                        if (list != null && !list.isEmpty() && expectedElementType.isAssignableFrom(list.get(0).getClass())) {
                            return f;
                        }
                    } catch (IllegalAccessException e) {
                    }
                } else {
                    return f;
                }
            }
        }
        return null;
    }
}
