package astrotweaks.tech.tdark;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;
import java.io.IOException;

import astrotweaks.AstrotweaksMod;
import astrotweaks.Multiverse.LevelData;
import astrotweaks.Multiverse.LevelDimensionType;
import astrotweaks.Multiverse.LevelManager;
import astrotweaks.Multiverse.MultiverseDims;
import astrotweaks.Multiverse.MultiverseUtil;


public class TDArkGUI {
	public static int GUIID = 20;

	// Message for transferring data from the GUI
	public static class TDArkActionMessage implements IMessage {
	    int buttonID;
	    int blockX, blockY, blockZ;
	    String uidStr;   // 8-hex хеш целевой вселенной (пусто = случайная)
	    String seedStr;  // сид, используется только при СОЗДАНИИ новой вселенной
	    String dimStr;   // относительный DimID целевой вселенной
	    String xStr, yStr, zStr;
	    private boolean clearMode;
	    private boolean captureEntities;
	    private boolean captureItems;

	    public TDArkActionMessage() {}

		public TDArkActionMessage(int buttonID, int blockX, int blockY, int blockZ, String seed, String uid, String dim, String x, String y, String z,
 					boolean clearMode, boolean captureEntities, boolean captureItems) {
	        this.buttonID = buttonID;
	        this.blockX = blockX; this.blockY = blockY; this.blockZ = blockZ;
	        this.uidStr = uid; this.seedStr = seed; this.dimStr = dim;
	        this.xStr = x; this.yStr = y; this.zStr = z;
	        this.clearMode = clearMode;
	        this.captureEntities = captureEntities;
	        this.captureItems = captureItems;
	    }
	    @Override
	    public void toBytes(ByteBuf buf) {
	        buf.writeInt(buttonID);
	        buf.writeInt(blockX); buf.writeInt(blockY); buf.writeInt(blockZ);
	        writeString(buf, seedStr); writeString(buf, uidStr); writeString(buf, dimStr);
	        writeString(buf, xStr); writeString(buf, yStr); writeString(buf, zStr);
	        buf.writeBoolean(clearMode);
	        buf.writeBoolean(captureEntities);
	        buf.writeBoolean(captureItems);
	    }
	    @Override
	    public void fromBytes(ByteBuf buf) {
	        buttonID = buf.readInt();
	        blockX = buf.readInt(); blockY = buf.readInt(); blockZ = buf.readInt();
	        seedStr = readString(buf); uidStr = readString(buf); dimStr = readString(buf);
	        xStr = readString(buf); yStr = readString(buf); zStr = readString(buf);
	        clearMode = buf.readBoolean();
	        captureEntities = buf.readBoolean();
	        captureItems = buf.readBoolean();
	    }
	    private void writeString(ByteBuf buf, String s) {
	        byte[] bytes = s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
	        buf.writeInt(bytes.length);
	        buf.writeBytes(bytes);
	    }
	    private String readString(ByteBuf buf) {
	        int len = buf.readInt();
	        byte[] bytes = new byte[len];
	        buf.readBytes(bytes);
	        return new String(bytes, StandardCharsets.UTF_8);
	    }
	}

	// Message handler
	public static class TDArkActionMessageHandler implements IMessageHandler<TDArkActionMessage, IMessage> {
	    @Override
	    public IMessage onMessage(TDArkActionMessage message, MessageContext context) {
	        EntityPlayerMP player = context.getServerHandler().player;
	        player.getServerWorld().addScheduledTask(() -> handle(player, message));
	        return null;
	    }

