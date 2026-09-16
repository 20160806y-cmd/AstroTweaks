package astrotweaks.block.mirage;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;



public final class MirageRemovalQueue {
    private static final int MAX_REMOVALS_PER_TICK = 32;
    private static final Map<World, Queue<RemovalJob>> JOBS = new IdentityHashMap<World, Queue<RemovalJob>>();

    private MirageRemovalQueue() {}

    public static void enqueue(World world, BlockPos start) {
        if (world == null || world.isRemote) {
            return;
        }

        Queue<RemovalJob> jobs = JOBS.get(world);

        if (jobs == null) {
            jobs = new ArrayDeque<RemovalJob>();
            JOBS.put(world, jobs);
        }
        jobs.add(new RemovalJob(start));
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END)  return;
        World world = event.world;
        if (world.isRemote)  return;

        Queue<RemovalJob> jobs = JOBS.get(world);

        if (jobs == null || jobs.isEmpty()) {
            JOBS.remove(world);
            return;
        }
        int removed = 0;

        while (removed < MAX_REMOVALS_PER_TICK && !jobs.isEmpty()) {
            RemovalJob job = jobs.peek();

            if (job.queue.isEmpty()) {
                jobs.poll();
                continue;
            }

            BlockPos pos = job.queue.poll();
            if (!job.visited.add(pos))  continue;

            if (world.getBlockState(pos).getBlock() != astrotweaks.block.mirage.MirageBlock.block)  continue;


            for (EnumFacing side : EnumFacing.values()) {
                BlockPos neighbour = pos.offset(side);
                if (!job.visited.contains(neighbour)) {
                    job.queue.add(neighbour);
                }
            }
            world.setBlockToAir(pos);
            removed++;
        }

        if (jobs.isEmpty()) {
            JOBS.remove(world);
        }
    }

    private static final class RemovalJob {
        private final Queue<BlockPos> queue = new ArrayDeque<BlockPos>();
        private final Set<BlockPos> visited = new HashSet<BlockPos>();

        private RemovalJob(BlockPos start) {
            queue.add(start);
        }
    }
}
