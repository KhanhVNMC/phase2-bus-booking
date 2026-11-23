package dev.janitor.busbooking.api.dto;

import dev.janitor.busbooking.consts.PaymentOutcome;

// POST /api/payments/callback: Sent by the gateway once the charge settles
// the raw body is signed: X-Gateway-Signature = base64(HMAC-SHA256(gateway secret, body)), anything else is a 401.
public record PaymentCallbackRequest(
    String bookingId,
    PaymentOutcome outcome // APPROVED | DECLINED | TIMED_OUT
) {}
