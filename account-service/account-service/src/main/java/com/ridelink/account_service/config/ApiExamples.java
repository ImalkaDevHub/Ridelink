package com.ridelink.account_service.config;

// JSON examples shown in Swagger UI, referenced from the controller's
// @ExampleObject annotations. Kept here so the controller stays readable.
public final class ApiExamples {

    public static final String REGISTER_REQUEST = """
            {"name": "Nimal Perera", "email": "nimal@example.com", "password": "secret123", "role": "PASSENGER"}""";

    public static final String LOGIN_REQUEST = """
            {"email": "nimal@example.com", "password": "secret123"}""";

    public static final String PROFILE_UPDATE_REQUEST = """
            {"name": "Nimal P. Perera", "email": "nimal.perera@example.com"}""";

    public static final String PASSWORD_CHANGE_REQUEST = """
            {"currentPassword": "secret123", "newPassword": "newSecret456"}""";

    public static final String AUTH_RESPONSE = """
            {"token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI2NmY...", "id": "66f1c0a2e4b0a1b2c3d4e5f6",
             "name": "Nimal Perera", "role": "PASSENGER"}""";

    public static final String ACCOUNT_RESPONSE = """
            {"id": "66f1c0a2e4b0a1b2c3d4e5f6", "name": "Nimal Perera", "email": "nimal@example.com",
             "role": "PASSENGER", "status": "ACTIVE"}""";

    public static final String ACCOUNT_PAGE = """
            {"content": [{"id": "66f1c0a2e4b0a1b2c3d4e5f6", "name": "Nimal Perera", "email": "nimal@example.com",
                          "role": "PASSENGER", "status": "ACTIVE"}],
             "page": 0, "size": 20, "totalElements": 1, "totalPages": 1}""";

    public static final String PASSWORD_UPDATED = """
            {"message": "Password updated"}""";

    public static final String VALIDATION_ERROR = """
            {"code": "VALIDATION_ERROR", "message": "email: Email must be valid",
             "details": {"email": "Email must be valid"}}""";

    public static final String INVALID_ROLE = """
            {"code": "INVALID_ROLE", "message": "Role must be one of PASSENGER, DRIVER."}""";

    public static final String INVALID_STATUS = """
            {"code": "INVALID_STATUS", "message": "Status must be one of ACTIVE, SUSPENDED."}""";

    public static final String EMAIL_ALREADY_EXISTS = """
            {"code": "EMAIL_ALREADY_EXISTS", "message": "An account with this email already exists."}""";

    public static final String INVALID_CREDENTIALS = """
            {"code": "INVALID_CREDENTIALS", "message": "Invalid email or password."}""";

    public static final String ACCOUNT_SUSPENDED = """
            {"code": "ACCOUNT_SUSPENDED", "message": "This account has been suspended."}""";

    public static final String WRONG_PASSWORD = """
            {"code": "WRONG_PASSWORD", "message": "Current password is incorrect."}""";

    public static final String SAME_PASSWORD = """
            {"code": "SAME_PASSWORD", "message": "New password must be different from the current password."}""";

    public static final String UNAUTHORIZED = """
            {"code": "UNAUTHORIZED", "message": "A valid access token is required."}""";

    public static final String FORBIDDEN = """
            {"code": "FORBIDDEN", "message": "You do not have permission to perform this action."}""";

    public static final String ACCOUNT_NOT_FOUND = """
            {"code": "ACCOUNT_NOT_FOUND", "message": "Account not found with id: 66f1c0a2e4b0a1b2c3d4e5f6"}""";

    private ApiExamples() {
    }
}
