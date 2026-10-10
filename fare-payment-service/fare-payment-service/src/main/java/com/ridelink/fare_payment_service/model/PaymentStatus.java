package com.ridelink.fare_payment_service.model;

// PENDING -> PAID | FAILED for payments made through POST /api/payments.
// COMPLETED is written only by the legacy POST /api/payments/finalize call
// (and by every payment recorded before PAID existed); it counts as paid.
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    PAID;

    public boolean isPaid() {
        return this == PAID || this == COMPLETED;
    }
}