	    private void handle(EntityPlayerMP player, TDArkActionMessage message) {
            World world = player.world;
            BlockPos pos = new BlockPos(message.blockX, message.blockY, message.blockZ);
            if (!world.isBlockLoaded(pos)) return;

            TileEntity te = world.getTileEntity(pos);
            if (!(te instanceof BlockTDArk.TileEntityCustom)) return;
            BlockTDArk.TileEntityCustom teTDArk = (BlockTDArk.TileEntityCustom) te;

            // Compute core position for area broadcasting
            BlockPos corePos = TDArkTransferHelper.findCore(world, pos);

            String uid = message.uidStr == null ? "" : message.uidStr.trim().toLowerCase(Locale.ROOT);

            // Parse strings
            long seed = 0;
            int targetDim = 0, targetX = 0, targetY = 65, targetZ = 0;
            boolean parseOk = true;
            try {
                seed = message.seedStr == null || message.seedStr.trim().isEmpty() ? 0 : Long.parseLong(message.seedStr.trim());
                targetDim = Integer.parseInt(message.dimStr);
                targetX = Integer.parseInt(message.xStr);
                targetY = Integer.parseInt(message.yStr);
                targetZ = Integer.parseInt(message.zStr);
            } catch (NumberFormatException e) {
                parseOk = false;
            }
            System.out.println("[TDArk] handle buttonID=" + message.buttonID
                    + " seedStr=\"" + message.seedStr + "\" parsedSeed=" + seed
                    + " uid=\"" + uid + "\" dim=" + targetDim);

            // 1 = сохранение при закрытии GUI: просто пишем сырые значения в TE.
            if (message.buttonID == 1) {
                teTDArk.setTargetSeed(seed);
                teTDArk.setTargetUid(uid);
                if (parseOk) {
                    teTDArk.setTargetDim(targetDim);
                    teTDArk.setTargetX(targetX);
                    teTDArk.setTargetY(targetY);
                    teTDArk.setTargetZ(targetZ);
                }
                teTDArk.setClearMode(message.clearMode);
                teTDArk.setCaptureEntities(message.captureEntities);
                teTDArk.setCaptureItems(message.captureItems);
                return;
            }

            // 0 = начало переноса
            if (message.buttonID == 0) {
                if (!parseOk || uid.length() > 8) {
                    player.sendMessage(new TextComponentTranslation("ark.invalid_coords"));
                    return;
                }

                // Строка "void" в поле UID -> перенос во вселенную пустоты (DimID -1000000). 
				// У неё нет подвселенных, поэтому DimID из GUI игнорируется
                if (uid.equals("void")) {
                    if (!MultiverseDims.isGlobalDimensionEnabled()) {
                        TDArkTransferHelper.broadcastToArea(world, corePos, player, new TextComponentTranslation("tdark.err.bad_hash"));
                        return;
                    }
                    System.out.println("[TDArk] Easter egg 'void': transfer to the Void dimension (-1000000), "
                            + "targetX=" + targetX + " targetY=" + targetY + " targetZ=" + targetZ);
                    TDArkTransferHelper.broadcastToArea(world, corePos, player, new TextComponentTranslation("tdark.delayed_start", BlockTDArk.TileEntityCustom.TRANSFER_DELAY));
                    teTDArk.startDelayedTransfer(player, pos, MultiverseDims.GLOBAL_DIM, targetX, targetY, targetZ,
                            message.clearMode, message.captureEntities, message.captureItems);
                    return;
                }

                LevelManager lm = LevelManager.getInstance();
                LevelData target;

                // Разрешаем UID -> вселенная
                if (uid.isEmpty() || uid.equals("0")) {
                    // Случайная вселенная: если слот занят - перемещение, если свободен - создание.
                    long effectiveSeed = seed == 0 ? new Random().nextLong() : seed;
                    System.out.println("[TDArk] uid empty, effectiveSeed=" + effectiveSeed + " (original seed=" + seed + ")");
                    target = lm.getRandomLevelOrCreate(player.getServer(), effectiveSeed);
                } else {
                    LevelData existing = lm.getLevelByUid(uid);
                    if (existing != null) {
                        // Известная вселенная: перемещаемся, сид игнорируется.
                        target = existing;
                    } else {
                        int number = lm.numberForUid(uid);
                        if (number < 0) {
                            TDArkTransferHelper.broadcastToArea(world, corePos, player, new TextComponentTranslation("tdark.err.bad_hash"));
                            return;
                        }
                    // Не созданная ранее вселенная с предсказанным UID: создаём (сид только при создании).
                    long effectiveSeed2 = seed == 0 ? new Random().nextLong() : seed;
                    System.out.println("[TDArk] uid known, creating number=" + number + " effectiveSeed=" + effectiveSeed2);
                    target = lm.getOrCreateLevel(player.getServer(), number, effectiveSeed2);
                    }
                }
                if (target == null) {
                    TDArkTransferHelper.broadcastToArea(world, corePos, player, new TextComponentTranslation("ark.err.target_world"));
                    return;
                }

                // Относительный DimID резолвится относительно ЦЕЛЕВОЙ вселенной.
                int resolvedDim = MultiverseUtil.resolveRelativeDim(target.baseId, targetDim);

                WorldServer targetWorld = null;
                LevelDimensionType tt = target.typeOf(resolvedDim);
                if (tt != null) {
                    targetWorld = lm.getOrCreateWorld(player.getServer(), target, tt);
                } else {
                    if (!DimensionManager.isDimensionRegistered(resolvedDim)) {
                        TDArkTransferHelper.broadcastToArea(world, corePos, player, new TextComponentTranslation("ark.err.dim", targetDim));
                        return;
                    }
                    targetWorld = player.getServer().getWorld(resolvedDim);
                }
                if (targetWorld == null) {
                    TDArkTransferHelper.broadcastToArea(world, corePos, player, new TextComponentTranslation("ark.err.target_world"));
                    return;
                }

                // MessageMultiverse with seed is now sent in performTeleport (right before
                // the actual teleport) so the client seed-stamp fires on the correct WorldClient.
	            TDArkTransferHelper.broadcastToArea(world, corePos, player, new TextComponentTranslation("tdark.delayed_start", BlockTDArk.TileEntityCustom.TRANSFER_DELAY));

                teTDArk.startDelayedTransfer(player, pos, resolvedDim, targetX, targetY, targetZ, message.clearMode, message.captureEntities, message.captureItems);
            }
	    }
	}

