package com.nazarukiv.aigamingclubmanagement.service;

import com.nazarukiv.aigamingclubmanagement.dto.AvailabilityResponse;
import com.nazarukiv.aigamingclubmanagement.dto.BookingResponse;
import com.nazarukiv.aigamingclubmanagement.entity.GamingStation;
import com.nazarukiv.aigamingclubmanagement.entity.User;
import com.nazarukiv.aigamingclubmanagement.enums.BookingStatus;
import com.nazarukiv.aigamingclubmanagement.enums.StationStatus;
import com.nazarukiv.aigamingclubmanagement.enums.StationType;
import com.nazarukiv.aigamingclubmanagement.enums.UserRole;
import com.nazarukiv.aigamingclubmanagement.exception.BookingTimeConflictException;
import com.nazarukiv.aigamingclubmanagement.repository.BookingRepository;
import com.nazarukiv.aigamingclubmanagement.repository.GamingStationRepository;
import com.nazarukiv.aigamingclubmanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Real configured PostgreSQL; fixtures and changes to seeded stations roll back after each test.
@SpringBootTest
@Transactional
class AvailabilityServiceIntegrationTests {

    @Autowired private AvailabilityService service;
    @Autowired private BookingService bookingService;
    @Autowired private GamingStationRepository stations;
    @Autowired private BookingRepository bookings;
    @Autowired private UserRepository users;

    private List<GamingStation> pcs;
    private User user;
    private Instant start;
    private Instant end;

    @BeforeEach
    void setUp() {
        pcs = stations.findByType(StationType.PC).stream()
                .sorted(Comparator.comparing(GamingStation::getStationNumber)).toList();
        user = users.saveAndFlush(new User("availability-" + UUID.randomUUID() + "@example.com",
                "test-hash", "Availability Customer", UserRole.CUSTOMER, Instant.now()));
        start = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        end = start.plusSeconds(3600);
    }

