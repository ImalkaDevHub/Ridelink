package com.ridelink.driver_service.config;

import com.mongodb.client.result.UpdateResult;
import com.ridelink.driver_service.model.Driver;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverVersionBackfillTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void setsVersionZeroOnlyWhereItIsMissing() {
        when(mongoTemplate.updateMulti(any(Query.class), any(UpdateDefinition.class), eq(Driver.class)))
                .thenReturn(UpdateResult.acknowledged(5, 5L, null));

        new DriverVersionBackfill(mongoTemplate, true).run();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongoTemplate).updateMulti(query.capture(), update.capture(), eq(Driver.class));
        assertThat(query.getValue().getQueryObject().toJson()).isEqualTo("{\"version\": {\"$exists\": false}}");
        assertThat(((Document) update.getValue().getUpdateObject().get("$set")).get("version")).isEqualTo(0L);
    }

    @Test
    void doesNothingWhenDisabled() {
        new DriverVersionBackfill(mongoTemplate, false).run();

        verifyNoInteractions(mongoTemplate);
    }
}
