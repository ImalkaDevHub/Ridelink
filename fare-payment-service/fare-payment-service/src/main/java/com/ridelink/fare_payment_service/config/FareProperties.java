package com.ridelink.fare_payment_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Fare rule configuration, bound from the fare.* properties. See
// util.FareCalculator for the formula these values feed into.
@ConfigurationProperties(prefix = "fare")
public class FareProperties {

    private String currency = "LKR";
    private BigDecimal baseFare = new BigDecimal("300");
    private BigDecimal perKm = new BigDecimal("120");
    private BigDecimal perMin = new BigDecimal("10");
    private BigDecimal minimumFare = new BigDecimal("500");
    private BigDecimal averageSpeedKmh = new BigDecimal("25");
    private Map<String, BigDecimal> multipliers = new LinkedHashMap<>();

    // Vehicle type lookup is case-insensitive.
    public Optional<BigDecimal> multiplierFor(String vehicleType) {
        if (vehicleType == null) {
            return Optional.empty();
        }
        String key = vehicleType.trim().toUpperCase(Locale.ROOT);
        return multipliers.entrySet().stream()
                .filter(entry -> entry.getKey().toUpperCase(Locale.ROOT).equals(key))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getBaseFare() {
        return baseFare;
    }

    public void setBaseFare(BigDecimal baseFare) {
        this.baseFare = baseFare;
    }

    public BigDecimal getPerKm() {
        return perKm;
    }

    public void setPerKm(BigDecimal perKm) {
        this.perKm = perKm;
    }

    public BigDecimal getPerMin() {
        return perMin;
    }

    public void setPerMin(BigDecimal perMin) {
        this.perMin = perMin;
    }

    public BigDecimal getMinimumFare() {
        return minimumFare;
    }

    public void setMinimumFare(BigDecimal minimumFare) {
        this.minimumFare = minimumFare;
    }

    public BigDecimal getAverageSpeedKmh() {
        return averageSpeedKmh;
    }

    public void setAverageSpeedKmh(BigDecimal averageSpeedKmh) {
        this.averageSpeedKmh = averageSpeedKmh;
    }

    public Map<String, BigDecimal> getMultipliers() {
        return multipliers;
    }

    public void setMultipliers(Map<String, BigDecimal> multipliers) {
        this.multipliers = multipliers;
    }
}
