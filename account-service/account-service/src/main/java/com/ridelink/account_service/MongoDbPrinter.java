package com.ridelink.account_service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class MongoDbPrinter implements CommandLineRunner {
    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("==========================================");
        System.out.println("Connected to MongoDB Database: " + mongoTemplate.getDb().getName());
        System.out.println("==========================================");
    }
}
