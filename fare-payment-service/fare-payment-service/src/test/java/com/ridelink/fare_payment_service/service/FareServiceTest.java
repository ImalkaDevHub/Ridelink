package com.ridelink.fare_payment_service.service;

import com.ridelink.fare_payment_service.dto.FareEstimateRequest;
import com.ridelink.fare_payment_service.dto.FinalFareRequest;
import com.ridelink.fare_payment_service.model.Payment;
import com.ridelink.fare_payment_service.model.PaymentStatus;
import com.ridelink.fare_payment_service.repository.FareEstimateRepository;
import com.ridelink.fare_payment_service.repository.PaymentRepository;
import com.ridelink.fare_payment_service.util.FareCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FareServiceTest {

    @Mock
    private FareEstimateRepository fareEstimateRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Spy
    private FareCalculator fareCalculator = new FareCalculator(100.0, 50.0, 10.0);

    @InjectMocks
    private FareService fareService;

    @Test
    void estimateAppliesTheDocumentedFormula() {
        FareEstimateRequest request = new FareEstimateRequest();
        request.setPickupLocation("SLIIT");
        request.setDestinationLocation("Malabe Junction");
        request.setDistanceKm(5.0);
        request.setDurationMin(12.0);

        var response = fareService.estimate(request);

        assertThat(response.getEstimatedFare()).isEqualTo(470.0); // 100 + 50*5 + 10*12
        verify(fareEstimateRepository).save(any());
    }

    @Test
    void calculateFinalFareRejectsASecondPaymentForTheSameRide() {
        when(paymentRepository.findByRideId("ride-1")).thenReturn(Optional.of(new Payment()));

        FinalFareRequest request = new FinalFareRequest();
        request.setRideId("ride-1");
        request.setPassengerId("passenger-1");
        request.setDistanceKm(5.0);
        request.setDurationMin(12.0);

        assertThatThrownBy(() -> fareService.calculateFinalFare(request))
                .isInstanceOf(FareService.DuplicatePaymentException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void calculateFinalFareSavesACompletedPaymentWhenNoneExistsYet() {
        when(paymentRepository.findByRideId("ride-2")).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FinalFareRequest request = new FinalFareRequest();
        request.setRideId("ride-2");
        request.setPassengerId("passenger-2");
        request.setDistanceKm(5.0);
        request.setDurationMin(12.0);

        var response = fareService.calculateFinalFare(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED.name());
        assertThat(response.getFinalFare()).isEqualTo(470.0);
        assertThat(response.getCompletedAt()).isNotNull();
    }

    @Test
    void getPaymentStatusThrowsWhenNoPaymentExistsForTheRide() {
        when(paymentRepository.findByRideId("missing-ride")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fareService.getPaymentStatus("missing-ride"))
                .isInstanceOf(FareService.PaymentNotFoundException.class);
    }

    @Test
    void getReceiptThrowsWhenPaymentIsNotYetCompleted() {
        Payment pending = new Payment();
        pending.setRideId("ride-3");
        pending.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findByRideId("ride-3")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> fareService.getReceipt("ride-3"))
                .isInstanceOf(FareService.PaymentNotFoundException.class);
    }

    @Test
    void getReceiptReturnsTheFormulaBreakdownForACompletedPayment() {
        Payment completed = new Payment();
        completed.setRideId("ride-4");
        completed.setPassengerId("passenger-4");
        completed.setDistanceKm(5.0);
        completed.setDurationMin(12.0);
        completed.setFinalFare(470.0);
        completed.setStatus(PaymentStatus.COMPLETED);
        when(paymentRepository.findByRideId("ride-4")).thenReturn(Optional.of(completed));

        var receipt = fareService.getReceipt("ride-4");

        assertThat(receipt.getFinalFare()).isEqualTo(470.0);
        assertThat(receipt.getBreakdown()).contains("470.00");
    }
}
