package com.ridelink.ride_service.config;

// JSON examples shown in Swagger UI, referenced from the controller's
// @ExampleObject annotations. Kept here so the controller stays readable.
public final class ApiExamples {

    public static final String RIDE_REQUEST = """
            {"passengerName": "Nimal Perera", "pickup": "SLIIT", "destination": "Kaduwela",
             "vehicleType": "SEDAN", "distanceKm": 5.0, "durationMin": 12.0,
             "pickupLat": 6.9147, "pickupLng": 79.9729, "destinationLat": 6.9330, "destinationLng": 79.9840}""";

    public static final String CANCEL_REQUEST = """
            {"reason": "Plans changed"}""";

    public static final String COMPLETE_REQUEST = """
            {"distanceKm": 5.2, "durationMin": 14}""";

    public static final String RIDE_ASSIGNED = """
            {"id": "66f1c3d9e4b0a1b2c3d4e5f8", "passengerId": "66f1c0a2e4b0a1b2c3d4e5f6", "passengerName": "Nimal Perera",
             "pickup": "SLIIT", "destination": "Kaduwela", "vehicleType": "SEDAN",
             "driverId": "66f1c1b7e4b0a1b2c3d4e5f7", "driverAccountId": "66f1c0b5e4b0a1b2c3d4e5f9",
             "status": "ASSIGNED", "estimatedFare": 1020.00, "finalFare": null,
             "requestedAt": "2026-10-10T08:00:00Z", "acceptedAt": null, "startedAt": null,
             "completedAt": null, "cancelledAt": null, "cancelReason": null}""";

    public static final String RIDE_ACCEPTED = """
            {"id": "66f1c3d9e4b0a1b2c3d4e5f8", "status": "ACCEPTED", "driverAccountId": "66f1c0b5e4b0a1b2c3d4e5f9",
             "estimatedFare": 1020.00, "acceptedAt": "2026-10-10T08:01:00Z"}""";

    public static final String RIDE_IN_PROGRESS = """
            {"id": "66f1c3d9e4b0a1b2c3d4e5f8", "status": "IN_PROGRESS", "driverAccountId": "66f1c0b5e4b0a1b2c3d4e5f9",
             "estimatedFare": 1020.00, "acceptedAt": "2026-10-10T08:01:00Z", "startedAt": "2026-10-10T08:05:00Z"}""";

    public static final String RIDE_COMPLETED = """
            {"id": "66f1c3d9e4b0a1b2c3d4e5f8", "status": "COMPLETED", "estimatedFare": 1020.00, "finalFare": 1064.00,
             "distanceKm": 5.2, "durationMin": 14.0, "completedAt": "2026-10-10T08:20:00Z"}""";

    public static final String RIDE_CANCELLED = """
            {"id": "66f1c3d9e4b0a1b2c3d4e5f8", "status": "CANCELLED", "cancelReason": "Plans changed",
             "cancelledAt": "2026-10-10T08:02:00Z"}""";

    public static final String RIDE_LIST = "[" + RIDE_ASSIGNED + "]";

    public static final String VALIDATION_ERROR = """
            {"code": "VALIDATION_ERROR", "message": "pickup: pickup is required",
             "details": {"pickup": "pickup is required"}}""";

    public static final String STATUS_FILTER_ERROR = """
            {"code": "VALIDATION_ERROR", "message": "status: status must be one of [REQUESTED, ASSIGNED, ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED]",
             "details": {"status": "status must be one of [REQUESTED, ASSIGNED, ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED]"}}""";

    public static final String UNAUTHORIZED = """
            {"code": "UNAUTHORIZED", "message": "A valid access token is required."}""";

    public static final String FORBIDDEN = """
            {"code": "FORBIDDEN", "message": "You do not have permission to perform this action."}""";

    public static final String NOT_PARTICIPANT = """
            {"code": "FORBIDDEN", "message": "Only the ride's passenger or driver can view it."}""";

    public static final String NOT_ASSIGNED_DRIVER = """
            {"code": "FORBIDDEN", "message": "Only the driver assigned to this ride can do this."}""";

    public static final String RIDE_NOT_FOUND = """
            {"code": "RIDE_NOT_FOUND", "message": "Ride not found with id: 66f1c3d9e4b0a1b2c3d4e5f8"}""";

    public static final String INVALID_STATUS_TRANSITION = """
            {"code": "INVALID_STATUS_TRANSITION", "message": "Cannot change ride status from IN_PROGRESS to CANCELLED."}""";

    public static final String NO_DRIVER_AVAILABLE = """
            {"code": "NO_DRIVER_AVAILABLE", "message": "No driver is available right now."}""";

    public static final String DRIVER_SERVICE_UNAVAILABLE = """
            {"code": "DRIVER_SERVICE_UNAVAILABLE", "message": "A driver cannot be assigned now. Please retry."}""";

    public static final String FARE_SERVICE_UNAVAILABLE = """
            {"code": "FARE_SERVICE_UNAVAILABLE", "message": "The final fare cannot be calculated now. The ride was not completed - please retry."}""";

    public static final String PAYMENT_FAILED = """
            {"code": "PAYMENT_FAILED", "message": "Fare & payment service is unavailable"}""";

    private ApiExamples() {
    }
}
