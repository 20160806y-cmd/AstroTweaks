package astrotweaks.Multiverse;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Server &rarr; client: tells the client which dimension ids belong to a multiverse
 * level (or the shared global dimension) so it can register the DimensionTypes
 * BEFORE the respawn packet arrives.
 */
public class MessageMultiverse implements IMessage {

    private static final int GLOBAL_SENTINEL = -1;
    private int baseDimId;
    private boolean global;
    private long seed;

    public MessageMultiverse() {}

    public MessageMultiverse(int baseDimId) {
        this(baseDimId, 0);
    }
    public MessageMultiverse(int baseDimId, long seed) {
        this.baseDimId = baseDimId;
        this.global = false;
        this.seed = seed;
    }
    public static MessageMultiverse forGlobal() {
        MessageMultiverse message = new MessageMultiverse(GLOBAL_SENTINEL);
        message.global = true;
        return message;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.baseDimId = buf.readInt();
        this.global = buf.readBoolean();
        this.seed = buf.readLong();
    }
    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.baseDimId);
        buf.writeBoolean(this.global);
        buf.writeLong(this.seed);
    }

    /**
     * Runs on the client netty thread. Must stay synchronous: the registration has to
     * be visible before the (already queued) respawn packet creates the WorldClient.
     */
    public static class ClientHandler implements IMessageHandler<MessageMultiverse, IMessage> {
        @Override
        public IMessage onMessage(MessageMultiverse message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                if (message.global) {
                    // Server is authoritative: it only sends this when the player is
                    // entering the void. Client registration is needed for rendering and
                    // has no save side effects, so the local Enable_uVOID flag is bypassed.
                    MultiverseDims.registerGlobalDimensionForClient();
                } else {
                    MultiverseDims.registerLevelDimensions(message.baseDimId);
                }
                if (message.seed != 0) {
                    final long s = message.seed;
                    net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                        if (mc.world != null && mc.world.getWorldInfo() != null) {
                            net.minecraft.world.storage.WorldInfo info = mc.world.getWorldInfo();
                            boolean applied = false;
                            // Try known field names first (MCP → SRG fallback)
                            for (String name : new String[]{"seed", "field_76100_a"}) {
                                try {
                                    java.lang.reflect.Field f =
                                            net.minecraft.world.storage.WorldInfo.class.getDeclaredField(name);
                                    f.setAccessible(true);
                                    f.setLong(info, s);
                                    applied = true;
                                    break;
                                } catch (Exception ignored) {}
                            }
                            // Fallback: find the long field whose current value matches getSeed()
                            if (!applied) {
                                long currentSeed = info.getSeed();
                                for (java.lang.reflect.Field f :
                                        net.minecraft.world.storage.WorldInfo.class.getDeclaredFields()) {
                                    if (f.getType() == long.class) {
                                        f.setAccessible(true);
                                        try {
                                            if (f.getLong(info) == currentSeed) {
                                                f.setLong(info, s);
                                                applied = true;
                                                break;
                                            }
                                        } catch (Exception ignored) {}
                                    }
                                }
                            }
                            if (!applied) {
                                System.err.println("[MULTIVERSE] Failed to set client world seed");
                            }
                        }
                    });
                }
            }
            return null;
        }
    }
}