	public static class GuiContainerMod extends Container {
		private final IInventory internal;
		public GuiContainerMod(World world, int x, int y, int z, EntityPlayer player) {
			IInventory inv = null;
			TileEntity ent = world.getTileEntity(new BlockPos(x, y, z));
			if (ent instanceof BlockTDArk.TileEntityCustom) {
			    inv = ((BlockTDArk.TileEntityCustom) ent).getInventory();
			}
			this.internal = inv;

			if (internal != null) {
			    // 3 слота Относительно GUI
			    this.addSlotToContainer(new Slot(internal, 0, -83, 153));
			    this.addSlotToContainer(new Slot(internal, 1, -61, 153));
			    this.addSlotToContainer(new Slot(internal, 2, -39, 153));
			}
			for (int si = 0; si < 3; ++si)
				for (int sj = 0; sj < 9; ++sj)
					this.addSlotToContainer(new Slot(player.inventory, sj + (si + 1) * 9, sj * 18 + 44, 94 + si * 18));
			for (int si = 0; si < 9; ++si)
				this.addSlotToContainer(new Slot(player.inventory, si, 8 + si * 18 + 36, 152));
		}
		@Override
		public boolean canInteractWith(EntityPlayer player) {
			return internal != null && internal.isUsableByPlayer(player);
		}
		@Override
		public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
			ItemStack itemstack = ItemStack.EMPTY;
			Slot slot = this.inventorySlots.get(index);
			if (slot != null && slot.getHasStack()) {
				ItemStack itemstack1 = slot.getStack();
				itemstack = itemstack1.copy();
				if (index < 3) {
					if (!super.mergeItemStack(itemstack1, 3, this.inventorySlots.size(), true)) {
						return ItemStack.EMPTY;
					}
					slot.onSlotChange(itemstack1, itemstack);
				} else if (!super.mergeItemStack(itemstack1, 0, 3, false)) {
					if (index < 3 + 27) {
						if (!super.mergeItemStack(itemstack1, 3 + 27, this.inventorySlots.size(), true)) {
							return ItemStack.EMPTY;
						}
					} else {
						if (!super.mergeItemStack(itemstack1, 3, 3 + 27, false)) {
							return ItemStack.EMPTY;
						}
					}
					return ItemStack.EMPTY;
				}
				if (itemstack1.getCount() == 0) {
					slot.putStack(ItemStack.EMPTY);
				} else {
					slot.onSlotChanged();
				}
				if (itemstack1.getCount() == itemstack.getCount()) {
					return ItemStack.EMPTY;
				}
				slot.onTake(playerIn, itemstack1);
			}
			return itemstack;
		}
	}

	public static class GuiWindow extends GuiContainer {
	    private final World world;
	    private final int x, y, z;
	    private final EntityPlayer entity;
	    private BlockTDArk.TileEntityCustom teTDArk;

	    private boolean clearMode = true;
	    private boolean captureEntities = true;
	    private boolean captureItems = true;

	    private GuiButton btnClearMode;
	    private GuiButton btnCaptureEntities;
	    private GuiButton btnCaptureItems;

	    private GuiTextField fieldSeed;
	    private GuiTextField fieldUid;
	    private GuiTextField fieldDim;
	    private GuiTextField TW_X;
	    private GuiTextField TW_Y;
	    private GuiTextField TW_Z;
	    private static final ResourceLocation texture = new ResourceLocation("astrotweaks:textures/tdark_gui_modern.png");

		public GuiWindow(World world, int x, int y, int z, EntityPlayer entity) {
			super(new GuiContainerMod(world, x, y, z, entity));
			this.world = world;
			this.x = x; this.y = y; this.z = z;
			this.entity = entity;
			this.xSize = 200;
			this.ySize = 166;
		}

	    @Override
	    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
	        this.drawDefaultBackground();
	        super.drawScreen(mouseX, mouseY, partialTicks);
	        this.renderHoveredToolTip(mouseX, mouseY);
	        fieldSeed.drawTextBox();
	        fieldUid.drawTextBox();
	        fieldDim.drawTextBox();
	        TW_X.drawTextBox();
	        TW_Y.drawTextBox();
	        TW_Z.drawTextBox();
	    }
	    @Override
	    protected void drawGuiContainerBackgroundLayer(float par1, int par2, int par3) {
			GL11.glColor4f(1, 1, 1, 1);
	        this.mc.renderEngine.bindTexture(texture);
	        this.drawModalRectWithCustomSizedTexture(this.guiLeft - 89, this.guiTop - 5, 0, 0, 300, 180, 300, 180);
	    }
	    @Override
	    protected void drawGuiContainerForegroundLayer(int par1, int par2) {
			// Это рисуется относительно GUI по умолчанию
	        fontRenderer.drawString(I18n.format("tdark.interface"), 40, 0, 0x8020FF);
	        fontRenderer.drawString("Seed", -8, 12, 0xEEEEEE);
	        fontRenderer.drawString("UID",  -16,32, 0xEEEEEE);
	        fontRenderer.drawString("DimID",-16,52, 0xEEEEEE);
	        fontRenderer.drawString("X", -16, 72, 0xEEEEEE);
	        fontRenderer.drawString("Y", -16, 92, 0xEEEEEE);
	        fontRenderer.drawString("Z", -16, 112, 0xEEEEEE);
		}

	    @Override
	    public void updateScreen() {
        super.updateScreen();
        fieldSeed.updateCursorCounter();
        fieldUid.updateCursorCounter();
        fieldDim.updateCursorCounter();
        TW_X.updateCursorCounter();
        TW_Y.updateCursorCounter();
        TW_Z.updateCursorCounter();

        // Live sync: if the TileEntity was updated over the network, refresh this open
        // GUI so changes are visible without reopening it.
        if (world != null && teTDArk != null && teTDArk.consumeGuiDirtyFlag()) {
            syncFieldsFromTE();
        }
    }

    /** Обновляет поля GUI из актуального состояния TileEntity. */
    private void syncFieldsFromTE() {
        boolean anyFocused = fieldSeed.isFocused() || fieldUid.isFocused() || fieldDim.isFocused()
                || TW_X.isFocused() || TW_Y.isFocused() || TW_Z.isFocused();
        if (!anyFocused) {
            fieldSeed.setText(String.valueOf(teTDArk.getTargetSeed()));
            fieldUid.setText(teTDArk.getTargetUid());
            fieldDim.setText(String.valueOf(teTDArk.getTargetDim()));
            TW_X.setText(String.valueOf(teTDArk.getTargetX()));
            TW_Y.setText(String.valueOf(teTDArk.getTargetY()));
            TW_Z.setText(String.valueOf(teTDArk.getTargetZ()));
        }
        clearMode = teTDArk.getClearMode();
        captureEntities = teTDArk.getCaptureEntities();
        captureItems = teTDArk.getCaptureItems();
        if (btnClearMode != null) btnClearMode.displayString = getClearModeText();
        if (btnCaptureEntities != null) btnCaptureEntities.displayString = getCaptureEntitiesText();
        if (btnCaptureItems != null) btnCaptureItems.displayString = getCaptureItemsText();
    }
	    @Override
	    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
	        super.mouseClicked(mouseX, mouseY, mouseButton);
		    fieldSeed.mouseClicked(mouseX, mouseY, mouseButton);
		    fieldUid.mouseClicked(mouseX, mouseY, mouseButton);
		    fieldDim.mouseClicked(mouseX, mouseY, mouseButton);
		    TW_X.mouseClicked(mouseX, mouseY, mouseButton);
		    TW_Y.mouseClicked(mouseX, mouseY, mouseButton);
		    TW_Z.mouseClicked(mouseX, mouseY, mouseButton);
	    }
	    @Override
	    protected void keyTyped(char typedChar, int keyCode) throws IOException {
	        if (fieldSeed.textboxKeyTyped(typedChar, keyCode)) return;
	        if (fieldUid.textboxKeyTyped(typedChar, keyCode)) return;
	        if (fieldDim.textboxKeyTyped(typedChar, keyCode)) return;
	        if (TW_X.textboxKeyTyped(typedChar, keyCode)) return;
	        if (TW_Y.textboxKeyTyped(typedChar, keyCode)) return;
	        if (TW_Z.textboxKeyTyped(typedChar, keyCode)) return;
	        super.keyTyped(typedChar, keyCode);
	    }

	    @Override
	    public void initGui() {
	        super.initGui();
	        this.guiLeft = (this.width - this.xSize) / 2;
	        this.guiTop = (this.height - this.ySize) / 2;

			int left = this.guiLeft;
			int top = this.guiTop;

	        Keyboard.enableRepeatEvents(true);
	        buttonList.clear();

	        TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
	        if (te instanceof BlockTDArk.TileEntityCustom) {
	            teTDArk = (BlockTDArk.TileEntityCustom) te;
	        }
	        clearMode = teTDArk.getClearMode();
			captureEntities = teTDArk.getCaptureEntities();
			captureItems = teTDArk.getCaptureItems();


		    fieldSeed = new GuiTextField(0, fontRenderer, left - 80, top + 12, 64, 12);
		    fieldSeed.setMaxStringLength(19);
		    fieldSeed.setText(teTDArk != null ? String.valueOf(teTDArk.getTargetSeed()) : "");

		    fieldUid = new GuiTextField(1, fontRenderer, left - 80, top + 32, 48, 12);
		    fieldUid.setMaxStringLength(8);
		    fieldUid.setText(teTDArk != null ? teTDArk.getTargetUid() : "");

		    fieldDim = new GuiTextField(2, fontRenderer, left - 80, top + 52, 48, 12);
		    fieldDim.setMaxStringLength(9);
		    fieldDim.setText(teTDArk != null ? String.valueOf(teTDArk.getTargetDim()) : "0");

		    TW_X = new GuiTextField(3, fontRenderer, left - 80, top + 72, 48, 12);
		    TW_X.setMaxStringLength(8);
		    TW_X.setText(teTDArk != null ? String.valueOf(teTDArk.getTargetX()) : "0");

		    TW_Y = new GuiTextField(4, fontRenderer, left - 80, top + 92, 48, 12);
		    TW_Y.setMaxStringLength(8);
		    TW_Y.setText(teTDArk != null ? String.valueOf(teTDArk.getTargetY()) : "65");

		    TW_Z = new GuiTextField(5, fontRenderer, left - 80, top + 112, 48, 12);
		    TW_Z.setMaxStringLength(8);
		    TW_Z.setText(teTDArk != null ? String.valueOf(teTDArk.getTargetZ()) : "0");

		    btnClearMode = new GuiButton(1, left + 145, top + 6, 56, 18, getClearModeText());
		    buttonList.add(btnClearMode);
		    btnCaptureEntities = new GuiButton(2, left + 145, top + 30, 56, 18, getCaptureEntitiesText());
		    buttonList.add(btnCaptureEntities);
		    btnCaptureItems = new GuiButton(3, left + 145, top + 54, 56, 18, getCaptureItemsText());
		    buttonList.add(btnCaptureItems);

		    buttonList.add(new GuiButton(0, left - 16, top + 152, 50, 18, I18n.format("tdark.send")));

	    }

	    private String getClearModeText() { return clearMode ? "Destroy" : "Replace"; }
	    private String getCaptureEntitiesText() { return captureEntities ? "Entities: ON" : "Entities: OFF"; }
	    private String getCaptureItemsText() { return captureItems ? "Items: ON" : "Items: OFF"; }
	    @Override
	    protected void actionPerformed(GuiButton button) throws IOException {
	        if (button.id == 0) { // SEND
	            mc.player.closeScreen();
	            AstrotweaksMod.PACKET_HANDLER.sendToServer(new TDArkActionMessage(
	                0, x, y, z,
	                fieldSeed.getText(),
	                fieldUid.getText(),
	                fieldDim.getText(),
	                TW_X.getText(),
	                TW_Y.getText(),
	                TW_Z.getText(),
	                clearMode,
	                captureEntities,
	                captureItems
	            ));
	        } else if (button.id == 1) { // Clear Mode toggle
	            clearMode = !clearMode;
	            btnClearMode.displayString = getClearModeText();
	        } else if (button.id == 2) { // Entities toggle
	            captureEntities = !captureEntities;
	            if (!captureEntities) {
	                captureItems = false;
	                btnCaptureItems.displayString = getCaptureItemsText();
	            }
	            btnCaptureEntities.displayString = getCaptureEntitiesText();
	        } else if (button.id == 3) { // Items toggle
	            if (captureEntities) {
	                captureItems = !captureItems;
	                btnCaptureItems.displayString = getCaptureItemsText();
	            }
	        }
	    }
	    @Override
	    public void onGuiClosed() {
	        super.onGuiClosed();
	        AstrotweaksMod.PACKET_HANDLER.sendToServer(new TDArkActionMessage(
	            1, x, y, z,
	            fieldSeed.getText(),
	            fieldUid.getText(),
	            fieldDim.getText(),
	            TW_X.getText(),
	            TW_Y.getText(),
	            TW_Z.getText(),
	            clearMode,
	            captureEntities,
	            captureItems
	        ));
	        Keyboard.enableRepeatEvents(false);
	    }
	    @Override public boolean doesGuiPauseGame() { return false; }
	}

	public static class GUIButtonPressedMessageHandler implements IMessageHandler<GUIButtonPressedMessage, IMessage> {
		@Override
		public IMessage onMessage(GUIButtonPressedMessage message, MessageContext context) {
			EntityPlayerMP entity = context.getServerHandler().player;
			entity.getServerWorld().addScheduledTask(() -> handleButtonAction(entity, message.buttonID, message.x, message.y, message.z));
			return null;
		}
	}
	public static class GUIButtonPressedMessage implements IMessage {
		int buttonID, x, y, z;
		public GUIButtonPressedMessage() {}
		public GUIButtonPressedMessage(int buttonID, int x, int y, int z) {
			this.buttonID = buttonID;
			this.x = x; this.y = y; this.z = z;
		}
		@Override public void toBytes(io.netty.buffer.ByteBuf buf) {
			buf.writeInt(buttonID);
			buf.writeInt(x); buf.writeInt(y); buf.writeInt(z);
		}
		@Override public void fromBytes(io.netty.buffer.ByteBuf buf) {
			buttonID = buf.readInt();
			x = buf.readInt(); y = buf.readInt(); z = buf.readInt();
		}
	}
	private static void handleButtonAction(EntityPlayer entity, int buttonID, int x, int y, int z) {
		World world = entity.world;
		if (!world.isBlockLoaded(new BlockPos(x, y, z))) return;
	}
}
