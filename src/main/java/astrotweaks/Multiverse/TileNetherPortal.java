package astrotweaks.Multiverse;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * Per-block portal data: every portal block of a nether-poral-link stores the same
 * "beacon" pointing at the exit portal (target dimension + exit interior center XZ +
 * interior base Y + exit axis). When a travel crosses a portal, the linked portal's
 * center is the exact teleport destination.
 */
public class TileNetherPortal extends TileEntity {
    public boolean hasLink;
    public int linkDim;
    public int linkX;
    public int linkY;
    public int linkZ;
    /** 0 = AXIS X, 1 = AXIS Z (axis of the linked exit portal). */
    public int linkAxis;

    public void setLink(int dim, int x, int y, int z, int axis) {
        this.hasLink = true;
        this.linkDim = dim;
        this.linkX = x;
        this.linkY = y;
        this.linkZ = z;
        this.linkAxis = axis;
        this.markDirty();
    }
    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.hasLink = compound.getBoolean("hasLink");
        this.linkDim = compound.getInteger("linkDim");
        this.linkX = compound.getInteger("linkX");
        this.linkY = compound.getInteger("linkY");
        this.linkZ = compound.getInteger("linkZ");
        this.linkAxis = compound.getInteger("linkAxis");
    }
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("hasLink", this.hasLink);
        compound.setInteger("linkDim", this.linkDim);
        compound.setInteger("linkX", this.linkX);
        compound.setInteger("linkY", this.linkY);
        compound.setInteger("linkZ", this.linkZ);
        compound.setInteger("linkAxis", this.linkAxis);
        return compound;
    }
}
