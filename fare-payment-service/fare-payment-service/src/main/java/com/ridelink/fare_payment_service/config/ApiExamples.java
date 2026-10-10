package com.ridelink.fare_payment_service.config;

// JSON examples shown in Swagger UI, referenced from the controller's
// @ExampleObject annotations. Kept here so the controller stays readable.
public final class ApiExamples {

    public static final String ESTIMATE_REQUEST_DISTANCE = """
            {"pickupLocation": "SLIIT", "destinationLocation": "Kaduwela", "distanceKm": 5.0, "durationMin": 12.0,
             "vehicleType": "SEDAN"}""";

    public static final String ESTIMATE_REQUEST_PLACES = """
            {"pickupLocation": "SLIIT", "destinationLocation": "Colombo Fort", "vehicleType": "VAN"}""";

    public static final String ESTIMATE_REQUEST_COORDINATES = """
            {"pickupLocation": "Home", "destinationLocation": "Office", "vehicleType": "TUK",
             "pickupLat": 6.9147, "pickupLng": 79.9729, "destinationLat": 6.9344, "destinationLng": 79.8428,
             "durationMin": 30}""";

    public static final String ESTIMATE_RESPONSE = """
            {"pickupLocation": "SLIIT", "destinationLocation": "Kaduwela", "distanceKm": 5.0, "durationMin": 12.0,
             "baseFare": 300, "perKmRate": 120, "perMinRate": 10, "estimatedFare": 1020.00, "vehicleType": "SEDAN",
             "subtotal": 1020.00, "minimumFare": 500, "minimumApplied": false, "vehicleMultiplier": 1.0,
             "currency": "LKR", "distanceSource": "PROVIDED", "durationEstimated": false,
             "breakdown": "max(500.00, 300.00 + 120.00/km x 5.00 km + 10.00/min x 12.00 min = 1020.00) x 1.0 (SEDAN) = 1020.00 LKR"}""";

    public static final String FINAL_REQUEST = """
            {"rideId": "66f1c3d9e4b0a1b2c3d4e5f8", "distanceKm": 5.2, "durationMin": 14, "vehicleType": "SEDAN",
             "passengerId": "66f1c0a2e4b0a1b2c3d4e5f6"}""";

    public static final String FARE_RECORD = """
            {"id": "66f1c4e1e4b0a1b2c3d4e5fa", "rideId": "66f1c3d9e4b0a1b2c3d4e5f8",
             "passengerId": "66f1c0a2e4b0a1b2c3d4e5f6", "vehicleType": "SEDAN", "distanceKm": 5.2, "durationMin": 14,
             "baseFare": 300, "perKmRate": 120, "perMinRate": 10, "distanceCharge": 624.00, "timeCharge": 140.00,
             "subtotal": 1064.00, "minimumFare": 500, "minimumApplied": false, "multiplier": 1.0,
             "totalFare": 1064.00, "currency": "LKR",
             "breakdown": "max(500.00, 300.00 + 120.00/km x 5.20 km + 10.00/min x 14.00 min = 1064.00) x 1.0 (SEDAN) = 1064.00 LKR",
             "createdAt": "2026-10-10T08:20:00Z"}""";

    public static final String LEGACY_FINALIZE_REQUEST = """
            {"rideId": "66f1c3d9e4b0a1b2c3d4e5f8", "passengerId": "66f1c0a2e4b0a1b2c3d4e5f6",
             "distanceKm": 5.2, "durationMin": 14, "vehicleType": "CAR"}""";

    public static final String PAY_CARD_REQUEST = """
            {"rideId": "66f1c3d9e4b0a1b2c3d4e5f8", "method": "CARD", "cardLast4": "4242"}""";

    public static final String PAY_CASH_REQUEST = """
            {"rideId": "66f1c3d9e4b0a1b2c3d4e5f8", "method": "CASH"}""";

    public static final String PAY_DECLINED_REQUEST = """
            {"rideId": "66f1c3d9e4b0a1b2c3d4e5f8", "method": "CARD", "cardLast4": "0000"}""";

    public static final String PAYMENT = """
            {"id": "66f1c5a3e4b0a1b2c3d4e5fb", "rideId": "66f1c3d9e4b0a1b2c3d4e5f8", "finalFare": 1064.00,
             "status": "PAID", "paymentMethod": "CARD", "createdAt": "2026-10-10T08:25:00Z",
             "completedAt": "2026-10-10T08:25:01Z", "method": "CARD", "cardLast4": "4242", "currency": "LKR",
             "passengerId": "66f1c0a2e4b0a1b2c3d4e5f6", "payerId": "66f1c0a2e4b0a1b2c3d4e5f6",
             "receiptNumber": "RL-20261010-D4E5FB", "failureReason": null}""";

