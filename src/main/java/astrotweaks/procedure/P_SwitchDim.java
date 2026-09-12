package astrotweaks.procedure;

import net.minecraft.world.WorldServer;
import net.minecraft.world.Teleporter;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Map;
import astrotweaks.world.DepthsDim;



public final class P_SwitchDim {
	public P_SwitchDim() {}

	private static boolean Slient = false;
	private static void sendMsg(EntityPlayerMP executor, String msg) {
	    if (executor != null && !Slient) executor.sendMessage(new TextComponentString(msg));
	    else System.out.println(msg);
	}

	// ---------- Резолвер целевого игрока ----------
	// Поддерживает: null/""/@s -> executor; @p -> ближайший к executor в его измерении;
	//               @r -> случайный; всё остальное -> ник.
	private static EntityPlayerMP resolveTargetPlayer(MinecraftServer server, EntityPlayerMP executor, String param) {
		if (param == null || param.isEmpty() || "@s".equals(param)) {
			return executor; // null, если команда из консоли
		}
		if ("@p".equals(param)) {
			if (executor == null) return null;
			EntityPlayerMP nearest = null;
			double best = Double.MAX_VALUE;
			for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
				if (p.dimension != executor.dimension) continue;
				double d = p.getDistanceSq(executor.posX, executor.posY, executor.posZ);
				if (d < best) { best = d; nearest = p; }
			}
			return nearest;
		}
		if ("@r".equals(param)) {
			java.util.List<EntityPlayerMP> list = server.getPlayerList().getPlayers();
			if (list.isEmpty()) return null;
			return list.get(new java.util.Random().nextInt(list.size()));
		}
		return server.getPlayerList().getPlayerByUsername(param);
	}

	// ---------- Резолвер измерения (расширенный) ----------
	private static Integer resolveDimensionId(String raw, MinecraftServer server, EntityPlayerMP executor) {
		if (raw == null || raw.isEmpty()) return null;
		raw = raw.trim();

		if (raw.equals("-6000")) return DepthsDim.DIMID;
		if (raw.equals("cavern"))return DepthsDim.DIMID;

		if (raw.equals("@s")) {
			return executor != null ? executor.dimension : null;
		}
		if (raw.equals("@p")) {
			EntityPlayerMP p = resolveTargetPlayer(server, executor, "@p");
			return p != null ? p.dimension : null;
		}

		try {
			return Integer.parseInt(raw);
		} catch (NumberFormatException e) { return null; }
	}

	public static void exect(Entity entity, Map<String, String> cmdparams, boolean Slient) {
		EntityPlayerMP executor = null;
		if (entity instanceof EntityPlayerMP) {
			executor = (EntityPlayerMP) entity;
		}
		if (entity != null && entity.world.isRemote) return; // server side only

		MinecraftServer mcServer = (executor != null) ? executor.getServer() : FMLCommonHandler.instance().getMinecraftServerInstance();
		if (mcServer == null) {
			sendMsg(executor, "Server not found.");
			return;
		}

		String dimParam        = getParam(cmdparams, 0);
		String playerNameParam = getParam(cmdparams, 1);
		String xParam          = getParam(cmdparams, 2);
		String yParam          = getParam(cmdparams, 3);
		String zParam          = getParam(cmdparams, 4);

		// ---- 1. Целевой игрок (поддержка @s / @p / @r / ник) ----
		EntityPlayerMP targetPlayer = resolveTargetPlayer(mcServer, executor, playerNameParam);
		if (targetPlayer == null) {
			String msg = (playerNameParam == null || playerNameParam.isEmpty())
					? "You must specify a player name when running from console."
					: "Player not found: " + playerNameParam;
			sendMsg(executor, msg);
			return;
		}

		// ---- 2. Проверка режима "tp к игроку" ----
		boolean teleportToPlayerMode = false;
		String  sourcePlayerName     = null;
		if (xParam != null && !xParam.isEmpty() && yParam.isEmpty() && zParam.isEmpty() && !isCoordinateToken(xParam)) {
			// Третий аргумент не похож на координату, а 4-й и 5-й пусты => это имя игрока.
			sourcePlayerName = xParam;
			teleportToPlayerMode = true;
		} else if (xParam != null && !xParam.isEmpty() && !isCoordinateToken(xParam)) {
			// xParam не координата, но yParam/zParam не пусты - синтаксическая ошибка.
			sendMsg(executor, "Invalid syntax: expected coordinates or player name.");
			return;
		}

		// ---- 3. Целевое измерение / координаты ----
		Integer targetDim = null;
		boolean hasCoords = false;
		double  tx = 0, ty = 0, tz = 0;

		if (teleportToPlayerMode) {
			// tp к игроку: @s / @p / @r тоже поддержаны
			EntityPlayerMP sourcePlayer = resolveTargetPlayer(mcServer, executor, sourcePlayerName);
			if (sourcePlayer == null) {
				sendMsg(executor, "Source player not found: " + sourcePlayerName);
				return;
			}
			targetDim = sourcePlayer.dimension;
			tx = sourcePlayer.posX;
			ty = sourcePlayer.posY;
			tz = sourcePlayer.posZ;
			hasCoords = true;
			sendMsg(executor, "Teleporting to " + sourcePlayerName + " at dim " + targetDim);
		} else {
			// Обычный режим: DIM [+ coords]
			targetDim = resolveDimensionId(dimParam, mcServer, executor);
			if (targetDim == null) {
				sendMsg(executor, "Unknown dimension id: " + dimParam);
				return;
			}
			try {
				if (xParam != null && !xParam.isEmpty() && yParam != null && !yParam.isEmpty() && zParam != null && !zParam.isEmpty()) {
					tx = parseCoord(xParam, targetPlayer.posX, false);
					ty = parseCoord(yParam, targetPlayer.posY, true);
					tz = parseCoord(zParam, targetPlayer.posZ, false);
					hasCoords = true;
				}
			} catch (NumberFormatException e) {
				sendMsg(executor, "Invalid coordinates.");
				return;
			}
		}

		// ---- 4. Сохраняем последнюю позицию ----
		NBTTagCompound data = targetPlayer.getEntityData();
		data.setDouble("lastDimPosX", targetPlayer.posX);
		data.setDouble("lastDimPosY", targetPlayer.posY);
		data.setDouble("lastDimPosZ", targetPlayer.posZ);
		data.setInteger("lastDimId",  targetPlayer.dimension);

		// ---- 5. Уже в нужном измерении ----
		if (targetDim == targetPlayer.dimension) {
			if (hasCoords) {
				targetPlayer.setPositionAndUpdate(tx, ty, tz);
				sendMsg(executor, "Teleported player " + targetPlayer.getName()
						+ " to dimension " + targetDim + " at " + tx + ", " + ty + ", " + tz);
			} else {
				sendMsg(executor, "Player " + targetPlayer.getName()
						+ " is already in dimension " + targetDim
						+ ". No coordinates provided, nothing changed.");
			}
			return;
		}

		// ---- 6. Переход в другое измерение ----
		WorldServer targetWorld = mcServer.getWorld(targetDim);
		if (targetWorld == null) {
			// Forge-API: force-load зарегистрированного измерения
			targetWorld = net.minecraftforge.common.DimensionManager.getWorld(targetDim, true);
		}
		if (targetWorld == null) {
			sendMsg(executor, "Failed to load target world: " + targetDim);
			return;
		}

		WorldServer playerWorld = (WorldServer) targetPlayer.world;
		mcServer.getPlayerList().transferPlayerToDimension(targetPlayer, targetDim, new TeleporterDirectWrapper(playerWorld, targetDim));

		if (hasCoords) {
			targetPlayer.setPositionAndUpdate(tx, ty, tz);
		}

		sendMsg(executor, "Teleported player " + targetPlayer.getName()
				+ " to dimension " + targetDim
				+ (hasCoords ? (" at " + tx + ", " + ty + ", " + tz) : "."));
	}
    private static boolean isCoordinateToken(String token) {
	    if (token == null || token.isEmpty()) return false;
	    char c = token.charAt(0);
	    // Allow: tilda, digits, minus, dot
	    return c == '~' || c == '-' || c == '.' || (c >= '0' && c <= '9');
	}
	// get param for index
	private static String getParam(Map<String, String> cmdparams, int index) {
	    if (cmdparams == null) return "";
	    return cmdparams.getOrDefault(Integer.toString(index), "");
	}
	private static double parseCoord(String token, double base, boolean isY) throws NumberFormatException {
	    token = token.trim();
	    if (token.startsWith("~")) {
	        String rest = token.length() == 1 ? "" : token.substring(1).trim();
	        if (rest.isEmpty()) return base;        // "~" -> base (no shift)
	        return base + Double.parseDouble(rest); // "~1" -> base+1 ; "~1.5" -> base+1.5
	    } else {
	        // absolute coords
	        if (token.contains(".") || token.contains(",")) {
	            return Double.parseDouble(token.replace(',', '.'));
	        } else {
	            // of center of block
	            if (!isY) {return Double.parseDouble(token) + 0.5; }
	            else {return Double.parseDouble(token); }
	        }
	    }
	}
	// Dimension ID resolution: support for aliases
	private static class TeleporterDirectWrapper extends Teleporter {
		private final int dim;
		private final MinecraftServer server;
		public TeleporterDirectWrapper(WorldServer world, int dimension) {
			// use the world for the current server dimension (the Teleporter constructor requires a WorldServer; we'll take the player's world before the transition)
			super(world);
			this.dim = dimension;
			this.server = world.getMinecraftServer();
		}
		@Override public void placeInPortal(Entity entity, float yawRotation) {}
		@Override public boolean placeInExistingPortal(Entity entity, float yawRotation) { return true; }
		@Override public boolean makePortal(Entity entity) { return true; }
	}
}