    @ParameterizedTest
    @EnumSource(StationType.class)
    void returnsAllAvailableStationsOfRequestedTypeInStationNumberOrder(StationType type) {
        int expectedCount = type == StationType.PC ? 30 : 5;

        List<AvailabilityResponse> result = service.getAvailableStations(start, end, type);

        assertThat(result).hasSize(expectedCount);
        assertThat(result).extracting(AvailabilityResponse::stationNumber)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, expectedCount).boxed().toList());
        assertThat(result).allSatisfy(station -> {
            assertThat(station.stationId()).isPositive();
            assertThat(station.stationType()).isEqualTo(type);
            assertThat(station.stationName()).isEqualTo("%s-%02d".formatted(type, station.stationNumber()));
        });
    }

    @ParameterizedTest
    @CsvSource({"-1800, 3600", "0, 3600", "1800, 3600", "-1800, 7200", "900, 900"})
    void overlappingConfirmedBookingExcludesOnlyItsStation(int offsetSeconds, int durationSeconds) {
        bookFirstPc();
        Instant searchStart = start.plusSeconds(offsetSeconds);

        List<AvailabilityResponse> result = service.getAvailableStations(
                searchStart, searchStart.plusSeconds(durationSeconds), StationType.PC);

        assertThat(result).hasSize(29);
        assertThat(result).extracting(AvailabilityResponse::stationId)
                .doesNotContain(pcs.getFirst().getId()).contains(pcs.get(1).getId());
    }

    @Test
    void cancelledBookingDoesNotBlockAvailability() {
        BookingResponse original = bookFirstPc();
        bookingService.cancelBooking(original.bookingId(), user.getId());

        assertThat(service.getAvailableStations(start, end, StationType.PC))
                .hasSize(30).extracting(AvailabilityResponse::stationId).contains(pcs.getFirst().getId());
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"PENDING", "COMPLETED"})
    void otherNonconfirmedBookingsDoNotBlockAvailability(BookingStatus status) {
        BookingResponse original = bookFirstPc();
        bookings.findById(original.bookingId()).orElseThrow().setStatus(status);

        assertThat(service.getAvailableStations(start, end, StationType.PC)).hasSize(30);
    }

    @ParameterizedTest
    @ValueSource(ints = {-3600, 3600})
    void adjacentTimeSlotsOnEitherSideAreAvailable(int offsetSeconds) {
        bookFirstPc();

        assertThat(service.getAvailableStations(start.plusSeconds(offsetSeconds),
                end.plusSeconds(offsetSeconds), StationType.PC))
                .hasSize(30).extracting(AvailabilityResponse::stationId).contains(pcs.getFirst().getId());
    }

    @ParameterizedTest
    @EnumSource(value = StationStatus.class, names = {"MAINTENANCE", "INACTIVE"})
    void excludesStationsThatAreNotAvailable(StationStatus status) {
        pcs.getFirst().setStatus(status);

        assertThat(service.getAvailableStations(start, end, StationType.PC))
                .hasSize(29).extracting(AvailabilityResponse::stationId).doesNotContain(pcs.getFirst().getId());
    }

    @Test
    void findsThreeAdjacentPcsAfterAnEarlierGap() {
        keepAvailablePcNumbers(Set.of(1, 2, 4, 5, 6));

        assertThat(service.findAdjacentPCs(start, end, 3)).extracting(AvailabilityResponse::stationNumber)
                .containsExactly(4, 5, 6);
    }

    @Test
    void returnsExactlyTheRequestedCountFromTheFirstValidGroup() {
        assertThat(service.findAdjacentPCs(start, end, 3)).extracting(AvailabilityResponse::stationNumber)
                .containsExactly(1, 2, 3);
        assertThat(service.findAdjacentPCs(start, end, 1)).extracting(AvailabilityResponse::stationNumber)
                .containsExactly(1);
    }

    @Test
    void nonconsecutivePcsAreNotAdjacent() {
        keepAvailablePcNumbers(Set.of(1, 3, 5));

        assertThat(service.findAdjacentPCs(start, end, 3)).isEmpty();
    }

    @Test
    void returnsEmptyWhenEveryConsecutiveGroupIsTooSmall() {
        keepAvailablePcNumbers(Set.of(1, 2, 4, 5));

        assertThat(service.findAdjacentPCs(start, end, 3)).isEmpty();
    }

    @Test
    void bookedPcsBreakAdjacentGroups() {
        keepAvailablePcNumbers(Set.of(1, 2, 3, 4));
        bookFirstPc();

        assertThat(service.findAdjacentPCs(start, end, 3)).extracting(AvailabilityResponse::stationNumber)
                .containsExactly(2, 3, 4);
    }

    @Test
    void neverUsesPs5StationsToFillAnAdjacentPcGroup() {
        keepAvailablePcNumbers(Set.of());

        assertThat(service.findAdjacentPCs(start, end, 1)).isEmpty();
        assertThat(service.getAvailableStations(start, end, StationType.PS5)).hasSize(5);
    }

    @Test
    void returnsEmptyWhenCountExceedsAvailablePcs() {
        assertThat(service.findAdjacentPCs(start, end, 31)).isEmpty();
    }

    @Test
    void availabilityResultDoesNotReserveStationOrBypassFinalBookingCheck() {
        List<AvailabilityResponse> available = service.getAvailableStations(start, end, StationType.PC);
        Long selectedId = available.getFirst().stationId();
        bookingService.createBooking(user.getId(), List.of(selectedId), start, end);

        assertThatThrownBy(() -> bookingService.createBooking(user.getId(), List.of(selectedId), start, end))
                .isInstanceOf(BookingTimeConflictException.class);
    }

    private BookingResponse bookFirstPc() {
        return bookingService.createBooking(user.getId(), List.of(pcs.getFirst().getId()), start, end);
    }

    private void keepAvailablePcNumbers(Set<Integer> numbers) {
        pcs.stream().filter(pc -> !numbers.contains(pc.getStationNumber()))
                .forEach(pc -> pc.setStatus(StationStatus.MAINTENANCE));
    }
}
