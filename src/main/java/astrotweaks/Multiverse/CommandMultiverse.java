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
    private static final int MAX_DIMS = LevelDimensionType.values().length;

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

        // Numeric layer ids map directly to universe slots (folder is MV_<slot>);
        // named universes are normalized to MV_<name> inside getOrCreateLevel.
        LevelData data;
        if (layerId != null) {
            data = lm.getOrCreateLevel(server, layerId, seed);
        } else {
            data = lm.getOrCreateLevel(server, sanitize(args[1]), seed);
        }
        if (data == null) {
            sender.sendMessage(new TextComponentString("Failed to create/load level '" + args[1] + "'"));
            return;
        }

        // Tell the CLIENT which dimension ids belong to this level BEFORE the respawn
        // packet is sent - otherwise the client cannot build a WorldProvider for them.
        AstrotweaksMod.PACKET_HANDLER.sendTo(new MessageMultiverse(data.baseId, data.seed), player);

        WorldServer targetWorld = lm.getOrCreateWorld(server, data, type);
        if (targetWorld == null) {
            sender.sendMessage(new TextComponentString("Failed to load world for level '" + data.name + "'"));
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
        sender.sendMessage(new TextComponentString("Teleported to level '" + data.name + "' (" + type.name().toLowerCase() + ")"));
    }
    /** layer 0: return to the save's overworld spawn. */
    private void joinOriginalWorld(MinecraftServer server, EntityPlayerMP player) {
        WorldServer world0 = server.getWorld(0);
        BlockPos anchor = world0.getSpawnPoint();

        int feetY;
        if (isColumnFree(world0, anchor)) {
            // Мировой спавн уже стоит там, где игрок реально может встать.
            feetY = anchor.getY();
        } else {
            // Спавн зарыт/залит — ищем верхний solid/liquid в той же колонне.
            int topY = findTopSolidOrLiquidY(world0, anchor.getX(), anchor.getZ());
            feetY = topY + 1;
        }
        BlockPos target = new BlockPos(anchor.getX(), feetY, anchor.getZ());

        MultiverseEvents.teleportIgnoringPortalRemap(player, 0, new MultiverseTeleporter(target));

        player.fallDistance = 0.0F;
        player.connection.setPlayerLocation( target.getX() + 0.5, target.getY(), target.getZ() + 0.5, player.rotationYaw, player.rotationPitch );
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
            BlockPos endSpawn = new BlockPos(0, 100, 0);
            world.getWorldInfo().setSpawn(endSpawn);
            return endSpawn;
        }
        if (type == LevelDimensionType.DEPTHS) return new BlockPos(8, 252, 8);

        if (type == LevelDimensionType.OVERWORLD) {
            BlockPos worldSpawn = world.getSpawnPoint();

            // 1) Мировой спавн, если игрок реально может там стоять
            if (isColumnFree(world, worldSpawn)) {
                return worldSpawn;
            }

            // 2) Иначе — верхний solid/liquid в центральной колонне чанка спавна
            int cx = (worldSpawn.getX() >> 4) * 16 + 8;
            int cz = (worldSpawn.getZ() >> 4) * 16 + 8;
            int topY = findTopSolidOrLiquidY(world, cx, cz);
            BlockPos result = new BlockPos(cx, topY + 1, cz);
            world.getWorldInfo().setSpawn(result);
            return result;
        }

        if (type == LevelDimensionType.NETHER) {
            BlockPos base = world.getSpawnPoint();
            // Центр чанка, в котором лежит «якорь» (0,0 по умолчанию)
            int cx = (base.getX() >> 4) * 16 + 8;
            int cz = (base.getZ() >> 4) * 16 + 8;

            // В Nether верхние ~5 блоков — bedrock-потолок. Начинаем чуть ниже.
            // getActualHeight() в 1.12 = 128, bedrock на 127. Стартуем с 107.
            int startY = 100;
            int surfaceY = -1;
            for (int y = startY; y > 1; y--) {
                net.minecraft.block.state.IBlockState s = world.getBlockState(new net.minecraft.util.math.BlockPos(cx, y, cz));
                if (s.getMaterial().isSolid() || s.getMaterial().isLiquid()) {
                    surfaceY = y;
                    break;
                }
            }

            // Fallback: если почему-то ничего не нашли (пустой чанк), берём 64.
            if (surfaceY < 0) surfaceY = 64;

            BlockPos result = new BlockPos(cx, surfaceY + 1, cz);
            world.getWorldInfo().setSpawn(result);
            return result;
        }

        // Fallback
        BlockPos baseSpawn = world.getSpawnPoint();
        int bx = baseSpawn.getX();
        int bz = baseSpawn.getZ();
        int safeY = MultiverseTeleporter.findSafeSpawnY(world, bx, bz, 100);
        if (safeY < 0) safeY = baseSpawn.getY() < 64 ? 64 : baseSpawn.getY();
        BlockPos result = new BlockPos(bx, safeY, bz);
        world.getWorldInfo().setSpawn(result);

        return result;
    }

    /** Блок под ногами — твёрдый/жидкость; блок в позиции и над ней — не solid/liquid. */
    private static boolean isColumnFree(WorldServer world, BlockPos pos) {
        net.minecraft.block.state.IBlockState below = world.getBlockState(pos.down());
        if (!below.getMaterial().isSolid() && !below.getMaterial().isLiquid()) return false;
        for (int dy = 0; dy <= 1; dy++) {
            net.minecraft.block.state.IBlockState s = world.getBlockState(pos.up(dy));
            if (s.getMaterial().isSolid() || s.getMaterial().isLiquid()) return false;
        }
        return true;
    }

    /** Самый верхний solid/liquid в колонне — над ним по построению AIR. */
    private static int findTopSolidOrLiquidY(WorldServer world, int x, int z) {
        for (int y = world.getActualHeight() - 1; y > 0; y--) {
            net.minecraft.block.state.IBlockState s =
                    world.getBlockState(new net.minecraft.util.math.BlockPos(x, y, z));
            if (s.getMaterial().isSolid() || s.getMaterial().isLiquid()) return y;
        }
        return 1;
    }
    private void teleportTo(EntityPlayerMP player, WorldServer targetWorld, BlockPos pos) {
        Entity travel = MultiverseEvents.teleportIgnoringPortalRemap(player, targetWorld.provider.getDimension(), new MultiverseTeleporter(pos));
        if (travel instanceof EntityPlayerMP) {
            EntityPlayerMP moved = (EntityPlayerMP) travel;
            moved.fallDistance = 0.0F;
            moved.connection.setPlayerLocation( pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, moved.rotationYaw, moved.rotationPitch );
        }
    }
    private static LevelDimensionType dimensionTypeFromArgs(String s, ICommandSender sender) throws CommandException {
        Integer id = tryParseInt(s);

        if (id == null) { 
            sender.sendMessage(new TextComponentString("Invalid dim_id: "+s+" (0=overworld, 1=nether, 2=end, 3=depths)"));
            throw new CommandException("Invalid dim_id: %s", s); 
        }
        if (id < 0) {
            sender.sendMessage(new TextComponentString("The MV ID cannot be less than 0. Received: " + s));
            throw new CommandException("The MV ID cannot be less than 0. Received: %s", s);
        }
        if (id > 3) {
            sender.sendMessage(new TextComponentString("The MV ID cannot be greater than 0. Received: " + s));
            throw new CommandException("The MV ID cannot be greater than %s. Received: %s", MAX_DIMS, s);
        }

        return LevelDimensionType.values()[id];
    }
    private static long parseSeed(String s, ICommandSender sender) throws CommandException {
        if ("random".equalsIgnoreCase(s)) 
            return new Random().nextLong();

        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            sender.sendMessage(new TextComponentString("Invalid seed: " + s));
            throw new CommandException("Invalid seed: %s", s);
        }
    }
    private static Integer tryParseInt(String s) {
        try {return Integer.parseInt(s);
        } catch (NumberFormatException e) { return null; }
    }
    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }
}
