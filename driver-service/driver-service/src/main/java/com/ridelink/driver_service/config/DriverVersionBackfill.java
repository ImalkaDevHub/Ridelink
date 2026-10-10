package com.ridelink.driver_service.config;

import com.ridelink.driver_service.model.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

// Driver gained an @Version field after documents already existed. Spring
// Data treats a null version as "new entity" and would try to INSERT those
// documents on save (failing on the duplicate _id), so give every
// pre-existing document version 0 once at startup. Idempotent.
@Component
public class DriverVersionBackfill implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DriverVersionBackfill.class);

    private final MongoTemplate mongoTemplate;
    private final boolean enabled;

    public DriverVersionBackfill(MongoTemplate mongoTemplate,
                                 @Value("${app.driver.version-backfill-enabled:true}") boolean enabled) {
        this.mongoTemplate = mongoTemplate;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }

        long updated = mongoTemplate.updateMulti(
                new Query(Criteria.where("version").exists(false)),
                new Update().set("version", 0L),
                Driver.class).getModifiedCount();

        if (updated > 0) {
            log.info("Backfilled version=0 on {} existing driver document(s).", updated);
        }
    }
}
