package com.nazarukiv.aigamingclubmanagement.service;

import com.nazarukiv.aigamingclubmanagement.dto.BookingResponse;
import com.nazarukiv.aigamingclubmanagement.entity.ActivityLog;
import com.nazarukiv.aigamingclubmanagement.entity.Booking;
import com.nazarukiv.aigamingclubmanagement.entity.GamingStation;
import com.nazarukiv.aigamingclubmanagement.entity.User;
import com.nazarukiv.aigamingclubmanagement.enums.BookingStatus;
import com.nazarukiv.aigamingclubmanagement.enums.StationStatus;
import com.nazarukiv.aigamingclubmanagement.enums.UserRole;
import com.nazarukiv.aigamingclubmanagement.exception.BookingNotFoundException;
import com.nazarukiv.aigamingclubmanagement.exception.BookingTimeConflictException;
import com.nazarukiv.aigamingclubmanagement.exception.CancellationNotAllowedException;
import com.nazarukiv.aigamingclubmanagement.exception.InvalidBookingRequestException;
import com.nazarukiv.aigamingclubmanagement.exception.StationNotFoundException;
import com.nazarukiv.aigamingclubmanagement.exception.StationUnavailableException;
import com.nazarukiv.aigamingclubmanagement.exception.UserNotFoundException;
import com.nazarukiv.aigamingclubmanagement.repository.ActivityLogRepository;
import com.nazarukiv.aigamingclubmanagement.repository.BookingRepository;
import com.nazarukiv.aigamingclubmanagement.repository.BookingStationRepository;
import com.nazarukiv.aigamingclubmanagement.repository.GamingStationRepository;
import com.nazarukiv.aigamingclubmanagement.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Real PostgreSQL; all changes in this class are rolled back after each test.
@SpringBootTest
@Transactional
class BookingServiceIntegrationTests {

    @Autowired private BookingService service;
    @Autowired private UserRepository users;
    @Autowired private GamingStationRepository stations;
    @Autowired private BookingRepository bookings;
    @Autowired private BookingStationRepository bookingStations;
    @Autowired private ActivityLogRepository activityLogs;
    @Autowired private EntityManager entityManager;

    private User user;
    private List<Long> stationIds;
    private Instant start;
    private Instant end;

    @BeforeEach
    void setUp() {
        user = users.saveAndFlush(new User("booking-" + UUID.randomUUID() + "@example.com",
                "test-hash", "Booking Customer", UserRole.CUSTOMER, Instant.now()));
        stationIds = stations.findAll().stream().map(GamingStation::getId).sorted().limit(2).toList();
        start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        end = start.plusSeconds(3600);
    }

    @Test
    void createsSingleStationBookingAndReturnsDetachedResponse() {
        BookingResponse result = create(List.of(stationIds.getFirst()));
        entityManager.flush();
        entityManager.clear();

        assertThat(result.bookingId()).isPositive();
        assertThat(result.userId()).isEqualTo(user.getId());
        assertThat(result.stationIds()).containsExactly(stationIds.getFirst());
        assertThat(result.startTime()).isEqualTo(start);
        assertThat(result.endTime()).isEqualTo(end);
        assertThat(result.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(service.getBookingById(result.bookingId())).isEqualTo(result);
        assertThat(bookingStations.findByBookingId(result.bookingId())).hasSize(1);
    }

    @Test
    void createsMultiStationBookingWithSortedStationIds() {
        BookingResponse result = create(stationIds.reversed());
        entityManager.flush();
        entityManager.clear();

        assertThat(service.getBookingById(result.bookingId()).stationIds()).isEqualTo(stationIds);
        assertThat(bookingStations.findByBookingId(result.bookingId())).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1800, 0, 1800})
    void rejectsOverlappingConfirmedBookings(int offsetSeconds) {
        create(List.of(stationIds.getFirst()));

        assertThatThrownBy(() -> service.createBooking(user.getId(), stationIds,
                start.plusSeconds(offsetSeconds), end.plusSeconds(offsetSeconds)))
                .isInstanceOf(BookingTimeConflictException.class);
        assertThat(bookings.findByUserId(user.getId())).hasSize(1);
        assertThat(activityLogs.findByUserId(user.getId())).hasSize(1);
    }

