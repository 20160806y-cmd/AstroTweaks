package astrotweaks.command;

import net.minecraft.util.math.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.Entity;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.ICommand;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.List;
import java.util.Collections;



public class CommandSwitchDim {
	public static class CommandHandler implements ICommand {
		@Override public int compareTo(ICommand c) { return getName().compareTo(c.getName()); }
		@Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return true; }
		@Override public List<String> getAliases() { return Collections.emptyList(); }
		@Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) { return Collections.emptyList(); }
		@Override public boolean isUsernameIndex(String[] args, int index) { return index == 1; }
		@Override public String getName() { return "dim"; }
		@Override public String getUsage(ICommandSender sender) {
			return "/dim <dimID> <playerName|@s> [x y z]|<playerName>";
		}
		@Override public void execute(MinecraftServer server, ICommandSender sender, String[] cmd) {
			if (!sender.canUseCommand(2, getName())) {
				sender.sendMessage(new TextComponentTranslation("command.no_permissions"));
				return;
			}
			// get source Entity may be null for console)
			Entity sourceEntity = sender.getCommandSenderEntity();
			// cmdparams
			astrotweaks.procedure.P_SwitchDim.exect( sourceEntity, false, arg(cmd, 0), arg(cmd, 1), arg(cmd, 2), arg(cmd, 3), arg(cmd, 4) );
		}
	}
	private static String arg(String[] a, int i) {
		return (i < a.length) ? a[i] : "";
	}
}
