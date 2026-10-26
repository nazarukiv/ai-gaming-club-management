package com.nazarukiv.aigamingclubmanagement.service;

import com.nazarukiv.aigamingclubmanagement.dto.BookingResponse;
import com.nazarukiv.aigamingclubmanagement.entity.ActivityLog;
import com.nazarukiv.aigamingclubmanagement.entity.Booking;
import com.nazarukiv.aigamingclubmanagement.entity.BookingStation;
import com.nazarukiv.aigamingclubmanagement.entity.GamingStation;
import com.nazarukiv.aigamingclubmanagement.entity.User;
import com.nazarukiv.aigamingclubmanagement.enums.BookingStatus;
import com.nazarukiv.aigamingclubmanagement.enums.StationStatus;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookingService {

    private final UserRepository users;
    private final GamingStationRepository stations;
    private final BookingRepository bookings;
    private final BookingStationRepository bookingStations;
    private final ActivityLogRepository activityLogs;

    public BookingService(UserRepository users, GamingStationRepository stations,
                          BookingRepository bookings, BookingStationRepository bookingStations,
                          ActivityLogRepository activityLogs) {
        this.users = users;
        this.stations = stations;
        this.bookings = bookings;
        this.bookingStations = bookingStations;
        this.activityLogs = activityLogs;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse createBooking(Long userId, List<Long> stationIds, Instant startTime, Instant endTime) {
        validateId(userId, "User ID");
        validateBookingRequest(stationIds, startTime, endTime);
        User user = users.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        List<Long> sortedIds = stationIds.stream().sorted().toList();
        List<GamingStation> selectedStations = new ArrayList<>();
        // Every creator locks in the same order to avoid deadlocks for multi-station bookings.
        // These database locks remain held until the surrounding transaction commits or rolls back.
        for (Long stationId : sortedIds) {
            GamingStation station = stations.findByIdForUpdate(stationId)
                    .orElseThrow(() -> new StationNotFoundException("Station not found: " + stationId));
            if (station.getStatus() != StationStatus.AVAILABLE) {
                throw new StationUnavailableException("Station is unavailable: " + stationId);
            }
            selectedStations.add(station);
        }

        // READ_COMMITTED lets this query see bookings committed while we waited for a station lock.
        // Strict comparisons implement half-open intervals: adjacent bookings do not overlap.
        if (bookingStations.existsOverlappingBooking(sortedIds, BookingStatus.CONFIRMED, startTime, endTime)) {
            throw new BookingTimeConflictException("A selected station is already booked for this time");
        }

        Booking booking = bookings.save(new Booking(user, startTime, endTime, BookingStatus.CONFIRMED, Instant.now()));
        for (GamingStation station : selectedStations) {
            BookingStation link = bookingStations.save(new BookingStation(booking, station));
            booking.getBookingStations().add(link);
        }
        activityLogs.save(new ActivityLog(user, "BOOKING_CREATED",
                "Booking " + booking.getId() + "; stations " + sortedIds + "; " + startTime + " to " + endTime,
                Instant.now()));
        return toResponse(booking);
    }

    @Transactional
    public BookingResponse cancelBooking(Long bookingId, Long requestingUserId) {
        validateId(bookingId, "Booking ID");
        validateId(requestingUserId, "Requesting user ID");
        Booking booking = bookings.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));
        if (!booking.getUser().getId().equals(requestingUserId)) {
            throw new CancellationNotAllowedException("Only the booking owner may cancel this booking");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new CancellationNotAllowedException("Only CONFIRMED bookings can be cancelled");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        activityLogs.save(new ActivityLog(booking.getUser(), "BOOKING_CANCELLED",
                "Booking " + bookingId + " cancelled", Instant.now()));
        return toResponse(booking);
    }

    public BookingResponse getBookingById(Long bookingId) {
        validateId(bookingId, "Booking ID");
        return toResponse(bookings.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId)));
    }

    public List<BookingResponse> getBookingsByUser(Long userId) {
        validateId(userId, "User ID");
        if (!users.existsById(userId)) {
            throw new UserNotFoundException("User not found: " + userId);
        }
        return bookings.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    private void validateBookingRequest(List<Long> stationIds, Instant startTime, Instant endTime) {
        if (stationIds == null || stationIds.isEmpty()) {
            throw new InvalidBookingRequestException("At least one station must be selected");
        }
        stationIds.forEach(id -> validateId(id, "Station ID"));
        if (new HashSet<>(stationIds).size() != stationIds.size()) {
            throw new InvalidBookingRequestException("Duplicate station IDs are not allowed");
        }
        if (startTime == null || endTime == null) {
            throw new InvalidBookingRequestException("Start time and end time are required");
        }
        if (!startTime.isAfter(Instant.now())) {
            throw new InvalidBookingRequestException("Start time must be in the future");
        }
        if (!endTime.isAfter(startTime)) {
            throw new InvalidBookingRequestException("End time must be after start time");
        }
    }

    private void validateId(Long id, String name) {
        if (id == null || id <= 0) {
            throw new InvalidBookingRequestException(name + " must be a positive number");
        }
    }

    private BookingResponse toResponse(Booking booking) {
        List<Long> stationIds = booking.getBookingStations().stream()
                .map(link -> link.getStation().getId()).sorted().toList();
        return new BookingResponse(booking.getId(), booking.getUser().getId(), stationIds,
                booking.getStartTime(), booking.getEndTime(), booking.getStatus());
    }
}
