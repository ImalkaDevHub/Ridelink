package com.ridelink.account_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Index creation and the admin seeder both talk to MongoDB at startup, so
// they are switched off here: this test must pass without a database.
@SpringBootTest(properties = {
		"spring.data.mongodb.auto-index-creation=false",
		"app.admin.seed-enabled=false"
})
class AccountServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
