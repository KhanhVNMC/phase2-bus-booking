package dev.janitor.busbooking.impl.payment;

import java.util.concurrent.ThreadLocalRandom;

import dev.janitor.busbooking.bootstrap.ApplicationConfig;
import dev.janitor.busbooking.consts.PaymentOutcome;
import dev.janitor.busbooking.repository.IPaymentProcessor;

// a sham: waits a bit, then mostly says yes. benchmark mode always says yes, right away
public class FakePaymentProcessor implements IPaymentProcessor {
	private final boolean benchmarkMode;
	private final int minDelayMs;
	private final int maxDelayMs;
	private final int declinePercent;
	private final int timeoutPercent;
	private final int timeoutMs;
	
	public FakePaymentProcessor(ApplicationConfig config) {
		this.benchmarkMode = config.isPaymentBenchmarkMode();
		this.minDelayMs = config.getPaymentMinDelayMs();
		this.maxDelayMs = config.getPaymentMaxDelayMs();
		this.declinePercent = config.getPaymentDeclinePercent();
		this.timeoutPercent = config.getPaymentTimeoutPercent();
		this.timeoutMs = config.getPaymentTimeoutMs();
	}
	
	@Override
	public PaymentOutcome charge(long bookingId, long amount) {
		if (this.benchmarkMode) {
			return PaymentOutcome.APPROVED;
		}
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int roll = random.nextInt(100);
		// the bank never answered: we waited the whole timeout for nothing
		if (roll < this.timeoutPercent) {
			sleep(this.timeoutMs);
			return PaymentOutcome.TIMED_OUT;
		}
		sleep(random.nextInt(this.minDelayMs, this.maxDelayMs + 1));
		if (roll < this.timeoutPercent + this.declinePercent) {
			return PaymentOutcome.DECLINED;
		}
		return PaymentOutcome.APPROVED;
	}
	
	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
