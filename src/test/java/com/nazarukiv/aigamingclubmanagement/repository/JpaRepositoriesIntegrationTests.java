package com.nazarukiv.aigamingclubmanagement.repository;

import com.nazarukiv.aigamingclubmanagement.entity.ActivityLog;
import com.nazarukiv.aigamingclubmanagement.entity.Booking;
import com.nazarukiv.aigamingclubmanagement.entity.BookingStation;
import com.nazarukiv.aigamingclubmanagement.entity.BookingStationId;
import com.nazarukiv.aigamingclubmanagement.entity.GamingStation;
import com.nazarukiv.aigamingclubmanagement.entity.User;
import com.nazarukiv.aigamingclubmanagement.enums.BookingStatus;
import com.nazarukiv.aigamingclubmanagement.enums.StationStatus;
import com.nazarukiv.aigamingclubmanagement.enums.StationType;
import com.nazarukiv.aigamingclubmanagement.enums.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Uses the configured PostgreSQL database. Every test rolls back its changes.
@SpringBootTest
@Transactional
class JpaRepositoriesIntegrationTests {

    private static final Instant CREATED_AT = Instant.parse("2026-10-22T10:00:00.123456Z");
    private static final Instant START_TIME = Instant.parse("2026-10-23T12:00:00Z");

    @Autowired
    private UserRepository users;

    @Autowired
    private GamingStationRepository stations;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private BookingStationRepository bookingStations;

    @Autowired
    private ActivityLogRepository activityLogs;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationsSeedExactlyThirtyPcsAndFivePs5Stations() {
        assertThat(stations.count()).isEqualTo(35);
        assertSeededStations(StationType.PC, 30);
        assertSeededStations(StationType.PS5, 5);
        assertThat(stations.findByStatus(StationStatus.AVAILABLE)).hasSize(35);
    }

