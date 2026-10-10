package com.ridelink.driver_service.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Bound directly from the request body on creation (see DriverController) -
// this service has no separate DTO layer, so the validation constraints
// that would norrmally live on a request DTO live here instead.
//
// All fields added after the first release (vehicleModel, vehicleColor,
// seats, version) are nullable so existing documents keep loading.
@Document(collection = "drivers")
public class Driver {

    // Letters/digits in groups separated by a hyphen or space, with at least
    // one digit - e.g. "CAB-1234", "WP CAB-1234", "300-1234", "BAC - 1099".
    public static final String VEHICLE_NUMBER_PATTERN = "^(?=.*\\d)[A-Za-z0-9]+(\\s*[- ]\\s*[A-Za-z0-9]+)*$";
    public static final String VEHICLE_NUMBER_MESSAGE = "vehicleNumber must be letters/digits separated by '-' or spaces, with at least one digit";

    @Id
    private String id;

    private String accountId;

    @NotBlank(message = "name is required")
    private String name;

    // sparse: documents created before email was required have no email,
    // and a non-sparse unique index would treat all of them as duplicates.
    @Indexed(unique = true, sparse = true)
    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    private String email;

    @Indexed(unique = true, sparse = true)
    @NotBlank(message = "vehicleNumber is required")
    @Size(min = 2, max = 15, message = "vehicleNumber must be 2-15 characters")
    @Pattern(regexp = VEHICLE_NUMBER_PATTERN, message = VEHICLE_NUMBER_MESSAGE)
    private String vehicleNumber;

    @NotBlank(message = "vehicleType is required")
    @Pattern(regexp = VehicleType.PATTERN, message = VehicleType.MESSAGE)
    private String vehicleType;

    @Size(max = 40, message = "vehicleModel must be at most 40 characters")
    private String vehicleModel;

    @Size(max = 30, message = "vehicleColor must be at most 30 characters")
    private String vehicleColor;

    @Min(value = 1, message = "seats must be at least 1")
    @Max(value = 20, message = "seats must be at most 20")
    private Integer seats;

    private boolean available = false;

    @NotBlank(message = "serviceArea is required")
    private String serviceArea;

    @DecimalMin(value = "-90.0", message = "currentLat must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "currentLat must be between -90 and 90")
    private Double currentLat;

    @DecimalMin(value = "-180.0", message = "currentLng must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "currentLng must be between -180 and 180")
    private Double currentLng;

    // Optimistic locking for read-modify-write updates. Documents created
    // before this field existed are backfilled to 0 at startup (see
    // config.DriverVersionBackfill).
    @Version
    private Long version;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getVehicleModel() {
        return vehicleModel;
    }

    public void setVehicleModel(String vehicleModel) {
        this.vehicleModel = vehicleModel;
    }

    public String getVehicleColor() {
        return vehicleColor;
    }

    public void setVehicleColor(String vehicleColor) {
        this.vehicleColor = vehicleColor;
    }

    public Integer getSeats() {
        return seats;
    }

    public void setSeats(Integer seats) {
        this.seats = seats;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getServiceArea() {
        return serviceArea;
    }

    public void setServiceArea(String serviceArea) {
        this.serviceArea = serviceArea;
    }

    public Double getCurrentLat() {
        return currentLat;
    }

    public void setCurrentLat(Double currentLat) {
        this.currentLat = currentLat;
    }

    public Double getCurrentLng() {
        return currentLng;
    }

    public void setCurrentLng(Double currentLng) {
        this.currentLng = currentLng;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
