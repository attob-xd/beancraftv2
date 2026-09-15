/*
 * TeaVM's java.util.Formatter, with fixed-point float conversion added.
 *
 * Everything here is TeaVM's own code except FormatWriter.formatFloat and the two switch cases
 * that reach it; see that method for why it had to exist. The class is reproduced in full
 * rather than extended because TFormatter is final, FormatWriter is package private, and the
 * conversion dispatch is a switch inside it - there is no seam to hook.
 *
 * tools/trim_teavm_jso.sh drops the jar's TFormatter and its FormatWriter because this file
 * exists, so this copy is the only one in the build.
 */
package org.teavm.classlib.java.util;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.DuplicateFormatFlagsException;
import java.util.FormatFlagsConversionMismatchException;
import java.util.IllegalFormatConversionException;
import java.util.Locale;
import java.util.UnknownFormatConversionException;
import org.teavm.classlib.impl.IntegerUtil;

public final class TFormatter implements Closeable, Flushable {
   private Locale locale;
   private Appendable out;
   private IOException ioException;

   public TFormatter() {
      this(Locale.getDefault());
   }

   public TFormatter(Appendable a) {
      this(a, Locale.getDefault());
   }

   public TFormatter(Locale l) {
      this(new StringBuilder(), l);
   }

   public TFormatter(Appendable a, Locale l) {
      this.out = a;
      this.locale = l;
   }

   public TFormatter(PrintStream ps) {
      this(new OutputStreamWriter(ps));
   }

   public TFormatter(OutputStream os) {
      this(new OutputStreamWriter(os));
   }

   public TFormatter(OutputStream os, String csn) throws UnsupportedEncodingException {
      this(new OutputStreamWriter(os, csn));
   }

   public TFormatter(OutputStream os, String csn, Locale l) throws UnsupportedEncodingException {
      this(new OutputStreamWriter(os, csn), l);
   }

   public Locale locale() {
      this.requireOpen();
      return this.locale;
   }

   public Appendable out() {
      this.requireOpen();
      return this.out;
   }

   private void requireOpen() {
      if (this.out == null) {
         throw new TFormatterClosedException();
      }
   }

   @Override
   public String toString() {
      this.requireOpen();
      return this.out.toString();
   }

   @Override
   public void flush() {
      this.requireOpen();
      if (this.out instanceof Flushable) {
         try {
            ((Flushable)this.out).flush();
         } catch (IOException var2) {
            this.ioException = var2;
         }
      }
   }

   @Override
   public void close() {
      this.requireOpen();

      try {
         if (this.out instanceof Closeable) {
            ((Closeable)this.out).close();
         }
      } catch (IOException var5) {
         this.ioException = var5;
      } finally {
         this.out = null;
      }
   }

   public IOException ioException() {
      return this.ioException;
   }

   public org.teavm.classlib.java.util.TFormatter format(String format, Object... args) {
      return this.format(this.locale, format, args);
   }

   public org.teavm.classlib.java.util.TFormatter format(Locale l, String format, Object... args) {
      this.requireOpen();

      try {
         if (args == null) {
            args = new Object[1];
         }

         new org.teavm.classlib.java.util.TFormatter.FormatWriter(this, this.out, l, format, args).write();
      } catch (IOException var5) {
         this.ioException = var5;
      }

      return this;
   }

   static class FormatWriter {
      private static final String FORMAT_FLAGS = "--#+ 0,(<";
      private static final int MASK_FOR_GENERAL_FORMAT = 263;
      private static final int MASK_FOR_CHAR_FORMAT = 259;
      private static final int MASK_FOR_INT_DECIMAL_FORMAT = 507;
      private static final int MASK_FOR_INT_RADIX_FORMAT = 423;
      private org.teavm.classlib.java.util.TFormatter formatter;
      Appendable out;
      Locale locale;
      String format;
      Object[] args;
      int index;
      int formatSpecifierStart;
      int defaultArgumentIndex;
      int argumentIndex;
      int previousArgumentIndex;
      int width;
      int precision;
      int flags;

      FormatWriter(org.teavm.classlib.java.util.TFormatter formatter, Appendable out, Locale locale, String format, Object[] args) {
         this.formatter = formatter;
         this.out = out;
         this.locale = locale;
         this.format = format;
         this.args = args;
      }

