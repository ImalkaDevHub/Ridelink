package com.ridelink.fare_payment_service.service;

import com.ridelink.fare_payment_service.dto.FareRecordResponse;
import com.ridelink.fare_payment_service.dto.FinalFareRequest;
import com.ridelink.fare_payment_service.dto.PaymentRequest;
import com.ridelink.fare_payment_service.dto.PaymentResponse;
import com.ridelink.fare_payment_service.dto.ReceiptResponse;
import com.ridelink.fare_payment_service.model.FareRecord;
import com.ridelink.fare_payment_service.model.Payment;
import com.ridelink.fare_payment_service.model.PaymentMethod;
import com.ridelink.fare_payment_service.model.PaymentStatus;
import com.ridelink.fare_payment_service.repository.FareRecordRepository;
import com.ridelink.fare_payment_service.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Simulated payments. Amounts always come from the ride's stored
// FareRecord, never from the client.
@Service
public class PaymentService {

    // Simulated decline: any card ending in 0000 fails.
    public static final String DECLINED_CARD_LAST4 = "0000";

    private static final Set<PaymentStatus> PAID_STATUSES = EnumSet.of(PaymentStatus.PAID, PaymentStatus.COMPLETED);
    private static final DateTimeFormatter RECEIPT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private FareRecordRepository fareRecordRepository;

    @Autowired
    private FareService fareService;

    // POST /api/payments. PENDING is saved first so every attempt leaves a
    // record, then the attempt resolves to PAID or FAILED.
    public PaymentResponse pay(PaymentRequest request, String callerId, boolean callerIsAdmin) {
        FareRecord fare = fareRecordRepository.findByRideId(request.getRideId())
                .orElseThrow(() -> new FareNotFinalizedException(
                        "No final fare exists yet for ride: " + request.getRideId()));

        if (fare.getPassengerId() != null && !callerIsAdmin && !fare.getPassengerId().equals(callerId)) {
            throw new NotPayerException("Only the ride's passenger can pay for it.");
        }

        if (request.getMethod() == PaymentMethod.CARD && request.getCardLast4() == null) {
            throw new MissingCardDetailsException("cardLast4 is required for CARD payments.");
        }

        if (paymentRepository.existsByRideIdAndStatusIn(request.getRideId(), PAID_STATUSES)) {
            throw new PaymentAlreadyExistsException("Ride " + request.getRideId() + " has already been paid.");
        }

        Payment payment = new Payment();
        payment.setRideId(fare.getRideId());
        payment.setPassengerId(fare.getPassengerId());
        payment.setPayerId(callerId);
        payment.setFareRecordId(fare.getId());
        payment.setFinalFare(fare.getTotalFare());
        payment.setCurrency(fare.getCurrency());
        payment.setDistanceKm(fare.getDistanceKm().doubleValue());
        payment.setDurationMin(fare.getDurationMin().doubleValue());
        payment.setVehicleType(fare.getVehicleType());
        payment.setMethod(request.getMethod());
        payment.setPaymentMethod(request.getMethod().name());
        payment.setCardLast4(request.getMethod() == PaymentMethod.CARD ? request.getCardLast4() : null);
        payment.setStatus(PaymentStatus.PENDING);
        payment = paymentRepository.save(payment);

        if (request.getMethod() == PaymentMethod.CARD && DECLINED_CARD_LAST4.equals(request.getCardLast4())) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Card declined (simulated: cards ending in 0000 always fail).");
            Payment failed = paymentRepository.save(payment);
            throw new PaymentFailedException(failed.getFailureReason(), failed.getId());
        }