    public static final String LEGACY_PAYMENT = """
            {"id": "66f1c5a3e4b0a1b2c3d4e5fb", "rideId": "66f1c3d9e4b0a1b2c3d4e5f8", "finalFare": 1064.00,
             "status": "COMPLETED", "paymentMethod": "SIMULATED_CARD", "createdAt": "2026-10-10T08:20:00Z",
             "completedAt": "2026-10-10T08:20:00Z", "currency": "LKR", "receiptNumber": "RL-20261010-D4E5F8"}""";

    public static final String PAYMENT_LIST = """
            [{"id": "66f1c59ce4b0a1b2c3d4e5fc", "rideId": "66f1c3d9e4b0a1b2c3d4e5f8", "finalFare": 1064.00,
              "status": "FAILED", "method": "CARD", "cardLast4": "0000",
              "failureReason": "Card declined (simulated: cards ending in 0000 always fail)."},
             {"id": "66f1c5a3e4b0a1b2c3d4e5fb", "rideId": "66f1c3d9e4b0a1b2c3d4e5f8", "finalFare": 1064.00,
              "status": "PAID", "method": "CARD", "cardLast4": "4242", "receiptNumber": "RL-20261010-D4E5FB"}]""";

    public static final String RECEIPT = """
            {"rideId": "66f1c3d9e4b0a1b2c3d4e5f8", "passengerId": "66f1c0a2e4b0a1b2c3d4e5f6", "finalFare": 1064.00,
             "distanceKm": 5.2, "durationMin": 14.0, "paymentMethod": "CARD", "status": "PAID",
             "completedAt": "2026-10-10T08:25:01Z",
             "breakdown": "max(500.00, 300.00 + 120.00/km x 5.20 km + 10.00/min x 14.00 min = 1064.00) x 1.0 (SEDAN) = 1064.00 LKR",
             "receiptNumber": "RL-20261010-D4E5FB", "paymentId": "66f1c5a3e4b0a1b2c3d4e5fb", "method": "CARD",
             "currency": "LKR", "timestamp": "2026-10-10T08:25:01Z",
             "fareBreakdown": {"rideId": "66f1c3d9e4b0a1b2c3d4e5f8", "totalFare": 1064.00, "multiplier": 1.0}}""";

    public static final String VALIDATION_ERROR = """
            {"code": "VALIDATION_ERROR", "message": "vehicleType: vehicleType must be one of SEDAN, HATCHBACK, VAN, TUK, CAR, BIKE",
             "details": {"vehicleType": "vehicleType must be one of SEDAN, HATCHBACK, VAN, TUK, CAR, BIKE"}}""";

    public static final String CARD_REQUIRED = """
            {"code": "VALIDATION_ERROR", "message": "cardLast4: cardLast4 is required for CARD payments.",
             "details": {"cardLast4": "cardLast4 is required for CARD payments."}}""";

    public static final String DISTANCE_UNRESOLVED = """
            {"code": "DISTANCE_UNRESOLVED", "message": "Provide distanceKm, all four coordinates, or known place names (...)."}""";

    public static final String UNAUTHORIZED = """
            {"code": "UNAUTHORIZED", "message": "A valid access token is required."}""";

    public static final String FORBIDDEN = """
            {"code": "FORBIDDEN", "message": "You do not have permission to perform this action."}""";

    public static final String NOT_PAYER = """
            {"code": "FORBIDDEN", "message": "Only the payer can view this receipt."}""";

    public static final String PAYMENT_FAILED = """
            {"code": "PAYMENT_FAILED", "message": "Card declined (simulated: cards ending in 0000 always fail).",
             "details": {"paymentId": "66f1c59ce4b0a1b2c3d4e5fc", "status": "FAILED"}}""";

    public static final String FARE_NOT_FINALIZED = """
            {"code": "FARE_NOT_FINALIZED", "message": "No final fare exists yet for ride: 66f1c3d9e4b0a1b2c3d4e5f8"}""";

    public static final String PAYMENT_ALREADY_EXISTS = """
            {"code": "PAYMENT_ALREADY_EXISTS", "message": "Ride 66f1c3d9e4b0a1b2c3d4e5f8 has already been paid."}""";

    public static final String DUPLICATE_PAYMENT = """
            {"code": "DUPLICATE_PAYMENT", "message": "A payment already exists for ride: 66f1c3d9e4b0a1b2c3d4e5f8"}""";

    public static final String PAYMENT_NOT_FOUND = """
            {"code": "PAYMENT_NOT_FOUND", "message": "No payment found for ride: 66f1c3d9e4b0a1b2c3d4e5f8"}""";

    private ApiExamples() {
    }
}
