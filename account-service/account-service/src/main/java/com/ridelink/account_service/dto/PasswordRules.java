package com.ridelink.account_service.dto;

// Single definition of the password rule, shared by registration and
// password change. Max 72 because BCrypt only uses the first 72 bytes and
// Spring Security's encoder rejects anything longer.
public final class PasswordRules {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;
    public static final String PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).*$";
    public static final String PATTERN_MESSAGE = "Password must contain at least one letter and one digit";
    public static final String LENGTH_MESSAGE = "Password must be 8-72 characters";

    private PasswordRules() {
    }
}
