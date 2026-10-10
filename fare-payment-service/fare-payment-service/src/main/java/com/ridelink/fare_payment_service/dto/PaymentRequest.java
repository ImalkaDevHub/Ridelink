package com.ridelink.fare_payment_service.dto;

import com.ridelink.fare_payment_service.model.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// Body of POST /api/payments. There is deliberately no amount field: the
// amount always comes from the ride's stored final fare.
public class PaymentRequest {

    @NotBlank(message = "rideId is required")
    private String rideId;

    @NotNull(message = "method is required (CARD or CASH)")
    private PaymentMethod method;

    // Required for CARD (checked in PaymentService). Simulated: "0000" is
    // always declined.
    @Pattern(regexp = "^\\d{4}$", message = "cardLast4 must be exactly 4 digits")
    private String cardLast4;

    public String getRideId() {
        return rideId;
    }

    public void setRideId(String rideId) {
        this.rideId = rideId;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(PaymentMethod method) {
        this.method = method;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public void setCardLast4(String cardLast4) {
        this.cardLast4 = cardLast4;
    }
}
