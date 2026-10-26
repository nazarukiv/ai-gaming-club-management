package com.nazarukiv.aigamingclubmanagement.service;

import com.nazarukiv.aigamingclubmanagement.entity.Booking;
import com.nazarukiv.aigamingclubmanagement.entity.BookingStation;
import com.nazarukiv.aigamingclubmanagement.entity.GamingStation;
import com.nazarukiv.aigamingclubmanagement.entity.User;
import com.nazarukiv.aigamingclubmanagement.enums.BookingStatus;
import com.nazarukiv.aigamingclubmanagement.enums.StationStatus;
import com.nazarukiv.aigamingclubmanagement.exception.BookingTimeConflictException;
import com.nazarukiv.aigamingclubmanagement.repository.ActivityLogRepository;
import com.nazarukiv.aigamingclubmanagement.repository.BookingRepository;
import com.nazarukiv.aigamingclubmanagement.repository.BookingStationRepository;
import com.nazarukiv.aigamingclubmanagement.repository.GamingStationRepository;
import com.nazarukiv.aigamingclubmanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTests {

    @Mock private UserRepository users;
    @Mock private GamingStationRepository stations;
    @Mock private BookingRepository bookings;
    @Mock private BookingStationRepository bookingStations;
    @Mock private ActivityLogRepository activityLogs;
    @InjectMocks private BookingService service;

    private final Instant start = Instant.now().plusSeconds(86400);
    private final Instant end = start.plusSeconds(3600);

    @BeforeEach
    void setUp() {
        User user = mock(User.class);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        for (Long id : List.of(2L, 9L)) {
            GamingStation station = mock(GamingStation.class);
            when(station.getStatus()).thenReturn(StationStatus.AVAILABLE);
            when(stations.findByIdForUpdate(id)).thenReturn(Optional.of(station));
        }
    }

    @Test
    void locksEveryStationInAscendingOrderBeforeCheckingOverlapsOrSaving() {
        when(bookings.save(any(Booking.class))).thenThrow(new IllegalStateException("Test stops at save"));

        assertThatThrownBy(() -> service.createBooking(1L, List.of(9L, 2L), start, end))
                .isInstanceOf(IllegalStateException.class).hasMessage("Test stops at save");

        var order = inOrder(stations, bookingStations, bookings);
        order.verify(stations).findByIdForUpdate(2L);
        order.verify(stations).findByIdForUpdate(9L);
        order.verify(bookingStations).existsOverlappingBooking(List.of(2L, 9L), BookingStatus.CONFIRMED, start, end);
        order.verify(bookings).save(any(Booking.class));
        verifyNoInteractions(activityLogs);
    }

    @Test
    void conflictStopsAllBookingAndActivityWrites() {
        when(bookingStations.existsOverlappingBooking(List.of(2L, 9L), BookingStatus.CONFIRMED, start, end))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createBooking(1L, List.of(9L, 2L), start, end))
                .isInstanceOf(BookingTimeConflictException.class);

        verifyNoInteractions(bookings, activityLogs);
        verify(bookingStations, never()).save(any(BookingStation.class));
    }
}
