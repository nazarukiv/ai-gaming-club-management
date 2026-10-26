package com.nazarukiv.aigamingclubmanagement.dto;

import com.nazarukiv.aigamingclubmanagement.enums.BookingStatus;

import java.time.Instant;
import java.util.List;

public record BookingResponse(Long bookingId, Long userId, List<Long> stationIds,
                              Instant startTime, Instant endTime, BookingStatus status) {

    public BookingResponse {
        stationIds = List.copyOf(stationIds);
    }
}
