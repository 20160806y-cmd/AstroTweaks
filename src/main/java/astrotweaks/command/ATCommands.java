package astrotweaks.command;



public class ATCommands {
	public static void init(net.minecraftforge.fml.common.event.FMLServerStartingEvent event) {
		event.registerServerCommand(new CommandSwitchDim.CommandHandler());
		event.registerServerCommand(new CommandGm.CommandHandler());
		event.registerServerCommand(new CommandRsummon.CommandHandler());
		event.registerServerCommand(new CommandShowDeathsCC.CommandHandler());
		event.registerServerCommand(new CommandATVars.CommandHandler());
		event.registerServerCommand(new CommandATCC.CommandHandler());
		event.registerServerCommand(new CommandAstrotechCC.CommandHandler());
		event.registerServerCommand(new CommandCKill());

		if (astrotweaks.ModVariables.MULTIVERSE) {
			event.registerServerCommand(new astrotweaks.Multiverse.CommandMultiverse());
		}
	}
}
