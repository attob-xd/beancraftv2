package net.minecraft.util.thread;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2BooleanFunction;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.Util;
import net.minecraft.util.profiling.metrics.MetricCategory;
import net.minecraft.util.profiling.metrics.MetricSampler;
import net.minecraft.util.profiling.metrics.MetricsRegistry;
import net.minecraft.util.profiling.metrics.ProfilerMeasured;
import org.slf4j.Logger;

public class ProcessorMailbox<T> implements ProfilerMeasured, ProcessorHandle<T>, AutoCloseable, Runnable {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final int CLOSED_BIT = 1;
   private static final int SCHEDULED_BIT = 2;
   private final AtomicInteger status = new AtomicInteger(0);
   private final StrictQueue<? super T, ? extends Runnable> queue;
   private final Executor dispatcher;
   private final String name;

   public static ProcessorMailbox<Runnable> create(Executor var0, String var1) {
      return new ProcessorMailbox<>(new StrictQueue.QueueStrictQueue<>(new ConcurrentLinkedQueue<>()), var0, var1);
   }

   public ProcessorMailbox(StrictQueue<? super T, ? extends Runnable> var1, Executor var2, String var3) {
      this.dispatcher = var2;
      this.queue = var1;
      this.name = var3;
      MetricsRegistry.INSTANCE.add(this);
   }

   private boolean setAsScheduled() {
      int var1;
      do {
         var1 = this.status.get();
         if ((var1 & 3) != 0) {
            return false;
         }
      } while (!this.status.compareAndSet(var1, var1 | 2));

      return true;
   }

   private void setAsIdle() {
      int var1;
      do {
         var1 = this.status.get();
      } while (!this.status.compareAndSet(var1, var1 & -3));
   }

   private boolean canBeScheduled() {
      return (this.status.get() & 1) != 0 ? false : !this.queue.isEmpty();
   }

   @Override
   public void close() {
      int var1;
      do {
         var1 = this.status.get();
      } while (!this.status.compareAndSet(var1, var1 | 1));
   }

   private boolean shouldProcess() {
      return (this.status.get() & 2) != 0;
   }

   private boolean pollTask() {
      if (!this.shouldProcess()) {
         return false;
      } else {
         Runnable var1 = this.queue.pop();
         if (var1 == null) {
            return false;
         } else {
            Util.wrapThreadWithTaskName(this.name, var1).run();
            return true;
         }
      }
   }

   /**
    * Drains the queue in a loop instead of one task per call.
    *
    * <p>Vanilla polls exactly one task - {@code pollUntil(var0 -> var0 == 0)} stops as soon as
    * the counter reaches 1 - and then leans on {@code registerForExecution} in the finally
    * block to come back for the next one. That is correct when the dispatcher is asynchronous,
    * because "come back" means a fresh task on a pool thread.
    *
    * <p>Every executor in this build runs its work inline on the calling thread; there is one
    * thread in a browser tab, and {@code TForkJoinPool} says so plainly. So "come back" meant
    * re-entering {@code run()} from inside {@code run()}'s own finally block - one nested call
    * per queued task, with no bound. {@code ChunkRenderDispatcher.runTask} re-tells itself
    * after every chunk it finishes, so joining a world recursed once per pending chunk and the
    * page stopped responding entirely: no frames, no packet processing, and the server dropped
    * the connection on a keep-alive timeout while the tab was still busy.
    *
    * <p>Looping does the same tasks in the same order with a flat stack. The counter is only
    * there to make a runaway queue visible in the log instead of appearing as a frozen tab.
    */
   @Override
   public void run() {
      try {
         int var1 = 0;
         while (this.pollTask()) {
            if (++var1 % 20000 == 0) {
               LOGGER.warn("Mailbox {} has run {} tasks in one drain and the queue still has {}",
                  this.name, var1, this.queue.size());
            }
         }
      } finally {
         this.setAsIdle();
         this.registerForExecution();
      }
   }

   public void runAll() {
      try {
         this.pollUntil(var0 -> true);
      } finally {
         this.setAsIdle();
         this.registerForExecution();
      }
   }

   @Override
   public void tell(T var1) {
      this.queue.push((T)var1);
      this.registerForExecution();
   }

   private void registerForExecution() {
      if (this.canBeScheduled() && this.setAsScheduled()) {
         try {
            this.dispatcher.execute(this);
         } catch (RejectedExecutionException var4) {
            try {
               this.dispatcher.execute(this);
            } catch (RejectedExecutionException var3) {
               LOGGER.error("Cound not schedule mailbox", var3);
            }
         }
      }
   }

   private int pollUntil(Int2BooleanFunction var1) {
      int var2 = 0;

      while (var1.get(var2) && this.pollTask()) {
         var2++;
      }

      return var2;
   }

   public int size() {
      return this.queue.size();
   }

   public boolean hasWork() {
      return this.shouldProcess() && !this.queue.isEmpty();
   }

   @Override
   public String toString() {
      return this.name + " " + this.status.get() + " " + this.queue.isEmpty();
   }

   @Override
   public String name() {
      return this.name;
   }

   @Override
   public List<MetricSampler> profiledMetrics() {
      return ImmutableList.of(MetricSampler.create(this.name + "-queue-size", MetricCategory.MAIL_BOXES, this::size));
   }
}
