package com.ridelink.fare_payment_service.service;

import com.ridelink.fare_payment_service.dto.FareEstimateRequest;
import com.ridelink.fare_payment_service.dto.FareEstimateResponse;
import com.ridelink.fare_payment_service.dto.FinalFareRequest;
import com.ridelink.fare_payment_service.dto.PaymentResponse;
import com.ridelink.fare_payment_service.dto.ReceiptResponse;
import com.ridelink.fare_payment_service.model.FareEstimate;
import com.ridelink.fare_payment_service.model.Payment;
import com.ridelink.fare_payment_service.model.PaymentStatus;
import com.ridelink.fare_payment_service.repository.FareEstimateRepository;
import com.ridelink.fare_payment_service.repository.PaymentRepository;
import com.ridelink.fare_payment_service.util.FareCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class FareService {

    @Autowired
    private FareEstimateRepository fareEstimateRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private FareCalculator fareCalculator;

    public FareEstimateResponse estimate(FareEstimateRequest request) {
        // distanceKm/durationMin > 0 is enforced by @Valid on the request DTO
        // and, as a second line of defence, by FareCalculator itself.
        double fare = fareCalculator.calculate(request.getDistanceKm(), request.getDurationMin());

        FareEstimate estimate = new FareEstimate();
        estimate.setPickupLocation(request.getPickupLocation());
        estimate.setDestinationLocation(request.getDestinationLocation());
        estimate.setDistanceKm(request.getDistanceKm());
        estimate.setDurationMin(request.getDurationMin());
        estimate.setEstimatedFare(fare);
        fareEstimateRepository.save(estimate);

        return new FareEstimateResponse(
                request.getPickupLocation(),
                request.getDestinationLocation(),
                request.getDistanceKm(),
                request.getDurationMin(),
                fareCalculator.getBaseFare(),
                fareCalculator.getPerKm(),
                fareCalculator.getPerMin(),
                fare);
    }

    // Called by Ride Management when a ride is completed. A ride may only be
    // paid once.
    public PaymentResponse calculateFinalFare(FinalFareRequest request) {
        if (paymentRepository.findByRideId(request.getRideId()).isPresent()) {
            throw new DuplicatePaymentException("A payment already exists for ride: " + request.getRideId());
        }

        double fare = fareCalculator.calculate(request.getDistanceKm(), request.getDurationMin());

        Payment payment = new Payment();
        payment.setRideId(request.getRideId());
        payment.setPassengerId(request.getPassengerId());
        payment.setDistanceKm(request.getDistanceKm());
        payment.setDurationMin(request.getDurationMin());
        payment.setFinalFare(fare);
        // Simulated payment processing - no real gateway involved.
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setCompletedAt(Instant.now());

        Payment saved = paymentRepository.save(payment);
        return toResponse(saved);
    }

    public PaymentResponse getPaymentStatus(String rideId) {
        return toResponse(findOrThrow(rideId));
    }

    public ReceiptResponse getReceipt(String rideId) {
        Payment payment = findOrThrow(rideId);

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new PaymentNotFoundException("No completed payment found for ride: " + rideId);
        }

        String breakdown = fareCalculator.describeBreakdown(payment.getDistanceKm(), payment.getDurationMin());

        return new ReceiptResponse(
                payment.getRideId(),
                payment.getPassengerId(),
                payment.getFinalFare(),
                payment.getDistanceKm(),
                payment.getDurationMin(),
                payment.getPaymentMethod(),
                payment.getStatus().name(),
                payment.getCompletedAt(),
                breakdown);
    }

    private Payment findOrThrow(String rideId) {
        return paymentRepository.findByRideId(rideId)
                .orElseThrow(() -> new PaymentNotFoundException("No payment found for ride: " + rideId));
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getRideId(),
                payment.getFinalFare(),
                payment.getStatus().name(),
                payment.getPaymentMethod(),
                payment.getCreatedAt(),
                payment.getCompletedAt());
    }

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
