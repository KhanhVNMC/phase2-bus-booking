package dev.janitor.busbooking.model;

// 128-bit seat bitset, x86 order: seat i = bit i, LSB first. seats 0..63 in lo, 64..127 in hi
public record SeatSet(long lo, long hi) {
	public static final int CAPACITY = 128;
	public static final SeatSet EMPTY = new SeatSet(0, 0);

	// the lowest n bits set, aka a unary counter of n
	public static SeatSet firstN(int n) {
		if (n < 0 || n > CAPACITY) {
			throw new IllegalArgumentException("Seat count must be between 0 and " + CAPACITY);
		}
		if (n == 0) return EMPTY;
		if (n < 64) return new SeatSet((1L << n) - 1, 0);
		if (n == 64) return new SeatSet(-1L, 0);
		if (n < CAPACITY) return new SeatSet(-1L, (1L << (n - 64)) - 1);
		return new SeatSet(-1L, -1L);
	}

	public static SeatSet of(int... seats) {
		long lo = 0, hi = 0;
		for (int seat : seats) {
			if (seat < 0 || seat >= CAPACITY) {
				throw new IllegalArgumentException("Seat index out of range: " + seat);
			}
			if (seat < 64) lo |= 1L << seat; else hi |= 1L << (seat - 64);
		}
		return new SeatSet(lo, hi);
	}

	public boolean get(int seat) {
		return seat < 64 ? (lo >>> seat & 1) != 0 : (hi >>> (seat - 64) & 1) != 0;
	}

	public int popcount() {
		return Long.bitCount(lo) + Long.bitCount(hi);
	}

	public boolean isEmpty() {
		return (lo | hi) == 0;
	}

	public boolean intersects(SeatSet other) {
		return ((lo & other.lo) | (hi & other.hi)) != 0;
	}

	public boolean containsAll(SeatSet other) {
		return (lo & other.lo) == other.lo && (hi & other.hi) == other.hi;
	}

	public SeatSet or(SeatSet other) {
		return new SeatSet(lo | other.lo, hi | other.hi);
	}

	public SeatSet and(SeatSet other) {
		return new SeatSet(lo & other.lo, hi & other.hi);
	}

	public SeatSet andNot(SeatSet other) {
		return new SeatSet(lo & ~other.lo, hi & ~other.hi);
	}
}
