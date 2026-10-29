package com.nazarukiv.aigamingclubmanagement.service;

import com.nazarukiv.aigamingclubmanagement.dto.PricingResponse;
import com.nazarukiv.aigamingclubmanagement.entity.GamingStation;
import com.nazarukiv.aigamingclubmanagement.enums.StationType;
import com.nazarukiv.aigamingclubmanagement.exception.StationNotFoundException;
import com.nazarukiv.aigamingclubmanagement.repository.GamingStationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PricingServiceIntegrationTests {

    @Autowired private PricingService service;
    @Autowired private GamingStationRepository stations;

    private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");

    @Test
    void pricesThreeSeededPcsForTwoHours() {
        List<Long> ids = stations.findByType(StationType.PC).stream().limit(3).map(GamingStation::getId).toList();

        PricingResponse result = service.calculatePrice(ids, START, START.plusSeconds(7200));

        assertThat(result.totalPrice()).isEqualTo(new BigDecimal("30.00"));
        assertThat(result.duration()).isEqualTo(Duration.ofHours(2));
        assertThat(result.currency()).isEqualTo("GBP");
    }

    @Test
    void pricesSeededPs5ForNinetyMinutes() {
        Long id = stations.findByType(StationType.PS5).getFirst().getId();

        assertThat(service.calculatePrice(List.of(id), START, START.plusSeconds(5400)).totalPrice())
                .isEqualTo(new BigDecimal("10.50"));
    }

    @Test
    void pricesMixedStationsAndPreservesRequestedIdOrder() {
        Long pcId = stations.findByType(StationType.PC).getFirst().getId();
        Long ps5Id = stations.findByType(StationType.PS5).getFirst().getId();
        List<Long> ids = List.of(ps5Id, pcId);

        PricingResponse result = service.calculatePrice(ids, START, START.plusSeconds(5400));

        assertThat(result.stationIds()).isEqualTo(ids);
        assertThat(result.totalPrice()).isEqualTo(new BigDecimal("18.00"));
        assertThat(result.duration()).isEqualTo(Duration.ofMinutes(90));
    }

    @Test
    void rejectsMissingStationAlongsideAnExistingStation() {
        Long id = stations.findByType(StationType.PC).getFirst().getId();

        assertThatThrownBy(() -> service.calculatePrice(List.of(id, Long.MAX_VALUE), START, START.plusSeconds(3600)))
                .isInstanceOf(StationNotFoundException.class);
    }
}