      void write() throws IOException {
         while (true) {
            int next = this.format.indexOf(37, this.index);
            if (next < 0) {
               this.out.append(this.format.substring(this.index));
               return;
            }

            this.out.append(this.format.substring(this.index, next));
            this.index = next + 1;
            this.formatSpecifierStart = this.index;
            char specifier = this.parseFormatSpecifier();
            // '%%' and '%n' are conversions the JDK handles but TeaVM's formatValue() does not:
            // they fall through its switch to UnknownFormatConversionException. Neither consumes an
            // argument, so both are handled here, before configureFormat() advances the arg index.
            // DebugScreenOverlay (F3) formats "(Mood %d%%)" and "Mem: % 2d%% ...", so without this
            // the debug overlay crashes the client the moment it is opened.
            if (specifier == '%') {
               this.out.append('%');
            } else if (specifier == 'n') {
               this.out.append((char) 10);
            } else {
               this.configureFormat();
               this.formatValue(specifier);
            }
         }
      }

      private void formatValue(char specifier) throws IOException {
         switch (specifier) {
            case 'B':
               this.formatBoolean(specifier, true);
               break;
            case 'C':
               this.formatChar(specifier, true);
               break;
            case 'D':
               this.formatDecimalInt(specifier, true);
               break;
            case 'E':
            case 'F':
            case 'G':
            case 'I':
            case 'J':
            case 'K':
            case 'L':
            case 'M':
            case 'N':
            case 'P':
            case 'Q':
            case 'R':
            case 'T':
            case 'U':
            case 'V':
            case 'W':
            case 'Y':
            case 'Z':
            case '[':
            case '\\':
            case ']':
            case '^':
            case '_':
            case '`':
            case 'a':
            case 'e':
            case 'g':
            case 'i':
            case 'j':
            case 'k':
            case 'l':
            case 'm':
            case 'n':
            case 'p':
            case 'q':
            case 'r':
            case 't':
            case 'u':
            case 'v':
            case 'w':
            default:
               throw new UnknownFormatConversionException(String.valueOf(specifier));
            case 'f':
               this.formatFloat(specifier);
               break;
            case 'H':
               this.formatHex(specifier, true);
               break;
            case 'O':
               this.formatRadixInt(specifier, 3, true);
               break;
            case 'S':
               this.formatString(specifier, true);
               break;
            case 'X':
               this.formatRadixInt(specifier, 4, true);
               break;
            case 'b':
               this.formatBoolean(specifier, false);
               break;
            case 'c':
               this.formatChar(specifier, false);
               break;
            case 'd':
               this.formatDecimalInt(specifier, false);
               break;
            case 'h':
               this.formatHex(specifier, false);
               break;
            case 'o':
               this.formatRadixInt(specifier, 3, false);
               break;
            case 's':
               this.formatString(specifier, false);
               break;
            case 'x':
               this.formatRadixInt(specifier, 4, false);
         }
      }

      private void formatBoolean(char specifier, boolean upperCase) throws IOException {
         this.verifyFlagsForGeneralFormat(specifier);
         Object arg = this.args[this.argumentIndex];
         String s = Boolean.toString(arg instanceof Boolean ? (Boolean)arg : arg != null);
         this.formatGivenString(upperCase, s);
      }

      private void formatHex(char specifier, boolean upperCase) throws IOException {
         this.verifyFlagsForGeneralFormat(specifier);
         Object arg = this.args[this.argumentIndex];
         String s = arg != null ? Integer.toHexString(arg.hashCode()) : "null";
         this.formatGivenString(upperCase, s);
      }

      private void formatString(char specifier, boolean upperCase) throws IOException {
         this.verifyFlagsForGeneralFormat(specifier);
         Object arg = this.args[this.argumentIndex];
         if (arg instanceof TFormattable) {
            int flagsToPass = this.flags & 7;
            if (upperCase) {
               flagsToPass |= 2;
            }

            ((TFormattable)arg).formatTo(this.formatter, flagsToPass, this.width, this.precision);
         } else {
            this.formatGivenString(upperCase, String.valueOf(arg));
         }
      }

      private void formatChar(char specifier, boolean upperCase) throws IOException {
         this.verifyFlags(specifier, 259);
         Object arg = this.args[this.argumentIndex];
         if (this.precision >= 0) {
            throw new TIllegalFormatPrecisionException(this.precision);
         } else {
            int c;
            if (arg instanceof Character) {
               c = (Character)arg;
            } else if (arg instanceof Byte) {
               c = (char)((Byte)arg).byteValue();
            } else if (arg instanceof Short) {
               c = (char)((Short)arg).shortValue();
            } else {
               if (!(arg instanceof Integer)) {
                  if (arg == null) {
                     this.formatGivenString(upperCase, "null");
                     return;
                  }

                  throw new IllegalFormatConversionException(specifier, arg.getClass());
               }

               c = (Integer)arg;
               if (!Character.isValidCodePoint(c)) {
                  throw new TIllegalFormatCodePointException(c);
               }
            }

            this.formatGivenString(upperCase, new String(Character.toChars(c)));
         }
      }

