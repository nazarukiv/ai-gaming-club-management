package com.nazarukiv.aigamingclubmanagement.service;

import com.nazarukiv.aigamingclubmanagement.dto.PricingResponse;
import com.nazarukiv.aigamingclubmanagement.entity.GamingStation;
import com.nazarukiv.aigamingclubmanagement.enums.StationStatus;
import com.nazarukiv.aigamingclubmanagement.enums.StationType;
import com.nazarukiv.aigamingclubmanagement.exception.StationNotFoundException;
import com.nazarukiv.aigamingclubmanagement.repository.GamingStationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingServiceTests {

    @Mock private GamingStationRepository stations;
    @InjectMocks private PricingService service;

    // Pricing only needs a positive duration; a historical interval can also be quoted.
    private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");

    @ParameterizedTest
    @MethodSource("priceExamples")
    void calculatesFixedPricesAndRoundsOnlyTheFinalTotal(List<StationType> types, Duration duration, String price) {
        List<Long> ids = IntStream.rangeClosed(1, types.size()).mapToObj(number -> (long) number).toList();
        when(stations.findAllById(ids)).thenReturn(types.stream().map(this::station).toList());

        PricingResponse result = service.calculatePrice(ids, START, START.plus(duration));

        assertThat(result.stationIds()).isEqualTo(ids);
        assertThat(result.duration()).isEqualTo(duration);
        assertThat(result.currency()).isEqualTo("GBP");
        assertThat(result.totalPrice()).isEqualTo(new BigDecimal(price));
        verify(stations).findAllById(ids);
    }

    @ParameterizedTest
    @MethodSource("invalidSelections")
    void rejectsInvalidStationIdsBeforeQueryingDatabase(List<Long> ids) {
        assertThatThrownBy(() -> service.calculatePrice(ids, START, START.plusSeconds(3600)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(stations);
    }

    @ParameterizedTest
    @MethodSource("invalidIntervals")
    void rejectsInvalidIntervalsBeforeQueryingDatabase(Instant start, Instant end) {
        assertThatThrownBy(() -> service.calculatePrice(List.of(1L), start, end))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(stations);
    }

    @Test
    void rejectsSelectionContainingANonexistentStation() {
        when(stations.findAllById(List.of(1L, 99L))).thenReturn(List.of(station(StationType.PC)));

        assertThatThrownBy(() -> service.calculatePrice(List.of(1L, 99L), START, START.plusSeconds(3600)))
                .isInstanceOf(StationNotFoundException.class);
    }

    @Test
    void rejectsSelectionWhenNoStationsExist() {
        when(stations.findAllById(List.of(99L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.calculatePrice(List.of(99L), START, START.plusSeconds(3600)))
                .isInstanceOf(StationNotFoundException.class);
    }

    @Test
    void responseCopiesSelectedIdsAndPreventsChanges() {
        List<Long> ids = new ArrayList<>(List.of(2L, 1L));
        when(stations.findAllById(ids)).thenReturn(List.of(station(StationType.PC), station(StationType.PS5)));

        PricingResponse result = service.calculatePrice(ids, START, START.plusSeconds(3600));
        ids.clear();

        assertThat(result.stationIds()).containsExactly(2L, 1L);
        assertThatThrownBy(() -> result.stationIds().add(3L)).isInstanceOf(UnsupportedOperationException.class);
    }

    private GamingStation station(StationType type) {
        return new GamingStation("Test " + type, type, 1, StationStatus.AVAILABLE, START);
    }

    private static Stream<Arguments> priceExamples() {
        return Stream.of(
                Arguments.of(List.of(StationType.PC, StationType.PC, StationType.PC), Duration.ofHours(2), "30.00"),
                Arguments.of(List.of(StationType.PS5), Duration.ofHours(1), "7.00"),
                Arguments.of(List.of(StationType.PC, StationType.PS5), Duration.ofHours(2), "24.00"),
                Arguments.of(List.of(StationType.PS5), Duration.ofMinutes(90), "10.50"),
                Arguments.of(List.of(StationType.PC), Duration.ofMinutes(20), "1.67"),
                Arguments.of(List.of(StationType.PC, StationType.PC, StationType.PC), Duration.ofSeconds(2), "0.01"),
                Arguments.of(List.of(StationType.PC), Duration.ofMillis(3600), "0.01"),
                Arguments.of(List.of(StationType.PS5), Duration.ofNanos(1), "0.00"),
                // More than 24 hours must not wrap to the time-of-day portion.
                Arguments.of(List.of(StationType.PC), Duration.ofHours(25), "125.00"));
    }

    private static Stream<Arguments> invalidSelections() {
        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(List.of()),
                Arguments.of(List.of(1L, 1L)),
                Arguments.of(Arrays.asList(1L, null)),
                Arguments.of(List.of(0L)),
                Arguments.of(List.of(-1L)));
    }

    private static Stream<Arguments> invalidIntervals() {
        return Stream.of(
                Arguments.of(null, START),
                Arguments.of(START, null),
                Arguments.of(START, START),
                Arguments.of(START, START.minusSeconds(1)));
    }
}
