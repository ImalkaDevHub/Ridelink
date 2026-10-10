package com.ridelink.fare_payment_service;

import com.ridelink.fare_payment_service.util.FareCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// Index creation talks to MongoDB at startup, so it is switched off here:
// this test must pass without a database.
@SpringBootTest(properties = "spring.data.mongodb.auto-index-creation=false")
class FarePaymentServiceApplicationTests {

	@Autowired
	private FareCalculator fareCalculator;

	@Test
	void contextLoads() {
	}

	// Proves the fare.* properties in application.properties bind as documented.
	@Test
	void fareRuleIsConfiguredFromApplicationProperties() {
		assertThat(fareCalculator.calculate(new BigDecimal("5"), new BigDecimal("12"), "VAN").total())
				.isEqualByComparingTo("1530.00");
		assertThat(fareCalculator.supportedVehicleTypes())
				.containsExactlyInAnyOrder("SEDAN", "HATCHBACK", "VAN", "TUK", "CAR", "BIKE");
	}

}
