package dev.janitor.busbooking.api.dto;

public record PaymentPageResponse(
    String bookingId,
    String bookingCode,
    long amount, // vnd
    String expiresAt // thoi gian het han thanh toan
) {

}
