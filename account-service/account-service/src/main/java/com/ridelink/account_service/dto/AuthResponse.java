package com.ridelink.account_service.dto;

public class AuthResponse {

    private String token;
    private String id;
    private String name;
    private String role;

    public AuthResponse(String token, String id, String name, String role) {
        this.token = token;
        this.id = id;
        this.name = name;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