    @Test
    void persistsUserAndFindsByEmail() {
        User user = saveUser();
        entityManager.clear();

        User loaded = users.findByEmail(user.getEmail()).orElseThrow();
        assertThat(loaded.getId()).isPositive();
        assertThat(loaded.getPasswordHash()).isEqualTo("test-password-hash");
        assertThat(loaded.getFullName()).isEqualTo("Test Customer");
        assertThat(loaded.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(loaded.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(users.existsByEmail(user.getEmail())).isTrue();
        assertThat(users.existsByEmail("missing-" + user.getEmail())).isFalse();
        assertThat(users.findByEmail("missing-" + user.getEmail())).isEmpty();

        loaded.setRole(UserRole.ADMIN);
        users.flush();
        entityManager.clear();
        assertThat(users.findById(user.getId()).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void persistsGamingStationAndUpdatesStatus() {
        GamingStation station = stations.saveAndFlush(new GamingStation(
                "Test-" + UUID.randomUUID(), StationType.PS5, 999, StationStatus.MAINTENANCE, CREATED_AT));
        entityManager.clear();

        GamingStation loaded = stations.findById(station.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo(station.getName());
        assertThat(loaded.getType()).isEqualTo(StationType.PS5);
        assertThat(loaded.getStationNumber()).isEqualTo(999);
        assertThat(loaded.getStatus()).isEqualTo(StationStatus.MAINTENANCE);
        assertThat(loaded.getCreatedAt()).isEqualTo(CREATED_AT);

        loaded.setStatus(StationStatus.INACTIVE);
        stations.flush();
        entityManager.clear();
        assertThat(stations.findByStatus(StationStatus.INACTIVE))
                .extracting(GamingStation::getId).contains(station.getId());
    }

    @Test
    void persistsBookingWithMultipleStationsAndCompositeKeys() {
        Booking booking = saveBooking();
        List<GamingStation> pcs = stations.findByType(StationType.PC);
        BookingStation first = bookingStations.saveAndFlush(new BookingStation(booking, pcs.get(0)));
        bookingStations.saveAndFlush(new BookingStation(booking, pcs.get(1)));
        entityManager.clear();

        Booking loaded = bookings.findById(booking.getId()).orElseThrow();
        assertThat(loaded.getUser().getEmail()).isEqualTo(booking.getUser().getEmail());
        assertThat(loaded.getStartTime()).isEqualTo(START_TIME);
        assertThat(loaded.getEndTime()).isEqualTo(START_TIME.plusSeconds(3600));
        assertThat(loaded.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(loaded.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(loaded.getBookingStations()).extracting(link -> link.getStation().getId())
                .containsExactlyInAnyOrder(pcs.get(0).getId(), pcs.get(1).getId());
        assertThat(bookings.findByUserId(loaded.getUser().getId()))
                .extracting(Booking::getId).contains(booking.getId());
        assertThat(bookings.findByStatus(BookingStatus.PENDING))
                .extracting(Booking::getId).contains(booking.getId());
        assertThat(bookingStations.findByBookingId(booking.getId())).hasSize(2);
        assertThat(bookingStations.findByStationId(pcs.get(0).getId()))
                .extracting(BookingStation::getId).contains(first.getId());
        assertThat(bookingStations.findById(new BookingStationId(booking.getId(), pcs.get(0).getId())))
                .isPresent();
    }

    @Test
    void persistsActivityLogsWithOptionalUserAndTextDetails() {
        User user = saveUser();
        String details = "Long activity details. ".repeat(100);
        ActivityLog linked = activityLogs.saveAndFlush(new ActivityLog(user, "TEST_ACTION", details, CREATED_AT));
        ActivityLog anonymous = activityLogs.saveAndFlush(new ActivityLog(null, "SYSTEM_EVENT", null, CREATED_AT));
        entityManager.clear();

        ActivityLog loaded = activityLogs.findById(linked.getId()).orElseThrow();
        assertThat(loaded.getUser().getId()).isEqualTo(user.getId());
        assertThat(loaded.getAction()).isEqualTo("TEST_ACTION");
        assertThat(loaded.getDetails()).isEqualTo(details);
        assertThat(loaded.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(activityLogs.findByUserId(user.getId())).extracting(ActivityLog::getId).contains(linked.getId());
        ActivityLog loadedAnonymous = activityLogs.findById(anonymous.getId()).orElseThrow();
        assertThat(loadedAnonymous.getUser()).isNull();
        assertThat(loadedAnonymous.getDetails()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -3600})
    void rejectsBookingWhoseEndIsNotAfterStart(int durationSeconds) {
        User user = saveUser();
        Booking invalid = new Booking(user, START_TIME, START_TIME.plusSeconds(durationSeconds),
                BookingStatus.PENDING, CREATED_AT);

        assertThatThrownBy(() -> bookings.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_bookings_time_range");
    }

    @Test
    void rejectsDuplicateEmail() {
        User user = saveUser();

        assertThatThrownBy(() -> users.saveAndFlush(new User(
                user.getEmail(), "another-hash", "Another User", UserRole.CUSTOMER, CREATED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_users_email");
    }

    @Test
    void rejectsDuplicateStationName() {
        GamingStation station = stations.findByType(StationType.PC).getFirst();

        assertThatThrownBy(() -> stations.saveAndFlush(new GamingStation(
                station.getName(), StationType.PC, 999, StationStatus.AVAILABLE, CREATED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_gaming_stations_name");
    }

    @Test
    void rejectsDuplicateStationNumberWithinType() {
        assertThatThrownBy(() -> stations.saveAndFlush(new GamingStation(
                "Test-" + UUID.randomUUID(), StationType.PC, 1, StationStatus.AVAILABLE, CREATED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_gaming_stations_type_number");
    }

    @Test
    void rejectsDuplicateBookingStationPair() {
        Booking booking = saveBooking();
        GamingStation station = stations.findByType(StationType.PC).getFirst();
        bookingStations.saveAndFlush(new BookingStation(booking, station));

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO booking_stations (booking_id, station_id) VALUES (?, ?)",
                booking.getId(), station.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "booking", "station"})
    void restrictsDeletionOfReferencedRecords(String record) {
        Booking booking = saveBooking();
        GamingStation station = stations.findByType(StationType.PC).getFirst();
        bookingStations.saveAndFlush(new BookingStation(booking, station));
        entityManager.clear();

        assertThatThrownBy(() -> {
            switch (record) {
                case "user" -> {
                    users.deleteById(booking.getUser().getId());
                    users.flush();
                }
                case "booking" -> {
                    bookings.deleteById(booking.getId());
                    bookings.flush();
                }
                case "station" -> {
                    stations.deleteById(station.getId());
                    stations.flush();
                }
                default -> throw new IllegalArgumentException(record);
            }
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void restrictsDeletionOfUserReferencedByActivityLog() {
        User user = saveUser();
        activityLogs.saveAndFlush(new ActivityLog(user, "TEST_ACTION", null, CREATED_AT));
        entityManager.clear();

        assertThatThrownBy(() -> {
            users.deleteById(user.getId());
            users.flush();
        }).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_activity_logs_user");
    }

    private User saveUser() {
        return users.saveAndFlush(new User("test-" + UUID.randomUUID() + "@example.com",
                "test-password-hash", "Test Customer", UserRole.CUSTOMER, CREATED_AT));
    }

    private Booking saveBooking() {
        return bookings.saveAndFlush(new Booking(saveUser(), START_TIME, START_TIME.plusSeconds(3600),
                BookingStatus.PENDING, CREATED_AT));
    }

    private void assertSeededStations(StationType type, int expectedCount) {
        List<GamingStation> seeded = stations.findByType(type);
        List<String> expectedNames = IntStream.rangeClosed(1, expectedCount)
                .mapToObj(number -> "%s-%02d".formatted(type, number)).toList();
        assertThat(seeded).extracting(GamingStation::getName).containsExactlyInAnyOrderElementsOf(expectedNames);
        assertThat(seeded).allSatisfy(station -> {
            assertThat(station.getName()).isEqualTo("%s-%02d".formatted(type, station.getStationNumber()));
            assertThat(station.getCreatedAt()).isNotNull();
        });
    }
}
