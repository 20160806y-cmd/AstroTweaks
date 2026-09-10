package astrotweaks.Multiverse;

import astrotweaks.AstrotweaksMod;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

import java.util.Random;

/**
 * /mv join &lt;layer_id&gt; [dim_id] [seed]
 * <ul>
 *   <li>layer_id 0 &rarr; the save's original world (dimension 0)</li>
 *   <li>layer_id &lt;0 &rarr; the shared global dimension (-1000000)</li>
 *   <li>layer_id &gt;0 or a name &rarr; the multiverse level of the current save</li>
 *   <li>dim_id 0/1/2/3 &rarr; overworld / nether / end / depths of the target</li>
 *   <li>seed &rarr; used only when the level is created for the first time; default random</li>
 * </ul>
 * /mv get &rarr; prints the current level and dimension id the sender is in.
 */
public class CommandMultiverse extends CommandBase {
    @Override
    public String getName() {
        return "mv";
    }
    @Override
    public String getUsage(ICommandSender sender) {
        return "/mv <join <layer_id> [dim_id] [seed] | get>";
    }
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            sender.sendMessage(new TextComponentString("Usage: " + getUsage(sender)));
            return;
        }
        if ("get".equalsIgnoreCase(args[0])) {
            handleGet(sender);
            return;
        }
        if (!"join".equalsIgnoreCase(args[0])) {
            sender.sendMessage(new TextComponentString("Usage: " + getUsage(sender)));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString("Usage: " + getUsage(sender) + " (layer 0 = original world, <0 = global, >0 or name = level)"));
            return;
        }

        Integer layerId = tryParseInt(args[1]);
        LevelDimensionType type = args.length >= 3 ? dimensionTypeFromArgs(args[2], sender) : LevelDimensionType.OVERWORLD;
        long seed = args.length >= 4 ? parseSeed(args[3], sender) : new Random().nextLong();

        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        LevelManager lm = LevelManager.getInstance();

        if (layerId != null && layerId == 0) {
            joinOriginalWorld(server, player);
            return;
        }
        if (layerId != null && layerId < 0) {
            joinGlobal(server, sender, player, lm);
            return;
        }

        String levelName = layerId != null ? Integer.toString(layerId) : sanitize(args[1]);
        LevelData data = lm.getOrCreateLevel(server, levelName, seed);
        if (data == null) {
            sender.sendMessage(new TextComponentString("Failed to create/load level '" + levelName + "'"));
            return;
        }

        // Tell the CLIENT which dimension ids belong to this level BEFORE the respawn
        // packet is sent - otherwise the client cannot build a WorldProvider for them.
        AstrotweaksMod.PACKET_HANDLER.sendTo(new MessageMultiverse(data.baseId), player);

        WorldServer targetWorld = lm.getOrCreateWorld(server, data, type);
        if (targetWorld == null) {
            sender.sendMessage(new TextComponentString("Failed to load world for level '" + levelName + "'"));
            return;
        }
        BlockPos target = spawnFor(targetWorld, type);
        if (type == LevelDimensionType.DEPTHS) {
            // The depths level is an all-fill cavern: carve the same 2-block arrival
            // pocket that the bedrock entry (MineDimEnter) keeps clear, so the player
            // does not spawn inside solid stone.
            targetWorld.destroyBlock(target, true);
            targetWorld.destroyBlock(target.up(), true);
        }
        teleportTo(player, targetWorld, target);
        sender.sendMessage(new TextComponentString("Teleported to level '" + levelName + "' (" + type.name().toLowerCase() + ")"));
    }
    /** layer 0: return to the save's overworld spawn. */
    private void joinOriginalWorld(MinecraftServer server, EntityPlayerMP player) {
        WorldServer world0 = server.getWorld(0);
        BlockPos spawn = world0.getSpawnPoint();
        MultiverseEvents.teleportIgnoringPortalRemap(player, 0, new MultiverseTeleporter(spawn));
        player.fallDistance = 0.0F;
        player.connection.setPlayerLocation( spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5, player.rotationYaw, player.rotationPitch );
        player.sendMessage(new TextComponentString("Returned to the original world"));
    }

    /** layer &lt;0: the shared global dimension. */
    private void joinGlobal(MinecraftServer server, ICommandSender sender, EntityPlayerMP player, LevelManager lm) {
        AstrotweaksMod.PACKET_HANDLER.sendTo(MessageMultiverse.forGlobal(), player);
        WorldServer global = lm.getOrCreateGlobalWorld(server);
        if (global == null) {
            sender.sendMessage(new TextComponentString("Failed to load the Void dimension"));
            return;
        }
        // Fixed arrival spot: (0,64,0) stands directly on the void bedrock at (0,63,0).
        BlockPos target = new BlockPos(0, 64, 0);
        global.getWorldInfo().setSpawn(target);
        // В измерении пустоты нельзя респавниться
        //global.setSpawnPoint(target);
        teleportTo(player, global, target);
        sender.sendMessage(new TextComponentString("Teleported to the Void dimension"));
    }

    /** /mv get: print the current level name and dimension id for debugging. */
    private void handleGet(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString("Only players have a current dimension"));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        LevelManager lm = LevelManager.getInstance();
        int dim = player.dimension;

        if (dim == MultiverseDims.GLOBAL_DIM) {
            player.sendMessage(new TextComponentString("[MULTIVERSE] Global Void dimension (-1000000) - DimID " + dim));
            return;
        }
        LevelData data = lm.getLevelByDimensionId(dim);
        if (data != null) {
            LevelDimensionType type = data.typeOf(dim);
            player.sendMessage(new TextComponentString("[MULTIVERSE] Level '"+data.name+"' (base "+data.baseId+") - "+type.name()+" - DimID "+dim + " - UID "+data.uid));
        } else if (dim == 0) {
            player.sendMessage(new TextComponentString("[MULTIVERSE] Original world (layer 0) - DimID " + dim));
        } else {
            player.sendMessage(new TextComponentString("[MULTIVERSE] Vanilla dimension (dim " + dim + ") - DimID " + dim));
        }
    }
    private BlockPos spawnFor(WorldServer world, LevelDimensionType type) {
        if (type == LevelDimensionType.END) {
            BlockPos coordinate = world.getSpawnCoordinate();
            if (coordinate == null || coordinate.equals(BlockPos.ORIGIN)) {
                return world.getSpawnPoint();
            }
            return coordinate;
        }
        if (type == LevelDimensionType.DEPTHS) {
            return new BlockPos(8, 252, 8);
        }
        BlockPos baseSpawn = world.getSpawnPoint();
        if (type == LevelDimensionType.OVERWORLD) {
            int radius = world.getGameRules().getInt("spawnRadius");
            if (radius > 0) {
                Random rand = world.rand;
                int dx = rand.nextInt(radius * 2 + 1) - radius;
                int dz = rand.nextInt(radius * 2 + 1) - radius;
                int newX = baseSpawn.getX() + dx;
                int newZ = baseSpawn.getZ() + dz;
                int safeY = LevelManager.findStandableY(world, newX, newZ);
                return new BlockPos(newX, safeY, newZ);
            }
        }
        return baseSpawn;
    }
    private void teleportTo(EntityPlayerMP player, WorldServer targetWorld, BlockPos pos) {
        Entity travel = MultiverseEvents.teleportIgnoringPortalRemap(player, targetWorld.provider.getDimension(), new MultiverseTeleporter(pos));
        if (travel instanceof EntityPlayerMP) {
            EntityPlayerMP moved = (EntityPlayerMP) travel;
            moved.fallDistance = 0.0F;
            moved.connection.setPlayerLocation( pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, moved.rotationYaw, moved.rotationPitch );
        }
    }
    private static LevelDimensionType dimensionTypeFromArgs(String s, ICommandSender sender) throws CommandException {
        Integer id = tryParseInt(s);
        if (id == null || id < 0 || id > 3) {
            sender.sendMessage(new TextComponentString("Invalid dim_id: " + s + " (0=overworld, 1=nether, 2=end, 3=depths)"));
            throw new CommandException("Invalid dim_id: %s", s);
        }
        return LevelDimensionType.values()[id];
    }
    private static long parseSeed(String s, ICommandSender sender) throws CommandException {
        if ("random".equalsIgnoreCase(s)) {
            return new Random().nextLong();
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            sender.sendMessage(new TextComponentString("Invalid seed: " + s));
            throw new CommandException("Invalid seed: %s", s);
        }
    }
    private static Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }
}