      private void formatDecimalInt(char specifier, boolean upperCase) throws IOException {
         this.verifyFlags(specifier, 507);
         this.verifyIntFlags();
         Object arg = this.args[this.argumentIndex];
         String str;
         boolean negative;
         if (arg instanceof Long) {
            long value = (Long)arg;
            str = Long.toString(Math.abs(value));
            negative = value < 0L;
         } else {
            if (!(arg instanceof Integer) && !(arg instanceof Byte) && !(arg instanceof Short)) {
               throw new IllegalFormatConversionException(specifier, arg != null ? arg.getClass() : null);
            }

            int value = ((Number)arg).intValue();
            str = Integer.toString(Math.abs(value));
            negative = value < 0;
         }

         int additionalSymbols = 0;
         StringBuilder sb = new StringBuilder();
         if (negative) {
            if ((this.flags & 128) != 0) {
               sb.append('(');
               additionalSymbols += 2;
            } else {
               sb.append('-');
               additionalSymbols++;
            }
         } else if ((this.flags & 8) != 0) {
            sb.append('+');
            additionalSymbols++;
         } else if ((this.flags & 16) != 0) {
            sb.append(' ');
            additionalSymbols++;
         }

         StringBuilder valueSb = new StringBuilder();
         if ((this.flags & 64) != 0) {
            char separator = new DecimalFormatSymbols(this.locale).getGroupingSeparator();
            int size = ((DecimalFormat)NumberFormat.getNumberInstance(this.locale)).getGroupingSize();
            int offset = str.length() % size;
            if (offset == 0) {
               offset = size;
            }

            int prev = 0;

            for (int i = offset; i < str.length(); i += size) {
               valueSb.append(str.substring(prev, i));
               valueSb.append(separator);
               prev = i;
            }

            valueSb.append(str.substring(prev));
         } else {
            valueSb.append(str);
         }

         if ((this.flags & 32) != 0) {
            int actual = valueSb.length() + additionalSymbols;

            for (int i = actual; i < this.width; i++) {
               sb.append(Character.forDigit(0, 10));
            }
         }

         sb.append((CharSequence)valueSb);
         if (negative && (this.flags & 128) != 0) {
            sb.append(')');
         }

         this.formatGivenString(upperCase, sb.toString());
      }

