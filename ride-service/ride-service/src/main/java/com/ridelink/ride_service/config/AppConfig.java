package com.ridelink.ride_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AppConfig {

    // Every call to Driver and Fare & Payment Service goes through this, so
    // a slow or unreachable service fails fast (and becomes a 503) instead
    // of hanging the request.
    @Bean
    public RestTemplate restTemplate(@Value("${ride.http.connect-timeout:2s}") Duration connectTimeout,
                                     @Value("${ride.http.read-timeout:3s}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return new RestTemplate(requestFactory);
    }
}
