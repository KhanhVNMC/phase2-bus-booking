package dev.janitor.busbooking.utils;

// standard twitter snowflake, 64 bits:
// [1 unused (always 0) | 41 ms since EPOCH | 10 instance | 12 sequence]
// 1024 instances, 4096 ids per ms per instance, ~69 (nice) years from EPOCH
// positive and time ordered: a larger id is always a newer one (within one instance, and across by ms)
public final class Snowflake {
	public static final long EPOCH = 1_767_225_600_000L; // 2026-01-01T00:00:00Z
	
	public static final int INSTANCE_BITS = 10;
	public static final int SEQUENCE_BITS = 12;
	
	public static final long MAX_INSTANCE_ID = 0b11_1111_1111; // 1023
	public static final long SEQUENCE_MASK = 0b1111_1111_1111; // 4095
	
	private static final int INSTANCE_SHIFT = SEQUENCE_BITS; // 12
	private static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + INSTANCE_BITS; // 22
	
	private static volatile Snowflake global;
	
	public static Snowflake get() {
		return global;
	}
	
	public static Snowflake configureGlobal(Snowflake snowflake) {
		global = snowflake;
		return snowflake;
	}
	
	private final long instanceId;
	private long lastTimestamp = -1;
	private long sequence = 0;
	
	public Snowflake(long instanceId) {
		if (instanceId < 0 || instanceId > MAX_INSTANCE_ID) {
			throw new IllegalArgumentException("Instance id must be between 0 and " + MAX_INSTANCE_ID + ", got " + instanceId);
		}
		this.instanceId = instanceId;
	}
	
	public synchronized long nextId() {
		long timestamp = System.currentTimeMillis();
		if (timestamp < lastTimestamp) {
			// the clock went backwards (ntp step, vm pause): handing out ids now could repeat old ones
			throw new IllegalStateException(
				"Clock moved backwards by " + (lastTimestamp - timestamp) + " ms, refusing to generate ids"
			);
		}
		if (timestamp == lastTimestamp) {
			sequence = (sequence + 1) & SEQUENCE_MASK;
			if (sequence == 0) {
				// 4096 ids already handed out this ms: spin until the next one
				while (timestamp <= lastTimestamp) {
					Thread.onSpinWait();
					timestamp = System.currentTimeMillis();
				}
			}
		} else {
			sequence = 0;
		}
		lastTimestamp = timestamp;
		return ((timestamp - EPOCH) << TIMESTAMP_SHIFT) | (instanceId << INSTANCE_SHIFT) | sequence;
	}
	
	// unix millis the id was made at
	public static long timestampOf(long id) {
		return (id >>> TIMESTAMP_SHIFT) + EPOCH;
	}
	
	public static long instanceOf(long id) {
		return (id >>> INSTANCE_SHIFT) & MAX_INSTANCE_ID;
	}
	
	public static long sequenceOf(long id) {
		return id & SEQUENCE_MASK;
	}
}