      /**
       * Fixed-point float conversion - {@code %f} - which TeaVM's formatter does not implement
       * at all.
       *
       * <p>There is deliberately no {@code %F}. Every other conversion in this class has an
       * upper-case twin, so adding one looked like consistency, and {@code tools/VerifyFormatFloat.java}
       * immediately reported 52 mismatches where this produced a number and the JDK threw:
       * {@code java.util.Formatter} has no {@code F} conversion at all. TeaVM was right to
       * leave {@code F} in the unsupported run, and it stays there.
       *
       * <p>Its {@code FormatWriter} has methods for boolean, char, string, hex, decimal int and
       * radix int, and nothing for a floating point value, so every {@code %f} raised
       * {@code UnknownFormatConversionException: Unknown format conversion: f}. That is not a
       * rare spelling in Minecraft: {@code Option.genericValueLabel} formats every settings
       * slider with {@code %.1f} or {@code %.2f}, the F3 overlay uses it for coordinates and
       * tick times, and {@code GameRenderer} uses it for the "Mouse location" and "Screen size"
       * lines of a crash report - which is where this was noticed, as two ~~ERROR~~ entries
       * inside a report about something else entirely.
       *
       * <p>The digits come from {@code DecimalFormat}, which TeaVM does implement, rather than
       * from hand-rolled scaling: it already knows the locale's decimal separator and grouping
       * separator, so the {@code ','} flag costs nothing here and a non-English locale is not
       * quietly wrong. Everything around the digits - the sign, {@code (} for negatives,
       * {@code 0} padding inside the field, {@code -} for left justification - follows
       * {@code formatDecimalInt} above flag for flag, so the two conversions agree wherever
       * they overlap.
       *
       * <p>Two deliberate departures from that method. Precision is not rejected, since it is
       * the point of this conversion, so {@code verifyIntFlags} is not reused and its other
       * checks are written out without the precision one. And the result does not go through
       * {@code formatGivenString}, because that truncates the string to {@code precision} -
       * right for {@code %s}, but for {@code %.2f} it would cut 3.14 down to "3.".
       *
       * <p>{@code %e}, {@code %g} and {@code %a} remain unimplemented, as they were before.
       * Nothing in vanilla 1.18.2 or in this project uses them - a grep for those conversions
       * over the decompiled reference finds none - so implementing them would be shipping
       * untested code. They still throw the same exception, which names the conversion.
       */
      private void formatFloat(char specifier) throws IOException {
         this.verifyFlags(specifier, 511);
         if ((this.flags & 8) != 0 && (this.flags & 16) != 0) {
            throw new TIllegalFormatFlagsException("+ ");
         }
         if ((this.flags & 32) != 0 && (this.flags & 1) != 0) {
            throw new TIllegalFormatFlagsException("0-");
         }
         if ((this.flags & 1) != 0 && this.width < 0) {
            throw new TMissingFormatWidthException(this.format.substring(this.formatSpecifierStart, this.index));
         }

         Object arg = this.args[this.argumentIndex];
         if (!(arg instanceof Double) && !(arg instanceof Float)) {
            throw new IllegalFormatConversionException(specifier, arg != null ? arg.getClass() : null);
         }
         double value = ((Number)arg).doubleValue();
         int fractionDigits = this.precision >= 0 ? this.precision : 6;

         String digits;
         boolean negative;
         boolean finite;
         if (Double.isNaN(value)) {
            digits = "NaN";
            negative = false;
            finite = false;
         } else if (Double.isInfinite(value)) {
            digits = "Infinity";
            negative = value < 0.0;
            finite = false;
         } else {
            // Dividing into a zero is the only way to see the sign of a negative zero, and
            // java.util.Formatter does print that one as -0.000000.
            negative = value < 0.0 || (value == 0.0 && 1.0 / value < 0.0);
            DecimalFormat format = (DecimalFormat)NumberFormat.getNumberInstance(this.locale);
            format.setMinimumFractionDigits(fractionDigits);
            format.setMaximumFractionDigits(fractionDigits);
            format.setGroupingUsed((this.flags & 64) != 0);
            format.setRoundingMode(java.math.RoundingMode.HALF_UP);
            digits = format.format(Math.abs(value));
            finite = true;
         }

         int additionalSymbols = 0;
         StringBuilder sb = new StringBuilder();
         if (negative) {
            if ((this.flags & 128) != 0) {
               sb.append('(');
               additionalSymbols += 2;
            } else {
               sb.append('-');
               additionalSymbols++;
            }
         } else if ((this.flags & 8) != 0) {
            sb.append('+');
            additionalSymbols++;
         } else if ((this.flags & 16) != 0) {
            sb.append(' ');
            additionalSymbols++;
         }

         // NaN and Infinity are never zero padded, matching java.util.Formatter.
         if (finite && (this.flags & 32) != 0) {
            int actual = digits.length() + additionalSymbols;

            for (int i = actual; i < this.width; i++) {
               sb.append(Character.forDigit(0, 10));
            }
         }

         sb.append(digits);
         if (negative && (this.flags & 128) != 0) {
            sb.append(')');
         }

         String str = sb.toString();
         if ((this.flags & 1) != 0) {
            this.out.append(str);
            this.mayBeAppendSpaces(str);
         } else {
            this.mayBeAppendSpaces(str);
            this.out.append(str);
         }
      }

      private void formatRadixInt(char specifier, int radixLog2, boolean upperCase) throws IOException {
         this.verifyFlags(specifier, 423);
         this.verifyIntFlags();
         Object arg = this.args[this.argumentIndex];
         String str;
         if (arg instanceof Long) {
            str = IntegerUtil.toUnsignedLogRadixString((Long)arg, radixLog2);
         } else if (arg instanceof Integer) {
            str = IntegerUtil.toUnsignedLogRadixString((Integer)arg, radixLog2);
         } else if (arg instanceof Short) {
            str = IntegerUtil.toUnsignedLogRadixString((Short)arg & '\uffff', radixLog2);
         } else {
            if (!(arg instanceof Byte)) {
               throw new IllegalFormatConversionException(specifier, arg != null ? arg.getClass() : null);
            }

            str = IntegerUtil.toUnsignedLogRadixString((Byte)arg & 255, radixLog2);
         }

         StringBuilder sb = new StringBuilder();
         if ((this.flags & 4) != 0) {
            String prefix = radixLog2 == 4 ? "0x" : "0";
            str = prefix + str;
         }

         if ((this.flags & 32) != 0) {
            for (int i = str.length(); i < this.width; i++) {
               sb.append(Character.forDigit(0, 10));
            }
         }

         sb.append(str);
         this.formatGivenString(upperCase, sb.toString());
      }

