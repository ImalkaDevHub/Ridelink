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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.ridelink.fare_payment_service.config.ApiExamples.*;

// Error responses for every endpoint here come from GlobalExceptionHandler,
// which maps the service exceptions to {"code", "message"} bodies.
@RestController
@Tag(name = "Fares & Payments",
        description = "Fare estimates, final fares (LKR, BigDecimal), simulated payments and receipts. "
                + "fare = max(minimumFare, baseFare + perKm*km + perMin*min) * vehicle multiplier")
public class FareController {

    private static final String JSON = "application/json";

    @Autowired
    private FareService fareService;

    @Autowired
    private PaymentService paymentService;

    // Public - fare estimates are often shown pre-login in real apps.
    @PostMapping("/api/fares/estimate")
    @SecurityRequirements
    @Operation(summary = "Estimate a fare",
            description = "Public. Distance comes from distanceKm, else the four coordinates (Haversine), else known "
                    + "place names. Without durationMin the duration is estimated at 25 km/h.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = JSON, examples = {
                    @ExampleObject(name = "with distance", value = ESTIMATE_REQUEST_DISTANCE),
                    @ExampleObject(name = "known place names", value = ESTIMATE_REQUEST_PLACES),
                    @ExampleObject(name = "coordinates", value = ESTIMATE_REQUEST_COORDINATES)})))
    @ApiResponse(responseCode = "200", description = "Estimate calculated",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = FareEstimateResponse.class),
                    examples = @ExampleObject(value = ESTIMATE_RESPONSE)))
    @ApiResponse(responseCode = "400", description = "Validation failed, unknown vehicle type, or distance could not be resolved",
            content = @Content(mediaType = JSON, examples = {
                    @ExampleObject(name = "vehicle type", value = VALIDATION_ERROR),
                    @ExampleObject(name = "distance", value = DISTANCE_UNRESOLVED)}))
    public ResponseEntity<FareEstimateResponse> estimate(@Valid @RequestBody FareEstimateRequest request) {
        return ResponseEntity.ok(fareService.estimate(request));
    }

    // Called when a ride is completed (by ride-service with the driver's
    // token, or by a driver/admin directly). Idempotent per rideId.
    @PostMapping("/api/fares/final")
    @PreAuthorize("hasAuthority('DRIVER') or hasAuthority('ADMIN')")
    @Operation(summary = "Store a ride's final fare",
            description = "DRIVER or ADMIN (ride-service calls this on completion with the driver's token). Idempotent "
                    + "per rideId: the first call stores the fare, later calls return it unchanged.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = JSON, examples = @ExampleObject(value = FINAL_REQUEST))))
    @ApiResponse(responseCode = "201", description = "Final fare stored",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = FareRecordResponse.class),
                    examples = @ExampleObject(value = FARE_RECORD)))
    @ApiResponse(responseCode = "200", description = "Final fare already existed for this ride - returned unchanged",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = FareRecordResponse.class),
                    examples = @ExampleObject(value = FARE_RECORD)))
    @ApiResponse(responseCode = "400", description = "Validation failed or unknown vehicle type",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = VALIDATION_ERROR)))
    @ApiResponse(responseCode = "401", description = "No valid access token",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = UNAUTHORIZED)))
    @ApiResponse(responseCode = "403", description = "Caller is neither a DRIVER nor an ADMIN",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
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
    @Operation(summary = "Finalize the fare and record a collected payment (legacy, internal)",
            description = "DRIVER or ADMIN. Used by ride-service on completion while "
                    + "ride.payment.auto-charge-on-complete is on: stores the final fare and a COMPLETED payment for it.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = JSON, examples = @ExampleObject(value = LEGACY_FINALIZE_REQUEST))))
    @ApiResponse(responseCode = "201", description = "Payment recorded",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = PaymentResponse.class),
                    examples = @ExampleObject(value = LEGACY_PAYMENT)))
    @ApiResponse(responseCode = "400", description = "Validation failed or malformed JSON",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = VALIDATION_ERROR)))
    @ApiResponse(responseCode = "403", description = "Caller is neither a DRIVER nor an ADMIN",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    @ApiResponse(responseCode = "409", description = "A payment already exists for this ride",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = DUPLICATE_PAYMENT)))
    public ResponseEntity<PaymentResponse> finalizePayment(@Valid @RequestBody FinalFareRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.finalizeAndCharge(request));
    }

    @PostMapping("/api/payments")
    @PreAuthorize("hasAuthority('PASSENGER')")
    @Operation(summary = "Pay for a ride",
            description = "PASSENGER only. The amount always comes from the ride's stored final fare. Simulated: a card "
                    + "ending in 0000 is declined (the attempt is saved as FAILED and can be retried). One paid payment per ride.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = JSON, examples = {
                    @ExampleObject(name = "card", value = PAY_CARD_REQUEST),
                    @ExampleObject(name = "cash", value = PAY_CASH_REQUEST),
                    @ExampleObject(name = "declined card", value = PAY_DECLINED_REQUEST)})))
    @ApiResponse(responseCode = "201", description = "Payment succeeded (status PAID)",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = PaymentResponse.class),
                    examples = @ExampleObject(value = PAYMENT)))
    @ApiResponse(responseCode = "400", description = "Validation failed, or CARD without cardLast4",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = CARD_REQUIRED)))
    @ApiResponse(responseCode = "401", description = "No valid access token",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = UNAUTHORIZED)))
    @ApiResponse(responseCode = "402", description = "Card declined - attempt saved as FAILED; retrying is allowed",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = PAYMENT_FAILED)))
    @ApiResponse(responseCode = "403", description = "Caller is not a PASSENGER, or not this ride's passenger",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = FORBIDDEN)))
    @ApiResponse(responseCode = "409", description = "Fare not finalized yet, or ride already paid",
            content = @Content(mediaType = JSON, examples = {
                    @ExampleObject(name = "no final fare", value = FARE_NOT_FINALIZED),
                    @ExampleObject(name = "already paid", value = PAYMENT_ALREADY_EXISTS)}))
    public ResponseEntity<PaymentResponse> pay(@Valid @RequestBody PaymentRequest request,
                                               @Parameter(hidden = true) Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.pay(request, authentication.getName(), isAdmin(authentication)));
    }

    @GetMapping("/api/payments/{id}")
    @Operation(summary = "Get a payment", description = "Any authenticated user.")
    @ApiResponse(responseCode = "200", description = "Payment found",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = PaymentResponse.class),
                    examples = @ExampleObject(value = PAYMENT)))
    @ApiResponse(responseCode = "401", description = "No valid access token",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = UNAUTHORIZED)))
    @ApiResponse(responseCode = "404", description = "No payment with this id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = PAYMENT_NOT_FOUND)))
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String id) {
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    @GetMapping("/api/payments/ride/{rideId}")
    @Operation(summary = "List a ride's payment attempts", description = "Any authenticated user. Oldest first, including FAILED attempts.")
    @ApiResponse(responseCode = "200", description = "Every payment attempt for the ride",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = PAYMENT_LIST)))
    @ApiResponse(responseCode = "404", description = "No payment for this rideId",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = PAYMENT_NOT_FOUND)))
    public ResponseEntity<List<PaymentResponse>> getPaymentsForRide(@PathVariable String rideId) {
        return ResponseEntity.ok(paymentService.getPaymentsForRide(rideId));
    }

    @GetMapping("/api/payments/{rideId}/status")
    @Operation(summary = "A ride's payment status", description = "Any authenticated user. The ride's paid payment, or its latest attempt.")
    @ApiResponse(responseCode = "200", description = "The ride's paid payment, or its latest attempt",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = PaymentResponse.class),
                    examples = @ExampleObject(value = PAYMENT)))
    @ApiResponse(responseCode = "404", description = "No payment for this rideId",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = PAYMENT_NOT_FOUND)))
    public ResponseEntity<PaymentResponse> getPaymentStatus(@PathVariable String rideId) {
        return ResponseEntity.ok(paymentService.getPaymentStatusForRide(rideId));
    }

    // {id} is a payment id. The original form of this path took a ride id,
    // which still works: if no payment has this id it is treated as one.
    @GetMapping("/api/payments/{id}/receipt")
    @Operation(summary = "Get a receipt",
            description = "The payer (or, for payments recorded on completion, the ride's passenger) or an ADMIN. "
                    + "{id} is a payment id; a ride id also works (the original form of this path).")
    @ApiResponse(responseCode = "200", description = "Receipt with receipt number and fare breakdown",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ReceiptResponse.class),
                    examples = @ExampleObject(value = RECEIPT)))
    @ApiResponse(responseCode = "401", description = "No valid access token",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = UNAUTHORIZED)))
    @ApiResponse(responseCode = "403", description = "Caller is neither the payer nor an ADMIN",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = NOT_PAYER)))
    @ApiResponse(responseCode = "404", description = "No paid payment for this payment id or ride id",
            content = @Content(mediaType = JSON, examples = @ExampleObject(value = PAYMENT_NOT_FOUND)))
    public ResponseEntity<ReceiptResponse> getReceipt(
            @Parameter(description = "Payment id (or ride id)", example = "66f1c5a3e4b0a1b2c3d4e5fb") @PathVariable String id,
            @Parameter(hidden = true) Authentication authentication) {
        return ResponseEntity.ok(paymentService.getReceipt(id, authentication.getName(), isAdmin(authentication)));
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ADMIN".equals(authority.getAuthority()));
    }
}
