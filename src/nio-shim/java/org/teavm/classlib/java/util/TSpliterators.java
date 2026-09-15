package org.teavm.classlib.java.util;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * Replaces TeaVM's own Spliterators, which is otherwise fine but has no nested
 * AbstractSpliterator. That omission is not cosmetic: SectionPos$1 and several other
 * vanilla classes extend it, and TeaVM writes a class's supertype into the class table as a
 * plain identifier evaluated at load time - so an absent supertype is a ReferenceError
 * before the page starts, not a lazy failure.
 *
 * A class here replaces the library's outright rather than adding to it, so the twelve
 * factory methods TeaVM's version provides are reimplemented alongside the nested classes.
 * They are ordinary iterator- and array-backed spliterators; nothing here is parallel,
 * because trySplit always declines - a page has one thread, so a split could never be
 * worked on by anyone else. Stream code handles that: it is exactly what a spliterator
 * over a non-splittable source reports.
 */
public final class TSpliterators {

	private TSpliterators() {
	}

	/** The reason this file exists; the shape vanilla subclasses. */
	public abstract static class AbstractSpliterator<T> implements Spliterator<T> {

		private final int characteristics;
		private long estimate;

		protected AbstractSpliterator(long estimate, int characteristics) {
			this.estimate = estimate;
			this.characteristics = (characteristics & Spliterator.SIZED) != 0
					? characteristics | Spliterator.SUBSIZED
					: characteristics;
		}

		@Override
		public Spliterator<T> trySplit() {
			return null;
		}

		@Override
		public long estimateSize() {
			return estimate;
		}

		@Override
		public int characteristics() {
			return characteristics;
		}

		@Override
		public Comparator<? super T> getComparator() {
			if ((characteristics & Spliterator.SORTED) != 0) {
				return null;
			}
			throw new IllegalStateException();
		}
	}

	public abstract static class AbstractIntSpliterator implements Spliterator.OfInt {

		private final int characteristics;
		private final long estimate;

		protected AbstractIntSpliterator(long estimate, int characteristics) {
			this.estimate = estimate;
			this.characteristics = characteristics;
		}

		@Override
		public Spliterator.OfInt trySplit() {
			return null;
		}

		@Override
		public long estimateSize() {
			return estimate;
		}

		@Override
		public int characteristics() {
			return characteristics;
		}
	}

	public abstract static class AbstractLongSpliterator implements Spliterator.OfLong {

		private final int characteristics;
		private final long estimate;

		protected AbstractLongSpliterator(long estimate, int characteristics) {
			this.estimate = estimate;
			this.characteristics = characteristics;
		}

		@Override
		public Spliterator.OfLong trySplit() {
			return null;
		}

		@Override
		public long estimateSize() {
			return estimate;
		}

		@Override
		public int characteristics() {
			return characteristics;
		}
	}

	public abstract static class AbstractDoubleSpliterator implements Spliterator.OfDouble {

		private final int characteristics;
		private final long estimate;

		protected AbstractDoubleSpliterator(long estimate, int characteristics) {
			this.estimate = estimate;
			this.characteristics = characteristics;
		}

		@Override
		public Spliterator.OfDouble trySplit() {
			return null;
		}

		@Override
		public long estimateSize() {
			return estimate;
		}

		@Override
		public int characteristics() {
			return characteristics;
		}
	}

	// ------------------------------------------------------------------ factories

	public static <T> Spliterator<T> emptySpliterator() {
		return spliterator(java.util.Collections.<T>emptyList().iterator(), 0,
				Spliterator.SIZED | Spliterator.SUBSIZED);
	}

	public static <T> Spliterator<T> spliterator(Object[] array, int characteristics) {
		return spliterator(array, 0, array.length, characteristics);
	}