      private void verifyIntFlags() {
         if ((this.flags & 8) != 0 && (this.flags & 16) != 0) {
            throw new TIllegalFormatFlagsException("+ ");
         } else if ((this.flags & 32) != 0 && (this.flags & 1) != 0) {
            throw new TIllegalFormatFlagsException("0-");
         } else if (this.precision >= 0) {
            throw new TIllegalFormatPrecisionException(this.precision);
         } else if ((this.flags & 1) != 0 && this.width < 0) {
            throw new TMissingFormatWidthException(this.format.substring(this.formatSpecifierStart, this.index));
         }
      }

      private void formatGivenString(boolean upperCase, String str) throws IOException {
         if (this.precision > 0) {
            str = str.substring(0, this.precision);
         }

         if (upperCase) {
            str = str.toUpperCase();
         }

         if ((this.flags & 1) != 0) {
            this.out.append(str);
            this.mayBeAppendSpaces(str);
         } else {
            this.mayBeAppendSpaces(str);
            this.out.append(str);
         }
      }

      private void verifyFlagsForGeneralFormat(char conversion) {
         this.verifyFlags(conversion, 263);
      }

      private void verifyFlags(char conversion, int mask) {
         if ((this.flags | mask) != mask) {
            throw new FormatFlagsConversionMismatchException(this.flagsToString(this.flags & ~mask), conversion);
         }
      }

      private String flagsToString(int flags) {
         int flagIndex = Integer.numberOfTrailingZeros(flags);
         return String.valueOf("--#+ 0,(<".charAt(flagIndex));
      }

      private void mayBeAppendSpaces(String str) throws IOException {
         if (this.width > str.length()) {
            int diff = this.width - str.length();
            StringBuilder sb = new StringBuilder(diff);

            for (int i = 0; i < diff; i++) {
               sb.append(' ');
            }

            this.out.append(sb);
         }
      }

      private void configureFormat() {
         if ((this.flags & 256) != 0) {
            this.argumentIndex = Math.max(0, this.previousArgumentIndex);
         }

         if (this.argumentIndex == -1) {
            this.argumentIndex = this.defaultArgumentIndex++;
         }

         this.previousArgumentIndex = this.argumentIndex;
      }

      private char parseFormatSpecifier() {
         this.flags = 0;
         this.argumentIndex = -1;
         this.width = -1;
         this.precision = -1;
         char c = this.format.charAt(this.index);
         if (c != '0' && isDigit(c)) {
            int n = this.readInt();
            if (this.index < this.format.length() && this.format.charAt(this.index) == '$') {
               this.index++;
               this.argumentIndex = n - 1;
            } else {
               this.width = n;
            }
         }

         this.parseFlags();
         if (this.width < 0 && this.index < this.format.length() && isDigit(this.format.charAt(this.index))) {
            this.width = this.readInt();
         }

         if (this.index < this.format.length() && this.format.charAt(this.index) == '.') {
            this.index++;
            if (this.index >= this.format.length() || !isDigit(this.format.charAt(this.index))) {
               throw new UnknownFormatConversionException(String.valueOf(this.format.charAt(this.index - 1)));
            }

            this.precision = this.readInt();
         }

         if (this.index >= this.format.length()) {
            throw new UnknownFormatConversionException(String.valueOf(this.format.charAt(this.format.length() - 1)));
         } else {
            return this.format.charAt(this.index++);
         }
      }

      private void parseFlags() {
         while (this.index < this.format.length()) {
            char c = this.format.charAt(this.index);
            int flag;
            switch (c) {
               case ' ':
                  flag = 16;
                  break;
               case '!':
               case '"':
               case '$':
               case '%':
               case '&':
               case '\'':
               case ')':
               case '*':
               case '.':
               case '/':
               case '1':
               case '2':
               case '3':
               case '4':
               case '5':
               case '6':
               case '7':
               case '8':
               case '9':
               case ':':
               case ';':
               default:
                  return;
               case '#':
                  flag = 4;
                  break;
               case '(':
                  flag = 128;
                  break;
               case '+':
                  flag = 8;
                  break;
               case ',':
                  flag = 64;
                  break;
               case '-':
                  flag = 1;
                  break;
               case '0':
                  flag = 32;
                  break;
               case '<':
                  flag = 256;
            }

            if ((this.flags & flag) != 0) {
               throw new DuplicateFormatFlagsException(String.valueOf(c));
            }

            this.flags |= flag;
            this.index++;
         }
      }

      private int readInt() {
         int result = 0;

         while (this.index < this.format.length() && isDigit(this.format.charAt(this.index))) {
            result = result * 10 + (this.format.charAt(this.index++) - '0');
         }

         return result;
      }

      private static boolean isDigit(char c) {
         return c >= '0' && c <= '9';
      }
   }
}
