package com.ridelink.driver_service.config;

// JSON examples shown in Swagger UI, referenced from the controller's
// @ExampleObject annotations. Kept here so the controller stays readable.
public final class ApiExamples {

    public static final String CREATE_REQUEST = """
            {"name": "Sunil Silva", "email": "sunil@example.com", "vehicleNumber": "CAB-1024",
             "vehicleType": "SEDAN", "vehicleModel": "Toyota Axio", "vehicleColor": "White", "seats": 4,
             "serviceArea": "Malabe"}""";

    public static final String UPDATE_REQUEST = """
            {"vehicleColor": "Silver", "seats": 5}""";

    public static final String LOCATION_REQUEST = """
            {"lat": 6.9061, "lng": 79.9696}""";

    public static final String SERVICE_AREA_REQUEST = """
            {"serviceArea": "Kaduwela"}""";

    public static final String DRIVER = """
            {"id": "66f1c1b7e4b0a1b2c3d4e5f7", "accountId": "66f1c0a2e4b0a1b2c3d4e5f6", "name": "Sunil Silva",
             "email": "sunil@example.com", "vehicleNumber": "CAB-1024", "vehicleType": "SEDAN",
             "vehicleModel": "Toyota Axio", "vehicleColor": "White", "seats": 4, "available": true,
             "serviceArea": "Malabe", "currentLat": 6.9061, "currentLng": 79.9696, "version": 3}""";

    public static final String DRIVER_RESERVED = """
            {"id": "66f1c1b7e4b0a1b2c3d4e5f7", "accountId": "66f1c0a2e4b0a1b2c3d4e5f6", "name": "Sunil Silva",
             "vehicleNumber": "CAB-1024", "vehicleType": "SEDAN", "available": false, "serviceArea": "Malabe",
             "version": 4}""";

    public static final String DRIVER_LIST = "[" + DRIVER + "]";

    public static final String VALIDATION_ERROR = """
            {"code": "VALIDATION_ERROR", "message": "vehicleType: vehicleType must be one of SEDAN, HATCHBACK, VAN, TUK, CAR, BIKE",
             "details": {"vehicleType": "vehicleType must be one of SEDAN, HATCHBACK, VAN, TUK, CAR, BIKE"}}""";

    public static final String LOCATION_VALIDATION_ERROR = """
            {"code": "VALIDATION_ERROR", "message": "lat: lat must be between -90 and 90",
             "details": {"lat": "lat must be between -90 and 90"}}""";

    public static final String UNAUTHORIZED = """
            {"code": "UNAUTHORIZED", "message": "A valid access token is required."}""";

    public static final String FORBIDDEN = """
            {"code": "FORBIDDEN", "message": "You do not have permission to perform this action."}""";

    public static final String DRIVER_NOT_FOUND = """
            {"code": "DRIVER_NOT_FOUND", "message": "Driver not found with id: 66f1c1b7e4b0a1b2c3d4e5f7"}""";

    public static final String VEHICLE_ALREADY_EXISTS = """
            {"code": "VEHICLE_ALREADY_EXISTS", "message": "A driver with vehicle number CAB-1024 already exists."}""";

    public static final String NO_DRIVER_AVAILABLE = """
            {"code": "NO_DRIVER_AVAILABLE", "message": "No driver is available right now."}""";

    private ApiExamples() {
    }
}
