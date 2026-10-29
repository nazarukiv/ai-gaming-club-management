package com.nazarukiv.aigamingclubmanagement.service;

import com.nazarukiv.aigamingclubmanagement.enums.StationType;
import com.nazarukiv.aigamingclubmanagement.repository.GamingStationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTests {

    @Mock private GamingStationRepository stations;
    @InjectMocks private AvailabilityService service;

    @ParameterizedTest
    @MethodSource("invalidSearches")
    void rejectsInvalidSearchBeforeQueryingDatabase(Instant start, Instant end, StationType type) {
        assertThatThrownBy(() -> service.getAvailableStations(start, end, type))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(stations);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsNonpositiveAdjacentCount(int count) {
        Instant start = Instant.now().plusSeconds(86400);

        assertThatThrownBy(() -> service.findAdjacentPCs(start, start.plusSeconds(3600), count))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positive");
        verifyNoInteractions(stations);
    }

    @Test
    void adjacentSearchUsesTheSameTimeValidation() {
        Instant past = Instant.now().minusSeconds(3600);

        assertThatThrownBy(() -> service.findAdjacentPCs(past, past.plusSeconds(7200), 3))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("future");
        verifyNoInteractions(stations);
    }

    private static Stream<Arguments> invalidSearches() {
        Instant start = Instant.now().plusSeconds(86400);
        return Stream.of(
                Arguments.of(null, start, StationType.PC),
                Arguments.of(start, null, StationType.PC),
                Arguments.of(Instant.now().minusSeconds(1), start, StationType.PC),
                Arguments.of(start, start, StationType.PC),
                Arguments.of(start, start.minusSeconds(1), StationType.PS5),
                Arguments.of(start, start.plusSeconds(3600), null));
    }
}
