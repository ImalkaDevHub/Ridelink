package com.ridelink.fare_payment_service.controller;

import com.ridelink.fare_payment_service.dto.FareEstimateRequest;
import com.ridelink.fare_payment_service.dto.FareEstimateResponse;
import com.ridelink.fare_payment_service.dto.FareRecordResponse;
import com.ridelink.fare_payment_service.dto.FinalFareRequest;
import com.ridelink.fare_payment_service.dto.FinalizeFareRequest;
import com.ridelink.fare_payment_service.dto.PaymentRequest;
import com.ridelink.fare_payment_service.dto.PaymentResponse;
import com.ridelink.fare_payment_service.dto.ReceiptResponse;
import com.ridelink.fare_payment_service.service.FareService;
import com.ridelink.fare_payment_service.service.PaymentService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Error responses for every endpoint here come from GlobalExceptionHandler,
// which maps the service exceptions to {"code", "message"} bodies.
@RestController
public class FareController {

    @Autowired
    private FareService fareService;

    @Autowired
    private PaymentService paymentService;

    // Public - fare estimates are often shown pre-login in real apps.
    @PostMapping("/api/fares/estimate")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Estimate calculated",
            content = @Content(schema = @Schema(implementation = FareEstimateResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed, unknown vehicle type, or distance could not be resolved")
    public ResponseEntity<FareEstimateResponse> estimate(@Valid @RequestBody FareEstimateRequest request) {
        return ResponseEntity.ok(fareService.estimate(request));
    }

    // Called when a ride is completed (by ride-service with the driver's
    // token, or by a driver/admin directly). Idempotent per rideId.
    @PostMapping("/api/fares/final")
    @PreAuthorize("hasAuthority('DRIVER') or hasAuthority('ADMIN')")
    @ApiResponse(responseCode = "201", description = "Final fare stored",
            content = @Content(schema = @Schema(implementation = FareRecordResponse.class)))
    @ApiResponse(responseCode = "200", description = "Final fare already existed for this ride - returned unchanged",
            content = @Content(schema = @Schema(implementation = FareRecordResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed or unknown vehicle type")
    @ApiResponse(responseCode = "403", description = "Caller is neither a DRIVER nor an ADMIN")
    public ResponseEntity<FareRecordResponse> finalizeFare(@Valid @RequestBody FinalizeFareRequest request) {
        FareService.FinalizeResult result = fareService.finalizeFare(
                request.getRideId(), request.getPassengerId(),
                request.getDistanceKm(), request.getDurationMin(), request.getVehicleType());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(FareRecordResponse.from(result.record()));
    }

    // Legacy interservice call: Ride Management invokes this when a ride is
    // marked COMPLETED. Finalizes the fare and records a simulated,
    // already-collected payment (status COMPLETED).
    @PostMapping("/api/payments/finalize")
    @PreAuthorize("hasAuthority('DRIVER') or hasAuthority('ADMIN')")
    @ApiResponse(responseCode = "201", description = "Payment recorded",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON")
    @ApiResponse(responseCode = "403", description = "Caller is neither a DRIVER nor an ADMIN")
    @ApiResponse(responseCode = "409", description = "A payment already exists for this ride")
    public ResponseEntity<PaymentResponse> finalizePayment(@Valid @RequestBody FinalFareRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.finalizeAndCharge(request));
    }

    @PostMapping("/api/payments")
    @PreAuthorize("hasAuthority('PASSENGER')")
    @ApiResponse(responseCode = "201", description = "Payment succeeded (status PAID)",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed, or CARD without cardLast4")
    @ApiResponse(responseCode = "402", description = "Card declined - attempt saved as FAILED; retrying is allowed")
    @ApiResponse(responseCode = "403", description = "Caller is not a PASSENGER, or not this ride's passenger")
    @ApiResponse(responseCode = "409", description = "Fare not finalized yet, or ride already paid")
    public ResponseEntity<PaymentResponse> pay(@Valid @RequestBody PaymentRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.pay(request, authentication.getName(), isAdmin(authentication)));
    }

    @GetMapping("/api/payments/{id}")
    @ApiResponse(responseCode = "200", description = "Payment found",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
    @ApiResponse(responseCode = "404", description = "No payment with this id")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String id) {
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    @GetMapping("/api/payments/ride/{rideId}")
    @ApiResponse(responseCode = "200", description = "Every payment attempt for the ride, oldest first")
    @ApiResponse(responseCode = "404", description = "No payment for this rideId")
    public ResponseEntity<List<PaymentResponse>> getPaymentsForRide(@PathVariable String rideId) {
        return ResponseEntity.ok(paymentService.getPaymentsForRide(rideId));
    }

    @GetMapping("/api/payments/{rideId}/status")
    @ApiResponse(responseCode = "200", description = "The ride's paid payment, or its latest attempt",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class)))
    @ApiResponse(responseCode = "404", description = "No payment for this rideId")
    public ResponseEntity<PaymentResponse> getPaymentStatus(@PathVariable String rideId) {
        return ResponseEntity.ok(paymentService.getPaymentStatusForRide(rideId));
    }

    // {id} is a payment id. The original form of this path took a ride id,
    // which still works: if no payment has this id it is treated as one.
    @GetMapping("/api/payments/{id}/receipt")
    @ApiResponse(responseCode = "200", description = "Receipt with receipt number and fare breakdown",
            content = @Content(schema = @Schema(implementation = ReceiptResponse.class)))
    @ApiResponse(responseCode = "403", description = "Caller is neither the payer nor an ADMIN")
    @ApiResponse(responseCode = "404", description = "No paid payment for this payment id or ride id")
    public ResponseEntity<ReceiptResponse> getReceipt(@PathVariable String id, Authentication authentication) {
        return ResponseEntity.ok(paymentService.getReceipt(id, authentication.getName(), isAdmin(authentication)));
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ADMIN".equals(authority.getAuthority()));
    }
}
