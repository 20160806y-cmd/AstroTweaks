package astrotweaks.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;

import java.util.List;
//import java.util.Map;
//import java.util.stream.IntStream;
//import java.util.stream.Collectors;
import java.util.Arrays;



public class CommandAstrotechCC {
	public static class CommandHandler implements ICommand {
		@Override public String getName() { return "astrotech"; }
		@Override public String getUsage(ICommandSender sender) { return "/astrotech [args]"; }
		@Override public List<String> getAliases() { return Arrays.asList(); }
		@Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return true; }
		@Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, net.minecraft.util.math.BlockPos pos) { return Arrays.asList(); }
		@Override public boolean isUsernameIndex(String[] args, int index) { return index == 1; }
		@Override public int compareTo(ICommand o) { return getName().compareTo(o.getName()); }

		@Override
		public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
			net.minecraft.entity.Entity entity = sender.getCommandSenderEntity();
			if (entity == null) return;
			java.util.Map<String, Object> cmdparams = java.util.stream.IntStream.range(0, args.length).boxed().collect(java.util.stream.Collectors.toMap(i -> Integer.toString(i), i -> args[i]));

			astrotweaks.procedure.P_AstroTechCP.exect(entity, cmdparams);
		}
	}
}
