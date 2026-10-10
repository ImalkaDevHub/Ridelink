package com.ridelink.fare_payment_service.service;

import com.ridelink.fare_payment_service.dto.FinalFareRequest;
import com.ridelink.fare_payment_service.dto.PaymentRequest;
import com.ridelink.fare_payment_service.model.FareRecord;
import com.ridelink.fare_payment_service.model.Payment;
import com.ridelink.fare_payment_service.model.PaymentMethod;
import com.ridelink.fare_payment_service.model.PaymentStatus;
import com.ridelink.fare_payment_service.repository.FareRecordRepository;
import com.ridelink.fare_payment_service.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Unit tests for PaymentService with repositories mocked - no MongoDB.
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private FareRecordRepository fareRecordRepository;

    @Mock
    private FareService fareService;

    @InjectMocks
    private PaymentService paymentService;

    // Every Payment passed to save(), as it looked at that moment.
    private final List<PaymentStatus> savedStatuses = new ArrayList<>();

    private FareRecord fare(String passengerId) {
        FareRecord record = new FareRecord();
        record.setId("fare-1");
        record.setRideId("ride-1");
        record.setPassengerId(passengerId);
        record.setVehicleType("SEDAN");
        record.setDistanceKm(new BigDecimal("5"));
        record.setDurationMin(new BigDecimal("12"));
        record.setTotalFare(new BigDecimal("1020.00"));
        record.setCurrency("LKR");
        record.setBreakdown("max(500.00, ...) x 1.0 (SEDAN) = 1020.00 LKR");
        return record;
    }

    private PaymentRequest request(PaymentMethod method, String cardLast4) {
        PaymentRequest request = new PaymentRequest();
        request.setRideId("ride-1");
        request.setMethod(method);
        request.setCardLast4(cardLast4);
        return request;
    }

    private void saveAssignsIdAndRecordsStatus() {
        when(paymentRepository.save(any())).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId("pay-000abc123");
            }
            savedStatuses.add(payment.getStatus());
            return payment;
        });
    }

    // --- pay: success -----------------------------------------------------

    @Test
    void cardPaymentChargesTheStoredFinalFareAndIsPaid() {
        when(fareRecordRepository.findByRideId("ride-1")).thenReturn(Optional.of(fare("p-1")));
        saveAssignsIdAndRecordsStatus();

        var response = paymentService.pay(request(PaymentMethod.CARD, "4242"), "p-1", false);

        assertThat(response.status()).isEqualTo("PAID");
        assertThat(response.finalFare()).isEqualByComparingTo("1020.00"); // from FareRecord, not the client
        assertThat(response.method()).isEqualTo("CARD");
        assertThat(response.cardLast4()).isEqualTo("4242");
        assertThat(response.currency()).isEqualTo("LKR");
        assertThat(response.receiptNumber()).matches("RL-\\d{8}-ABC123");
        assertThat(response.completedAt()).isNotNull();
        assertThat(savedStatuses).containsExactly(PaymentStatus.PENDING, PaymentStatus.PAID);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getValue().getPaidRideId()).isEqualTo("ride-1");
        assertThat(saved.getValue().getPayerId()).isEqualTo("p-1");
    }

    @Test
    void cashPaymentIsPaidAndStoresNoCardDigits() {
        when(fareRecordRepository.findByRideId("ride-1")).thenReturn(Optional.of(fare(null)));
        saveAssignsIdAndRecordsStatus();

        var response = paymentService.pay(request(PaymentMethod.CASH, "4242"), "p-9", false);

        assertThat(response.status()).isEqualTo("PAID");
        assertThat(response.method()).isEqualTo("CASH");
        assertThat(response.cardLast4()).isNull();
    }

    @Test
    void adminMayPayForAnotherPassengersRide() {
        when(fareRecordRepository.findByRideId("ride-1")).thenReturn(Optional.of(fare("p-1")));
        saveAssignsIdAndRecordsStatus();

        assertThat(paymentService.pay(request(PaymentMethod.CASH, null), "admin-1", true).status()).isEqualTo("PAID");
    }

    // --- pay: failures ----------------------------------------------------

    @Test
    void cardEndingIn0000IsSavedAsFailedAndReported() {
        when(fareRecordRepository.findByRideId("ride-1")).thenReturn(Optional.of(fare("p-1")));
        saveAssignsIdAndRecordsStatus();

        assertThatThrownBy(() -> paymentService.pay(request(PaymentMethod.CARD, "0000"), "p-1", false))
                .isInstanceOf(PaymentService.PaymentFailedException.class)
                .satisfies(e -> assertThat(((PaymentService.PaymentFailedException) e).getPaymentId())
                        .isEqualTo("pay-000abc123"));

        assertThat(savedStatuses).containsExactly(PaymentStatus.PENDING, PaymentStatus.FAILED);
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getValue().getPaidRideId()).isNull(); // a failed attempt never blocks a retry
        assertThat(saved.getValue().getFailureReason()).contains("declined");
    }

    @Test
    void retryAfterAFailedCardIsAllowed() {
        // existsByRideIdAndStatusIn only looks at PAID/COMPLETED, so a
        // previous FAILED attempt doesn't count.
        when(fareRecordRepository.findByRideId("ride-1")).thenReturn(Optional.of(fare("p-1")));
        when(paymentRepository.existsByRideIdAndStatusIn(eq("ride-1"), any())).thenReturn(false);
        saveAssignsIdAndRecordsStatus();

        assertThat(paymentService.pay(request(PaymentMethod.CARD, "1111"), "p-1", false).status()).isEqualTo("PAID");
    }

    @Test
    void secondPaymentForAPaidRideIsRejected() {
        when(fareRecordRepository.findByRideId("ride-1")).thenReturn(Optional.of(fare("p-1")));
        when(paymentRepository.existsByRideIdAndStatusIn(eq("ride-1"), any())).thenReturn(true);

        assertThatThrownBy(() -> paymentService.pay(request(PaymentMethod.CARD, "4242"), "p-1", false))
                .isInstanceOf(PaymentService.PaymentAlreadyExistsException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void concurrentDuplicateRejectedByTheUniqueIndexIsMarkedFailed() {
        when(fareRecordRepository.findByRideId("ride-1")).thenReturn(Optional.of(fare("p-1")));
        when(paymentRepository.save(any()))
                .thenAnswer(invocation -> {           // 1st save: PENDING insert
                    Payment payment = invocation.getArgument(0);
                    payment.setId("pay-1");
                    savedStatuses.add(payment.getStatus());
                    return payment;
                })
                .thenAnswer(invocation -> {           // 2nd save: PAID rejected by paidRideId index
                    savedStatuses.add(((Payment) invocation.getArgument(0)).getStatus());
                    throw new DuplicateKeyException("E11000 paidRideId");
                })
                .thenAnswer(invocation -> {           // 3rd save: marked FAILED
                    Payment payment = invocation.getArgument(0);
                    savedStatuses.add(payment.getStatus());
                    assertThat(payment.getPaidRideId()).isNull();
                    return payment;
                });

        assertThatThrownBy(() -> paymentService.pay(request(PaymentMethod.CARD, "4242"), "p-1", false))
                .isInstanceOf(PaymentService.PaymentAlreadyExistsException.class);

        assertThat(savedStatuses).containsExactly(PaymentStatus.PENDING, PaymentStatus.PAID, PaymentStatus.FAILED);
    }

    @Test
    void paymentWithoutAFinalFareIsRejected() {
        when(fareRecordRepository.findByRideId("ride-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.pay(request(PaymentMethod.CARD, "4242"), "p-1", false))
                .isInstanceOf(PaymentService.FareNotFinalizedException.class);

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void cardPaymentWithoutCardDigitsIsRejected() {
        when(fareRecordRepository.findByRideId("ride-1")).thenReturn(Optional.of(fare("p-1")));

        assertThatThrownBy(() -> paymentService.pay(request(PaymentMethod.CARD, null), "p-1", false))
                .isInstanceOf(PaymentService.MissingCardDetailsException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void anotherPassengerCannotPay() {
        when(fareRecordRepository.findByRideId("ride-1")).thenReturn(Optional.of(fare("p-1")));

        assertThatThrownBy(() -> paymentService.pay(request(PaymentMethod.CASH, null), "p-2", false))
                .isInstanceOf(PaymentService.NotPayerException.class);

        verify(paymentRepository, never()).save(any());
    }

    // --- legacy finalize --------------------------------------------------

    @Test
    void legacyFinalizeChargesTheFinalizedFareAsCompleted() {
        when(paymentRepository.existsByRideIdAndStatusIn(eq("ride-1"), any())).thenReturn(false);
        when(fareService.finalizeFareLenient(eq("ride-1"), eq("p-1"), any(), any(), eq("Car")))
                .thenReturn(new FareService.FinalizeResult(fare("p-1"), true));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FinalFareRequest request = new FinalFareRequest();
        request.setRideId("ride-1");
        request.setPassengerId("p-1");
        request.setDistanceKm(5.0);
        request.setDurationMin(12.0);
        request.setVehicleType("Car");

        var response = paymentService.finalizeAndCharge(request);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.finalFare()).isEqualByComparingTo("1020.00");
        assertThat(response.paymentMethod()).isEqualTo("SIMULATED_CARD");
    }

    @Test
    void legacyFinalizeRejectsAnAlreadyPaidRide() {
        when(paymentRepository.existsByRideIdAndStatusIn(eq("ride-1"), any())).thenReturn(true);
        FinalFareRequest request = new FinalFareRequest();
        request.setRideId("ride-1");

        assertThatThrownBy(() -> paymentService.finalizeAndCharge(request))
                .isInstanceOf(PaymentService.DuplicatePaymentException.class);

        verifyNoInteractions(fareService);
    }

    // --- reads ------------------------------------------------------------

    @Test
    void getPaymentByUnknownIdIsNotFound() {
        when(paymentRepository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment("nope"))
                .isInstanceOf(PaymentService.PaymentNotFoundException.class);
    }

    @Test
    void paymentsForRideWithNoneIsNotFound() {
        when(paymentRepository.findByRideIdOrderByCreatedAtAsc("ride-x")).thenReturn(List.of());

        assertThatThrownBy(() -> paymentService.getPaymentsForRide("ride-x"))
                .isInstanceOf(PaymentService.PaymentNotFoundException.class);
    }

    @Test
    void statusPrefersThePaidPaymentOverLaterFailures() {
        Payment paid = new Payment();
        paid.setId("pay-paid");
        paid.setRideId("ride-1");
        paid.setFinalFare(new BigDecimal("1020.00"));
        paid.setStatus(PaymentStatus.PAID);
        when(paymentRepository.findFirstByRideIdAndStatusIn(eq("ride-1"), any())).thenReturn(Optional.of(paid));

        assertThat(paymentService.getPaymentStatusForRide("ride-1").id()).isEqualTo("pay-paid");
    }

    @Test
    void receiptByPaymentIdIncludesTheFareBreakdown() {
        Payment paid = new Payment();
        paid.setId("pay-000abc123");
        paid.setRideId("ride-1");
        paid.setFareRecordId("fare-1");
        paid.setFinalFare(new BigDecimal("1020.00"));
        paid.setCurrency("LKR");
        paid.setMethod(PaymentMethod.CARD);
        paid.setPaymentMethod("CARD");
        paid.setStatus(PaymentStatus.PAID);
        paid.setCompletedAt(Instant.parse("2026-10-10T08:00:00Z"));
        paid.setReceiptNumber("RL-20261010-ABC123");
        paid.setPayerId("p-1");
        when(paymentRepository.findById("pay-000abc123")).thenReturn(Optional.of(paid));
        when(fareRecordRepository.findById("fare-1")).thenReturn(Optional.of(fare("p-1")));

        var receipt = paymentService.getReceipt("pay-000abc123", "p-1", false); // the payer

        assertThat(receipt.receiptNumber()).isEqualTo("RL-20261010-ABC123");
        assertThat(receipt.method()).isEqualTo("CARD");
        assertThat(receipt.status()).isEqualTo("PAID");
        assertThat(receipt.timestamp()).isEqualTo(Instant.parse("2026-10-10T08:00:00Z"));
        assertThat(receipt.fareBreakdown().totalFare()).isEqualByComparingTo("1020.00");
        assertThat(receipt.breakdown()).endsWith("1020.00 LKR");
    }

    @Test
    void receiptFallsBackToRideIdForTheLegacyPathAndHandlesLegacyPayments() {
        Payment legacy = new Payment();
        legacy.setId("6aba04f50808e1bfb34446cf");
        legacy.setRideId("ride-legacy");
        legacy.setFinalFare(new BigDecimal("350.0"));
        legacy.setStatus(PaymentStatus.COMPLETED);
        legacy.setCompletedAt(Instant.parse("2026-09-28T06:11:01Z"));
        when(paymentRepository.findById("ride-legacy")).thenReturn(Optional.empty());
        when(paymentRepository.findFirstByRideIdAndStatusIn(eq("ride-legacy"), any())).thenReturn(Optional.of(legacy));

        var receipt = paymentService.getReceipt("ride-legacy", "admin-1", true);

        assertThat(receipt.rideId()).isEqualTo("ride-legacy");
        assertThat(receipt.receiptNumber()).isEqualTo("RL-20260928-4446CF");
        assertThat(receipt.fareBreakdown()).isNull();
        assertThat(receipt.breakdown()).contains("350.00 LKR");
        assertThat(receipt.currency()).isEqualTo("LKR");
    }

    @Test
    void receiptForAFailedPaymentIsNotAvailable() {
        Payment failed = new Payment();
        failed.setId("pay-1");
        failed.setStatus(PaymentStatus.FAILED);
        when(paymentRepository.findById(anyString())).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> paymentService.getReceipt("pay-1", "admin-1", true))
                .isInstanceOf(PaymentService.PaymentNotFoundException.class);
    }

    // --- receipt ownership ------------------------------------------------

    private Payment paidPayment(String payerId, String passengerId) {
        Payment paid = new Payment();
        paid.setId("pay-9");
        paid.setRideId("ride-9");
        paid.setPayerId(payerId);
        paid.setPassengerId(passengerId);
        paid.setFinalFare(new BigDecimal("500.00"));
        paid.setStatus(PaymentStatus.PAID);
        paid.setCompletedAt(Instant.parse("2026-10-10T08:00:00Z"));
        when(paymentRepository.findById("pay-9")).thenReturn(Optional.of(paid));
        return paid;
    }

    @Test
    void receiptIsHiddenFromSomeoneWhoIsNotThePayer() {
        paidPayment("p-1", "p-1");

        assertThatThrownBy(() -> paymentService.getReceipt("pay-9", "p-2", false))
                .isInstanceOf(PaymentService.NotPayerException.class);
    }

    @Test
    void receiptOwnershipIsCheckedBeforeRevealingWhetherItWasPaid() {
        paidPayment("p-1", "p-1").setStatus(PaymentStatus.FAILED);

        // A stranger gets 403, not the 404 that would reveal the status.
        assertThatThrownBy(() -> paymentService.getReceipt("pay-9", "p-2", false))
                .isInstanceOf(PaymentService.NotPayerException.class);
    }

    @Test
    void legacyReceiptIsVisibleToTheRidesPassenger() {
        // Payments recorded on completion have no payerId - the passenger paid.
        paidPayment(null, "p-1");

        assertThat(paymentService.getReceipt("pay-9", "p-1", false).paymentId()).isEqualTo("pay-9");
    }

    @Test
    void adminSeesAnyReceipt() {
        paidPayment("p-1", "p-1");

        assertThat(paymentService.getReceipt("pay-9", "admin-1", true).paymentId()).isEqualTo("pay-9");
    }
}
