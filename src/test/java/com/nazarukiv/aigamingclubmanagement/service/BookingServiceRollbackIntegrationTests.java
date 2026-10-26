package com.nazarukiv.aigamingclubmanagement.service;

import com.nazarukiv.aigamingclubmanagement.dto.BookingResponse;
import com.nazarukiv.aigamingclubmanagement.entity.ActivityLog;
import com.nazarukiv.aigamingclubmanagement.entity.GamingStation;
import com.nazarukiv.aigamingclubmanagement.entity.User;
import com.nazarukiv.aigamingclubmanagement.enums.BookingStatus;
import com.nazarukiv.aigamingclubmanagement.enums.UserRole;
import com.nazarukiv.aigamingclubmanagement.repository.ActivityLogRepository;
import com.nazarukiv.aigamingclubmanagement.repository.BookingRepository;
import com.nazarukiv.aigamingclubmanagement.repository.GamingStationRepository;
import com.nazarukiv.aigamingclubmanagement.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

// No test transaction: the service must roll back its own transaction after flushed writes.
@SpringBootTest
class BookingServiceRollbackIntegrationTests {

    @Autowired private BookingService service;
    @Autowired private UserRepository users;
    @Autowired private GamingStationRepository stations;
    @Autowired private BookingRepository bookings;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private ActivityLogRepository activityLogs;

    private Long userId;
    private List<Long> stationIds;
    private Instant start;

    @BeforeEach
    void setUp() {
        userId = users.saveAndFlush(new User("rollback-" + UUID.randomUUID() + "@example.com",
                "test-hash", "Rollback Customer", UserRole.CUSTOMER, Instant.now())).getId();
        stationIds = stations.findAll().stream().map(GamingStation::getId).sorted().limit(2).toList();
        start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    @AfterEach
    void cleanUpOnlyThisTestsCommittedFixtures() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM booking_stations WHERE booking_id IN (SELECT id FROM bookings WHERE user_id = ?)", userId);
            jdbc.update("DELETE FROM activity_logs WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM bookings WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        });
    }

    @Test
    void loggingFailureRollsBackFlushedBookingAndAllStationLinks() {
        doAnswer(invocation -> {
            entityManager.flush();
            assertThat(bookings.findByUserId(userId)).hasSize(1);
            assertThat(countStationLinks()).isEqualTo(2);
            throw new DataAccessResourceFailureException("Simulated activity log failure");
        }).when(activityLogs).save(any(ActivityLog.class));

        assertThatThrownBy(() -> service.createBooking(userId, stationIds, start, start.plusSeconds(3600)))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(bookings.findByUserId(userId)).isEmpty();
        assertThat(countStationLinks()).isZero();
        assertThat(activityLogs.findByUserId(userId)).isEmpty();
    }

    @Test
    void loggingFailureRollsBackFlushedCancellation() {
        BookingResponse original = service.createBooking(userId, stationIds, start, start.plusSeconds(3600));
        doAnswer(invocation -> {
            entityManager.flush();
            assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, original.bookingId()))
                    .isEqualTo("CANCELLED");
            throw new DataAccessResourceFailureException("Simulated activity log failure");
        }).when(activityLogs).save(any(ActivityLog.class));

        assertThatThrownBy(() -> service.cancelBooking(original.bookingId(), userId))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(service.getBookingById(original.bookingId()).status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(activityLogs.findByUserId(userId)).extracting(ActivityLog::getAction)
                .containsExactly("BOOKING_CREATED");
    }

    private Integer countStationLinks() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM booking_stations bs
                JOIN bookings b ON b.id = bs.booking_id WHERE b.user_id = ?
                """, Integer.class, userId);
    }
}
