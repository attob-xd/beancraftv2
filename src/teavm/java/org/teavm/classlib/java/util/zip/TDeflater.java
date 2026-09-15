package org.teavm.classlib.java.util.zip;

import com.jcraft.jzlib.Deflater;
import com.jcraft.jzlib.GZIPException;
import java.util.Arrays;

/**
 * TeaVM's class, with one zlib status code reclassified: Z_BUF_ERROR (-5) is a result, not a
 * failure.
 *
 * <p>Upstream accepts only Z_OK and Z_STREAM_END and turns everything else into
 * {@code RuntimeException("Error: " + err)}. zlib returns Z_BUF_ERROR when a call could make no
 * progress - no input consumed and no output produced - which is an ordinary thing to happen
 * when a stream is flushed with nothing buffered, or flushed twice. The real JDK does not throw
 * for it: {@code Deflater.deflate} simply returns 0, and callers treat 0 as "nothing to write".
 * Returning the byte count, which is zero in that case, is exactly that behaviour.
 *
 * <p>Left as a throw, it killed the integrated server every time a world was saved:
 * {@code NbtIo.writeCompressed} writes level.dat through a GZIPOutputStream, closing it flushes,
 * and the flush landed here.
 *
 * <pre>    [IntegratedServer/ERROR] Server process encountered a fatal error!
 *    java.lang.RuntimeException: Error: -5
 *        at java.util.zip.Deflater.deflate()
 *        at java.util.zip.GZIPOutputStream.flush()
 *        at net.minecraft.nbt.NbtIo.writeCompressed()</pre>
 *
 * <p>The world had loaded; saving it took the server down. TGZIPOutputStream in this package
 * also avoids the pointless second deflate, but this is the fix that matters: any caller of
 * Deflater could hit the same status, and none of them expect an exception.
 *
 * <p>Everything else is upstream's, unchanged.
 */
public class TDeflater {
   public static final int BEST_COMPRESSION = 9;
   public static final int BEST_SPEED = 1;
   public static final int DEFAULT_COMPRESSION = -1;
   public static final int DEFAULT_STRATEGY = 0;
   public static final int DEFLATED = 8;
   public static final int FILTERED = 1;
   public static final int HUFFMAN_ONLY = 2;
   public static final int NO_COMPRESSION = 0;
   static final int Z_NO_FLUSH = 0;
   static final int Z_SYNC_FLUSH = 2;
   static final int Z_FINISH = 4;
   private int flushParm = 0;
   private boolean finished;
   private int compressLevel = -1;
   private int strategy = 0;
   private Deflater impl;
   private int inRead;
   private int inLength;
   private boolean nowrap;

   public TDeflater() {
      this(-1, false);
   }

   public TDeflater(int level) {
      this(level, false);
   }

   public TDeflater(int level, boolean noHeader) {
      if (level >= -1 && level <= 9) {
         this.compressLevel = level;

         try {
            this.impl = new Deflater(this.compressLevel, noHeader);
         } catch (GZIPException var4) {
         }

         this.nowrap = noHeader;
      } else {
         throw new IllegalArgumentException();
      }
   }

   public int deflate(byte[] buf) {
      return this.deflate(buf, 0, buf.length);
   }

   public int deflate(byte[] buf, int off, int nbytes) {
      return this.deflate(buf, off, nbytes, this.flushParm);
   }

   int deflate(byte[] buf, int off, int nbytes, int flushParam) {
      if (this.impl == null) {
         throw new IllegalStateException();
      } else if (off <= buf.length && nbytes >= 0 && off >= 0 && buf.length - off >= nbytes) {
         long sin = this.impl.total_in;
         long sout = this.impl.total_out;
         this.impl.setOutput(buf, off, nbytes);
         int err = this.impl.deflate(flushParam);
         switch (err) {
            case 1:
               this.finished = true;
            case 0:
            case -5:
               this.inRead = (int)(this.inRead + (this.impl.total_in - sin));
               return (int)(this.impl.total_out - sout);
            default:
               throw new RuntimeException("Error: " + err);
         }
      } else {
         throw new ArrayIndexOutOfBoundsException();
      }
   }

   public void end() {
      this.impl = null;
   }

   @Override
   protected void finalize() {
      this.end();
   }

   public void finish() {
      this.flushParm = 4;
   }

   public boolean finished() {
      return this.finished;
   }

   public int getAdler() {
      if (this.impl == null) {
         throw new IllegalStateException();
      } else {
         return (int)this.impl.getAdler();
      }
   }

   public int getTotalIn() {
      if (this.impl == null) {
         throw new IllegalStateException();
      } else {
         return (int)this.impl.getTotalIn();
      }
   }

   public int getTotalOut() {
      if (this.impl == null) {
         throw new IllegalStateException();
      } else {
         return (int)this.impl.getTotalOut();
      }
   }

   public boolean needsInput() {
      return this.inRead == this.inLength;
   }

   public void reset() {
      if (this.impl == null) {
         throw new NullPointerException();
      } else {
         this.flushParm = 0;
         this.finished = false;
         this.impl.init(this.compressLevel, 15, this.nowrap);
         this.impl.params(this.compressLevel, this.strategy);
      }
   }

   public void setDictionary(byte[] buf) {
      this.setDictionary(buf, 0, buf.length);
   }

   public void setDictionary(byte[] buf, int off, int nbytes) {
      if (this.impl == null) {
         throw new IllegalStateException();
      } else if (off <= buf.length && nbytes >= 0 && off >= 0 && buf.length - off >= nbytes) {
         this.impl.setDictionary(Arrays.copyOfRange(buf, off, buf.length), nbytes);
      } else {
         throw new ArrayIndexOutOfBoundsException();
      }
   }

   public void setInput(byte[] buf) {
      this.setInput(buf, 0, buf.length);
   }

   public void setInput(byte[] buf, int off, int nbytes) {
      if (this.impl == null) {
         throw new IllegalStateException();
      } else if (off <= buf.length && nbytes >= 0 && off >= 0 && buf.length - off >= nbytes) {
         this.inLength = nbytes;
         this.inRead = 0;
         if (this.impl.next_in == null) {
            this.impl.init(this.compressLevel, 15, this.nowrap);
         }

         this.impl.setInput(buf, off, nbytes, false);
      } else {
         throw new ArrayIndexOutOfBoundsException();
      }
   }

   public void setLevel(int level) {
      if (level >= -1 && level <= 9) {
         this.compressLevel = level;
      } else {
         throw new IllegalArgumentException();
      }
   }

   public void setStrategy(int strategy) {
      if (strategy >= 0 && strategy <= 2) {
         this.strategy = strategy;
      } else {
         throw new IllegalArgumentException();
      }
   }

   public long getBytesRead() {
      if (this.impl == null) {
         throw new NullPointerException();
      } else {
         return this.impl.getTotalIn();
      }
   }

   public long getBytesWritten() {
      if (this.impl == null) {
         throw new NullPointerException();
      } else {
         return this.impl.getTotalOut();
      }
   }
}
