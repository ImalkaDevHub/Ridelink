package com.ridelink.fare_payment_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FarePaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FarePaymentServiceApplication.class, args);
	}

}
