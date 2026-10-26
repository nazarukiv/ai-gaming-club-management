package com.nazarukiv.aigamingclubmanagement.service;

import com.nazarukiv.aigamingclubmanagement.dto.BookingResponse;
import com.nazarukiv.aigamingclubmanagement.entity.ActivityLog;
import com.nazarukiv.aigamingclubmanagement.entity.GamingStation;
import com.nazarukiv.aigamingclubmanagement.entity.User;
import com.nazarukiv.aigamingclubmanagement.enums.BookingStatus;
import com.nazarukiv.aigamingclubmanagement.enums.StationStatus;
import com.nazarukiv.aigamingclubmanagement.enums.StationType;
import com.nazarukiv.aigamingclubmanagement.enums.UserRole;
import com.nazarukiv.aigamingclubmanagement.exception.BookingTimeConflictException;
import com.nazarukiv.aigamingclubmanagement.exception.CancellationNotAllowedException;
import com.nazarukiv.aigamingclubmanagement.repository.ActivityLogRepository;
import com.nazarukiv.aigamingclubmanagement.repository.BookingRepository;
import com.nazarukiv.aigamingclubmanagement.repository.GamingStationRepository;
import com.nazarukiv.aigamingclubmanagement.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

// Real PostgreSQL, separate threads/connections/transactions, and no mocked components.
@SpringBootTest
class BookingServiceConcurrencyIntegrationTests {

    @Autowired private BookingService service;
    @Autowired private UserRepository users;
    @Autowired private GamingStationRepository stations;
    @Autowired private BookingRepository bookings;
    @Autowired private ActivityLogRepository activityLogs;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private Long userId;
    private List<Long> stationIds;
    private Instant start;

    @BeforeEach
    void setUpCommittedFixtures() {
        userId = users.saveAndFlush(new User("concurrency-" + UUID.randomUUID() + "@example.com",
                "test-hash", "Concurrent Customer", UserRole.CUSTOMER, Instant.now())).getId();
        GamingStation first = stations.saveAndFlush(new GamingStation("Concurrency-" + UUID.randomUUID(),
                StationType.PC, 10000, StationStatus.AVAILABLE, Instant.now()));
        GamingStation second = stations.saveAndFlush(new GamingStation("Concurrency-" + UUID.randomUUID(),
                StationType.PC, 10001, StationStatus.AVAILABLE, Instant.now()));
        stationIds = List.of(first.getId(), second.getId());
        start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    @AfterEach
    void cleanUpOnlyThisTestsCommittedFixtures() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("DELETE FROM booking_stations WHERE booking_id IN (SELECT id FROM bookings WHERE user_id = ?)", userId);
            jdbc.update("DELETE FROM activity_logs WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM bookings WHERE user_id = ?", userId);
            for (Long stationId : stationIds) {
                jdbc.update("DELETE FROM gaming_stations WHERE id = ?", stationId);
            }
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        });
    }

    @Test
    void concurrentRequestsForSameStationAllowExactlyOneBooking() throws Exception {
        assertConcurrentCreation(List.of(stationIds.getFirst()), List.of(stationIds.getFirst()));
    }

    @Test
    void reversedMultiStationRequestsUseTheSameLockOrderAndAllowExactlyOneBooking() throws Exception {
        assertConcurrentCreation(stationIds, stationIds.reversed());
    }

    private void assertConcurrentCreation(List<Long> firstSelection, List<Long> secondSelection) throws Exception {
        CountDownLatch firstCreated = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transaction().execute(status -> {
                firstPid.set(backendPid());
                BookingResponse result = service.createBooking(userId, firstSelection, start, start.plusSeconds(3600));
                firstCreated.countDown();
                waitFor(allowCommit);
                return result;
            }));
            try {
                waitFor(firstCreated);
                var second = executor.submit(() -> transaction().execute(status -> {
                    secondPid.set(backendPid());
                    secondStarted.countDown();
                    return service.createBooking(userId, secondSelection, start, start.plusSeconds(3600));
                }));
                waitFor(secondStarted);

                // Wait for an observed database lock, not an assumed thread schedule or fixed sleep.
                await().atMost(Duration.ofSeconds(10)).until(() -> isBlockedBy(secondPid.get(), firstPid.get()));
                assertThat(second.isDone()).isFalse();
                allowCommit.countDown();

                assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo(BookingStatus.CONFIRMED);
                assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(BookingTimeConflictException.class);
                assertThat(firstPid.get()).isNotEqualTo(secondPid.get());
                assertThat(bookings.findByUserId(userId)).hasSize(1);
                assertThat(activityLogs.findByUserId(userId)).extracting(ActivityLog::getAction)
                        .containsExactly("BOOKING_CREATED");
                assertThat(service.getBookingsByUser(userId).getFirst().stationIds()).isEqualTo(firstSelection);
            } finally {
                allowCommit.countDown();
            }
        }
    }

    @Test
    void concurrentCancellationsProduceOnlyOneCancellationLog() throws Exception {
        BookingResponse original = service.createBooking(userId, stationIds, start, start.plusSeconds(3600));
        CountDownLatch firstCancelled = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger();
        AtomicInteger secondPid = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transaction().execute(status -> {
                firstPid.set(backendPid());
                BookingResponse result = service.cancelBooking(original.bookingId(), userId);
                firstCancelled.countDown();
                waitFor(allowCommit);
                return result;
            }));
            try {
                waitFor(firstCancelled);
                var second = executor.submit(() -> transaction().execute(status -> {
                    secondPid.set(backendPid());
                    secondStarted.countDown();
                    return service.cancelBooking(original.bookingId(), userId);
                }));
                waitFor(secondStarted);
                await().atMost(Duration.ofSeconds(10)).until(() -> isBlockedBy(secondPid.get(), firstPid.get()));
                allowCommit.countDown();

                assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo(BookingStatus.CANCELLED);
                assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(CancellationNotAllowedException.class);
                assertThat(activityLogs.findByUserId(userId)).extracting(ActivityLog::getAction)
                        .containsExactlyInAnyOrder("BOOKING_CREATED", "BOOKING_CANCELLED");
            } finally {
                allowCommit.countDown();
            }
        }
    }

    private TransactionTemplate transaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionTemplate.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(20);
        return transaction;
    }

    private int backendPid() {
        return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    private boolean isBlockedBy(int waitingPid, int blockingPid) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))",
                Boolean.class, blockingPid, waitingPid));
    }

    private static void waitFor(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent transaction interrupted", exception);
        }
    }
}