	public static <T> Spliterator<T> spliterator(Object[] array, int from, int to,
			int characteristics) {
		return new AbstractSpliterator<T>(to - from,
				characteristics | Spliterator.SIZED | Spliterator.SUBSIZED) {
			private int index = from;

			@SuppressWarnings("unchecked")
			@Override
			public boolean tryAdvance(Consumer<? super T> action) {
				if (index >= to) {
					return false;
				}
				action.accept((T) array[index++]);
				return true;
			}
		};
	}

	public static <T> Spliterator<T> spliterator(Collection<? extends T> collection,
			int characteristics) {
		return spliterator(collection.iterator(), collection.size(),
				characteristics | Spliterator.SIZED | Spliterator.SUBSIZED);
	}

	public static <T> Spliterator<T> spliterator(Iterator<? extends T> iterator, long size,
			int characteristics) {
		return new AbstractSpliterator<T>(size, characteristics) {
			@Override
			public boolean tryAdvance(Consumer<? super T> action) {
				if (!iterator.hasNext()) {
					return false;
				}
				action.accept(iterator.next());
				return true;
			}
		};
	}

	public static <T> Spliterator<T> spliteratorUnknownSize(Iterator<? extends T> iterator,
			int characteristics) {
		return spliterator(iterator, Long.MAX_VALUE,
				characteristics & ~(Spliterator.SIZED | Spliterator.SUBSIZED));
	}

	public static Spliterator.OfInt spliterator(int[] array, int characteristics) {
		return spliterator(array, 0, array.length, characteristics);
	}

	public static Spliterator.OfInt spliterator(int[] array, int from, int to,
			int characteristics) {
		return new AbstractIntSpliterator(to - from,
				characteristics | Spliterator.SIZED | Spliterator.SUBSIZED) {
			private int index = from;

			@Override
			public boolean tryAdvance(IntConsumer action) {
				if (index >= to) {
					return false;
				}
				action.accept(array[index++]);
				return true;
			}
		};
	}

	public static Spliterator.OfInt spliterator(PrimitiveIterator.OfInt iterator, long size,
			int characteristics) {
		return new AbstractIntSpliterator(size, characteristics) {
			@Override
			public boolean tryAdvance(IntConsumer action) {
				if (!iterator.hasNext()) {
					return false;
				}
				action.accept(iterator.nextInt());
				return true;
			}
		};
	}

	public static Spliterator.OfInt spliteratorUnknownSize(PrimitiveIterator.OfInt iterator,
			int characteristics) {
		return spliterator(iterator, Long.MAX_VALUE,
				characteristics & ~(Spliterator.SIZED | Spliterator.SUBSIZED));
	}

	public static Spliterator.OfLong spliterator(PrimitiveIterator.OfLong iterator, long size,
			int characteristics) {
		return new AbstractLongSpliterator(size, characteristics) {
			@Override
			public boolean tryAdvance(LongConsumer action) {
				if (!iterator.hasNext()) {
					return false;
				}
				action.accept(iterator.nextLong());
				return true;
			}
		};
	}

	public static Spliterator.OfLong spliteratorUnknownSize(PrimitiveIterator.OfLong iterator,
			int characteristics) {
		return spliterator(iterator, Long.MAX_VALUE,
				characteristics & ~(Spliterator.SIZED | Spliterator.SUBSIZED));
	}

	public static Spliterator.OfDouble spliterator(PrimitiveIterator.OfDouble iterator,
			long size, int characteristics) {
		return new AbstractDoubleSpliterator(size, characteristics) {
			@Override
			public boolean tryAdvance(DoubleConsumer action) {
				if (!iterator.hasNext()) {
					return false;
				}
				action.accept(iterator.nextDouble());
				return true;
			}
		};
	}

	public static Spliterator.OfDouble spliteratorUnknownSize(PrimitiveIterator.OfDouble iterator,
			int characteristics) {
		return spliterator(iterator, Long.MAX_VALUE,
				characteristics & ~(Spliterator.SIZED | Spliterator.SUBSIZED));
	}
}
