package astrotweaks.command;

import astrotweaks.item.TotemOfGod.TotemOfGod;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;



public class CommandCKill extends CommandBase {
    @Override public String getName() { return "ckill"; }
    @Override public String getUsage(ICommandSender sender) { return "/ckill"; }
    // Уровень прав 0
    @Override public int getRequiredPermissionLevel() { return 0; }
    // Всегда разрешаем - команда не требует прав.
    @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return true; }
    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) { throw new CommandException("command.only_for_players"); }
        EntityPlayerMP player = (EntityPlayerMP) sender;

        // Пока в инвентаре есть Тотем Бога — команда не выполняется.
        if (hasTotemOfGod(player)) { throw new CommandException("command.ckill.totem_blocked"); }

        // Сбрасываем точку спавна (аналогично тому, как её сбрасывает кровать → world spawn)
        // setSpawnChunk(pos, forced, dimension) — при pos = null точка возрождения сбрасывается на спавн мира.
        player.setSpawnChunk(null, false, 0);
        // Убиваем игрока (то же, что делает ванильный /kill без аргументов)
        player.onKillCommand();

        // После смерти переносим игрока в Overworld.
        if (player.dimension != 0) {
            WorldServer overworld = server.getWorld(0);
            server.getPlayerList().transferPlayerToDimension(player, 0, overworld.getDefaultTeleporter());
        }
    }
    private static boolean hasTotemOfGod(EntityPlayerMP player) {
        if (containsTotem(player.inventory.mainInventory)) return true;
        if (containsTotem(player.inventory.armorInventory)) return true;
        if (containsTotem(player.inventory.offHandInventory)) return true;
        return false;
    }
    private static boolean containsTotem(List<ItemStack> inventory) {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty() && stack.getItem() == TotemOfGod.TOTEM_OF_GOD) return true;
        }
        return false;
    }
    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) { return Collections.emptyList(); }
}
