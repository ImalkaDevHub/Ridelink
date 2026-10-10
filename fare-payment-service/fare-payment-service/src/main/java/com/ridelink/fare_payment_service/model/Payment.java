package com.ridelink.fare_payment_service.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.math.BigDecimal;
import java.time.Instant;

// One payment attempt. A ride may have several (a FAILED card attempt can
// be retried) but at most one paid one - enforced by the sparse unique
// index on paidRideId, which is set only while a payment is PAID/COMPLETED.
//
// Fields added after the first release are nullable so the payments
// recorded before them (status COMPLETED, money stored as doubles) still
// load; Spring converts those doubles to BigDecimal on read.
@Document(collection = "payments")
public class Payment {

    @Id
    private String id;

    @Indexed
    private String rideId;

    @Indexed(unique = true, sparse = true)
    private String paidRideId;

    private String passengerId;

    // Account id of whoever made this payment (POST /api/payments).
    private String payerId;

    private String fareRecordId;

    private BigDecimal finalFare;
    private String currency;
    private double distanceKm;
    private double durationMin;

    private String vehicleType;

    private PaymentStatus status = PaymentStatus.PENDING;

    // CARD / CASH for payments made through POST /api/payments; null for
    // legacy ones, whose paymentMethod is "SIMULATED_CARD".
    private PaymentMethod method;

    private String paymentMethod = "SIMULATED_CARD";

    private String cardLast4;

    private String failureReason;

    private String receiptNumber;

    private Instant createdAt = Instant.now();
    private Instant completedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRideId() {
        return rideId;
    }

    public void setRideId(String rideId) {
        this.rideId = rideId;
    }

    public String getPaidRideId() {
        return paidRideId;
    }

    public void setPaidRideId(String paidRideId) {
        this.paidRideId = paidRideId;
    }

    public String getPassengerId() {
        return passengerId;
    }

    public void setPassengerId(String passengerId) {
        this.passengerId = passengerId;
    }

    public String getPayerId() {
        return payerId;
    }

    public void setPayerId(String payerId) {
        this.payerId = payerId;
    }

    public String getFareRecordId() {
        return fareRecordId;
    }

    public void setFareRecordId(String fareRecordId) {
        this.fareRecordId = fareRecordId;
    }

    public BigDecimal getFinalFare() {
        return finalFare;
    }

    public void setFinalFare(BigDecimal finalFare) {
        this.finalFare = finalFare;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public double getDurationMin() {
        return durationMin;
    }

    public void setDurationMin(double durationMin) {
        this.durationMin = durationMin;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(PaymentMethod method) {
        this.method = method;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public void setCardLast4(String cardLast4) {
        this.cardLast4 = cardLast4;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public void setReceiptNumber(String receiptNumber) {
        this.receiptNumber = receiptNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
