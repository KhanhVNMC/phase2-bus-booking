package dev.janitor.busbooking.repository;

import dev.janitor.busbooking.consts.PaymentOutcome;

// the card network behind the gateway. blocks for as long as the charge takes
public interface IPaymentProcessor {
	PaymentOutcome charge(long bookingId, long amount);
}