        markPaid(payment);
        try {
            return PaymentResponse.from(paymentRepository.save(payment));
        } catch (DuplicateKeyException e) {
            // Another attempt for this ride was marked paid in between; the
            // unique index on paidRideId rejected this one.
            payment.setStatus(PaymentStatus.FAILED);
            payment.setPaidRideId(null);
            payment.setReceiptNumber(null);
            payment.setCompletedAt(null);
            payment.setFailureReason("Ride was already paid by another payment.");
            paymentRepository.save(payment);
            throw new PaymentAlreadyExistsException("Ride " + request.getRideId() + " has already been paid.");
        }
    }

    // Legacy POST /api/payments/finalize, kept for ride-service: finalizes
    // the fare (idempotently) and records a simulated, already-collected
    // payment for it, status COMPLETED.
    public PaymentResponse finalizeAndCharge(FinalFareRequest request) {
        if (paymentRepository.existsByRideIdAndStatusIn(request.getRideId(), PAID_STATUSES)) {
            throw new DuplicatePaymentException("A payment already exists for ride: " + request.getRideId());
        }

        FareRecord fare = fareService.finalizeFareLenient(
                request.getRideId(), request.getPassengerId(),
                BigDecimal.valueOf(request.getDistanceKm()), BigDecimal.valueOf(request.getDurationMin()),
                request.getVehicleType()).record();

        Payment payment = new Payment();
        payment.setRideId(fare.getRideId());
        payment.setPassengerId(request.getPassengerId());
        payment.setFareRecordId(fare.getId());
        payment.setFinalFare(fare.getTotalFare());
        payment.setCurrency(fare.getCurrency());
        payment.setDistanceKm(request.getDistanceKm());
        payment.setDurationMin(request.getDurationMin());
        payment.setVehicleType(request.getVehicleType());
        // Simulated payment processing - no real gateway involved.
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidRideId(fare.getRideId());
        payment.setCompletedAt(Instant.now());
        payment.setReceiptNumber(receiptNumber(payment));

        try {
            return PaymentResponse.from(paymentRepository.save(payment));
        } catch (DuplicateKeyException e) {
            throw new DuplicatePaymentException("A payment already exists for ride: " + request.getRideId());
        }
    }

    public PaymentResponse getPayment(String paymentId) {
        return PaymentResponse.from(findById(paymentId));
    }

    public List<PaymentResponse> getPaymentsForRide(String rideId) {
        List<Payment> payments = paymentRepository.findByRideIdOrderByCreatedAtAsc(rideId);
        if (payments.isEmpty()) {
            throw new PaymentNotFoundException("No payment found for ride: " + rideId);
        }
        return payments.stream().map(PaymentResponse::from).toList();
    }

    // Legacy GET /api/payments/{rideId}/status: a ride used to have exactly
    // one payment. Now it reports the paid one if there is one, otherwise
    // the latest attempt.
    public PaymentResponse getPaymentStatusForRide(String rideId) {
        return PaymentResponse.from(primaryPaymentForRide(rideId));
    }

    // GET /api/payments/{id}/receipt. {id} is a payment id; for the legacy
    // form of this path it is a ride id, so fall back to that when no
    // payment has this id.
    public ReceiptResponse getReceipt(String paymentIdOrRideId) {
        Payment payment = paymentRepository.findById(paymentIdOrRideId)
                .orElseGet(() -> primaryPaymentForRide(paymentIdOrRideId));

        if (!payment.getStatus().isPaid()) {
            throw new PaymentNotFoundException("No completed payment found for: " + paymentIdOrRideId);
        }

        FareRecord fare = payment.getFareRecordId() == null
                ? null
                : fareRecordRepository.findById(payment.getFareRecordId()).orElse(null);

        String breakdown = fare != null
                ? fare.getBreakdown()
                : String.format(Locale.ROOT, "%s %s (recorded before itemised fare breakdowns)",
                        payment.getFinalFare().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(),
                        payment.getCurrency() != null ? payment.getCurrency() : "LKR");

        return new ReceiptResponse(
                payment.getRideId(),
                payment.getPassengerId(),
                payment.getFinalFare(),
                payment.getDistanceKm(),
                payment.getDurationMin(),
                payment.getPaymentMethod(),
                payment.getStatus().name(),
                payment.getCompletedAt(),
                breakdown,
                payment.getReceiptNumber() != null ? payment.getReceiptNumber() : receiptNumber(payment),
                payment.getId(),
                payment.getMethod() != null ? payment.getMethod().name() : payment.getPaymentMethod(),
                payment.getCurrency() != null ? payment.getCurrency() : "LKR",
                payment.getCompletedAt() != null ? payment.getCompletedAt() : payment.getCreatedAt(),
                fare != null ? FareRecordResponse.from(fare) : null);
    }

    private Payment primaryPaymentForRide(String rideId) {
        return paymentRepository.findFirstByRideIdAndStatusIn(rideId, PAID_STATUSES)
                .orElseGet(() -> paymentRepository.findByRideIdOrderByCreatedAtAsc(rideId).stream()
                        .max(Comparator.comparing(Payment::getCreatedAt))
                        .orElseThrow(() -> new PaymentNotFoundException("No payment found for ride: " + rideId)));
    }

    private Payment findById(String paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("No payment found with id: " + paymentId));
    }

    private void markPaid(Payment payment) {
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidRideId(payment.getRideId());
        payment.setCompletedAt(Instant.now());
        payment.setFailureReason(null);
        payment.setReceiptNumber(receiptNumber(payment));
    }

    // e.g. RL-20261010-A1B2C3: date paid (UTC) + the last 6 characters of
    // the payment id. Payments without an id yet (legacy finalize) use the
    // ride id instead.
    static String receiptNumber(Payment payment) {
        String source = payment.getId() != null ? payment.getId() : payment.getRideId();
        String suffix = source.length() > 6 ? source.substring(source.length() - 6) : source;
        Instant when = payment.getCompletedAt() != null ? payment.getCompletedAt() : payment.getCreatedAt();
        return "RL-" + RECEIPT_DATE.format(when) + "-" + suffix.toUpperCase(Locale.ROOT);
    }

    public static class FareNotFinalizedException extends RuntimeException {
        public FareNotFinalizedException(String message) {
            super(message);
        }
    }

    public static class PaymentAlreadyExistsException extends RuntimeException {
        public PaymentAlreadyExistsException(String message) {
            super(message);
        }
    }

    public static class PaymentFailedException extends RuntimeException {
        private final String paymentId;

        public PaymentFailedException(String message, String paymentId) {
            super(message);
            this.paymentId = paymentId;
        }

        public String getPaymentId() {
            return paymentId;
        }
    }

    public static class MissingCardDetailsException extends RuntimeException {
        public MissingCardDetailsException(String message) {
            super(message);
        }
    }

    public static class NotPayerException extends RuntimeException {
        public NotPayerException(String message) {
            super(message);
        }
    }

    // Legacy finalize keeps its original DUPLICATE_PAYMENT code.
    public static class DuplicatePaymentException extends RuntimeException {
        public DuplicatePaymentException(String message) {
            super(message);
        }
    }

    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(String message) {
            super(message);
        }
    }
}
