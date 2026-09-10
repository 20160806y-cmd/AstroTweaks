package astrotweaks.command;

import net.minecraft.client.resources.I18n;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

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
        if (!(sender instanceof EntityPlayerMP)) { throw new CommandException(I18n.format("command.only_for_players")); }
        EntityPlayerMP player = (EntityPlayerMP) sender;

        // Сбрасываем точку спавна (аналогично тому, как её сбрасывает кровать → world spawn)
        // setSpawnChunk(pos, forced, dimension) — при pos = null точка возрождения сбрасывается на спавн мира.
        player.setSpawnChunk(null, false, player.dimension);
        // Убиваем игрока (то же, что делает ванильный /kill без аргументов)
        player.onKillCommand();
    }
    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) { return Collections.emptyList(); }
}