    @Test
    void rejectsOverlapWhenNewBookingContainsExistingInterval() {
        create(List.of(stationIds.getFirst()));

        assertThatThrownBy(() -> service.createBooking(user.getId(), stationIds,
                start.minusSeconds(1800), end.plusSeconds(1800)))
                .isInstanceOf(BookingTimeConflictException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-3600, 3600})
    void allowsAdjacentTimeSlotsOnEitherSide(int offsetSeconds) {
        create(stationIds);

        BookingResponse adjacent = service.createBooking(user.getId(), stationIds,
                start.plusSeconds(offsetSeconds), end.plusSeconds(offsetSeconds));

        assertThat(adjacent.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(bookings.findByUserId(user.getId())).hasSize(2);
    }

    @Test
    void allowsSameTimeOnDifferentStations() {
        create(List.of(stationIds.getFirst()));

        assertThat(create(List.of(stationIds.getLast())).status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void cancelledBookingsDoNotBlockAvailabilityAndAreNotDeleted() {
        BookingResponse original = create(stationIds);

        BookingResponse cancelled = service.cancelBooking(original.bookingId(), user.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(service.getBookingById(original.bookingId()).status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingStations.findByBookingId(original.bookingId())).hasSize(2);
        assertThat(create(stationIds).status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void rejectsMissingUser() {
        assertThatThrownBy(() -> service.createBooking(Long.MAX_VALUE, stationIds, start, end))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void rejectsMissingStationWithoutSavingPartialBooking() {
        assertThatThrownBy(() -> create(List.of(stationIds.getFirst(), Long.MAX_VALUE)))
                .isInstanceOf(StationNotFoundException.class);
        assertThat(bookings.findByUserId(user.getId())).isEmpty();
        assertThat(activityLogs.findByUserId(user.getId())).isEmpty();
    }

    @Test
    void rejectsDuplicateStationIds() {
        assertThatThrownBy(() -> create(List.of(stationIds.getFirst(), stationIds.getFirst())))
                .isInstanceOf(InvalidBookingRequestException.class);
    }

    @Test
    void rejectsEmptyOrNullStationSelection() {
        assertThatThrownBy(() -> create(List.of())).isInstanceOf(InvalidBookingRequestException.class);
        assertThatThrownBy(() -> create(null)).isInstanceOf(InvalidBookingRequestException.class);
    }

    @Test
    void rejectsNullOrNonpositiveIds() {
        assertThatThrownBy(() -> create(Arrays.asList(stationIds.getFirst(), null)))
                .isInstanceOf(InvalidBookingRequestException.class);
        assertThatThrownBy(() -> create(List.of(0L))).isInstanceOf(InvalidBookingRequestException.class);
        assertThatThrownBy(() -> service.createBooking(null, stationIds, start, end))
                .isInstanceOf(InvalidBookingRequestException.class);
        assertThatThrownBy(() -> service.getBookingById(-1L)).isInstanceOf(InvalidBookingRequestException.class);
        assertThatThrownBy(() -> service.getBookingsByUser(null)).isInstanceOf(InvalidBookingRequestException.class);
        assertThatThrownBy(() -> service.cancelBooking(1L, null)).isInstanceOf(InvalidBookingRequestException.class);
    }

    @Test
    void rejectsPastStartTime() {
        assertThatThrownBy(() -> service.createBooking(user.getId(), stationIds, Instant.now().minusSeconds(1), end))
                .isInstanceOf(InvalidBookingRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsEndAtOrBeforeStart(int durationSeconds) {
        assertThatThrownBy(() -> service.createBooking(user.getId(), stationIds, start, start.plusSeconds(durationSeconds)))
                .isInstanceOf(InvalidBookingRequestException.class);
    }

    @Test
    void rejectsNullTimestamps() {
        assertThatThrownBy(() -> service.createBooking(user.getId(), stationIds, null, end))
                .isInstanceOf(InvalidBookingRequestException.class);
        assertThatThrownBy(() -> service.createBooking(user.getId(), stationIds, start, null))
                .isInstanceOf(InvalidBookingRequestException.class);
    }

    @ParameterizedTest
    @EnumSource(value = StationStatus.class, names = {"MAINTENANCE", "INACTIVE"})
    void rejectsUnavailableStation(StationStatus status) {
        GamingStation station = stations.findById(stationIds.getLast()).orElseThrow();
        station.setStatus(status);

        assertThatThrownBy(() -> create(stationIds)).isInstanceOf(StationUnavailableException.class);
        assertThat(bookings.findByUserId(user.getId())).isEmpty();
        assertThat(activityLogs.findByUserId(user.getId())).isEmpty();
    }

    @Test
    void rejectsCancellationByAnotherUserEvenWhenTheyAreAdmin() {
        BookingResponse original = create(stationIds);
        User admin = users.saveAndFlush(new User("admin-" + UUID.randomUUID() + "@example.com",
                "test-hash", "Other User", UserRole.ADMIN, Instant.now()));

        assertThatThrownBy(() -> service.cancelBooking(original.bookingId(), admin.getId()))
                .isInstanceOf(CancellationNotAllowedException.class);
        assertThat(service.getBookingById(original.bookingId()).status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(activityLogs.findByUserId(admin.getId())).isEmpty();
        assertThat(activityLogs.findByUserId(user.getId())).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"PENDING", "CANCELLED", "COMPLETED"})
    void rejectsCancellationOfNonConfirmedBooking(BookingStatus status) {
        BookingResponse original = create(stationIds);
        if (status == BookingStatus.CANCELLED) {
            service.cancelBooking(original.bookingId(), user.getId());
        } else {
            Booking booking = bookings.findById(original.bookingId()).orElseThrow();
            booking.setStatus(status);
        }
        int logCount = activityLogs.findByUserId(user.getId()).size();

        assertThatThrownBy(() -> service.cancelBooking(original.bookingId(), user.getId()))
                .isInstanceOf(CancellationNotAllowedException.class);
        assertThat(activityLogs.findByUserId(user.getId())).hasSize(logCount);
    }

    @Test
    void logsCreationAndCancellationWithActingUserDetailsAndTimestamp() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.MICROS);
        BookingResponse original = create(stationIds);
        service.cancelBooking(original.bookingId(), user.getId());
        entityManager.flush();
        entityManager.clear();

        List<ActivityLog> logs = activityLogs.findByUserId(user.getId());
        assertThat(logs).extracting(ActivityLog::getAction)
                .containsExactlyInAnyOrder("BOOKING_CREATED", "BOOKING_CANCELLED");
        assertThat(logs).allSatisfy(log -> {
            assertThat(log.getUser().getId()).isEqualTo(user.getId());
            assertThat(log.getDetails()).contains("Booking " + original.bookingId());
            assertThat(log.getCreatedAt()).isBetween(before, Instant.now());
        });
    }

    @Test
    void queriesOnlyBookingsBelongingToRequestedUserIncludingCancelledBookings() {
        BookingResponse original = create(stationIds);
        service.cancelBooking(original.bookingId(), user.getId());
        BookingResponse second = service.createBooking(user.getId(), stationIds, end, end.plusSeconds(3600));
        User other = users.saveAndFlush(new User("other-" + UUID.randomUUID() + "@example.com",
                "test-hash", "Other Customer", UserRole.CUSTOMER, Instant.now()));
        service.createBooking(other.getId(), stationIds, end.plusSeconds(3600), end.plusSeconds(7200));
        entityManager.flush();
        entityManager.clear();

        assertThat(service.getBookingsByUser(user.getId())).extracting(BookingResponse::bookingId)
                .containsExactlyInAnyOrder(original.bookingId(), second.bookingId());
        assertThat(service.getBookingsByUser(user.getId())).allSatisfy(result -> {
            assertThat(result.userId()).isEqualTo(user.getId());
            assertThat(result.stationIds()).isEqualTo(stationIds);
        });
    }

    @Test
    void returnsEmptyListForUserWithoutBookings() {
        assertThat(service.getBookingsByUser(user.getId())).isEmpty();
    }

    @Test
    void queriesAndCancellationRejectMissingRecords() {
        assertThatThrownBy(() -> service.getBookingById(Long.MAX_VALUE))
                .isInstanceOf(BookingNotFoundException.class);
        assertThatThrownBy(() -> service.cancelBooking(Long.MAX_VALUE, user.getId()))
                .isInstanceOf(BookingNotFoundException.class);
        assertThatThrownBy(() -> service.getBookingsByUser(Long.MAX_VALUE))
                .isInstanceOf(UserNotFoundException.class);
    }

    private BookingResponse create(List<Long> ids) {
        return service.createBooking(user.getId(), ids, start, end);
    }
}
